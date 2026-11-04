package com.fuad.tts;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FallbackTtsEngineTest {
    private final AtomicLong clock = new AtomicLong();

    @Test
    void shouldUsePrimaryAndNotTouchFallbackWhileHealthy() throws Exception {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 3);

        engine.start();

        assertSame(primary.audio, engine.synthesize("hola"));
        assertEquals(List.of("hola"), primary.requests);
        assertTrue(fallback.requests.isEmpty());
        assertTrue(primary.started && fallback.started);
    }

    @Test
    void shouldAnswerFromFallbackWhenAnIndividualRequestFailsAndKeepUsingPrimary() throws Exception {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 3);
        primary.failNext = 1;

        assertSame(fallback.audio, engine.synthesize("uno"));
        assertSame(primary.audio, engine.synthesize("dos"));
        assertEquals(List.of("uno", "dos"), primary.requests);
        assertEquals(List.of("uno"), fallback.requests);
    }

    @Test
    void shouldBypassPrimaryAfterConsecutiveFailuresUntilTheCooldownElapses() throws Exception {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 2);
        primary.failNext = 2;

        engine.synthesize("a");
        engine.synthesize("b");
        assertEquals(2, primary.requests.size());

        assertSame(fallback.audio, engine.synthesize("c"));
        assertEquals(2, primary.requests.size(), "Primary must be bypassed during the cooldown");

        clock.addAndGet(Duration.ofSeconds(30).toNanos());
        engine.synthesize("d");
        assertEquals(2, primary.requests.size(), "Cooldown has not elapsed yet");

        clock.addAndGet(Duration.ofSeconds(31).toNanos());
        assertSame(primary.audio, engine.synthesize("e"));
        assertEquals(1, primary.startCalls, "Primary is restarted before being retried");
        assertEquals(3, primary.requests.size());
    }

    @Test
    void aSuccessfulRequestShouldResetTheFailureCount() throws Exception {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 2);

        primary.failNext = 1;
        engine.synthesize("a");
        engine.synthesize("b");
        primary.failNext = 1;
        engine.synthesize("c");
        engine.synthesize("d");

        assertEquals(List.of("a", "b", "c", "d"), primary.requests);
    }

    @Test
    void shouldUseFallbackWhenPrimaryFailsToStartAndRetryItAfterTheCooldown() throws Exception {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 3);
        primary.failStart = true;

        assertDoesNotThrow(engine::start);
        assertSame(fallback.audio, engine.synthesize("hola"));
        assertTrue(primary.requests.isEmpty());

        primary.failStart = false;
        clock.addAndGet(Duration.ofSeconds(61).toNanos());
        assertSame(primary.audio, engine.synthesize("otra"));
    }

    @Test
    void shouldStayDisabledWhenTheRestartAfterTheCooldownFails() throws Exception {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 3);
        primary.failStart = true;
        engine.start();

        clock.addAndGet(Duration.ofSeconds(61).toNanos());
        assertSame(fallback.audio, engine.synthesize("a"));
        assertSame(fallback.audio, engine.synthesize("b"));
        assertEquals(2, primary.startCalls, "Initial start plus a single retry; the cooldown restarts");
    }

    @Test
    void shouldIgnoreAFailingFallbackStartWhilePrimaryWorks() throws Exception {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        fallback.failStart = true;
        FallbackTtsEngine engine = engine(primary, fallback, 3);

        assertDoesNotThrow(engine::start);
        assertSame(primary.audio, engine.synthesize("hola"));
    }

    @Test
    void shouldFailToStartWhenNeitherEngineStarts() {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        primary.failStart = true;
        fallback.failStart = true;
        FallbackTtsEngine engine = engine(primary, fallback, 3);

        IllegalStateException error = assertThrows(IllegalStateException.class, engine::start);

        assertEquals(1, error.getSuppressed().length);
    }

    @Test
    void shouldPropagateFallbackFailureWhenBothEnginesFailTheRequest() {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 3);
        primary.failNext = 1;
        fallback.failNext = 1;

        assertThrows(IllegalStateException.class, () -> engine.synthesize("hola"));
    }

    @Test
    void shouldRejectBlankTextWithoutCountingItAgainstThePrimary() {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        FallbackTtsEngine engine = engine(primary, fallback, 1);

        assertThrows(IllegalArgumentException.class, () -> engine.synthesize(" "));
        assertThrows(IllegalArgumentException.class, () -> engine.synthesize(null));

        assertSame(primary.audio, engine.synthesize("hola"));
        assertTrue(fallback.requests.isEmpty());
    }

    @Test
    void closeShouldCloseBothEnginesEvenWhenTheFirstFails() {
        FakeEngine primary = new FakeEngine("primary");
        FakeEngine fallback = new FakeEngine("fallback");
        primary.failClose = true;
        FallbackTtsEngine engine = engine(primary, fallback, 3);

        assertThrows(IllegalStateException.class, engine::close);

        assertTrue(primary.closed);
        assertTrue(fallback.closed);
    }

    private FallbackTtsEngine engine(FakeEngine primary, FakeEngine fallback, int threshold) {
        return new FallbackTtsEngine(primary, "primary", fallback, "fallback", threshold, Duration.ofSeconds(60),
                clock::get);
    }

    private static final class FakeEngine implements TtsEngine {
        private final TtsAudio audio;
        private final List<String> requests = new ArrayList<>();
        private int failNext;
        private int startCalls;
        private boolean failStart;
        private boolean failClose;
        private boolean started;
        private boolean closed;

        private FakeEngine(String name) {
            this.audio = new TtsAudio(new float[]{name.length()}, 16_000);
        }

        @Override
        public void start() {
            startCalls++;
            if (failStart) {
                throw new IllegalStateException("start failed");
            }
            started = true;
        }

        @Override
        public TtsAudio synthesize(String text) {
            requests.add(text);
            if (failNext > 0) {
                failNext--;
                throw new IllegalStateException("synthesis failed");
            }
            return audio;
        }

        @Override
        public void close() {
            closed = true;
            if (failClose) {
                throw new IllegalStateException("close failed");
            }
        }
    }
}
