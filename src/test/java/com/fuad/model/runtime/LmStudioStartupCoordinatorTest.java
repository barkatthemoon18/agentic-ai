package com.fuad.model.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LmStudioStartupCoordinatorTest {
    private LmStudioStartupCoordinator coordinator;

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.close();
        }
    }

    @Test
    void shouldBecomeReadyWithoutMutatingWhenEverythingAlreadyExists() throws Exception {
        FakeRunner runner = readyRunner();
        coordinator = coordinator(runner, Duration.ofMillis(10));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().state() == RuntimeState.READY);
        assertEquals(0, runner.mutations.get());
        assertTrue(coordinator.isPhiUsable());
        assertTrue(coordinator.isQwenUsable());
    }

    @Test
    void preCheckShouldAvoidLoadWhenModelAppearsBetweenChecks() throws Exception {
        FakeRunner runner = readyRunner();
        runner.models.remove("qwen-main");
        runner.appearOnSecondQwenProbe.set(true);
        coordinator = coordinator(runner, Duration.ofMillis(20));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().state() == RuntimeState.READY);
        assertEquals(0, runner.qwenLoads.get());
    }

    @Test
    void timeoutCompletedLaterShouldBeFoundBeforeAutomaticRetryMutates() throws Exception {
        FakeRunner runner = readyRunner();
        runner.models.remove("qwen-main");
        runner.timeoutQwenAndCompleteLater.set(true);
        coordinator = coordinator(runner, Duration.ofMillis(80));

        coordinator.startAsync();

        await(() -> runner.qwenLoads.get() == 1);
        await(() -> coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).state()
                == ComponentState.READY);
        assertEquals(1, runner.qwenLoads.get());
    }

    @Test
    void successfulManualRetryShouldInvalidatePendingAutomaticRetry() throws Exception {
        FakeRunner runner = readyRunner();
        runner.models.remove("qwen-main");
        runner.failQwenLoads.set(true);
        coordinator = coordinator(runner, Duration.ofMillis(200));
        coordinator.startAsync();
        await(() -> coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).state()
                == ComponentState.RETRY_WAIT);
        assertEquals(RuntimeState.PARTIALLY_READY, coordinator.snapshot().state());

        long oldGeneration = coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).generation();
        runner.failQwenLoads.set(false);
        coordinator.retry(RuntimeComponent.QWEN_MAIN);

        await(() -> coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).state()
                == ComponentState.READY);
        int callsAfterManualSuccess = runner.qwenLoads.get();
        Thread.sleep(260);
        assertEquals(callsAfterManualSuccess, runner.qwenLoads.get());
        assertTrue(coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).generation()
                > oldGeneration);
    }

    @Test
    void shouldUseInitialAttemptAndThreeRelativeRetries() throws Exception {
        FakeRunner runner = readyRunner();
        runner.models.remove("qwen-main");
        runner.failQwenLoads.set(true);
        coordinator = new LmStudioStartupCoordinator(runner,
                () -> new ServerHealthProbe.Result(true, true, "healthy"),
                new ObjectMapper(), Executors.newScheduledThreadPool(4),
                List.of(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(30)));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).state()
                == ComponentState.FAILED);
        assertEquals(4, runner.qwenLoads.get());
        assertEquals(3, coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).retriesUsed());
        assertEquals(RuntimeState.DEGRADED, coordinator.snapshot().state());
    }

    @Test
    void occupiedPortShouldNotBeTreatedAsStoppedServer() throws Exception {
        FakeRunner runner = readyRunner();
        runner.serverRunning.set(false);
        coordinator = coordinator(runner,
                () -> new ServerHealthProbe.Result(true, false, "different service"),
                Duration.ofMillis(10));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().component(RuntimeComponent.API_SERVER).state()
                == ComponentState.FAILED);
        assertEquals(0, runner.serverStarts.get());
        assertTrue(coordinator.snapshot().component(RuntimeComponent.API_SERVER).detail()
                .contains("PORT_CONFLICT"));
    }

    @Test
    void unhealthyRunningServerShouldNotBeStartedAgain() throws Exception {
        FakeRunner runner = readyRunner();
        coordinator = coordinator(runner,
                () -> new ServerHealthProbe.Result(true, false, "invalid response"),
                Duration.ofMillis(10));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().component(RuntimeComponent.API_SERVER).state()
                == ComponentState.FAILED);
        assertEquals(0, runner.serverStarts.get());
        assertTrue(coordinator.snapshot().component(RuntimeComponent.API_SERVER).detail()
                .contains("UNHEALTHY"));
    }

    @Test
    void stoppedServerShouldBeStartedAndHealthChecked() throws Exception {
        FakeRunner runner = readyRunner();
        runner.serverRunning.set(false);
        coordinator = coordinator(runner, () -> runner.serverRunning.get()
                        ? new ServerHealthProbe.Result(true, true, "healthy")
                        : new ServerHealthProbe.Result(false, false, "not listening"),
                Duration.ofMillis(10));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().component(RuntimeComponent.API_SERVER).state()
                == ComponentState.READY);
        assertEquals(1, runner.serverStarts.get());
    }

    @Test
    void conflictingModelIdentityShouldNotBeOverwritten() throws Exception {
        FakeRunner runner = readyRunner();
        runner.qwenPath = "another/publisher/wrong-model";
        coordinator = coordinator(runner, Duration.ofMillis(10));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).state()
                == ComponentState.FAILED);
        assertEquals(0, runner.qwenLoads.get());
        assertTrue(coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).detail()
                .contains("otro modelo"));
    }

    @Test
    void exactAliasWithUnknownIdentityShouldBeAccepted() throws Exception {
        FakeRunner runner = readyRunner();
        runner.qwenPath = null;
        coordinator = coordinator(runner, Duration.ofMillis(10));

        coordinator.startAsync();

        await(() -> coordinator.snapshot().state() == RuntimeState.READY);
        assertTrue(coordinator.snapshot().component(RuntimeComponent.QWEN_MAIN).detail()
                .contains("no verificable"));
    }

    private LmStudioStartupCoordinator coordinator(FakeRunner runner, Duration retryDelay) {
        return coordinator(runner,
                () -> new ServerHealthProbe.Result(true, true, "healthy"), retryDelay);
    }

    private LmStudioStartupCoordinator coordinator(FakeRunner runner,
                                                   ServerHealthProbe healthProbe,
                                                   Duration retryDelay) {
        return new LmStudioStartupCoordinator(runner, healthProbe,
                new ObjectMapper(), Executors.newScheduledThreadPool(4),
                List.of(retryDelay, retryDelay, retryDelay));
    }

    private FakeRunner readyRunner() {
        FakeRunner runner = new FakeRunner();
        runner.daemonRunning.set(true);
        runner.serverRunning.set(true);
        runner.models.add("phi-router");
        runner.models.add("qwen-main");
        return runner;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.getAsBoolean(), "condition was not reached before timeout");
    }

    private static final class FakeRunner implements LmsCommandRunner {
        private final AtomicBoolean daemonRunning = new AtomicBoolean();
        private final AtomicBoolean serverRunning = new AtomicBoolean();
        private final Set<String> models = java.util.Collections.synchronizedSet(new HashSet<>());
        private final AtomicInteger mutations = new AtomicInteger();
        private final AtomicInteger qwenLoads = new AtomicInteger();
        private final AtomicInteger serverStarts = new AtomicInteger();
        private final AtomicInteger qwenProbes = new AtomicInteger();
        private final AtomicBoolean appearOnSecondQwenProbe = new AtomicBoolean();
        private final AtomicBoolean timeoutQwenAndCompleteLater = new AtomicBoolean();
        private final AtomicBoolean failQwenLoads = new AtomicBoolean();
        private volatile String qwenPath = "qwen/qwen3.5-9b";

        @Override
        public CommandResult run(RuntimeComponent owner, List<String> command, Duration timeout) {
            if (command.contains("status") && command.contains("daemon")) {
                return ok("{\"status\":\"" + (daemonRunning.get() ? "running" : "not-running") + "\"}");
            }
            if (command.contains("status") && command.contains("server")) {
                return ok("{\"running\":" + serverRunning.get() + ",\"port\":1234}");
            }
            if (command.contains("ps")) {
                if (owner == RuntimeComponent.QWEN_MAIN
                        && appearOnSecondQwenProbe.get()
                        && qwenProbes.incrementAndGet() == 2) {
                    models.add("qwen-main");
                }
                return ok(modelsJson());
            }
            mutations.incrementAndGet();
            if (command.contains("up")) {
                daemonRunning.set(true);
                return ok("{\"status\":\"running\"}");
            }
            if (command.contains("start")) {
                serverStarts.incrementAndGet();
                serverRunning.set(true);
                return ok("started");
            }
            if (command.contains("qwen/qwen3.5-9b")) {
                qwenLoads.incrementAndGet();
                if (timeoutQwenAndCompleteLater.compareAndSet(true, false)) {
                    Thread.ofVirtual().start(() -> {
                        try {
                            Thread.sleep(25);
                            models.add("qwen-main");
                        }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    return new CommandResult(-1, "timeout", true);
                }
                if (!failQwenLoads.get()) {
                    models.add("qwen-main");
                    return ok("loaded");
                }
                return new CommandResult(1, "failed", false);
            }
            if (command.contains("phi-3.5-mini-instruct")) {
                models.add("phi-router");
                return ok("loaded");
            }
            return new CommandResult(1, "unexpected command", false);
        }

        @Override
        public void cancel(RuntimeComponent owner) {
            // No blocking fake process to cancel.
        }

        private CommandResult ok(String output) {
            return new CommandResult(0, output, false);
        }

        private String modelsJson() {
            List<String> entries = new ArrayList<>();
            if (models.contains("phi-router")) {
                entries.add("{\"identifier\":\"phi-router\",\"path\":\"phi-3.5-mini-instruct\"}");
            }
            if (models.contains("qwen-main")) {
                entries.add(qwenPath == null
                        ? "{\"identifier\":\"qwen-main\"}"
                        : "{\"identifier\":\"qwen-main\",\"path\":\"" + qwenPath + "\"}");
            }
            return "[" + String.join(",", entries) + "]";
        }
    }
}
