package com.fuad.tts;

import java.util.function.Function;

/**
 * TTS configuration, read from JVM system properties (same convention as {@code AppConfig}).
 *
 * <ul>
 *   <li>{@code -Dares.tts.engine=supertonic|piper} (default {@code supertonic})</li>
 *   <li>{@code -Dares.tts.fallback=true|false} fall back to Piper when Supertonic fails (default {@code true})</li>
 *   <li>{@code -Dares.tts.supertonic.model-dir=models/supertonic-3}</li>
 *   <li>{@code -Dares.tts.supertonic.voice=M3} (M1-M5, F1-F5)</li>
 *   <li>{@code -Dares.tts.supertonic.lang=es}</li>
 *   <li>{@code -Dares.tts.supertonic.speed=0.88} (parsimonious, calm pace; 1.05 was upstream fast speed)</li>
 *   <li>{@code -Dares.tts.supertonic.steps=8} (more steps: higher quality, slower)</li>
 *   <li>{@code -Dares.tts.supertonic.silence=0.40} (seconds of breathing silence between sentences)</li>
 *   <li>{@code -Dares.tts.supertonic.chunk-length=100} (maximum characters before sentence chunking)</li>
 * </ul>
 * Invalid values are reported and replaced by the default so a typo never prevents startup.
 */
public record TtsSettings(Engine engine, boolean fallbackToPiper, Supertonic supertonic) {

    public enum Engine { SUPERTONIC, PIPER }

    public record Supertonic(String modelDir, String voice, String lang, double speed, int steps,
                             double silenceDuration, int chunkLength) {
        public static final String DEFAULT_MODEL_DIR = "models/supertonic-3";
        public static final String DEFAULT_VOICE = "M3";
        public static final String DEFAULT_LANG = "es";
        public static final double DEFAULT_SPEED = 0.88;
        public static final int DEFAULT_STEPS = 8;
        public static final double DEFAULT_SILENCE_DURATION = 0.40;
        public static final int DEFAULT_CHUNK_LENGTH = 100;

        public Supertonic {
            if (modelDir == null || modelDir.isBlank() || voice == null || voice.isBlank()
                    || lang == null || lang.isBlank() || !(speed > 0) || steps <= 0
                    || !(silenceDuration >= 0) || chunkLength <= 0) {
                throw new IllegalArgumentException("Invalid Supertonic settings");
            }
        }

        public Supertonic(String modelDir, String voice, String lang, double speed, int steps) {
            this(modelDir, voice, lang, speed, steps, DEFAULT_SILENCE_DURATION, DEFAULT_CHUNK_LENGTH);
        }

        public static Supertonic defaults() {
            return new Supertonic(DEFAULT_MODEL_DIR, DEFAULT_VOICE, DEFAULT_LANG, DEFAULT_SPEED, DEFAULT_STEPS,
                    DEFAULT_SILENCE_DURATION, DEFAULT_CHUNK_LENGTH);
        }
    }

    public TtsSettings {
        if (engine == null || supertonic == null) {
            throw new IllegalArgumentException("Invalid TTS settings");
        }
    }

    public static TtsSettings fromSystemProperties() {
        return from(System::getProperty);
    }

    public static TtsSettings from(Function<String, String> property) {
        Engine engine = switch (text(property, "ares.tts.engine", "supertonic").toLowerCase()) {
            case "supertonic" -> Engine.SUPERTONIC;
            case "piper" -> Engine.PIPER;
            default -> {
                warn("ares.tts.engine", property.apply("ares.tts.engine"), "supertonic");
                yield Engine.SUPERTONIC;
            }
        };
        boolean fallback = !"false".equalsIgnoreCase(text(property, "ares.tts.fallback", "true"));
        Supertonic defaults = Supertonic.defaults();
        double speed = number(property, "ares.tts.supertonic.speed", defaults.speed());
        int steps = (int) number(property, "ares.tts.supertonic.steps", defaults.steps());
        double silence = number(property, "ares.tts.supertonic.silence", defaults.silenceDuration());
        int chunkLength = (int) number(property, "ares.tts.supertonic.chunk-length", defaults.chunkLength());
        if (!(speed > 0)) {
            warn("ares.tts.supertonic.speed", String.valueOf(speed), String.valueOf(defaults.speed()));
            speed = defaults.speed();
        }
        if (steps <= 0) {
            warn("ares.tts.supertonic.steps", String.valueOf(steps), String.valueOf(defaults.steps()));
            steps = defaults.steps();
        }
        if (!(silence >= 0)) {
            warn("ares.tts.supertonic.silence", String.valueOf(silence), String.valueOf(defaults.silenceDuration()));
            silence = defaults.silenceDuration();
        }
        if (chunkLength <= 0) {
            warn("ares.tts.supertonic.chunk-length", String.valueOf(chunkLength), String.valueOf(defaults.chunkLength()));
            chunkLength = defaults.chunkLength();
        }
        return new TtsSettings(engine, fallback, new Supertonic(
                text(property, "ares.tts.supertonic.model-dir", defaults.modelDir()),
                text(property, "ares.tts.supertonic.voice", defaults.voice()),
                text(property, "ares.tts.supertonic.lang", defaults.lang()),
                speed, steps, silence, chunkLength));
    }

    private static String text(Function<String, String> property, String key, String fallback) {
        String value = property.apply(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static double number(Function<String, String> property, String key, double fallback) {
        String value = property.apply(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        }
        catch (NumberFormatException e) {
            warn(key, value, String.valueOf(fallback));
            return fallback;
        }
    }

    private static void warn(String key, String value, String fallback) {
        System.err.println("Invalid TTS setting " + key + "=" + value + "; using " + fallback);
    }
}
