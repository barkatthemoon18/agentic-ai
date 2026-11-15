package com.fuad.presentation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fuad.audio.AssistantAudioSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.fuad.logging.LogCategory.PRESENTATION;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleVisualOutputTest {
    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> capturedEvents;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(ConsoleVisualOutput.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        capturedEvents = new ListAppender<>();
        capturedEvents.start();
        logger.addAppender(capturedEvents);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(capturedEvents);
        capturedEvents.stop();
        logger.setLevel(originalLevel);
    }

    @Test
    void shouldPrintMutedState() {
        ConsoleVisualOutput output = new ConsoleVisualOutput();

        output.show(new VisualMessage(
                "Respuesta silenciada",
                new AssistantAudioSnapshot(40, true)));

        assertPresentationLog("TEXT UI [MUTED]:Respuesta silenciada");
    }

    @Test
    void shouldPrintLowVolumeState() {
        ConsoleVisualOutput output = new ConsoleVisualOutput();

        output.show(new VisualMessage(
                "Respuesta visible",
                new AssistantAudioSnapshot(10, false)));

        assertPresentationLog("TEXT UI [VOL 10%]:Respuesta visible");
    }

    @Test
    void shouldPrintHiddenState() {
        ConsoleVisualOutput output = new ConsoleVisualOutput();

        output.hide();

        assertPresentationLog("TEXT UI: hidden");
    }

    private void assertPresentationLog(String expectedMessage) {
        assertEquals(1, capturedEvents.list.size());
        ILoggingEvent event = capturedEvents.list.getFirst();
        assertEquals(expectedMessage, event.getFormattedMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(List.of(PRESENTATION), event.getMarkerList());
    }
}
