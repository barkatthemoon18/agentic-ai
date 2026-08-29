package com.fuad.stt.fasterwhisper;

import com.fuad.speech.SpeechSegment;
import com.fuad.stt.TranscriptionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FasterWhisperAdapterTest {
    @Test
    void clientShouldReportNotAliveAndRejectTranscriptionBeforeStart() {
        FasterWhisperClient client = new FasterWhisperClient();

        assertFalse(client.isAlive());
        assertFalse(client.ping());
        assertThrows(IllegalStateException.class,
                () -> client.transcribe(new SpeechSegment(new float[]{0}, 16_000, 0)));
        assertDoesNotThrow(client::close);
    }

    @Test
    void engineShouldDelegateTranscribeAndClose() {
        TrackingClient client = new TrackingClient();
        FasterWhisperSttEngine engine = new FasterWhisperSttEngine(client);
        SpeechSegment segment = new SpeechSegment(new float[]{0}, 16_000, 0);

        assertSame(client.result, engine.transcribe(segment));
        assertSame(segment, client.segment);
        engine.close();
        assertTrue(client.closed);
    }

    private static final class TrackingClient extends FasterWhisperClient {
        private final TranscriptionResult result = new TranscriptionResult("hola", "es", 1);
        private SpeechSegment segment;
        private boolean closed;
        @Override public synchronized TranscriptionResult transcribe(SpeechSegment segment) { this.segment = segment; return result; }
        @Override public synchronized void close() { closed = true; }
    }
}
