package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.assistant.AssistantPayload;
import com.fuad.audio.output.AudioDeliveryState;
import lombok.Getter;

import java.util.Objects;

@Getter
public class VisualMessage {
    private final String text;
    private final AssistantAudioSnapshot audioSnapshot;
    private final AudioDeliveryState audioDeliveryState;
    private final AssistantPayload payload;

    public VisualMessage(String text, AssistantAudioSnapshot audioSnapshot) {
        this(text, audioSnapshot, null, AudioDeliveryState.NORMAL);
    }

    public VisualMessage(String text, AssistantAudioSnapshot audioSnapshot, AssistantPayload payload) {
        this(text, audioSnapshot, payload, AudioDeliveryState.NORMAL);
    }

    public VisualMessage(String text, AssistantAudioSnapshot audioSnapshot, AssistantPayload payload,
                         AudioDeliveryState audioDeliveryState) {
        this.text = Objects.requireNonNull(text);
        this.audioSnapshot = Objects.requireNonNull(audioSnapshot);
        this.payload = payload;
        this.audioDeliveryState = Objects.requireNonNull(audioDeliveryState);
    }
}
