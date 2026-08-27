import sys
import struct
import traceback
import numpy as np

# From imports #
from faster_whisper import WhisperModel

MAGIC_REQUEST = 0x46535454
MAGIC_RESPONSE = 0x46535452
VERSION = 1
OP_TRANSCRIBE = 1
OP_PING = 2
OP_SHUTDOWN = 3
STATUS_OK = 0
STATUS_ERROR = 1
EXPECTED_SAMPLE_RATE = 16000 # 16 Khz

def log(message: str):
    # stdout reserved only for binary IPC-comm
    print(message, file = sys.stderr, flush = True)

def read_exact(stream, size: int) -> bytes:
    data = bytearray()
    while len(data) < size:
        chunk = stream.read(size - len(data))
        if not chunk:
            raise EOFError()
        data.extend(chunk)
    return bytes(data)

def send_response(request_id: int, status: int, text: str, language: str = "", duration_seconds: float = 0.0):
    text_bytes = text.encode("utf-8")
    language_bytes = language.encode("utf-8")
    header = struct.pack(">IBBHQdII", MAGIC_RESPONSE, VERSION, status, 0, request_id, duration_seconds,
                         len(language_bytes), len(text_bytes))
    stdout = sys.stdout.buffer
    stdout.write(header)
    stdout.write(language_bytes)
    stdout.write(text_bytes)
    stdout.flush()

def transcribe(model: WhisperModel, request_id: int, sample_rate: int, samples: np.ndarray):
    if sample_rate != EXPECTED_SAMPLE_RATE:
        raise ValueError(f"Unsupported sample rate {sample_rate}. " f"Expected {EXPECTED_SAMPLE_RATE}.")
    duration_seconds = len(samples) / sample_rate
    segments, info = model.transcribe(samples, task = "transcribe", beam_size = 5, no_speech_threshold = 0.6,
                                      language = "es", vad_filter = False)
    # faster-whisper -> iterable/generator
    # Force inference()
    segments = list(segments)
    text = "".join(segment.text for segment in segments).strip()
    send_response(request_id = request_id, status = STATUS_OK, text = text, language = info.language or "",
                  duration_seconds = duration_seconds)

def handle_transcribe(model: WhisperModel, request_id: int):
    stdin = sys.stdin.buffer
    metadata = read_exact(stdin, 16)
    start_timestamp_nanos, sample_rate, sample_count = struct.unpack(">qii", metadata)
    if (sample_count <= 0):
        raise ValueError("Invalid sample count")
    payload_size = sample_count * 4
    payload = read_exact(stdin, payload_size)
    samples = np.frombuffer(payload, dtype = ">f4").astype(np.float32, copy = False)
    transcribe(model, request_id, sample_rate, samples)

def main():
    log("Loading Faster Whisper Model...")
    model = WhisperModel("small", device = "cpu", compute_type = "float32")
    log("Faster Whisper ready")
    stdin = sys.stdin.buffer
    while True:
        try:
            header = read_exact(stdin, 16)
            (magic, version, opcode, _reserved, request_id) = struct.unpack(">IBBHQ", header)
            if magic != MAGIC_REQUEST:
                raise ValueError(f"Invalid magic: {magic:#x}")
            if version != VERSION:
                raise ValueError(f"Unsupported version: {version}")
            if opcode == OP_TRANSCRIBE:
                try:
                    handle_transcribe(model, request_id)
                except Exception as e:
                    log(f"Transcription error: {e}")
                    send_response(request_id = request_id, status = STATUS_ERROR, text = str(e))
            elif opcode == OP_PING:
                send_response(request_id = request_id, status = STATUS_OK, text = "Working...")
            elif opcode == OP_SHUTDOWN:
                send_response(request_id = request_id, status = STATUS_OK, text = "Shutdown in progress...")
                break
            else:
                send_response(request_id = request_id, status = STATUS_ERROR, text = f"Unknown opcode: {opcode}")
        except EOFError:
            break
        except Exception:
            traceback.print_exc(file = sys.stderr)
            break

if __name__ == "__main__":
    main()