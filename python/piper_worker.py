import os
import sys
import struct
import traceback
import numpy as np

from piper import PiperVoice

MAGIC_REQUEST = 0x50545453   # "PTTS"
MAGIC_RESPONSE = 0x50545452  # "PTTR"

VERSION = 1

OP_SYNTHESIZE = 1
OP_PING = 2
OP_SHUTDOWN = 3

STATUS_OK = 0
STATUS_ERROR = 1

REQUEST_HEADER_FORMAT = ">IBBHQI"
REQUEST_HEADER_SIZE = struct.calcsize(REQUEST_HEADER_FORMAT)
RESPONSE_HEADER_FORMAT = ">IBBHQIII"
MODEL_PATH = os.path.join("models", "es_MX-claude-high.onnx")

def log(message):
    print(f"[Piper] {message}", file=sys.stderr, flush=True)

def read_exact(length):
    data = bytearray()
    while len(data) < length:
        chunk = sys.stdin.buffer.read(length - len(data))
        if not chunk:
            raise EOFError("Piper IPC stream closed")
        data.extend(chunk)
    return bytes(data)

def send_response(request_id, status, sample_rate=0, samples=None,
                  message=""):
    if samples is None:
        samples = np.empty(0, dtype=np.float32)
    samples = np.asarray(samples, dtype=np.float32)
    message_bytes = message.encode("utf-8")
    header = struct.pack(RESPONSE_HEADER_FORMAT, MAGIC_RESPONSE, VERSION, status, 0, request_id, sample_rate, len(samples),
                         len(message_bytes))
    sys.stdout.buffer.write(header)
    if message_bytes:
        sys.stdout.buffer.write(message_bytes)
    if len(samples) > 0:
        audio_bytes = samples.astype(">f4", copy=False).tobytes()
        sys.stdout.buffer.write(audio_bytes)
    sys.stdout.buffer.flush()

def synthesize(voice, text):
    text = text.strip()
    if not text:
        raise ValueError("TTS text cannot be empty")
    chunks = []
    sample_rate = None
    for chunk in voice.synthesize(text):
        if sample_rate is None:
            sample_rate = chunk.sample_rate
        elif sample_rate != chunk.sample_rate:
            raise RuntimeError("Piper returned chunks with ""different sample rates")
        chunks.append(np.asarray(chunk.audio_float_array, dtype=np.float32))
    if not chunks:
        raise RuntimeError("Piper produced no audio")
    if len(chunks) == 1:
        audio = chunks[0]
    else:
        audio = np.concatenate(chunks)
    return sample_rate, audio

def handle_synthesize(voice, request_id, payload):
    text = payload.decode("utf-8")
    log(f"Synthesizing request "f"{request_id}: {text[:80]!r}")
    sample_rate, audio = synthesize(voice, text)
    duration = (len(audio) / sample_rate)
    log(f"Generated {len(audio)} samples "f"@ {sample_rate} Hz "f"({duration:.2f}s)")
    send_response(request_id=request_id, status=STATUS_OK, sample_rate=sample_rate, samples=audio)

def main():
    # Important on Windows when streams come
    # from ProcessBuilder pipes.
    if os.name == "nt":
        import msvcrt
        msvcrt.setmode(sys.stdin.fileno(), os.O_BINARY)
        msvcrt.setmode(sys.stdout.fileno(), os.O_BINARY)
    log(f"Loading Piper voice: "f"{MODEL_PATH}")
    voice = PiperVoice.load(MODEL_PATH, use_cuda=False)
    log(f"Piper ready | "f"sampleRate={voice.config.sample_rate}")
    while True:
        try:
            header = read_exact(REQUEST_HEADER_SIZE)
            (magic, version, opcode, _reserved, request_id, payload_length) = struct.unpack(REQUEST_HEADER_FORMAT,header)
            if magic != MAGIC_REQUEST:
                raise ValueError(f"Invalid request magic: "f"0x{magic:08X}")
            if version != VERSION:
                raise ValueError(f"Unsupported protocol "f"version: {version}")
            payload = (read_exact(payload_length)
                if payload_length > 0
                else b"")
            if opcode == OP_PING:
                send_response(request_id=request_id, status=STATUS_OK, message="Working...")
            elif opcode == OP_SYNTHESIZE:
                handle_synthesize(voice, request_id, payload)
            elif opcode == OP_SHUTDOWN:
                log("Shutdown requested")
                send_response(request_id=request_id, status=STATUS_OK, message="Shutdown")
                break
            else:
                send_response(request_id=request_id, status=STATUS_ERROR, message=(f"Unknown opcode: "f"{opcode}"))
        except EOFError:
            log("IPC stream closed")
            break
        except Exception as error:
            log(f"Request failed: {error}")
            traceback.print_exc(file=sys.stderr)
            try:
                send_response(request_id=request_id, status=STATUS_ERROR, message=str(error))
            except Exception:
                break
    log("Piper worker stopped")

if __name__ == "__main__":
    main()