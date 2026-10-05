package com.fuad.bootstrap;

import com.fuad.assistant.skills.os.CatalogSessionStore;
import com.fuad.assistant.skills.os.OsCommandSkill;
import com.fuad.enums.Capability;
import com.fuad.interaction.DefaultInteractionService;
import com.fuad.interaction.InteractionLifecycleListener;
import com.fuad.interaction.InteractionPresenter;
import com.fuad.interaction.InteractionVoiceRouter;
import com.fuad.pipeline.AssistantExecutionLifecycleListener;
import com.fuad.pipeline.AssistantPipeline;
import com.fuad.presentation.ConsoleVisualOutput;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.JavaFxVisualOutput;
import com.fuad.presentation.OverlayDisplayResolver;
import com.fuad.presentation.VisualOutput;
import com.fuad.presentation.WindowsOverlayOwnerSupport;
import com.fuad.presentation.core.AssistantVisualStateStore;
import com.fuad.presentation.core.CoreVisual;
import com.fuad.presentation.core.JavaFxCoreVisual;
import com.fuad.presentation.core.RealCoreVisualSource;
import com.fuad.presentation.core.RuntimeStatusCoordinator;
import com.fuad.presentation.interaction.DefaultInteractionDisplayResolver;
import com.fuad.presentation.interaction.InteractionDisplayResolver;
import com.fuad.presentation.interaction.JavaFxInteractionPresenter;
import com.fuad.presentation.interaction.UnavailableInteractionPresenter;
import com.fuad.presentation.media.MediaWorkspaceController;
import com.fuad.presentation.tools.DeferredMoreToolsHandler;
import com.fuad.presentation.tools.DeferredToolsActionHandler;
import com.fuad.presentation.tools.MoreToolsBrowser;
import com.fuad.presentation.tools.ToolsWorkspaceConfig;
import com.fuad.presentation.tools.ToolsWorkspaceConfigLoader;
import com.fuad.speech.SpeechProcessingService;
import com.fuad.telemetry.gpu.nvidia.NvidiaGpuTelemetryProvider;
import com.fuad.telemetry.host.oshi.OshiHostTelemetryProvider;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

final class PresentationBootstrap {
    record ToolsComponents(ToolsWorkspaceConfig config, DeferredToolsActionHandler actions,
                           DeferredMoreToolsHandler more) { }
    record PresentationComponents(VisualOutput visualOutput, InteractionPresenter interactionPresenter,
                                  CoreVisual coreVisual, InteractionLifecycleListener interactionLifecycleListener,
                                  AssistantExecutionLifecycleListener executionLifecycleListener,
                                  JavaFxRuntime javaFxRuntime) { }
    record InteractionComponents(DefaultInteractionService service, InteractionVoiceRouter voiceRouter,
                                 MoreToolsBrowser moreTools) { }
    record DisplayBindings(InteractionDisplayResolver interaction, OverlayDisplayResolver overlay) { }
    record Inputs(CatalogSessionStore catalogSessions, AudioBootstrap.AudioResources audio,
                  ToolsComponents tools, MediaWorkspaceController media) { }

    ToolsComponents createTools() {
        return new ToolsComponents(new ToolsWorkspaceConfigLoader(Path.of("config", "ares-tools-applications.json")).load(),
                new DeferredToolsActionHandler(), new DeferredMoreToolsHandler());
    }

    PresentationComponents create(Inputs inputs, ResourceCleanup cleanup) {
        PresentationComponents presentation = createPresentation(inputs);
        Map<ResourceCleanup.Resource, AutoCloseable> resources = new EnumMap<>(ResourceCleanup.Resource.class);
        resources.put(ResourceCleanup.Resource.VISUAL_OUTPUT, presentation.visualOutput());
        if (presentation.javaFxRuntime() != null) {
            resources.put(ResourceCleanup.Resource.JAVAFX_RUNTIME, presentation.javaFxRuntime());
        }
        if (presentation.coreVisual() != null) {
            resources.put(ResourceCleanup.Resource.CORE_VISUAL, presentation.coreVisual());
        }
        cleanup.registerAll(resources);
        return presentation;
    }

