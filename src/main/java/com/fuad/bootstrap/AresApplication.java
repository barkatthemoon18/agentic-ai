package com.fuad.bootstrap;

import com.fuad.model.runtime.LmStudioStartupCoordinator;
import com.fuad.presentation.core.AssistantVisualStateStore;
import com.fuad.presentation.core.RuntimeStatusCoordinator;

import java.util.Objects;
import java.util.function.Supplier;

/** Composition root. Subsystems expose explicit dependencies rather than a global context. */
public final class AresApplication {
    private final AudioBootstrap audio;
    private final AssistantBootstrap assistant;
    private final MediaBootstrap media;
    private final PresentationBootstrap presentation;
    private final Supplier<LmStudioStartupCoordinator> models;
    private final Supplier<ApplicationLifecycle> lifecycles;

    public AresApplication() {
        this(new AudioBootstrap(), new AssistantBootstrap(), new MediaBootstrap(), new PresentationBootstrap(),
                LmStudioStartupCoordinator::new, ApplicationLifecycle::new);
    }

    AresApplication(AudioBootstrap audio, AssistantBootstrap assistant, MediaBootstrap media,
                    PresentationBootstrap presentation, Supplier<LmStudioStartupCoordinator> models,
                    Supplier<ApplicationLifecycle> lifecycles) {
        this.audio = Objects.requireNonNull(audio);
        this.assistant = Objects.requireNonNull(assistant);
        this.media = Objects.requireNonNull(media);
        this.presentation = Objects.requireNonNull(presentation);
        this.models = Objects.requireNonNull(models);
        this.lifecycles = Objects.requireNonNull(lifecycles);
    }

    public void run() {
        try (ApplicationLifecycle lifecycle = lifecycles.get()) {
            lifecycle.installShutdownHook();
            ResourceCleanup cleanup = lifecycle.resources();
            var audioResources = audio.createResources(cleanup);
            var os = assistant.createOs();
            var tools = presentation.createTools();
            var mediaController = media.create(cleanup);
            var visuals = presentation.create(new PresentationBootstrap.Inputs(
                    os.catalogSessions(), audioResources, tools, mediaController), cleanup);
            var interaction = presentation.createInteraction(visuals, tools, cleanup);
            LmStudioStartupCoordinator modelRuntime = models.get();
            cleanup.register(ResourceCleanup.Resource.MODEL_RUNTIME, modelRuntime);
            AssistantVisualStateStore activity = new AssistantVisualStateStore();
            RuntimeStatusCoordinator status = new RuntimeStatusCoordinator(activity::setDegraded);
            presentation.startCore(visuals, status, activity, cleanup);
            var assistantComponents = assistant.create(os, modelRuntime, audioResources.controller(),
                    visuals.executionLifecycleListener());
            var voice = audio.createVoice(cleanup, audioResources, assistantComponents, visuals, interaction,
                    mediaController, activity);
            presentation.bindTools(tools, interaction, voice.speechProcessor(), assistantComponents.pipeline(), os.skill());
            VoiceRuntimeCoordinator voiceRuntime = new VoiceRuntimeCoordinator(modelRuntime, status,
                    visuals.visualOutput(), voice.starts());
            cleanup.register(ResourceCleanup.Resource.MODEL_RUNTIME, voiceRuntime);
            voiceRuntime.start();
            lifecycle.awaitShutdown();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Ares interrupted");
        }
        catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
