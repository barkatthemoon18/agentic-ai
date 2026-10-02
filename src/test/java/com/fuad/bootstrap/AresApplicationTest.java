package com.fuad.bootstrap;

import com.fuad.model.runtime.LmStudioStartupCoordinator;
import com.fuad.presentation.VisualOutput;
import com.fuad.presentation.media.MediaWorkspaceController;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AresApplicationTest {
    private final AudioBootstrap audio = mock(AudioBootstrap.class);
    private final AssistantBootstrap assistant = mock(AssistantBootstrap.class);
    private final MediaBootstrap media = mock(MediaBootstrap.class);
    private final PresentationBootstrap presentation = mock(PresentationBootstrap.class);
    private final LmStudioStartupCoordinator models = mock(LmStudioStartupCoordinator.class);
    private final ApplicationLifecycle.ShutdownHooks hooks = mock(ApplicationLifecycle.ShutdownHooks.class);
    private final ResourceCleanup cleanup = new ResourceCleanup();
    private final ApplicationLifecycle lifecycle = new ApplicationLifecycle(cleanup, hooks);
    private final AresApplication application = new AresApplication(audio, assistant, media, presentation,
            () -> models, () -> lifecycle);

    @Test
    void partialStartupFailureShouldReleaseAcquiredResourcesAndRemoveHook() {
        AtomicBoolean released = new AtomicBoolean();
        when(audio.createResources(cleanup)).thenAnswer(invocation -> {
            cleanup.register(ResourceCleanup.Resource.CAPTURE, () -> released.set(true));
            return mock(AudioBootstrap.AudioResources.class);
        });
        when(assistant.createOs()).thenThrow(new IllegalStateException("catalog startup failed"));

        application.run();

        assertTrue(released.get());
        verify(hooks).add(any());
        verify(hooks).remove(any());
        verifyNoInteractions(media, presentation, models);
    }

    @Test
    void compositionShouldBindBeforeVoiceStartupAndWaitForShutdown() throws Exception {
        Fixture fixture = configureStartup();
        Thread running = Thread.ofPlatform().start(application::run);
        try {
            assertTrue(fixture.started().await(3, TimeUnit.SECONDS));
            assertTrue(running.isAlive());
            var order = inOrder(presentation, fixture.starts(), models);
            order.verify(presentation).bindTools(fixture.tools(), fixture.interaction(),
                    fixture.voice().speechProcessor(), fixture.assistant().pipeline(), fixture.os().skill());
            order.verify(fixture.starts()).start();
            order.verify(models).startAsync();
            verify(presentation).create(new PresentationBootstrap.Inputs(fixture.os().catalogSessions(),
                    fixture.audio(), fixture.tools(), fixture.media()), cleanup);
        }
        finally {
            lifecycle.close();
            running.join(3000);
        }
        assertFalse(running.isAlive());
        verify(fixture.subscription()).close();
        verify(models).close();
        verify(hooks).remove(any());
    }

    @Test
    void interruptionShouldCloseRuntimeAndRestoreInterruptFlag() throws Exception {
        Fixture fixture = configureStartup();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread running = Thread.ofPlatform().start(() -> {
            application.run();
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            assertTrue(fixture.started().await(3, TimeUnit.SECONDS));
            running.interrupt();
            running.join(3000);
            assertFalse(running.isAlive());
            assertTrue(interrupted.get());
            verify(fixture.subscription()).close();
            verify(models).close();
        }
        finally {
            lifecycle.close();
            running.join(3000);
        }
    }

    private Fixture configureStartup() throws Exception {
        var resources = mock(AudioBootstrap.AudioResources.class);
        var os = mock(AssistantBootstrap.OsComponents.class);
        var tools = mock(PresentationBootstrap.ToolsComponents.class);
        var controller = mock(MediaWorkspaceController.class);
        var visuals = mock(PresentationBootstrap.PresentationComponents.class);
        var interaction = mock(PresentationBootstrap.InteractionComponents.class);
        var components = mock(AssistantBootstrap.AssistantComponents.class);
        var start = mock(VoiceRuntimeCoordinator.StartAction.class);
        var voice = new AudioBootstrap.VoiceComponents(null, null,
                new VoiceRuntimeCoordinator.VoiceStarts(() -> { }, () -> { }, start));
        var subscription = mock(AutoCloseable.class);
        CountDownLatch started = new CountDownLatch(1);
        when(audio.createResources(cleanup)).thenReturn(resources);
        when(assistant.createOs()).thenReturn(os);
        when(presentation.createTools()).thenReturn(tools);
        when(media.create(cleanup)).thenReturn(controller);
        when(presentation.create(any(), eq(cleanup))).thenReturn(visuals);
        when(presentation.createInteraction(visuals, tools, cleanup)).thenReturn(interaction);
        when(visuals.visualOutput()).thenReturn(mock(VisualOutput.class));
        when(assistant.create(os, models, resources.controller(), visuals.executionLifecycleListener()))
                .thenReturn(components);
        when(audio.createVoice(eq(cleanup), eq(resources), eq(components), eq(visuals), eq(interaction),
                eq(controller), any())).thenReturn(voice);
        when(models.subscribe(any())).thenAnswer(invocation -> {
            java.util.function.Consumer<com.fuad.model.runtime.ModelRuntimeSnapshot> listener = invocation.getArgument(0);
            listener.accept(VoiceRuntimeCoordinatorTest.snapshot(true, false));
            return subscription;
        });
        doAnswer(invocation -> { started.countDown(); return null; }).when(models).startAsync();
        return new Fixture(resources, os, tools, controller, interaction, components, voice, start, subscription, started);
    }

    private record Fixture(AudioBootstrap.AudioResources audio, AssistantBootstrap.OsComponents os,
                           PresentationBootstrap.ToolsComponents tools, MediaWorkspaceController media,
                           PresentationBootstrap.InteractionComponents interaction,
                           AssistantBootstrap.AssistantComponents assistant, AudioBootstrap.VoiceComponents voice,
                           VoiceRuntimeCoordinator.StartAction starts, AutoCloseable subscription,
                           CountDownLatch started) { }
}
