package com.fuad.tts;

import com.fuad.tts.piper.PiperClient;
import com.fuad.tts.piper.PiperTtsEngine;
import com.fuad.tts.supertonic.SupertonicClient;
import com.fuad.tts.supertonic.SupertonicTtsEngine;

/** Builds the configured TTS engine: Supertonic by default, with Piper as automatic fallback. */
public final class TtsEngineFactory {
    private TtsEngineFactory() {
        /* Empty intentionally */
    }

    public static TtsEngine create(TtsSettings settings) {
        if (settings.engine() == TtsSettings.Engine.PIPER) {
            return new PiperTtsEngine(new PiperClient());
        }
        TtsEngine supertonic = new SupertonicTtsEngine(new SupertonicClient(settings.supertonic()));
        if (!settings.fallbackToPiper()) {
            return supertonic;
        }
        return new FallbackTtsEngine(supertonic, "Supertonic", new PiperTtsEngine(new PiperClient()), "Piper");
    }
}
