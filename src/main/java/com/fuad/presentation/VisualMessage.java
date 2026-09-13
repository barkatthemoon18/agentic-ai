package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.assistant.AssistantPayload;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class VisualMessage {
    @NonNull
    private final String text;
    @NonNull
    private final AssistantAudioSnapshot audioSnapshot;
    private final AssistantPayload payload;

    public VisualMessage(String text, AssistantAudioSnapshot audioSnapshot) {
        this(text, audioSnapshot, null);
    }

    public VisualMessage(String text, AssistantAudioSnapshot audioSnapshot, AssistantPayload payload) {
        this.text = text;
        this.audioSnapshot = audioSnapshot;
        this.payload = payload;
    }
}
