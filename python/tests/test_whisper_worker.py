import importlib.util
import io
import struct
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

import numpy as np


def load_worker():
    path = Path(__file__).parents[1] / "whisper_worker.py"
    spec = importlib.util.spec_from_file_location("whisper_worker_under_test", path)
    module = importlib.util.module_from_spec(spec)
    # Contract tests do not require the inference package or download a model.
    with patch.dict(sys.modules, {"faster_whisper": SimpleNamespace(WhisperModel=object)}):
        spec.loader.exec_module(module)
    return module


worker = load_worker()


class BinaryOutput:
    def __init__(self):
        self.buffer = io.BytesIO()


class WhisperWorkerTest(unittest.TestCase):
    def test_transcribe_passes_current_spanish_application_recognition_settings(self):
        model = Mock()
        model.transcribe.return_value = (iter([]), SimpleNamespace(language="es"))
        samples = np.zeros(8_000, dtype=np.float32)
        with patch.object(worker, "send_response"):
            worker.transcribe(model, 7, 16_000, samples)

        args, kwargs = model.transcribe.call_args
        self.assertIs(samples, args[0])
        self.assertEqual("transcribe", kwargs["task"])
        self.assertEqual("es", kwargs["language"])
        self.assertEqual(2, kwargs["beam_size"])
        self.assertEqual(0.6, kwargs["no_speech_threshold"])
        self.assertFalse(kwargs["vad_filter"])
        self.assertFalse(kwargs["condition_on_previous_text"])
        for name in ("IntelliJ", "WebStorm", "Topaz", "Tidal", "Spotify", "Firefox"):
            self.assertIn(name, kwargs["initial_prompt"])

    def test_empty_segments_and_missing_language_return_empty_success(self):
        model = Mock()
        model.transcribe.return_value = (iter([]), SimpleNamespace(language=None))
        with patch.object(worker, "send_response") as send:
            worker.transcribe(model, 8, 16_000, np.zeros(160, dtype=np.float32))
        send.assert_called_once_with(request_id=8, status=worker.STATUS_OK, text="",
                                     language="", duration_seconds=0.01)

    def test_handle_transcribe_decodes_big_endian_samples_and_forwards_request_id(self):
        packet = struct.pack(">qii", 123, 16_000, 2) + struct.pack(">ff", 0.25, -0.5)
        model = object()
        with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(packet))), \
                patch.object(worker, "transcribe") as transcribe:
            worker.handle_transcribe(model, 99)
        args = transcribe.call_args.args
        self.assertEqual((model, 99, 16_000), args[:3])
        self.assertEqual(np.float32, args[3].dtype)
        np.testing.assert_allclose([0.25, -0.5], args[3])

    def test_handle_transcribe_rejects_invalid_sample_count_and_truncated_audio(self):
        for count in (0, -1):
            with self.subTest(count=count), \
                    patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(struct.pack(">qii", 0, 16_000, count)))), \
                    patch.object(worker, "transcribe") as transcribe:
                with self.assertRaisesRegex(ValueError, "Invalid sample count"):
                    worker.handle_transcribe(object(), 1)
                transcribe.assert_not_called()
        truncated = struct.pack(">qii", 0, 16_000, 2) + struct.pack(">f", 0.5)
        with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(truncated))):
            with self.assertRaises(EOFError):
                worker.handle_transcribe(object(), 1)

    def test_main_handles_ping_unknown_opcode_and_shutdown_without_loading_real_model(self):
        packet = b"".join(struct.pack(">IBBHQ", worker.MAGIC_REQUEST, worker.VERSION, opcode, 0, request_id)
                          for opcode, request_id in ((worker.OP_PING, 1), (99, 2), (worker.OP_SHUTDOWN, 3)))
        with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(packet))), \
                patch.object(worker, "WhisperModel", Mock()) as factory, \
                patch.object(worker, "send_response") as send, patch.object(worker, "log"):
            worker.main()
        factory.assert_called_once_with("large-v3-turbo", device="cpu", compute_type="int8_float32", cpu_threads=12)
        self.assertEqual([1, 2, 3], [call.kwargs["request_id"] for call in send.call_args_list])
        self.assertEqual([worker.STATUS_OK, worker.STATUS_ERROR, worker.STATUS_OK],
                         [call.kwargs["status"] for call in send.call_args_list])

    def test_transcription_failure_does_not_prevent_next_request(self):
        packet = struct.pack(">IBBHQqii", worker.MAGIC_REQUEST, worker.VERSION, worker.OP_TRANSCRIBE, 0, 1, 0, 8000, 1)
        packet += struct.pack(">f", 0.5)
        packet += struct.pack(">IBBHQ", worker.MAGIC_REQUEST, worker.VERSION, worker.OP_PING, 0, 2)
        with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(packet))), \
                patch.object(worker, "WhisperModel", Mock()), \
                patch.object(worker, "send_response") as send, patch.object(worker, "log"):
            worker.main()
        self.assertEqual([worker.STATUS_ERROR, worker.STATUS_OK],
                         [call.kwargs["status"] for call in send.call_args_list])
        self.assertIn("Unsupported sample rate", send.call_args_list[0].kwargs["text"])

    def test_response_lengths_count_utf8_bytes(self):
        output = BinaryOutput()
        with patch.object(sys, "stdout", output):
            worker.send_response(42, worker.STATUS_OK, "canción", "español", 1.5)
        packet = output.buffer.getvalue()
        size = struct.calcsize(">IBBHQdII")
        header = struct.unpack(">IBBHQdII", packet[:size])
        self.assertEqual((len("español".encode()), len("canción".encode())), header[-2:])
        self.assertEqual("españolcanción".encode(), packet[size:])

    def test_read_exact_combines_partial_reads(self):
        stream = io.BufferedReader(io.BytesIO(b"abcdef"), buffer_size=2)

        self.assertEqual(b"abcdef", worker.read_exact(stream, 6))

    def test_read_exact_raises_on_early_eof(self):
        with self.assertRaises(EOFError):
            worker.read_exact(io.BytesIO(b"abc"), 4)

    def test_send_response_writes_big_endian_protocol_packet(self):
        output = BinaryOutput()
        with patch.object(sys, "stdout", output):
            worker.send_response(42, worker.STATUS_OK, "hola", "es", 1.5)

        packet = output.buffer.getvalue()
        header_size = struct.calcsize(">IBBHQdII")
        header = struct.unpack(">IBBHQdII", packet[:header_size])
        self.assertEqual((worker.MAGIC_RESPONSE, worker.VERSION, worker.STATUS_OK, 0, 42, 1.5, 2, 4), header)
        self.assertEqual(b"eshola", packet[header_size:])

    def test_transcribe_rejects_unsupported_sample_rate(self):
        with self.assertRaisesRegex(ValueError, "Unsupported sample rate"):
            worker.transcribe(object(), 1, 8_000, np.zeros(10, dtype=np.float32))

    def test_transcribe_joins_segments_and_reports_duration(self):
        class Model:
            def transcribe(self, samples, **kwargs):
                return iter([SimpleNamespace(text=" hola"), SimpleNamespace(text=" mundo ")]), SimpleNamespace(language="es")

        with patch.object(worker, "send_response") as send:
            worker.transcribe(Model(), 7, 16_000, np.zeros(8_000, dtype=np.float32))

        send.assert_called_once_with(
            request_id=7,
            status=worker.STATUS_OK,
            text="hola mundo",
            language="es",
            duration_seconds=0.5,
        )


if __name__ == "__main__":
    unittest.main()
