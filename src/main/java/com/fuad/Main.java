package com.fuad;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.RuleBasedActivationDetector;
import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.GptAssistantEngine;
import com.fuad.assistant.GraniteAssistantEngine;
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
        System.out.println("Api Key: " + System.getenv("OPENAI_API_KEY"));
        OpenAIClient openAiClient = OpenAIOkHttpClient.fromEnv();
        AssistantEngine assistantEngine = new GptAssistantEngine(openAiClient);
        SemanticRouter semanticRouter = new GraniteSemanticRouter();
        SystemTimeSkill systemTimeSkill = new SystemTimeSkill();
        GeneralSkill generalSkill = new GeneralSkill(assistantEngine);
        SkillRouter skillRouter = new AiSkillRouter(semanticRouter, systemTimeSkill, generalSkill);
        AssistantPipeline assistantPipeline = new AssistantPipeline(assistantEngine, skillRouter);
        ActivationDetector activationDetector = new RuleBasedActivationDetector(AppConfig.wakeWords,
                AppConfig.intentPhrases);
        final SileroVadEngine vad = new SileroVadEngine(AppConfig.SILERO_MODEL_PATH, AppConfig.VAD_THRESHOLD);

        /*String[] tests = {
                "Oye Ares, ¿qué hora es?",
                "Pon el volumen al 40%.",
                "¿Por qué Windows me baja solo el volumen?",
                "Abre IntelliJ.",
                "¿Qué versión de Firefox tengo instalada?",
                "¿Cuál es la última versión disponible de Firefox?",
                "¿Quién fue Alan Turing?",
                "Explícame en detalle cómo funciona RSA."
        };
        for (String test : tests) {
            long start = System.nanoTime();
            Capability result = graniteAssistantEngine.classify(test);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            System.out.printf("%-65s -> %-20s %.2f ms%n", test, result, elapsedMs);
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
            client.close();
            stt.close();
            vad.close();
            if (speechProcessor != null) {
                speechProcessor.close();
            }
        }
    }
}