    PresentationComponents createPresentation(Inputs inputs) {
        JavaFxRuntime runtime = null;
        JavaFxVisualOutput visual = null;
        JavaFxCoreVisual core = null;
        try {
            runtime = createRuntime();
            DisplayBindings displays = createDisplays();
            visual = createVisual(inputs.catalogSessions(), runtime, displays);
            InteractionPresenter interaction = createInteraction(runtime, displays);
            core = createCore(inputs, runtime, displays);
            return new PresentationComponents(visual, interaction, core, core, core, runtime);
        }
        catch (Exception e) {
            System.err.println("Unable to initialize JavaFX visual output: " + e.getMessage());
            if (core != null) core.close();
            if (visual != null) visual.close();
            if (runtime != null) runtime.close();
        }
        return new PresentationComponents(new ConsoleVisualOutput(),
                new UnavailableInteractionPresenter("JavaFX is unavailable"), null,
                InteractionLifecycleListener.noop(), AssistantExecutionLifecycleListener.noop(), null);
    }

    JavaFxRuntime createRuntime() {
        return new JavaFxRuntime();
    }

    DisplayBindings createDisplays() {
        WindowsOverlayOwnerSupport windowSupport = WindowsOverlayOwnerSupport.platformDefault();
        InteractionDisplayResolver resolver = DefaultInteractionDisplayResolver.platformDefault(
                Path.of("config", "interaction-display.json"));
        return new DisplayBindings(resolver, new OverlayDisplayResolver(resolver, windowSupport));
    }

    JavaFxVisualOutput createVisual(CatalogSessionStore sessions, JavaFxRuntime runtime, DisplayBindings displays) {
        return new JavaFxVisualOutput(sessions, runtime, displays.overlay());
    }

    InteractionPresenter createInteraction(JavaFxRuntime runtime, DisplayBindings displays) {
        return new JavaFxInteractionPresenter(runtime, displays.interaction());
    }

    JavaFxCoreVisual createCore(Inputs inputs, JavaFxRuntime runtime, DisplayBindings displays) {
        return new JavaFxCoreVisual(runtime, displays.interaction(), inputs.audio().signals()::current,
                inputs.audio().input(), inputs.tools().actions(), inputs.tools().more(), inputs.tools().config(),
                inputs.media()::current, inputs.media());
    }

    InteractionComponents createInteraction(PresentationComponents presentation, ToolsComponents tools,
                                             ResourceCleanup cleanup) {
        DefaultInteractionService service = new DefaultInteractionService(presentation.interactionPresenter(),
                presentation.interactionLifecycleListener());
        cleanup.register(ResourceCleanup.Resource.INTERACTION, service);
        return new InteractionComponents(service, new InteractionVoiceRouter(service),
                new MoreToolsBrowser(service, tools.actions(), tools.config()));
    }

    void startCore(PresentationComponents presentation, RuntimeStatusCoordinator status,
                   AssistantVisualStateStore activity, ResourceCleanup cleanup) {
        CoreVisual core = presentation.coreVisual();
        if (core != null) {
            RealCoreVisualSource source = new RealCoreVisualSource(new OshiHostTelemetryProvider(),
                    new NvidiaGpuTelemetryProvider(), status, activity);
            cleanup.register(ResourceCleanup.Resource.CORE_VISUAL_SOURCE, source);
            core.show();
            source.start(core::update);
        }
    }

    void bindTools(ToolsComponents tools, InteractionComponents interaction,
                   SpeechProcessingService speech, AssistantPipeline assistant, OsCommandSkill os) {
        tools.actions().bind(request -> speech.submitDirectTurn(
                "TOUCH // " + request.action() + " // " + request.target(),
                () -> assistant.processDirectTurn(Capability.OS_COMMAND,
                        () -> os.executionAction(request.action(), request.target()))));
        tools.more().bind(interaction.moreTools()::open);
    }
}
