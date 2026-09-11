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
import com.fuad.pipeline.AssistantPipeline;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.pipeline.VoicePipeline;
import com.fuad.presentation.*;
import com.fuad.speech.SpeechBuffer;
import com.fuad.speech.SpeechProcessingService;
import com.fuad.speech.validation.BasicSpeechSegmentValidator;
import com.fuad.speech.validation.SpeechSegmentValidator;
import com.fuad.stt.SttEngine;
import com.fuad.stt.fasterwhisper.FasterWhisperClient;
import com.fuad.stt.fasterwhisper.FasterWhisperSttEngine;
import com.fuad.tts.TtsEngine;
import com.fuad.tts.piper.PiperClient;
import com.fuad.tts.piper.PiperTtsEngine;
import com.fuad.vad.SileroVadEngine;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.nio.file.Path;
import java.util.Map;

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
            final ApplicationController applicationController = new WindowsApplicationController();
            final OsCommandSafetyGuard safetyGuard = new OsCommandSafetyGuard();
            OsCommandSkill osCommandSkill = new OsCommandSkill(
                    osCommandParser, applicationRegistry, applicationController, safetyGuard, catalogSessions);
            OutputPresentationPolicy presentationPolicy = new OutputPresentationPolicy(AppConfig.TEXT_UI_VOLUME_THRESHOLD);
            VisualOutput visualOutput = createVisualOutput(catalogSessions);
            cleanup.register(ResourceCleanup.Resource.VISUAL_OUTPUT, visualOutput);

            OpenAIClient openAiClient = OpenAIOkHttpClient.fromEnv();
            AssistantEngine assistantEngine = new GptAssistantEngine(openAiClient);
            SemanticRouter semanticRouter = new GuardedSemanticRouter(new LocalSemanticRouter(localAiClient));
            SystemTimeSkill systemTimeSkill = new SystemTimeSkill();
            LocalQwenChatClient localQwenChatClient = new LocalQwenChatClient(
                    AppConfig.LOCAL_QWEN_BASE_URL,
                    AppConfig.LOCAL_AI_API_KEY,
                    AppConfig.LOCAL_QWEN_MODEL_ID);
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
            AssistantPipeline assistantPipeline = new AssistantPipeline(skillRouter);
            WakeWordMatcher wakeWordMatcher = new WakeWordMatcher(
                    AppConfig.wakeWords, AppConfig.WAKE_HIGH_THRESHOLD, AppConfig.WAKE_LOW_THRESHOLD);
            WakeClassifier wakeClassifier = new LocalWakeClassifier(localAiClient);
            UtteranceClassifier utteranceClassifier = new LocalUtteranceClassifier(localAiClient);
            ActivationDetector activationDetector = new RuleBasedActivationDetector(
                    wakeWordMatcher, wakeClassifier, AppConfig.intentPhrases);
            final SileroVadEngine vad = new SileroVadEngine(AppConfig.SILERO_MODEL_PATH, AppConfig.VAD_THRESHOLD);
            cleanup.register(ResourceCleanup.Resource.VAD, vad);
            client.start();
            piperClient.start();
            System.out.println("Workers running properly");

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
                    tts, playbackService, deviceOutFocusrite, audioController);
            AssistantOutputCoordinator outputCoordinator = new AssistantOutputCoordinator(audioController,
                    presentationPolicy, audioPipeline, visualOutput);
            cleanup.register(ResourceCleanup.Resource.VISUAL_OUTPUT, outputCoordinator);

            SpeechProcessingService speechProcessor = new SpeechProcessingService(stt, assistantPipeline, activationDetector,
                    new ConversationSession(), audioPipeline, speechSegmentValidator, utteranceClassifier, outputCoordinator);
            cleanup.register(ResourceCleanup.Resource.SPEECH_PROCESSOR, speechProcessor);
            VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), speechProcessor, audioPipeline);

            captureService.start(deviceFocusrite, pipeline::process);
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

    private static VisualOutput createVisualOutput(CatalogSessionStore catalogSessions) {
        try {
            return new JavaFxVisualOutput(catalogSessions);
        }
        catch (Exception e) {
            System.err.println("Unable to initialize JavaFX visual output: " + e.getMessage());
        }
        return new ConsoleVisualOutput();
    }
}
