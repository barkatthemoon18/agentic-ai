"""Supertonic 3 TTS worker.

Speaks the same binary IPC protocol as ``piper_worker.py`` so the JVM can use a
single client for both engines. Stdout is reserved for protocol frames; anything
a library prints is redirected to stderr.
"""
import argparse
import os
import struct
import sys
import traceback

import numpy as np

from supertonic import TTS

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

MODEL_NAME = "supertonic-3"
DEFAULT_MODEL_DIR = os.path.join("models", "supertonic-3")
DEFAULT_VOICE = "M3"
DEFAULT_LANG = "es"
# Parsimonious, calm prose cadence (0.88 is unhurried and clear; 1.05 is the upstream rushed speed).
DEFAULT_SPEED = 0.88
DEFAULT_STEPS = 8
# Pauses between sentences / clauses (seconds).
DEFAULT_SILENCE_DURATION = 0.40
# Maximum characters per chunk before splitting into sentences to insert natural breathing pauses.
DEFAULT_CHUNK_LENGTH = 100
WARMUP_TEXT = "Hola."

# Real protocol channel; set by main() before sys.stdout is redirected to stderr.
_protocol_out = None


def log(message):
    print(f"[Supertonic] {message}", file=sys.stderr, flush=True)


def read_exact(length):
    data = bytearray()
    while len(data) < length:
        chunk = sys.stdin.buffer.read(length - len(data))
        if not chunk:
            raise EOFError("Supertonic IPC stream closed")
        data.extend(chunk)
    return bytes(data)


def send_response(request_id, status, sample_rate=0, samples=None, message=""):
    if samples is None:
        samples = np.empty(0, dtype=np.float32)
    samples = np.asarray(samples, dtype=np.float32)
    message_bytes = message.encode("utf-8")
    header = struct.pack(RESPONSE_HEADER_FORMAT, MAGIC_RESPONSE, VERSION, status, 0, request_id, sample_rate,
                         len(samples), len(message_bytes))
    out = (_protocol_out or sys.stdout).buffer
    out.write(header)
    if message_bytes:
        out.write(message_bytes)
    if len(samples) > 0:
        out.write(samples.astype(">f4", copy=False).tobytes())
    out.flush()


class SupertonicVoice:
    """Loaded model plus fixed synthesis settings."""

    def __init__(self, tts, style, lang, speed, steps, silence_duration=DEFAULT_SILENCE_DURATION,
                 chunk_length=DEFAULT_CHUNK_LENGTH):
        self.tts = tts
        self.style = style
        self.lang = lang
        self.speed = speed
        self.steps = steps
        self.silence_duration = silence_duration
        self.chunk_length = chunk_length

    @property
    def sample_rate(self):
        return int(self.tts.sample_rate)

    @property
    def execution_providers(self):
        vocoder = getattr(getattr(self.tts, "model", None), "vocoder_ort", None)
        if vocoder and hasattr(vocoder, "get_providers"):
            return vocoder.get_providers()
        return ["CPUExecutionProvider"]

    def synthesize(self, text):
        wav, _duration = self.tts.synthesize(
            text,
            voice_style=self.style,
            lang=self.lang,
            speed=self.speed,
            total_steps=self.steps,
            silence_duration=self.silence_duration,
            max_chunk_length=self.chunk_length,
        )
        return np.asarray(wav, dtype=np.float32).reshape(-1)


def load_voice(model_dir, voice, lang, speed, steps, silence_duration=DEFAULT_SILENCE_DURATION,
               chunk_length=DEFAULT_CHUNK_LENGTH):
    # auto_download=False: a missing model must fail fast so the JVM can fall back to Piper.
    tts = TTS(model=MODEL_NAME, model_dir=model_dir, auto_download=False)
    return SupertonicVoice(tts, tts.get_voice_style(voice_name=voice), lang, speed, steps,
                           silence_duration, chunk_length)


def synthesize(voice, text):
    text = text.strip()
    if not text:
        raise ValueError("TTS text cannot be empty")
    audio = voice.synthesize(text)
    if audio.size == 0:
        raise RuntimeError("Supertonic produced no audio")
    if not np.all(np.isfinite(audio)):
        raise RuntimeError("Supertonic produced invalid audio samples")
    return voice.sample_rate, np.clip(audio, -1.0, 1.0)


def handle_synthesize(voice, request_id, payload):
    text = payload.decode("utf-8")
    log(f"Synthesizing request {request_id}: {text[:80]!r}")
    sample_rate, audio = synthesize(voice, text)
    duration = len(audio) / sample_rate
    log(f"Generated {len(audio)} samples @ {sample_rate} Hz ({duration:.2f}s)")
    send_response(request_id=request_id, status=STATUS_OK, sample_rate=sample_rate, samples=audio)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Supertonic 3 TTS worker")
    parser.add_argument("--model-dir", default=DEFAULT_MODEL_DIR)
    parser.add_argument("--voice", default=DEFAULT_VOICE)
    parser.add_argument("--lang", default=DEFAULT_LANG)
    parser.add_argument("--speed", type=float, default=DEFAULT_SPEED)
    parser.add_argument("--steps", type=int, default=DEFAULT_STEPS)
    parser.add_argument("--silence-duration", type=float, default=DEFAULT_SILENCE_DURATION)
    parser.add_argument("--chunk-length", type=int, default=DEFAULT_CHUNK_LENGTH)
    return parser.parse_args(argv)


def main(argv=None):
    global _protocol_out
    args = parse_args(argv)
    # Important on Windows when streams come from ProcessBuilder pipes.
    if os.name == "nt":
        import msvcrt
        msvcrt.setmode(sys.stdin.fileno(), os.O_BINARY)
        msvcrt.setmode(sys.stdout.fileno(), os.O_BINARY)
    _protocol_out = sys.stdout
    sys.stdout = sys.stderr
    log(f"Loading Supertonic: dir={args.model_dir} voice={args.voice} lang={args.lang} "
        f"speed={args.speed} steps={args.steps} silence={args.silence_duration} chunk={args.chunk_length}")
    voice = load_voice(args.model_dir, args.voice, args.lang, args.speed, args.steps,
                       args.silence_duration, args.chunk_length)
    # Warm-up so the first real request does not pay ONNX session initialisation.
    synthesize(voice, WARMUP_TEXT)
    log(f"Supertonic ready | sampleRate={voice.sample_rate} | providers={voice.execution_providers}")
    while True:
        request_id = 0
        try:
            header = read_exact(REQUEST_HEADER_SIZE)
            (magic, version, opcode, _reserved, request_id, payload_length) = struct.unpack(REQUEST_HEADER_FORMAT,
                                                                                            header)
            if magic != MAGIC_REQUEST:
                raise ValueError(f"Invalid request magic: 0x{magic:08X}")
            if version != VERSION:
                raise ValueError(f"Unsupported protocol version: {version}")
            payload = read_exact(payload_length) if payload_length > 0 else b""
            if opcode == OP_PING:
                send_response(request_id=request_id, status=STATUS_OK, message="Working...")
            elif opcode == OP_SYNTHESIZE:
                handle_synthesize(voice, request_id, payload)
            elif opcode == OP_SHUTDOWN:
                log("Shutdown requested")
                send_response(request_id=request_id, status=STATUS_OK, message="Shutdown")
                break
            else:
                send_response(request_id=request_id, status=STATUS_ERROR, message=f"Unknown opcode: {opcode}")
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
    log("Supertonic worker stopped")


if __name__ == "__main__":
    main()
