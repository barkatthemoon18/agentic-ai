package com.fuad.presentation;

import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.os.ApplicationCatalogPayload;
import com.fuad.assistant.skills.os.OpenApplicationsPayload;
import com.fuad.audio.output.AudioDeliveryState;
import com.fuad.audio.output.MediaExclusiveAudioDetector;
import com.fuad.enums.PresentationMode;
import com.fuad.pipeline.AudioPipeline;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.Objects;

@AllArgsConstructor
public class AssistantOutputCoordinator implements AutoCloseable {
    @NonNull
    private final AssistantAudioController audioController;
    @NonNull
    private final OutputPresentationPolicy presentationPolicy;
    @NonNull
    private final AudioPipeline audioPipeline;
    @NonNull
    private final VisualOutput visualOutput;
    @NonNull
    private final MediaExclusiveAudioDetector exclusiveAudioDetector;

    public void present(String text) {
        present(new AssistantResult(text));
    }

    public void present(AssistantResult result) {
        AssistantAudioSnapshot audioSnapshot;
        PresentationMode presentationMode;
        String text;
        boolean voiceRequested;

        Objects.requireNonNull(result, "result must not be null");
        text = Objects.requireNonNull(result.getText(), "result.getText() must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Assistant output text must not be blank");
        }

        audioSnapshot = audioController.getSnapshot();
        presentationMode = presentationPolicy.resolve(audioSnapshot);
        voiceRequested = presentationMode != PresentationMode.TEXT_ONLY;

        if (voiceRequested && exclusiveAudioDetector.isOutputReserved()) {
            System.out.println("AUDIO OUTPUT -> media playback reserved; response redirected to visual output");
            showVisualSafely(new VisualMessage(text, audioSnapshot, result.getPayload(), AudioDeliveryState.OUTPUT_RESERVED));
            return;
        }
        if (result.getPayload() instanceof ApplicationCatalogPayload) {
            showVisualSafely(new VisualMessage(text, audioSnapshot, result.getPayload()));
            if (voiceRequested) {
                audioPipeline.speak(text);
            }
            return;
        }
        if (result.getPayload() instanceof OpenApplicationsPayload openApplications) {
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
