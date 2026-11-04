package com.fuad.tts;

import com.fuad.tts.piper.PiperTtsEngine;
import com.fuad.tts.supertonic.SupertonicTtsEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TtsSettingsTest {
    private static TtsSettings settings(Map<String, String> properties) {
        return TtsSettings.from(properties::get);
    }

    @Test
    void shouldDefaultToSupertonicSpanishWithPiperFallback() {
        TtsSettings settings = settings(Map.of());

        assertEquals(TtsSettings.Engine.SUPERTONIC, settings.engine());
        assertTrue(settings.fallbackToPiper());
        assertEquals(TtsSettings.Supertonic.defaults(), settings.supertonic());
        assertEquals("es", settings.supertonic().lang());
        assertEquals("models/supertonic-3", settings.supertonic().modelDir());
        assertEquals(0.88, settings.supertonic().speed());
        assertEquals(0.40, settings.supertonic().silenceDuration());
        assertEquals(100, settings.supertonic().chunkLength());
    }

    @Test
    void shouldReadEveryOverride() {
        TtsSettings settings = settings(Map.of(
                "ares.tts.engine", "PIPER",
                "ares.tts.fallback", "false",
                "ares.tts.supertonic.model-dir", "other/dir",
                "ares.tts.supertonic.voice", "F2",
                "ares.tts.supertonic.lang", "pt",
                "ares.tts.supertonic.speed", "0.9",
                "ares.tts.supertonic.steps", "12",
                "ares.tts.supertonic.silence", "0.5",
                "ares.tts.supertonic.chunk-length", "80"));

        assertEquals(TtsSettings.Engine.PIPER, settings.engine());
        assertFalse(settings.fallbackToPiper());
        assertEquals(new TtsSettings.Supertonic("other/dir", "F2", "pt", 0.9, 12, 0.5, 80), settings.supertonic());
    }

    @Test
    void invalidValuesShouldFallBackToDefaultsInsteadOfBreakingStartup() {
        TtsSettings settings = settings(Map.of(
                "ares.tts.engine", "festival",
                "ares.tts.supertonic.speed", "fast",
                "ares.tts.supertonic.steps", "0",
                "ares.tts.supertonic.voice", "  ",
                "ares.tts.supertonic.silence", "-0.2",
                "ares.tts.supertonic.chunk-length", "-10"));

        assertEquals(TtsSettings.Engine.SUPERTONIC, settings.engine());
        assertEquals(TtsSettings.Supertonic.defaults(), settings.supertonic());
    }

    @Test
    void negativeOrNonFiniteSpeedShouldUseTheDefault() {
        assertEquals(TtsSettings.Supertonic.DEFAULT_SPEED,
                settings(Map.of("ares.tts.supertonic.speed", "-1")).supertonic().speed());
        assertEquals(TtsSettings.Supertonic.DEFAULT_SPEED,
                settings(Map.of("ares.tts.supertonic.speed", "NaN")).supertonic().speed());
    }

    @Test
    void factoryShouldBuildTheConfiguredEngine() {
        assertInstanceOf(FallbackTtsEngine.class, TtsEngineFactory.create(settings(Map.of())));
        assertInstanceOf(SupertonicTtsEngine.class,
                TtsEngineFactory.create(settings(Map.of("ares.tts.fallback", "false"))));
        assertInstanceOf(PiperTtsEngine.class,
                TtsEngineFactory.create(settings(Map.of("ares.tts.engine", "piper"))));
    }
}
