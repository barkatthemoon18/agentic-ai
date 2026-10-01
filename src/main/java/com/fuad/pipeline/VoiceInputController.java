package com.fuad.pipeline;

import java.util.concurrent.atomic.AtomicBoolean;

public final class VoiceInputController {
    private final AtomicBoolean muted = new AtomicBoolean(false);

    public boolean isMuted() {
        return muted.get();
    }

    public void setMuted(boolean muted) {
        this.muted.set(muted);
    }
}
