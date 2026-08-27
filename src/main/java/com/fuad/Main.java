package com.fuad;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.RuleBasedActivationDetector;
import com.fuad.activation.wake.GraniteWakeClassifier;
import com.fuad.activation.wake.WakeClassifier;
import com.fuad.activation.wake.WakeWordMatch;
import com.fuad.activation.wake.WakeWordMatcher;
import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.GptAssistantEngine;
import com.fuad.assistant.routing.AiSkillRouter;
import com.fuad.assistant.routing.GraniteSemanticRouter;
import com.fuad.assistant.routing.SemanticRouter;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.assistant.skills.*;
import com.fuad.audio.AudioCaptureService;
import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioDeviceManager;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.config.AppConfig;
import com.fuad.enums.Capability;
import com.fuad.pipeline.AssistantPipeline;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.pipeline.VoicePipeline;
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

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        final AudioCaptureService captureService = new AudioCaptureService();
        final AudioPlaybackService  playbackService = new AudioPlaybackService();
        final FasterWhisperClient client = new FasterWhisperClient();
        final SttEngine stt = new FasterWhisperSttEngine(client);
        final PiperClient piperClient = new PiperClient();
        final TtsEngine tts = new PiperTtsEngine(piperClient);
        final SpeechSegmentValidator speechSegmentValidator = new BasicSpeechSegmentValidator(300, 0.008, 0.02);
        SpeechProcessingService speechProcessor = null;

        /* Init Assistant GPT */
        OpenAIClient openAiClient = OpenAIOkHttpClient.fromEnv();
        AssistantEngine assistantEngine = new GptAssistantEngine(openAiClient);
        SemanticRouter semanticRouter = new GraniteSemanticRouter();
        SystemTimeSkill systemTimeSkill = new SystemTimeSkill();
        GeneralSkill generalSkill = new GeneralSkill(assistantEngine);
        SkillRegistry skillRegistry =
                new SkillRegistry(Map.of(Capability.SYSTEM_TIME, systemTimeSkill, Capability.GENERAL, generalSkill,
                                Capability.AUDIO_CONTROL, new UnsupportedSkill(Capability.AUDIO_CONTROL),
                                Capability.OS_COMMAND, new UnsupportedSkill(Capability.OS_COMMAND),
                                Capability.CURRENT_RESEARCH, new UnsupportedSkill(Capability.CURRENT_RESEARCH)));
        SkillRouter skillRouter = new AiSkillRouter(semanticRouter, skillRegistry);
        AssistantPipeline assistantPipeline = new AssistantPipeline(assistantEngine, skillRouter);
        WakeWordMatcher wakeWordMatcher = new WakeWordMatcher(AppConfig.wakeWords, AppConfig.WAKE_HIGH_THRESHOLD, AppConfig.WAKE_LOW_THRESHOLD);
        WakeClassifier wakeClassifier = new GraniteWakeClassifier();
        ActivationDetector activationDetector = new RuleBasedActivationDetector(wakeWordMatcher, wakeClassifier,
                AppConfig.intentPhrases);
        final SileroVadEngine vad = new SileroVadEngine(AppConfig.SILERO_MODEL_PATH, AppConfig.VAD_THRESHOLD);

        String[] tests = {
                "Oye Ares, abre Spotify",
                "Oye eres, abre Spotify",
                "Oye Res, abre Spotify",
                "Oyares, abre Spotify",
                "Oeres, abre Spotify",
                "Oh ya eres, abre Spotify",
                "Spotify se está cerrando solo",
                "Ayer fui a Spotify",
                "Eres bastante rápido",
                "Oye, Spotify se está cerrando",
                "Las áreas están delimitadas",
                "Oye Juan, abre Spotify"
        };

        for (String test : tests) {
            WakeWordMatch result = wakeWordMatcher.match(test);
            System.out.printf("%-35s -> %-10s | %.2f | candidate='%s' | command='%s'%n", test, result.getStatus(),
                    result.getSimilarity(), result.getCandidate(), result.getCommand());
        }

        try {
            /* Run worker */
            client.start();
            piperClient.start();
            System.out.println("Workers running properly");

            /* Find Focusrite */
            AudioDeviceInfo deviceFocusrite = new AudioDeviceManager().getInputDevices().stream()
                    .filter(device -> device.getName().contains("Analogue 1 + 2") && device.getName()
                            .contains("Focusrite") && !device.getName().contains("Port")).findFirst().orElseThrow();
            AudioDeviceInfo deviceOutFocusrite = new AudioDeviceManager().getOutputDevices().stream()
                    .filter(device -> device.getName().contains("Altavoces") && device.getName()
                            .contains("Focusrite")).findFirst().orElseThrow();

            /* Initialize in pipeline */
            AudioPipeline audioPipeline = new AudioPipeline(tts, playbackService, deviceOutFocusrite);

            /* Processor */
            speechProcessor = new SpeechProcessingService(stt, assistantPipeline, activationDetector,
                    new ConversationSession(), audioPipeline, speechSegmentValidator);

            /* Initialize out pipeline */
            VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), speechProcessor, audioPipeline);


            /* Capture service (mic-on) */
            captureService.start(deviceFocusrite, pipeline::process);
            Thread.currentThread().join();

            System.out.println("Worker alive: " + client.isAlive());
            System.out.println("Ping: " + client.ping());
        }
        catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
        finally {
            captureService.stop();
            if (speechProcessor != null) {
                speechProcessor.close();
            }
            try {
                tts.close();
            }
            catch (Exception e) {
                System.out.println("Unable to close TTS: " + e.getMessage());
                e.printStackTrace();
            }
            stt.close();
            vad.close();
        }
    }
}
