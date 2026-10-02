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
    path = Path(__file__).parents[1] / "piper_worker.py"
    spec = importlib.util.spec_from_file_location("piper_worker_under_test", path)
    module = importlib.util.module_from_spec(spec)
    with patch.dict(sys.modules, {"piper": SimpleNamespace(PiperVoice=object)}):
        spec.loader.exec_module(module)
    return module


worker = load_worker()


class BinaryOutput:
    def __init__(self):
        self.buffer = io.BytesIO()


class Voice:
    def __init__(self, chunks):
        self.chunks = chunks

    def synthesize(self, text):
        return iter(self.chunks)


def chunk(rate, samples):
    return SimpleNamespace(sample_rate=rate, audio_float_array=np.asarray(samples, dtype=np.float32))


class PiperWorkerTest(unittest.TestCase):
    def test_handle_synthesize_decodes_utf8_and_trims_text_before_voice_call(self):
        voice = Mock()
        voice.synthesize.return_value = iter([chunk(16_000, [0.25])])
        with patch.object(worker, "send_response") as send, patch.object(worker, "log"):
            worker.handle_synthesize(voice, 9, "  canción  ".encode())
        voice.synthesize.assert_called_once_with("canción")
        self.assertEqual(9, send.call_args.kwargs["request_id"])
        self.assertEqual(worker.STATUS_OK, send.call_args.kwargs["status"])
        self.assertEqual(16_000, send.call_args.kwargs["sample_rate"])
        np.testing.assert_allclose([0.25], send.call_args.kwargs["samples"])

    def test_response_with_no_audio_preserves_utf8_error_and_zero_counts(self):
        output = BinaryOutput()
        with patch.object(sys, "stdout", output):
            worker.send_response(3, worker.STATUS_ERROR, message="síntesis falló")
        packet = output.buffer.getvalue()
        size = struct.calcsize(worker.RESPONSE_HEADER_FORMAT)
        header = struct.unpack(worker.RESPONSE_HEADER_FORMAT, packet[:size])
        self.assertEqual((3, 0, 0, len("síntesis falló".encode())), header[-4:])
        self.assertEqual("síntesis falló".encode(), packet[size:])

    def test_main_returns_error_then_continues_with_ping_and_shutdown(self):
        packet = struct.pack(worker.REQUEST_HEADER_FORMAT, worker.MAGIC_REQUEST, worker.VERSION,
                             worker.OP_SYNTHESIZE, 0, 1, 1) + b" "
        packet += b"".join(struct.pack(worker.REQUEST_HEADER_FORMAT, worker.MAGIC_REQUEST,
                                       worker.VERSION, opcode, 0, request_id, 0)
                            for opcode, request_id in ((worker.OP_PING, 2), (worker.OP_SHUTDOWN, 3)))
        factory = Mock()
        factory.load.return_value.config.sample_rate = 16_000
        with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(packet))), \
                patch.object(worker.os, "name", "posix"), patch.object(worker, "PiperVoice", factory), \
                patch.object(worker, "send_response") as send, patch.object(worker, "log"), \
                patch.object(worker.traceback, "print_exc"):
            worker.main()
        self.assertEqual([1, 2, 3], [call.kwargs["request_id"] for call in send.call_args_list])
        self.assertEqual([worker.STATUS_ERROR, worker.STATUS_OK, worker.STATUS_OK],
                         [call.kwargs["status"] for call in send.call_args_list])
        self.assertIn("cannot be empty", send.call_args_list[0].kwargs["message"])

    def test_read_exact_raises_on_early_eof(self):
        with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(b"abc"))):
            with self.assertRaises(EOFError):
                worker.read_exact(4)

    def test_synthesize_rejects_blank_text_and_empty_audio(self):
        with self.assertRaisesRegex(ValueError, "cannot be empty"):
            worker.synthesize(Voice([]), "  ")
        with self.assertRaisesRegex(RuntimeError, "no audio"):
            worker.synthesize(Voice([]), "hola")

    def test_synthesize_concatenates_chunks_with_same_sample_rate(self):
        rate, audio = worker.synthesize(Voice([
            chunk(22_050, [0.1, 0.2]),
            chunk(22_050, [0.3]),
        ]), " hola ")

        self.assertEqual(22_050, rate)
        np.testing.assert_allclose([0.1, 0.2, 0.3], audio)

    def test_synthesize_rejects_inconsistent_sample_rates(self):
        with self.assertRaisesRegex(RuntimeError, "different sample rates"):
            worker.synthesize(Voice([chunk(22_050, [0.1]), chunk(16_000, [0.2])]), "hola")

    def test_send_response_writes_header_message_and_big_endian_samples(self):
        output = BinaryOutput()
        with patch.object(sys, "stdout", output):
            worker.send_response(9, worker.STATUS_OK, 16_000, np.asarray([0.5], dtype=np.float32), "ok")

        packet = output.buffer.getvalue()
        header_size = struct.calcsize(worker.RESPONSE_HEADER_FORMAT)
        header = struct.unpack(worker.RESPONSE_HEADER_FORMAT, packet[:header_size])
        self.assertEqual((worker.MAGIC_RESPONSE, worker.VERSION, worker.STATUS_OK, 0, 9, 16_000, 1, 2), header)
        self.assertEqual(b"ok", packet[header_size:header_size + 2])
        self.assertEqual(0.5, struct.unpack(">f", packet[-4:])[0])


if __name__ == "__main__":
    unittest.main()
