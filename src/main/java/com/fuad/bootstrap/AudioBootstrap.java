package com.fuad.bootstrap;

import com.fuad.assistant.session.ConversationSession;
import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AudioCaptureService;
import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioDeviceManager;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.audio.output.MediaExclusiveAudioDetector;
import com.fuad.config.AppConfig;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.pipeline.VoiceInputController;
import com.fuad.pipeline.VoicePipeline;
import com.fuad.presentation.AssistantOutputCoordinator;
import com.fuad.presentation.OutputPresentationPolicy;
import com.fuad.presentation.core.AssistantVisualStateStore;
import com.fuad.presentation.core.VoiceSignalStore;
import com.fuad.presentation.media.MediaWorkspaceController;
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

final class AudioBootstrap {
    record SpeechEngines(FasterWhisperClient sttClient, SttEngine stt, PiperClient ttsClient, TtsEngine tts) { }
    record AudioResources(AudioCaptureService capture, AudioPlaybackService playback, SpeechEngines speech,
                          SpeechSegmentValidator validator, AssistantAudioController controller,
                          VoiceSignalStore signals, VoiceInputController input) { }
    record VoiceComponents(SpeechProcessingService speechProcessor, VoicePipeline pipeline,
                           VoiceRuntimeCoordinator.VoiceStarts starts) { }

    AudioResources createResources(ResourceCleanup cleanup) {
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

        return new AudioResources(captureService, playbackService, new SpeechEngines(client, stt, piperClient, tts),
                speechSegmentValidator, new AssistantAudioController(), new VoiceSignalStore(), new VoiceInputController());
    }

    VoiceComponents createVoice(ResourceCleanup cleanup, AudioResources audio,
                                AssistantBootstrap.AssistantComponents assistant,
                                PresentationBootstrap.PresentationComponents presentation,
                                PresentationBootstrap.InteractionComponents interaction,
                                MediaWorkspaceController media, AssistantVisualStateStore activity) throws Exception {
        final SileroVadEngine vad = new SileroVadEngine(AppConfig.SILERO_MODEL_PATH, AppConfig.VAD_THRESHOLD);
        cleanup.register(ResourceCleanup.Resource.VAD, vad);
        AudioDeviceInfo deviceFocusrite = new AudioDeviceManager().getInputDevices().stream()
                .filter(device -> device.name().contains("Analogue 1 + 2")
                        && device.name().contains("Focusrite")
                        && !device.name().contains("Port"))
                .findFirst()
                .orElseThrow();
        AudioDeviceInfo deviceOutFocusrite = new AudioDeviceManager().getOutputDevices().stream()
                .filter(device -> device.name().contains("Altavoces")
                        && device.name().contains("Focusrite"))
                .findFirst()
                .orElseThrow();

        AudioPipeline audioPipeline = new AudioPipeline(audio.speech().tts(), audio.playback(), deviceOutFocusrite,
                audio.controller(), activity, audio.signals());
        AssistantOutputCoordinator output = new AssistantOutputCoordinator(audio.controller(),
                new OutputPresentationPolicy(AppConfig.TEXT_UI_VOLUME_THRESHOLD), audioPipeline,
                presentation.visualOutput(), new MediaExclusiveAudioDetector(media::current));
        cleanup.register(ResourceCleanup.Resource.VISUAL_OUTPUT, output);
        SpeechProcessingService speech = new SpeechProcessingService(audio.speech().stt(), assistant.pipeline(),
                assistant.activation(), new ConversationSession(), audioPipeline, audio.validator(),
                assistant.utterances(), output, interaction.service(), interaction.voiceRouter());
        cleanup.register(ResourceCleanup.Resource.SPEECH_PROCESSOR, speech);
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), speech, audioPipeline,
                activity, audio.signals(), audio.input());
        return new VoiceComponents(speech, pipeline, new VoiceRuntimeCoordinator.VoiceStarts(
                audio.speech().sttClient()::start, audio.speech().ttsClient()::start,
                () -> audio.capture().start(deviceFocusrite, pipeline::process)));
    }
}
