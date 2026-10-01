package com.fuad.presentation.core;

import com.fuad.pipeline.VoiceSignalListener;
import com.fuad.pipeline.VoiceSignalSnapshot;

import java.util.concurrent.atomic.AtomicReference;

public final class VoiceSignalStore implements VoiceSignalListener {
    private final AtomicReference<VoiceSignalSnapshot> current = new  AtomicReference<>(VoiceSignalSnapshot.silence());

    @Override
    public void onSignal(VoiceSignalSnapshot snapshot) {
        current.set(snapshot);
    }

    public VoiceSignalSnapshot current() {
        return current.get();
    }
}
