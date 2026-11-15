package com.fuad.audio.output;

public enum AudioReservation {
    AVAILABLE,
    POTENTIALLY_RESERVED,
    UNKNOWN;

    public boolean blockTts() {
        return this != AVAILABLE;
    }
}
