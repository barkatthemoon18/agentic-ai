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
    path = Path(__file__).parents[1] / "supertonic_worker.py"
    spec = importlib.util.spec_from_file_location("supertonic_worker_under_test", path)
    module = importlib.util.module_from_spec(spec)
    with patch.dict(sys.modules, {"supertonic": SimpleNamespace(TTS=object)}):
        spec.loader.exec_module(module)
    return module


worker = load_worker()


class BinaryOutput:
    def __init__(self):
        self.buffer = io.BytesIO()


class Voice:
    sample_rate = 44_100
    execution_providers = ["CPUExecutionProvider"]

    def __init__(self, audio):
        self.audio = np.asarray(audio, dtype=np.float32)
        self.texts = []

    def synthesize(self, text):
        self.texts.append(text)
        return self.audio


class SupertonicWorkerTest(unittest.TestCase):
    def test_synthesize_trims_text_and_returns_sample_rate_and_audio(self):
        voice = Voice([0.1, -0.2])
        rate, audio = worker.synthesize(voice, "  canción  ")
        self.assertEqual(["canción"], voice.texts)
        self.assertEqual(44_100, rate)
        np.testing.assert_allclose([0.1, -0.2], audio)

    def test_synthesize_rejects_blank_text_empty_and_invalid_audio(self):
        with self.assertRaisesRegex(ValueError, "cannot be empty"):
            worker.synthesize(Voice([0.1]), "  ")
        with self.assertRaisesRegex(RuntimeError, "no audio"):
            worker.synthesize(Voice([]), "hola")
        with self.assertRaisesRegex(RuntimeError, "invalid"):
            worker.synthesize(Voice([0.1, np.nan]), "hola")
        with self.assertRaisesRegex(RuntimeError, "invalid"):
            worker.synthesize(Voice([np.inf]), "hola")

    def test_synthesize_clips_samples_to_unit_range(self):
        _, audio = worker.synthesize(Voice([1.5, -2.0, 0.5]), "hola")
        np.testing.assert_allclose([1.0, -1.0, 0.5], audio)

    def test_supertonic_voice_flattens_batch_output_and_forwards_settings(self):
        tts = Mock(sample_rate=44_100)
        tts.synthesize.return_value = (np.asarray([[0.1, 0.2]], dtype=np.float32), np.asarray([0.5]))
        voice = worker.SupertonicVoice(tts, "style", "es", 0.88, 6, 0.40, 100)

        audio = voice.synthesize("hola")

        self.assertEqual((2,), audio.shape)
        tts.synthesize.assert_called_once_with(
            "hola", voice_style="style", lang="es", speed=0.88, total_steps=6,
            silence_duration=0.40, max_chunk_length=100
        )
        self.assertEqual(44_100, voice.sample_rate)

    def test_load_voice_never_downloads_models(self):
        factory = Mock()
        factory.return_value.get_voice_style.return_value = "style"
        with patch.object(worker, "TTS", factory):
            voice = worker.load_voice("models/x", "M3", "es", 0.88, 8, 0.40, 100)
        factory.assert_called_once_with(model="supertonic-3", model_dir="models/x", auto_download=False)
        factory.return_value.get_voice_style.assert_called_once_with(voice_name="M3")
        self.assertEqual("style", voice.style)

    def test_parse_args_defaults_to_spanish_m3_and_parsimonious_cadence(self):
        args = worker.parse_args([])
        self.assertEqual((worker.DEFAULT_MODEL_DIR, "M3", "es", 0.88, 8, 0.40, 100),
                         (args.model_dir, args.voice, args.lang, args.speed, args.steps,
                          args.silence_duration, args.chunk_length))
        args = worker.parse_args(["--voice", "F1", "--steps", "4", "--speed", "0.92",
                                  "--silence-duration", "0.5", "--chunk-length", "80"])
        self.assertEqual(("F1", 4, 0.92, 0.5, 80),
                         (args.voice, args.steps, args.speed, args.silence_duration, args.chunk_length))

    def test_send_response_writes_header_message_and_big_endian_samples(self):
        output = BinaryOutput()
        with patch.object(sys, "stdout", output), patch.object(worker, "_protocol_out", None):
            worker.send_response(9, worker.STATUS_OK, 44_100, np.asarray([0.5], dtype=np.float32), "ok")

        packet = output.buffer.getvalue()
        header_size = struct.calcsize(worker.RESPONSE_HEADER_FORMAT)
        header = struct.unpack(worker.RESPONSE_HEADER_FORMAT, packet[:header_size])
        self.assertEqual((worker.MAGIC_RESPONSE, worker.VERSION, worker.STATUS_OK, 0, 9, 44_100, 1, 2), header)
        self.assertEqual(b"ok", packet[header_size:header_size + 2])
        self.assertEqual(0.5, struct.unpack(">f", packet[-4:])[0])

    def test_read_exact_raises_on_early_eof(self):
        with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(b"abc"))):
            with self.assertRaises(EOFError):
                worker.read_exact(4)

    def test_main_warms_up_reports_errors_then_continues_with_ping_and_shutdown(self):
        packet = struct.pack(worker.REQUEST_HEADER_FORMAT, worker.MAGIC_REQUEST, worker.VERSION,
                             worker.OP_SYNTHESIZE, 0, 1, 1) + b" "
        packet += b"".join(struct.pack(worker.REQUEST_HEADER_FORMAT, worker.MAGIC_REQUEST,
                                       worker.VERSION, opcode, 0, request_id, 0)
                           for opcode, request_id in ((worker.OP_PING, 2), (worker.OP_SHUTDOWN, 3)))
        voice = Voice([0.1])
        out = BinaryOutput()
        saved_stdout = sys.stdout
        try:
            with patch.object(sys, "stdin", SimpleNamespace(buffer=io.BytesIO(packet))), \
                    patch.object(sys, "stdout", out), \
                    patch.object(worker.os, "name", "posix"), \
                    patch.object(worker, "load_voice", return_value=voice), \
                    patch.object(worker, "send_response") as send, patch.object(worker, "log"), \
                    patch.object(worker.traceback, "print_exc"), patch.object(worker, "_protocol_out", None):
                worker.main([])
                self.assertIs(sys.stderr, sys.stdout, "library prints must not corrupt the protocol channel")
        finally:
            sys.stdout = saved_stdout
        self.assertEqual(["Hola."], voice.texts, "Only the warm-up reached the model; blank text was rejected")
        self.assertEqual([1, 2, 3], [call.kwargs["request_id"] for call in send.call_args_list])
        self.assertEqual([worker.STATUS_ERROR, worker.STATUS_OK, worker.STATUS_OK],
                         [call.kwargs["status"] for call in send.call_args_list])
        self.assertIn("cannot be empty", send.call_args_list[0].kwargs["message"])


if __name__ == "__main__":
    unittest.main()
