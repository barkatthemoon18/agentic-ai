package com.fuad.bootstrap;

import com.fuad.model.runtime.LmStudioStartupCoordinator;
import com.fuad.model.runtime.ModelRuntimeSnapshot;
import com.fuad.presentation.InfrastructureStatus;
import com.fuad.presentation.VisualOutput;
import com.fuad.presentation.core.RuntimeStatusCoordinator;
import com.fuad.presentation.core.RuntimeVisualState;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes voice startup with model-runtime shutdown, without adding worker threads. */
final class VoiceRuntimeCoordinator implements AutoCloseable {
    @FunctionalInterface
    interface StartAction {
        void start() throws Exception;
    }

    record VoiceStarts(StartAction stt, StartAction tts, StartAction capture) {
        VoiceStarts {
            Objects.requireNonNull(stt);
            Objects.requireNonNull(tts);
            Objects.requireNonNull(capture);
        }
    }

    private final LmStudioStartupCoordinator models;
    private final RuntimeStatusCoordinator status;
    private final VisualOutput visual;
    private final VoiceStarts starts;
    private final Object lock = new Object();
    private final AtomicBoolean voiceStarted = new AtomicBoolean(false);
    private boolean subscribed;
    private boolean closing;
    private AutoCloseable subscription;

    VoiceRuntimeCoordinator(LmStudioStartupCoordinator models, RuntimeStatusCoordinator status,
                            VisualOutput visual, VoiceStarts starts) {
        this.models = Objects.requireNonNull(models);
        this.status = Objects.requireNonNull(status);
        this.visual = Objects.requireNonNull(visual);
        this.starts = Objects.requireNonNull(starts);
    }

    void start() {
        synchronized (lock) {
            if (closing || subscribed) return;
            subscribed = true;
            subscription = models.subscribe(this::onSnapshot);
            models.startAsync();
        }
    }

    private void onSnapshot(ModelRuntimeSnapshot snapshot) {
        status.updateModels(snapshot);
        visual.showInfrastructureStatus(new InfrastructureStatus(snapshot, models::retry));
        if (snapshot.isPhiUsable()) {
            synchronized (lock) {
                if (closing || !voiceStarted.compareAndSet(false, true)) return;
                try {
                    status.setStt(RuntimeVisualState.LOADING);
                    try {
                        starts.stt().start();
                        status.setStt(RuntimeVisualState.READY);
                    }
                    catch (Exception e) {
                        status.setStt(RuntimeVisualState.FAILED);
                        throw e;
                    }
                    status.setTts(RuntimeVisualState.LOADING);
                    try {
                        starts.tts().start();
                        status.setTts(RuntimeVisualState.READY);
                    }
                    catch (Exception e) {
                        status.setTts(RuntimeVisualState.FAILED);
                        throw e;
                    }
                    starts.capture().start();
                    System.out.println("Voice runtime active: daemon, API server and phi-router are ready");
                }
                catch (Exception e) {
                    voiceStarted.set(false);
                    System.err.println("Unable to start voice runtime: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        synchronized (lock) {
            if (closing) return;
            closing = true;
            try {
                if (subscription != null) subscription.close();
            }
            finally {
                models.close();
            }
        }
    }
}
