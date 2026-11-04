package com.fuad.tts.supertonic;

import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SupertonicAdapterTest {
    @Test
    void workerCommandShouldCarryEverySetting() {
        TtsSettings.Supertonic settings = new TtsSettings.Supertonic("models/x", "F3", "es", 0.9, 6, 0.45, 90);

        assertEquals(List.of("python", "python/supertonic_worker.py", "--model-dir", "models/x", "--voice", "F3",
                "--lang", "es", "--speed", "0.9", "--steps", "6", "--silence-duration", "0.45",
                "--chunk-length", "90"), SupertonicClient.command(settings));
    }

    @Test
    void clientShouldRejectOperationsBeforeStartAndCloseSafely() {
        SupertonicClient client = new SupertonicClient(TtsSettings.Supertonic.defaults());

        assertThrows(IllegalStateException.class, () -> client.synthesize("hola"));
        assertThrows(IllegalStateException.class, client::ping);
        assertFalse(client.isAlive());
        assertDoesNotThrow(client::close);
    }

    @Test
    void engineShouldDelegateStartSynthesisAndClose() throws Exception {
        TrackingClient client = new TrackingClient();
        SupertonicTtsEngine engine = new SupertonicTtsEngine(client);

        engine.start();
        assertSame(client.audio, engine.synthesize("hola"));
        assertEquals("hola", client.text);
        engine.close();

        assertTrue(client.started);
        assertTrue(client.closed);
    }

    private static final class TrackingClient extends SupertonicClient {
        private final TtsAudio audio = new TtsAudio(new float[]{0}, 44_100);
        private String text;
        private boolean started;
        private boolean closed;

        private TrackingClient() {
            super(TtsSettings.Supertonic.defaults());
        }

        @Override public void start() { started = true; }
        @Override public synchronized TtsAudio synthesize(String text) { this.text = text; return audio; }
        @Override public void close() { closed = true; }
    }
}
