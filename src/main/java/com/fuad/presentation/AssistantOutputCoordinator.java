package com.fuad.presentation;

import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.os.ApplicationCatalogPayload;
import com.fuad.assistant.skills.os.OpenApplicationsPayload;
import com.fuad.audio.output.AudioDeliveryState;
import com.fuad.audio.output.MediaExclusiveAudioDetector;
import com.fuad.pipeline.AudioPipeline;

import java.util.Objects;

public class AssistantOutputCoordinator implements AutoCloseable {

    private final AssistantAudioController audioController;

    private final OutputPresentationPolicy presentationPolicy;

    private final AudioPipeline audioPipeline;

    private final VisualOutput visualOutput;

    private final MediaExclusiveAudioDetector exclusiveAudioDetector;

    public AssistantOutputCoordinator(AssistantAudioController audioController,
                                      OutputPresentationPolicy presentationPolicy,
                                      AudioPipeline audioPipeline,
                                      VisualOutput visualOutput,
                                      MediaExclusiveAudioDetector exclusiveAudioDetector) {
        this.audioController = Objects.requireNonNull(audioController, "audioController cannot be null");
        this.presentationPolicy = Objects.requireNonNull(presentationPolicy, "presentationPolicy cannot be null");
        this.audioPipeline = Objects.requireNonNull(audioPipeline, "audioPipeline cannot be null");
        this.visualOutput = Objects.requireNonNull(visualOutput, "visualOutput cannot be null");
        this.exclusiveAudioDetector = Objects.requireNonNull(exclusiveAudioDetector, "exclusiveAudioDetector cannot be null");
    }

    public void present(String text) {
        present(new AssistantResult(text));
    }

    public void present(AssistantResult result) {
        AssistantAudioSnapshot audioSnapshot;
        PresentationMode presentationMode;
        String text;
        boolean voiceRequested;

        Objects.requireNonNull(result, "result must not be null");
        text = Objects.requireNonNull(result.text(), "result.getText() must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Assistant output text must not be blank");
        }

        audioSnapshot = audioController.getSnapshot();
        presentationMode = presentationPolicy.resolve(audioSnapshot);
        voiceRequested = presentationMode != PresentationMode.TEXT_ONLY;

        if (voiceRequested && exclusiveAudioDetector.isOutputReserved()) {
            System.out.println("AUDIO OUTPUT -> media playback reserved; response redirected to visual output");
            showVisualSafely(new VisualMessage(text, audioSnapshot, result.payload(), AudioDeliveryState.OUTPUT_RESERVED));
            return;
        }
        if (result.payload() instanceof ApplicationCatalogPayload) {
            showVisualSafely(new VisualMessage(text, audioSnapshot, result.payload()));
            if (voiceRequested) {
                audioPipeline.speak(text);
            }
            return;
        }
        if (result.payload() instanceof OpenApplicationsPayload openApplications) {
            presentOpenApplications(text, audioSnapshot, openApplications, presentationMode);
            return;
        }
        switch (presentationMode) {
            case AUDIO_ONLY -> {
                hideVisualSafely();
                audioPipeline.speak(text);
            }
            case AUDIO_AND_TEXT -> {
                showVisualSafely(new VisualMessage(text, audioSnapshot));
                audioPipeline.speak(text);
            }
            case TEXT_ONLY -> showVisualSafely(new VisualMessage(text, audioSnapshot));
        }
    }

    private void presentOpenApplications(String text, AssistantAudioSnapshot audioSnapshot,
                                         OpenApplicationsPayload payload, PresentationMode presentationMode) {
        boolean forceVisual = payload.items().size() > 5;
        if (forceVisual) {
            showVisualSafely(new VisualMessage(text, audioSnapshot, payload));
            if (presentationMode != PresentationMode.TEXT_ONLY) audioPipeline.speak(text);
            return;
        }
        switch (presentationMode) {
            case AUDIO_ONLY -> {
                hideVisualSafely();
                audioPipeline.speak(text);
            }
            case AUDIO_AND_TEXT -> {
                showVisualSafely(new VisualMessage(text, audioSnapshot, payload));
                audioPipeline.speak(text);
            }
            case TEXT_ONLY -> showVisualSafely(new VisualMessage(text, audioSnapshot, payload));
        }
    }

    private void showVisualSafely(VisualMessage visualMessage) {
        try {
            visualOutput.show(visualMessage);
        }
        catch (Exception e) {
            System.err.println("Unable to show visual output: " + e.getMessage());
        }
    }

    private void hideVisualSafely() {
        try {
            visualOutput.hide();
        }
        catch (Exception e) {
            System.err.println("Unable to hide visual output: " + e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        try {
            visualOutput.close();
        }
        catch (Exception e) {
            System.err.println("Unable to close visual output: " + e.getMessage());
        }
    }
}
