package com.fuad;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.ActivationResult;
import com.fuad.activation.RuleBasedActivationDetector;
import com.fuad.activation.context.ContextContinuationClassifier;
import com.fuad.activation.context.GraniteContextContinuationClassifier;
import com.fuad.activation.semantic.GraniteSemanticActivationClassifier;
import com.fuad.activation.semantic.SemanticActivationClassifier;
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
import com.fuad.assistant.skills.os.*;
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
import com.fuad.stt.TranscriptionResult;
import com.fuad.stt.fasterwhisper.FasterWhisperClient;
import com.fuad.stt.fasterwhisper.FasterWhisperSttEngine;
import com.fuad.tts.TtsEngine;
import com.fuad.tts.piper.PiperClient;
import com.fuad.tts.piper.PiperTtsEngine;
import com.fuad.vad.SileroVadEngine;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.util.List;
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
        final OpenAIClient localAiClient = OpenAIOkHttpClient.builder()
                .baseUrl("http://localhost:1234/v1")
                .apiKey("lm-studio")
                .build();
        final OsCommandParser osCommandParser = new GraniteOsCommandParser(localAiClient);
        final ApplicationDefinition spotify = new ApplicationDefinition("spotify", "Spotify",
                List.of("cmd.exe", "/c", "start", "", "spotify:"), "Spotify.exe");
        final ApplicationRegistry applicationRegistry = new ApplicationRegistry(Map.of("spotify", spotify));
        final ApplicationController applicationController = new WindowsApplicationController();
        final OsCommandSafetyGuard safetyGuard = new OsCommandSafetyGuard();
        OsCommandSkill osCommandSkill = new OsCommandSkill(osCommandParser, applicationRegistry, applicationController, safetyGuard);
        SpeechProcessingService speechProcessor = null;

        /* Init Assistant GPT */
        OpenAIClient openAiClient = OpenAIOkHttpClient.fromEnv();
        AssistantEngine assistantEngine = new GptAssistantEngine(openAiClient);
        SemanticRouter semanticRouter = new GraniteSemanticRouter(localAiClient);
        SystemTimeSkill systemTimeSkill = new SystemTimeSkill();
        GeneralSkill generalSkill = new GeneralSkill(assistantEngine);
        SkillRegistry skillRegistry =
                new SkillRegistry(Map.of(Capability.SYSTEM_TIME, systemTimeSkill, Capability.GENERAL, generalSkill,
                                Capability.AUDIO_CONTROL, new UnsupportedSkill(Capability.AUDIO_CONTROL),
                                Capability.OS_COMMAND, osCommandSkill,
                                Capability.CURRENT_RESEARCH, new UnsupportedSkill(Capability.CURRENT_RESEARCH)));
        SkillRouter skillRouter = new AiSkillRouter(semanticRouter, skillRegistry);
        AssistantPipeline assistantPipeline = new AssistantPipeline(assistantEngine, skillRouter);
        WakeWordMatcher wakeWordMatcher = new WakeWordMatcher(AppConfig.wakeWords, AppConfig.WAKE_HIGH_THRESHOLD, AppConfig.WAKE_LOW_THRESHOLD);
        WakeClassifier wakeClassifier = new GraniteWakeClassifier(localAiClient);
        SemanticActivationClassifier semanticActivationClassifier = new GraniteSemanticActivationClassifier(localAiClient);
        ContextContinuationClassifier contextContinuationClassifier = new GraniteContextContinuationClassifier(localAiClient);
        ActivationDetector activationDetector = new RuleBasedActivationDetector(wakeWordMatcher, wakeClassifier,
                semanticActivationClassifier, AppConfig.intentPhrases);
        final SileroVadEngine vad = new SileroVadEngine(AppConfig.SILERO_MODEL_PATH, AppConfig.VAD_THRESHOLD);

        /*
        String[] tests = {
                "¿Me puedes sugerir alguna canción?",
                "¿Puedes recomendarme una película?",
                "¿Podrías buscar algo sobre NVIDIA?",
                "¿Me ayudas a entender RSA?",
                "¿Qué canción me recomiendas?",
                "¿Sabes qué hora es?",

                "Puedes venir mañana si quieres",
                "Juan puede abrir Spotify",
                "Creo que puedes hacerlo",
                "Mañana podrías escuchar música",
                "Me dijeron que puedes abrir Spotify"
        };
        String[] contextTests = {
                "¿Y por qué?",
                "¿Y cuándo ocurrió?",
                "¿Y cómo funciona?",
                "Explícame eso mejor",
                "¿Qué quieres decir con eso?",
                "Dame otro ejemplo",
                "¿Y después qué pasó?",

                "Spotify se está cerrando solo",
                "Mañana voy a abrir Spotify",
                "Está lloviendo afuera",
                "Juan llegó temprano",
                "Creo que hoy voy a escuchar música"
        }; */

        String[] osTests = {
                "Abre Spotify",
                "¿Puedes abrir Spotify?",
                "Quiero que abras Spotify",

                "Cierra Spotify",
                "¿Puedes cerrar Spotify?",
                "Quiero que cierres Spotify",

                "Spotify se está cerrando solo",
                "Spotify se cerró solo",
                "Spotify está cerrado",

                "Mañana voy a cerrar Spotify",
                "Después voy a cerrar Spotify",
                "Más tarde cerraré Spotify",

                "Mañana voy a abrir Spotify",
                "Spotify se abre solo",
                "Ayer abrí Spotify",

                "Qué es Spotify"
        };

        String[] safetyTests = {
                "Abre Spotify",
                "Cierra Spotify",
                "¿Puedes cerrar Spotify?",
                "Quiero que cierres Spotify",

                "Spotify se está cerrando solo",
                "Spotify se cerró solo",
                "Spotify está cerrado",
                "Mañana voy a cerrar Spotify",
                "Después voy a cerrar Spotify",
                "Más tarde cerraré Spotify",
                "Mañana voy a abrir Spotify",
                "Ayer abrí Spotify"
        };

        for (String osTest : osTests) {
            OsCommandIntent intent = osCommandParser.parse(osTest);
            System.out.printf("OS TEST='%s' -> action=%s | target='%s'%n", osTest, intent.getAction(), intent.getTarget());
        }

        for (String safetyTest : safetyTests) {
            boolean allowed = safetyGuard.canExecute(safetyTest);
            System.out.printf("OS SAFETY='%s' -> %s%n", safetyTest, allowed ? "ALLOW" : "REJECT");
        }

        /*for (String test : tests) {
            TranscriptionResult transcriptionResult = new TranscriptionResult(test, "es", 1.0);
            ActivationResult result = activationDetector.detect(transcriptionResult);
            System.out.printf("TEST='%s' -> activated%s | type=%s | command'%s'%n", test, result.isActivated(), result.getType(), result.getCommand());
        }
        for (String contextTest : contextTests) {
            boolean continuation = contextContinuationClassifier.shouldContinue(contextTest);
            System.out.printf("CONTEXT TEST='%s -> %s%n", contextTest, continuation ? "CONTINUE" : "NONE");
        }*/

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
                    new ConversationSession(), audioPipeline, speechSegmentValidator, contextContinuationClassifier);

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
