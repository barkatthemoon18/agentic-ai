package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.assistant.AssistantPayload;
import com.fuad.audio.output.AudioDeliveryState;

import java.util.Objects;

public record VisualMessage(
        String text,
        AssistantAudioSnapshot audioSnapshot,
        AssistantPayload payload,
        AudioDeliveryState audioDeliveryState) {

    public VisualMessage {
        text = Objects.requireNonNull(text);
        audioSnapshot = Objects.requireNonNull(audioSnapshot);
        audioDeliveryState = Objects.requireNonNull(audioDeliveryState);
    }

    public VisualMessage(String text, AssistantAudioSnapshot audioSnapshot) {
        this(text, audioSnapshot, null, AudioDeliveryState.NORMAL);
    }

    public VisualMessage(String text, AssistantAudioSnapshot audioSnapshot, AssistantPayload payload) {
        this(text, audioSnapshot, payload, AudioDeliveryState.NORMAL);
    }

}
