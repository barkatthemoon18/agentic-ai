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
    path = Path(__file__).parents[1] / "whisper_worker.py"
    spec = importlib.util.spec_from_file_location("whisper_worker_under_test", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


worker = load_worker()


class BinaryOutput:
    def __init__(self):
        self.buffer = io.BytesIO()


class WhisperWorkerTest(unittest.TestCase):
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
