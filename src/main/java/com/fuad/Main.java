package com.fuad;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.RuleBasedActivationDetector;
import com.fuad.activation.utterance.LocalUtteranceClassifier;
import com.fuad.activation.utterance.UtteranceClassifier;
import com.fuad.activation.wake.LocalWakeClassifier;
import com.fuad.activation.wake.WakeClassifier;
import com.fuad.activation.wake.WakeWordMatcher;
import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.GptAssistantEngine;
import com.fuad.assistant.local.LocalQwenChatClient;
import com.fuad.assistant.routing.AiSkillRouter;
import com.fuad.assistant.routing.LocalSemanticRouter;
import com.fuad.assistant.routing.GuardedSemanticRouter;
import com.fuad.assistant.routing.SemanticRouter;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.assistant.skills.GeneralSkill;
import com.fuad.assistant.skills.general.DefaultGeneralBackendSelector;
import com.fuad.assistant.skills.general.GptGeneralEngine;
import com.fuad.assistant.skills.general.LocalGeneralComplexityClassifier;
import com.fuad.assistant.skills.general.QwenGeneralEngine;
import com.fuad.assistant.skills.SkillRegistry;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.assistant.skills.SystemTimeSkill;
import com.fuad.assistant.skills.audio.AudioControlParser;
import com.fuad.assistant.skills.audio.AudioControlSkill;
import com.fuad.assistant.skills.audio.LocalAudioControlParser;
import com.fuad.assistant.skills.os.ApplicationController;
import com.fuad.assistant.skills.os.ApplicationAliasConfigLoader;
import com.fuad.assistant.skills.os.ApplicationCatalog;
import com.fuad.assistant.skills.os.ApplicationRegistry;
import com.fuad.assistant.skills.os.CatalogSessionStore;
import com.fuad.assistant.skills.os.LocalOsCommandParser;
import com.fuad.assistant.skills.os.OsCommandParser;
import com.fuad.assistant.skills.os.OsCommandSafetyGuard;
import com.fuad.assistant.skills.os.OsCommandSkill;
import com.fuad.assistant.skills.os.WindowsApplicationController;
import com.fuad.assistant.skills.os.WindowsApplicationDiscovery;
import com.fuad.assistant.skills.research.CurrentResearchSkill;
import com.fuad.assistant.skills.research.DefaultResearchBackendClassifier;
import com.fuad.assistant.skills.research.GptWebResearchEngine;
import com.fuad.assistant.skills.research.LocalResearchDepthClassifier;
import com.fuad.assistant.skills.research.QwenLocalResearchEngine;
import com.fuad.assistant.skills.research.ResearchDepthClassifier;
import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AudioCaptureService;
import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioDeviceManager;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.config.AppConfig;
import com.fuad.enums.Capability;
import com.fuad.interaction.InteractionLifecycleListener;
import com.fuad.model.runtime.LmStudioStartupCoordinator;
import com.fuad.interaction.DefaultInteractionService;
import com.fuad.interaction.InteractionPresenter;
import com.fuad.interaction.InteractionVoiceRouter;
import com.fuad.pipeline.*;
import com.fuad.presentation.*;
import com.fuad.presentation.core.*;
import com.fuad.presentation.dev.DeferredDevActionHandler;
import com.fuad.presentation.dev.DevActionHandler;
import com.fuad.presentation.interaction.DefaultInteractionDisplayResolver;
import com.fuad.presentation.interaction.JavaFxInteractionPresenter;
import com.fuad.presentation.interaction.UnavailableInteractionPresenter;
import com.fuad.speech.SpeechBuffer;
import com.fuad.speech.SpeechProcessingService;
import com.fuad.speech.validation.BasicSpeechSegmentValidator;
import com.fuad.speech.validation.SpeechSegmentValidator;
import com.fuad.stt.SttEngine;
import com.fuad.stt.fasterwhisper.FasterWhisperClient;
import com.fuad.stt.fasterwhisper.FasterWhisperSttEngine;
import com.fuad.telemetry.gpu.nvidia.NvidiaGpuTelemetryProvider;
import com.fuad.telemetry.host.oshi.OshiHostTelemetryProvider;
import com.fuad.tts.TtsEngine;
import com.fuad.tts.piper.PiperClient;
import com.fuad.tts.piper.PiperTtsEngine;
import com.fuad.vad.SileroVadEngine;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Main {
    public static void main(String[] args) {
        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            final AudioCaptureService captureService = new AudioCaptureService();
            cleanup.register(ResourceCleanup.Resource.CAPTURE, captureService::stop);
            final AudioPlaybackService playbackService = new AudioPlaybackService();
            final FasterWhisperClient client = new FasterWhisperClient();
            final SttEngine stt = new FasterWhisperSttEngine(client);
            cleanup.register(ResourceCleanup.Resource.STT, stt);
            final PiperClient piperClient = new PiperClient();
            final TtsEngine tts = new PiperTtsEngine(piperClient);
            cleanup.register(ResourceCleanup.Resource.TTS, tts);
            final SpeechSegmentValidator speechSegmentValidator = new BasicSpeechSegmentValidator(300, 0.008, 0.02);
            final OpenAIClient localAiClient = OpenAIOkHttpClient.builder()
                    .baseUrl(AppConfig.LOCAL_AI_BASE_URL)
                    .apiKey(AppConfig.LOCAL_AI_API_KEY)
                    .build();
            final OsCommandParser osCommandParser = new LocalOsCommandParser(localAiClient);
            final AudioControlParser audioControlParser = new LocalAudioControlParser(localAiClient);
            final AssistantAudioController audioController = new AssistantAudioController();
            final ApplicationCatalog applicationCatalog = new ApplicationCatalog(
                    new WindowsApplicationDiscovery(),
                    new ApplicationAliasConfigLoader(Path.of("config", "os-applications.json")));
            applicationCatalog.refresh();
            final ApplicationRegistry applicationRegistry = new ApplicationRegistry(applicationCatalog);
            final CatalogSessionStore catalogSessions = new CatalogSessionStore();
            final ApplicationController applicationController = new WindowsApplicationController(
                    applicationCatalog::applications);
            final OsCommandSafetyGuard safetyGuard = new OsCommandSafetyGuard();
            OsCommandSkill osCommandSkill = new OsCommandSkill(
                    osCommandParser, applicationRegistry, applicationController, safetyGuard, catalogSessions);
            OutputPresentationPolicy presentationPolicy = new OutputPresentationPolicy(AppConfig.TEXT_UI_VOLUME_THRESHOLD);
            VoiceSignalStore voiceSignalStore = new VoiceSignalStore();
            VoiceInputController voiceInputController = new VoiceInputController();
            DeferredDevActionHandler devActionHandler = new DeferredDevActionHandler();
            PresentationComponents presentation = createPresentation(catalogSessions, voiceSignalStore::current, voiceInputController, devActionHandler);
            VisualOutput visualOutput = presentation.visualOutput();
            cleanup.register(ResourceCleanup.Resource.VISUAL_OUTPUT, visualOutput);
            if (presentation.javaFxRuntime() != null) {
                cleanup.register(ResourceCleanup.Resource.JAVAFX_RUNTIME,
                        presentation.javaFxRuntime());
            }
            DefaultInteractionService interactionService = new DefaultInteractionService(
                    presentation.interactionPresenter(), presentation.interactionLifecycleListener());
            InteractionVoiceRouter interactionVoiceRouter =
                    new InteractionVoiceRouter(interactionService);
            cleanup.register(ResourceCleanup.Resource.INTERACTION, interactionService);
            LmStudioStartupCoordinator modelRuntime = new LmStudioStartupCoordinator();
            AssistantVisualStateStore assistantVisualStateStore = new AssistantVisualStateStore();
            RuntimeStatusCoordinator runtimeStatusCoordinator = new RuntimeStatusCoordinator(assistantVisualStateStore::setDegraded);
            Object voiceRuntimeLock = new Object();
            AtomicBoolean applicationClosing = new AtomicBoolean(false);
            CoreVisual coreVisual = presentation.coreVisual();
            if (coreVisual != null) {
                RealCoreVisualSource coreVisualSource = new RealCoreVisualSource(new OshiHostTelemetryProvider(),
                        new NvidiaGpuTelemetryProvider(), runtimeStatusCoordinator, assistantVisualStateStore);
                cleanup.register(ResourceCleanup.Resource.CORE_VISUAL_SOURCE, coreVisualSource);
                cleanup.register(ResourceCleanup.Resource.CORE_VISUAL, coreVisual);
                coreVisual.show();
                coreVisualSource.start(coreVisual::update);
            }
            cleanup.register(ResourceCleanup.Resource.MODEL_RUNTIME, () -> {
                synchronized (voiceRuntimeLock) {
                    applicationClosing.set(true);
                    modelRuntime.close();
                }
            });

            OpenAIClient openAiClient = OpenAIOkHttpClient.fromEnv();
            AssistantEngine assistantEngine = new GptAssistantEngine(openAiClient);
            SemanticRouter semanticRouter = new GuardedSemanticRouter(new LocalSemanticRouter(localAiClient));
            SystemTimeSkill systemTimeSkill = new SystemTimeSkill();
            LocalQwenChatClient localQwenChatClient = new LocalQwenChatClient(
                    AppConfig.LOCAL_QWEN_BASE_URL,
                    AppConfig.LOCAL_AI_API_KEY,
                    AppConfig.LOCAL_QWEN_MODEL_ID,
                    modelRuntime::isQwenUsable);
            GeneralSkill generalSkill = new GeneralSkill(
                    new GptGeneralEngine(assistantEngine),
                    new QwenGeneralEngine(localQwenChatClient),
                    new DefaultGeneralBackendSelector(
                            new LocalGeneralComplexityClassifier(localAiClient)));
            AudioControlSkill audioControlSkill = new AudioControlSkill(audioControlParser, audioController);
            ResearchDepthClassifier researchDepthClassifier = new LocalResearchDepthClassifier(localAiClient);
            CurrentResearchSkill currentResearchSkill = new CurrentResearchSkill(
                    new GptWebResearchEngine(assistantEngine),
                    new QwenLocalResearchEngine(localQwenChatClient),
                    researchDepthClassifier,
                    new DefaultResearchBackendClassifier());
            SkillRegistry skillRegistry = new SkillRegistry(Map.of(
                    Capability.SYSTEM_TIME, systemTimeSkill,
                    Capability.GENERAL, generalSkill,
                    Capability.AUDIO_CONTROL, audioControlSkill,
                    Capability.OS_COMMAND, osCommandSkill,
                    Capability.CURRENT_RESEARCH, currentResearchSkill));
            SkillRouter skillRouter = new AiSkillRouter(semanticRouter, skillRegistry);
            AssistantPipeline assistantPipeline = new AssistantPipeline(skillRouter, presentation.executionLifecycleListener);
            WakeWordMatcher wakeWordMatcher = new WakeWordMatcher(
                    AppConfig.wakeWords, AppConfig.WAKE_HIGH_THRESHOLD, AppConfig.WAKE_LOW_THRESHOLD);
            WakeClassifier wakeClassifier = new LocalWakeClassifier(localAiClient);
            UtteranceClassifier utteranceClassifier = new LocalUtteranceClassifier(localAiClient);
            ActivationDetector activationDetector = new RuleBasedActivationDetector(
                    wakeWordMatcher, wakeClassifier, AppConfig.intentPhrases);
            final SileroVadEngine vad = new SileroVadEngine(AppConfig.SILERO_MODEL_PATH, AppConfig.VAD_THRESHOLD);
            cleanup.register(ResourceCleanup.Resource.VAD, vad);
            AudioDeviceInfo deviceFocusrite = new AudioDeviceManager().getInputDevices().stream()
                    .filter(device -> device.getName().contains("Analogue 1 + 2")
                            && device.getName().contains("Focusrite")
                            && !device.getName().contains("Port"))
                    .findFirst()
                    .orElseThrow();
            AudioDeviceInfo deviceOutFocusrite = new AudioDeviceManager().getOutputDevices().stream()
                    .filter(device -> device.getName().contains("Altavoces")
                            && device.getName().contains("Focusrite"))
                    .findFirst()
                    .orElseThrow();

            AudioPipeline audioPipeline = new AudioPipeline(
                    tts, playbackService, deviceOutFocusrite, audioController, assistantVisualStateStore, voiceSignalStore);
            AssistantOutputCoordinator outputCoordinator = new AssistantOutputCoordinator(audioController,
                    presentationPolicy, audioPipeline, visualOutput);
            cleanup.register(ResourceCleanup.Resource.VISUAL_OUTPUT, outputCoordinator);

            SpeechProcessingService speechProcessor = new SpeechProcessingService(stt, assistantPipeline, activationDetector,
                    new ConversationSession(), audioPipeline, speechSegmentValidator, utteranceClassifier,
                    outputCoordinator, interactionService, interactionVoiceRouter);
            devActionHandler.bind(request ->
                speechProcessor.submitDirectTurn("TOUCH // " + request.action() + " // " + request.target(),
                        () -> assistantPipeline.processDirectTurn(Capability.OS_COMMAND, () ->
                                osCommandSkill.executionAction(request.action(), request.target()))));
            cleanup.register(ResourceCleanup.Resource.SPEECH_PROCESSOR, speechProcessor);
            VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), speechProcessor, audioPipeline,
                    assistantVisualStateStore, voiceSignalStore, voiceInputController);

            AtomicBoolean voiceRuntimeStarted = new AtomicBoolean(false);
            modelRuntime.subscribe(snapshot -> {
                runtimeStatusCoordinator.updateModels(snapshot);
                visualOutput.showInfrastructureStatus(new InfrastructureStatus(snapshot, modelRuntime::retry));
                if (snapshot.isPhiUsable()) {
                    synchronized (voiceRuntimeLock) {
                        if (applicationClosing.get() || !voiceRuntimeStarted.compareAndSet(false, true)) {
                            return;
                        }
                        try {
                            runtimeStatusCoordinator.setStt(RuntimeVisualState.LOADING);
                            try {
                                client.start();
                                runtimeStatusCoordinator.setStt(RuntimeVisualState.READY);
                            }
                            catch (Exception e) {
                                runtimeStatusCoordinator.setStt(RuntimeVisualState.FAILED);
                                throw e;
                            }
                            runtimeStatusCoordinator.setTts(RuntimeVisualState.LOADING);
                            try {
                                piperClient.start();
                                runtimeStatusCoordinator.setTts(RuntimeVisualState.READY);
                            }
                            catch (Exception e) {
                                runtimeStatusCoordinator.setTts(RuntimeVisualState.FAILED);
                                throw e;
                            }
                            captureService.start(deviceFocusrite, pipeline::process);
                            System.out.println("Voice runtime active: daemon, API server and phi-router are ready");
                        }
                        catch (Exception e) {
                            voiceRuntimeStarted.set(false);
                            System.err.println("Unable to start voice runtime: " + e.getMessage());
                        }
                    }
                }
            });
            modelRuntime.startAsync();
            Thread.currentThread().join();

            System.out.println("Worker alive: " + client.isAlive());
            System.out.println("Ping: " + client.ping());
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

    private static PresentationComponents createPresentation(CatalogSessionStore catalogSessions,
                                                             Supplier<VoiceSignalSnapshot> voiceSignalSupplier,
                                                             VoiceInputController voiceInputController,
                                                             DevActionHandler devActionHandler) {
        JavaFxRuntime javaFxRuntime = null;
        JavaFxVisualOutput visualOutput = null;
        CoreVisual coreVisual = null;

        try {
            javaFxRuntime = new JavaFxRuntime();
            WindowsOverlayOwnerSupport overlayWindowSupport = WindowsOverlayOwnerSupport.platformDefault();
            var displayResolver = DefaultInteractionDisplayResolver.platformDefault(Path.of("config",
                    "interaction-display.json"));
            OverlayDisplayResolver overlayDisplayResolver =  new OverlayDisplayResolver(displayResolver, overlayWindowSupport);
            visualOutput = new JavaFxVisualOutput(catalogSessions, javaFxRuntime, overlayDisplayResolver);
            InteractionPresenter interactionPresenter = new JavaFxInteractionPresenter(javaFxRuntime, displayResolver);
            JavaFxCoreVisual javaFxCoreVisual = new JavaFxCoreVisual(javaFxRuntime, displayResolver, voiceSignalSupplier,
                    voiceInputController, devActionHandler);
            coreVisual = javaFxCoreVisual;
            return new PresentationComponents(visualOutput, interactionPresenter, coreVisual, javaFxCoreVisual,
                    javaFxCoreVisual, javaFxRuntime);
        }
        catch (Exception e) {
            System.err.println("Unable to initialize JavaFX visual output: " + e.getMessage());
            if (coreVisual != null) {
                coreVisual.close();
            }
            if (visualOutput != null) {
                visualOutput.close();
            }
            if (javaFxRuntime != null) {
                javaFxRuntime.close();
            }
        }
        return new PresentationComponents(new ConsoleVisualOutput(),
                new UnavailableInteractionPresenter("JavaFX is unavailable"), null, InteractionLifecycleListener.noop(),
                AssistantExecutionLifecycleListener.noop(), null);
    }

    private record PresentationComponents(VisualOutput visualOutput,
                                          InteractionPresenter interactionPresenter,
                                          CoreVisual coreVisual,
                                          InteractionLifecycleListener interactionLifecycleListener,
                                          AssistantExecutionLifecycleListener executionLifecycleListener,
                                          JavaFxRuntime javaFxRuntime) {
    }
}
