import importlib.util
import io
import struct
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import numpy as np


def load_worker():
    path = Path(__file__).parents[1] / "piper_worker.py"
    spec = importlib.util.spec_from_file_location("piper_worker_under_test", path)
    module = importlib.util.module_from_spec(spec)
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
