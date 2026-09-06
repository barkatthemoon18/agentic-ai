package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
public class VisualMessage {
    @NonNull
    private final String text;
    @NonNull
    private final AssistantAudioSnapshot audioSnapshot;
}
