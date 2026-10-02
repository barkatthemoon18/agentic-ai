package com.fuad.activation.wake;

import com.fuad.enums.WakeMatchStatus;

public record WakeWordMatch(
        WakeMatchStatus status,
        String candidate,
        String command,
        double similarity) {

    public static WakeWordMatch none() {
        return new WakeWordMatch(WakeMatchStatus.NONE, "", "", 0.0);
    }
}
