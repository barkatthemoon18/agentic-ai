package com.fuad.tts.piper;

import com.fuad.tts.TtsAudio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiperAdapterTest {
    @Test
    void clientShouldRejectOperationsBeforeStartAndCloseSafely() {
        PiperClient client = new PiperClient();

        assertThrows(IllegalStateException.class, () -> client.synthesize("hola"));
        assertThrows(IllegalStateException.class, client::ping);
        assertDoesNotThrow(client::close);
    }

    @Test
    void engineShouldDelegateSynthesisAndClose() throws Exception {
        TrackingClient client = new TrackingClient();
        PiperTtsEngine engine = new PiperTtsEngine(client);

        assertSame(client.audio, engine.synthesize("hola"));
        assertEquals("hola", client.text);
        engine.close();
        assertTrue(client.closed);
    }

    private static final class TrackingClient extends PiperClient {
        private final TtsAudio audio = new TtsAudio(new float[]{0}, 16_000);
        private String text;
        private boolean closed;
        @Override public synchronized TtsAudio synthesize(String text) { this.text = text; return audio; }
        @Override public void close() { closed = true; }
    }
}
