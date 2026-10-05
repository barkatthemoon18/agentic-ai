package com.fuad.bootstrap;

import com.fuad.interaction.InteractionPresenter;
import com.fuad.presentation.ConsoleVisualOutput;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.JavaFxVisualOutput;
import com.fuad.presentation.core.JavaFxCoreVisual;
import com.fuad.presentation.interaction.UnavailableInteractionPresenter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresentationBootstrapTest {
    private final PresentationBootstrap bootstrap = spy(new PresentationBootstrap());
    private final JavaFxRuntime runtime = mock(JavaFxRuntime.class);
    private final JavaFxVisualOutput visual = mock(JavaFxVisualOutput.class);
    private final JavaFxCoreVisual core = mock(JavaFxCoreVisual.class);
    private final InteractionPresenter interaction = mock(InteractionPresenter.class);
    private final PresentationBootstrap.Inputs inputs = mock(PresentationBootstrap.Inputs.class);
    private final PresentationBootstrap.DisplayBindings displays = mock(PresentationBootstrap.DisplayBindings.class);

    private void successfulFactories() {
        doReturn(runtime).when(bootstrap).createRuntime();
        doReturn(displays).when(bootstrap).createDisplays();
        doReturn(visual).when(bootstrap).createVisual(any(), same(runtime), same(displays));
        doReturn(interaction).when(bootstrap).createInteraction(runtime, displays);
        doReturn(core).when(bootstrap).createCore(inputs, runtime, displays);
    }

    @Test
    void successfulJavaFxCompositionShouldShareCoreListenersAndRegisterOwnership() {
        successfulFactories();
        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            var result = bootstrap.create(inputs, cleanup);
            assertSame(visual, result.visualOutput());
            assertSame(interaction, result.interactionPresenter());
            assertSame(core, result.coreVisual());
            assertSame(core, result.executionLifecycleListener());
            assertSame(core, result.interactionLifecycleListener());
            assertSame(runtime, result.javaFxRuntime());
            verifyNoInteractions(core, visual, runtime);
        }
        var order = inOrder(core, visual, runtime);
        order.verify(core).close();
        order.verify(visual).close();
        order.verify(runtime).close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void javaFxFailureShouldReleaseAcquiredResourcesAndFallBackToConsole(int stage) {
        successfulFactories();
        RuntimeException failure = new IllegalStateException("JavaFX unavailable");
        switch (stage) {
            case 0 -> doThrow(failure).when(bootstrap).createRuntime();
            case 1 -> doThrow(failure).when(bootstrap).createDisplays();
            case 2 -> doThrow(failure).when(bootstrap).createVisual(any(), same(runtime), same(displays));
            case 3 -> doThrow(failure).when(bootstrap).createInteraction(runtime, displays);
            case 4 -> doThrow(failure).when(bootstrap).createCore(inputs, runtime, displays);
        }
        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            var result = bootstrap.create(inputs, cleanup);
            assertInstanceOf(ConsoleVisualOutput.class, result.visualOutput());
            assertInstanceOf(UnavailableInteractionPresenter.class, result.interactionPresenter());
            assertNull(result.coreVisual());
            assertNull(result.javaFxRuntime());
            assertNotNull(result.executionLifecycleListener());
            assertNotNull(result.interactionLifecycleListener());
        }
        verify(runtime, times(stage > 0 ? 1 : 0)).close();
        verify(visual, times(stage > 2 ? 1 : 0)).close();
        verify(core, never()).close();
        if (stage > 2) {
            var order = inOrder(visual, runtime);
            order.verify(visual).close();
            order.verify(runtime).close();
        }
    }

    @Test
    void javaFxLinkageErrorShouldKeepExistingExceptionBoundary() {
        doThrow(new UnsatisfiedLinkError("native error")).when(bootstrap).createRuntime();
        assertThrows(UnsatisfiedLinkError.class, () -> bootstrap.createPresentation(inputs));
    }

    @Test
    void shutdownDuringConstructionShouldReleaseEntirePresentationBundle() {
        successfulFactories();
        ResourceCleanup cleanup = new ResourceCleanup();
        cleanup.close();
        assertThrows(IllegalStateException.class, () -> bootstrap.create(inputs, cleanup));
        verify(core).close();
        verify(visual).close();
        verify(runtime).close();
    }
}
