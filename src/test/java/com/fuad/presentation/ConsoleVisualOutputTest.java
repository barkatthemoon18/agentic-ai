package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleVisualOutputTest {
    private PrintStream originalOut;
    private ByteArrayOutputStream capturedOutput;

    @BeforeEach
    void captureSystemOut() {
        originalOut = System.out;
        capturedOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreSystemOut() {
        System.setOut(originalOut);
    }

    @Test
    void shouldPrintMutedState() {
        ConsoleVisualOutput output = new ConsoleVisualOutput();

        output.show(new VisualMessage(
                "Respuesta silenciada",
                new AssistantAudioSnapshot(40, true)));

        assertEquals(
                "TEXT UI [MUTED]:Respuesta silenciada" + System.lineSeparator(),
                capturedOutput.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldPrintLowVolumeState() {
        ConsoleVisualOutput output = new ConsoleVisualOutput();

        output.show(new VisualMessage(
                "Respuesta visible",
                new AssistantAudioSnapshot(10, false)));

        assertEquals(
                "TEXT UI [VOL 10%]:Respuesta visible" + System.lineSeparator(),
                capturedOutput.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldPrintHiddenState() {
        ConsoleVisualOutput output = new ConsoleVisualOutput();

        output.hide();

        assertEquals(
                "TEXT UI: hidden" + System.lineSeparator(),
                capturedOutput.toString(StandardCharsets.UTF_8));
    }
}
