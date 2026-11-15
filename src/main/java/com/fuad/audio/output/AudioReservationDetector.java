package com.fuad.audio.output;

@FunctionalInterface
public interface AudioReservationDetector {
    boolean isOutputReserved();
}
