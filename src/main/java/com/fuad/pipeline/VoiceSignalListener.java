package com.fuad.pipeline;

@FunctionalInterface
public interface VoiceSignalListener {

    void onSignal(VoiceSignalSnapshot snapshot);

    static VoiceSignalListener noop() {
        return snapshot -> { /* Empty intentionally */ };
    }
}
