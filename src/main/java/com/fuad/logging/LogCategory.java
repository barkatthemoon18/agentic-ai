package com.fuad.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** Event categories rendered as [CATEGORY] by the console logging configuration. */
public final class LogCategory {
    public static final Marker APP = MarkerFactory.getMarker("APP");
    public static final Marker ACTIVATION = MarkerFactory.getMarker("ACTIVATION");
    public static final Marker ASSISTANT = MarkerFactory.getMarker("ASSISTANT");
    public static final Marker AUDIO = MarkerFactory.getMarker("AUDIO");
    public static final Marker CONVERSATION = MarkerFactory.getMarker("CONVERSATION");
    public static final Marker EVALUATION = MarkerFactory.getMarker("EVALUATION");
    public static final Marker GENERAL = MarkerFactory.getMarker("GENERAL");
    public static final Marker INTERACTION = MarkerFactory.getMarker("INTERACTION");
    public static final Marker MEDIA = MarkerFactory.getMarker("MEDIA");
    public static final Marker MODEL = MarkerFactory.getMarker("MODEL");
    public static final Marker OS = MarkerFactory.getMarker("OS");
    public static final Marker PRESENTATION = MarkerFactory.getMarker("PRESENTATION");
    public static final Marker RESEARCH = MarkerFactory.getMarker("RESEARCH");
    public static final Marker ROUTING = MarkerFactory.getMarker("ROUTING");
    public static final Marker SPEECH = MarkerFactory.getMarker("SPEECH");
    public static final Marker STT = MarkerFactory.getMarker("STT");
    public static final Marker TELEMETRY = MarkerFactory.getMarker("TELEMETRY");
    public static final Marker TTS = MarkerFactory.getMarker("TTS");
    public static final Marker VAD = MarkerFactory.getMarker("VAD");

    private LogCategory() {
    }
}
