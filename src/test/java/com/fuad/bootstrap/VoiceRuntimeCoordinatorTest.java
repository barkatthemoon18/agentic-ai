package com.fuad.bootstrap;

import com.fuad.model.runtime.ComponentSnapshot;
import com.fuad.model.runtime.ComponentState;
import com.fuad.model.runtime.LmStudioStartupCoordinator;
import com.fuad.model.runtime.ModelRuntimeSnapshot;
import com.fuad.model.runtime.RuntimeComponent;
import com.fuad.model.runtime.RuntimeState;
import com.fuad.presentation.InfrastructureStatus;
import com.fuad.presentation.VisualOutput;
import com.fuad.presentation.core.RuntimeStatusCoordinator;
import com.fuad.presentation.core.RuntimeVisualState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceRuntimeCoordinatorTest {
    private final LmStudioStartupCoordinator models = mock(LmStudioStartupCoordinator.class);
    private final VisualOutput visual = mock(VisualOutput.class);
    private final AutoCloseable subscription = mock(AutoCloseable.class);
    private final VoiceRuntimeCoordinator.StartAction stt = mock(VoiceRuntimeCoordinator.StartAction.class);
    private final VoiceRuntimeCoordinator.StartAction tts = mock(VoiceRuntimeCoordinator.StartAction.class);
    private final VoiceRuntimeCoordinator.StartAction capture = mock(VoiceRuntimeCoordinator.StartAction.class);
    private final List<Boolean> degradation = new ArrayList<>();
    private final RuntimeStatusCoordinator status = new RuntimeStatusCoordinator(degradation::add);
    private final AtomicReference<Consumer<ModelRuntimeSnapshot>> listener = new AtomicReference<>();

    private VoiceRuntimeCoordinator coordinator(ModelRuntimeSnapshot initial) {
        when(models.subscribe(any())).thenAnswer(invocation -> {
            Consumer<ModelRuntimeSnapshot> callback = invocation.getArgument(0);
            listener.set(callback);
            callback.accept(initial);
            return subscription;
        });
        return new VoiceRuntimeCoordinator(models, status, visual,
                new VoiceRuntimeCoordinator.VoiceStarts(stt, tts, capture));
    }

    @Test
    void unusablePhiShouldOnlyPublishInfrastructureAndKeepRetryAction() throws Exception {
        ModelRuntimeSnapshot initial = snapshot(false, false);
        try (var coordinator = coordinator(initial)) {
            coordinator.start();
            verifyNoInteractions(stt, tts, capture);
            var message = org.mockito.ArgumentCaptor.forClass(InfrastructureStatus.class);
            verify(visual).showInfrastructureStatus(message.capture());
            assertSame(initial, message.getValue().snapshot());
            message.getValue().retryAction().accept(RuntimeComponent.PHI_ROUTER);
            verify(models).retry(RuntimeComponent.PHI_ROUTER);
            assertEquals(RuntimeVisualState.CHECKING, status.snapshot().sttState());
        }
    }

    @Test
    void immediateUsableSnapshotShouldStartInOrderWithoutWaitingForQwen() throws Exception {
        try (var coordinator = coordinator(snapshot(true, false))) {
            coordinator.start();
            coordinator.start();
            listener.get().accept(snapshot(true, true));
            listener.get().accept(snapshot(false, false));
            listener.get().accept(snapshot(true, true));

            var order = inOrder(models, stt, tts, capture);
            order.verify(models).subscribe(any());
            order.verify(stt).start();
            order.verify(tts).start();
            order.verify(capture).start();
            order.verify(models).startAsync();
            verify(stt).start();
            verify(tts).start();
            verify(capture).start();
            assertEquals(RuntimeVisualState.READY, status.snapshot().sttState());
            assertEquals(RuntimeVisualState.READY, status.snapshot().ttsState());
        }
        var order = inOrder(subscription, models);
        order.verify(subscription).close();
        order.verify(models).close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void failedStageShouldPreserveStatesAndAllowRetryOnNextSnapshot(int stage) throws Exception {
        var failedAction = List.of(stt, tts, capture).get(stage);
        doThrow(new IllegalStateException("startup failure")).doNothing().when(failedAction).start();
        try (var coordinator = coordinator(snapshot(false, true))) {
            coordinator.start();
            listener.get().accept(snapshot(true, true));
            assertEquals(stage == 0 ? RuntimeVisualState.FAILED : RuntimeVisualState.READY,
                    status.snapshot().sttState());
            assertEquals(stage == 0 ? RuntimeVisualState.CHECKING
                    : stage == 1 ? RuntimeVisualState.FAILED : RuntimeVisualState.READY,
                    status.snapshot().ttsState());
            if (stage == 0) verifyNoInteractions(tts, capture);
            if (stage == 1) verifyNoInteractions(capture);
            assertEquals(stage < 2 ? List.of(true) : List.of(), degradation);

            listener.get().accept(snapshot(true, true));
            verify(stt, times(2)).start();
            verify(tts, times(stage == 0 ? 1 : 2)).start();
            verify(capture, times(stage == 2 ? 2 : 1)).start();
            assertEquals(RuntimeVisualState.READY, status.snapshot().sttState());
            assertEquals(RuntimeVisualState.READY, status.snapshot().ttsState());
            assertEquals(stage < 2 ? List.of(true, false) : List.of(), degradation);
        }
    }

    @Test
    void concurrentSnapshotsShouldNotStartVoiceTwice() throws Exception {
        try (var coordinator = coordinator(snapshot(false, false)); var executor = Executors.newFixedThreadPool(2)) {
            coordinator.start();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            doAnswer(invocation -> {
                entered.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS));
                return null;
            }).when(stt).start();
            var first = executor.submit(() -> listener.get().accept(snapshot(true, true)));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var second = executor.submit(() -> listener.get().accept(snapshot(true, true)));
            release.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            verify(stt).start();
            verify(tts).start();
            verify(capture).start();
        }
    }

    @Test
    void closeShouldWaitForOngoingStartupThenPreventLaterStarts() throws Exception {
        try (var coordinator = coordinator(snapshot(false, false)); var executor = Executors.newFixedThreadPool(2)) {
            coordinator.start();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            doAnswer(invocation -> {
                entered.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS));
                return null;
            }).when(stt).start();
            var start = executor.submit(() -> listener.get().accept(snapshot(true, true)));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            CountDownLatch closing = new CountDownLatch(1);
            var close = executor.submit(() -> { closing.countDown(); coordinator.close(); return null; });
            assertTrue(closing.await(3, TimeUnit.SECONDS));
            verify(models, never()).close();
            release.countDown();
            start.get(3, TimeUnit.SECONDS);
            close.get(3, TimeUnit.SECONDS);
            coordinator.start();
            listener.get().accept(snapshot(true, true));
            var order = inOrder(capture, models);
            order.verify(capture).start();
            order.verify(models).close();
            verify(stt).start();
            verify(models).startAsync();
        }
        verify(subscription).close();
        verify(models).close();
    }

    @Test
    void closeBeforeSubscriptionShouldPreventStartup() throws Exception {
        var coordinator = coordinator(snapshot(true, true));
        coordinator.close();
        coordinator.start();
        verify(models, never()).subscribe(any());
        verifyNoInteractions(stt, tts, capture);
        verify(models).close();
    }

    static ModelRuntimeSnapshot snapshot(boolean phi, boolean qwen) {
        var components = new EnumMap<RuntimeComponent, ComponentSnapshot>(RuntimeComponent.class);
        for (RuntimeComponent component : RuntimeComponent.values()) {
            boolean ready = switch (component) {
                case PHI_ROUTER -> phi;
                case QWEN_MAIN -> qwen;
                default -> true;
            };
            components.put(component, new ComponentSnapshot(component,
                    ready ? ComponentState.READY : ComponentState.CHECKING, "test", null, 0, null, 0));
        }
        return new ModelRuntimeSnapshot(phi && qwen ? RuntimeState.READY
                : phi ? RuntimeState.PARTIALLY_READY : RuntimeState.STARTING, components);
    }
}
