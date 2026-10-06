package com.fuad.bootstrap;

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
import com.fuad.assistant.routing.GuardedSemanticRouter;
import com.fuad.assistant.routing.LocalSemanticRouter;
import com.fuad.assistant.routing.SemanticRouter;
import com.fuad.assistant.skills.GeneralSkill;
import com.fuad.assistant.skills.SkillRegistry;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.assistant.skills.SystemTimeSkill;
import com.fuad.assistant.skills.audio.AudioControlSkill;
import com.fuad.assistant.skills.audio.LocalAudioControlParser;
import com.fuad.assistant.skills.general.DefaultGeneralBackendSelector;
import com.fuad.assistant.skills.general.GptGeneralEngine;
import com.fuad.assistant.skills.general.LocalGeneralComplexityClassifier;
import com.fuad.assistant.skills.general.QwenGeneralEngine;
import com.fuad.assistant.skills.os.ApplicationAliasConfigLoader;
import com.fuad.assistant.skills.os.ApplicationCatalog;
import com.fuad.assistant.skills.os.ApplicationController;
import com.fuad.assistant.skills.os.ApplicationRegistry;
import com.fuad.assistant.skills.os.CatalogSessionStore;
import com.fuad.assistant.skills.os.LocalOsCommandParser;
import com.fuad.assistant.skills.os.OsCommandParser;
import com.fuad.assistant.skills.os.OsCommandSafetyGuard;
import com.fuad.assistant.skills.os.OsCommandSkill;
import com.fuad.assistant.skills.os.WindowsApplicationController;
import com.fuad.assistant.skills.os.WindowsApplicationDiscovery;
import com.fuad.assistant.skills.research.CurrentResearchSkill;
import com.fuad.assistant.skills.research.LocalResearchPlanClassifier;
import com.fuad.assistant.skills.research.OpenAiResearchEngine;
import com.fuad.assistant.skills.research.QwenLocalResearchEngine;
import com.fuad.audio.AssistantAudioController;
import com.fuad.config.AppConfig;
import com.fuad.enums.Capability;
import com.fuad.model.runtime.LmStudioStartupCoordinator;
import com.fuad.pipeline.AssistantExecutionLifecycleListener;
import com.fuad.pipeline.AssistantPipeline;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.nio.file.Path;
import java.util.Map;

final class AssistantBootstrap {
    record OsComponents(OpenAIClient localClient, OsCommandSkill skill, CatalogSessionStore catalogSessions) { }
    record AssistantComponents(AssistantPipeline pipeline, ActivationDetector activation,
                               UtteranceClassifier utterances) { }

    OsComponents createOs() {
        final OpenAIClient localAiClient = OpenAIOkHttpClient.builder()
                .baseUrl(AppConfig.LOCAL_AI_BASE_URL)
                .apiKey(AppConfig.LOCAL_AI_API_KEY)
                .build();
        final OsCommandParser osCommandParser = new LocalOsCommandParser(localAiClient);
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

        return new OsComponents(localAiClient, osCommandSkill, catalogSessions);
    }

    AssistantComponents create(OsComponents os, LmStudioStartupCoordinator modelRuntime,
                               AssistantAudioController audioController,
                               AssistantExecutionLifecycleListener executionListener) {
        OpenAIClient localAiClient = os.localClient();
        OsCommandSkill osCommandSkill = os.skill();
        OpenAIClient openAiClient = OpenAIOkHttpClient.fromEnv();
        AssistantEngine assistantEngine = new GptAssistantEngine(openAiClient);
        SemanticRouter semanticRouter = new GuardedSemanticRouter(new LocalSemanticRouter(localAiClient));
        SystemTimeSkill systemTimeSkill = new SystemTimeSkill();
        LocalQwenChatClient localQwenChatClient = new LocalQwenChatClient(
                AppConfig.LOCAL_QWEN_BASE_URL,
                AppConfig.LOCAL_AI_API_KEY,
                AppConfig.LOCAL_QWEN_MODEL_ID,
                modelRuntime::ensureQwenReady);
        GeneralSkill generalSkill = new GeneralSkill(
                new GptGeneralEngine(assistantEngine),
                new QwenGeneralEngine(localQwenChatClient),
                new DefaultGeneralBackendSelector(
                        new LocalGeneralComplexityClassifier(localAiClient)));
        AudioControlSkill audioControlSkill = new AudioControlSkill(new LocalAudioControlParser(localAiClient), audioController);
        CurrentResearchSkill currentResearchSkill = new CurrentResearchSkill(new OpenAiResearchEngine(assistantEngine),
                new QwenLocalResearchEngine(localQwenChatClient), new LocalResearchPlanClassifier(localAiClient));
        SkillRegistry skillRegistry = new SkillRegistry(Map.of(
                Capability.SYSTEM_TIME, systemTimeSkill,
                Capability.GENERAL, generalSkill,
                Capability.AUDIO_CONTROL, audioControlSkill,
                Capability.OS_COMMAND, osCommandSkill,
                Capability.CURRENT_RESEARCH, currentResearchSkill));
        SkillRouter skillRouter = new AiSkillRouter(semanticRouter, skillRegistry);
        AssistantPipeline assistantPipeline = new AssistantPipeline(skillRouter, executionListener);
        WakeWordMatcher wakeWordMatcher = new WakeWordMatcher(
                AppConfig.wakeWords, AppConfig.WAKE_HIGH_THRESHOLD, AppConfig.WAKE_LOW_THRESHOLD);
        WakeClassifier wakeClassifier = new LocalWakeClassifier(localAiClient);
        UtteranceClassifier utteranceClassifier = new LocalUtteranceClassifier(localAiClient);
        ActivationDetector activationDetector = new RuleBasedActivationDetector(
                wakeWordMatcher, wakeClassifier, AppConfig.intentPhrases);

        return new AssistantComponents(assistantPipeline, activationDetector, utteranceClassifier);
    }
}
