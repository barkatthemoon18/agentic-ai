package com.fuad.interaction;

@FunctionalInterface
public interface ChoiceVoiceResolver {
    ChoiceVoiceResolution resolve(String transcription);
}
