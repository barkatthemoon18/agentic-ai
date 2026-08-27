package com.fuad.config;

import java.util.List;

public class AppConfig {
    public static final double WAKE_HIGH_THRESHOLD = 0.85;
    public static final double WAKE_LOW_THRESHOLD = 0.55;
    public static final float VAD_THRESHOLD = 0.5f;
    public static final String SILERO_MODEL_PATH = "models/silero_vad.onnx";
    public static final int MAGIC_REQUEST = 0x46535454; /* "FSTT" */
    public static final int MAGIC_RESPONSE = 0x46535452; /* *FSTR" */
    public static final byte VERSION = 1;
    public static final byte OP_TRANSCRIBE = 1;
    public static final byte OP_PING = 2;
    public static final byte OP_SHUTDOWN = 3;
    public static final List<String> wakeWords = List.of("Ares", "oye ares");
    public static final List<String> intentPhrases = List.of("puedes revisar", "necesito que", "ayúdame con",
            "podrías buscar", "puedes buscar");

    private AppConfig() {
        /* Empty intentionally */
    }
}
