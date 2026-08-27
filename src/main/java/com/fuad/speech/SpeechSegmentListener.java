package com.fuad.speech;

@FunctionalInterface
public interface SpeechSegmentListener {
    void onSpeechSegment(SpeechSegment segment);
}
