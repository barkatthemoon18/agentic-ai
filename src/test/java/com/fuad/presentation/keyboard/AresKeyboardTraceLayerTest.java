package com.fuad.presentation.keyboard;

import com.fuad.presentation.JavaFxRuntime;
import javafx.scene.Group;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AresKeyboardTraceLayerTest {
    private static JavaFxRuntime runtime;

    @BeforeAll
    static void initToolkit() {
        runtime = new JavaFxRuntime();
    }

    @Test
    void communicationBarsChangeOverTimeAndReproduceAnExportedFrameWhenSeeking() {
        runtime.runAndWait(() -> {
            AresKeyboardTraceLayer traces = new AresKeyboardTraceLayer();
            traces.setAnimationsEnabled(false);
            Group bars = traces.communicationBars();

            traces.jumpTo(Duration.ZERO);
            List<BarFrame> initial = frame(bars);
            traces.jumpTo(Duration.seconds(1.7));
            List<BarFrame> later = frame(bars);

            assertFalse(initial.isEmpty());
            assertNotEquals(initial, later, "The waiting indicator must visibly animate");
            assertTrue(later.stream().allMatch(bar -> bar.bottom() > bar.top()));
            traces.jumpTo(Duration.ZERO);
            assertEquals(initial, frame(bars), "Seeking must reproduce the same snapshot");
            assertSame(bars, traces.communicationBars());
        });
    }

    @Test
    void rebuildingTheViewPreservesTheCommunicationAnimationClock() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.setAnimationsEnabled(false);
            view.setAnimationTime(Duration.seconds(1.7));
            Group bars = assertInstanceOf(Group.class, view.lookup("#keyboard-communication-bars"));
            List<BarFrame> before = frame(bars);

            view.activate("mcp-dj");

            assertFalse(view.areAnimationsEnabled());
            assertEquals(Duration.seconds(1.7), view.getAnimationTime());
            assertSame(bars, view.lookup("#keyboard-communication-bars"));
            assertEquals(before, frame(bars));
        });
    }

    private static List<BarFrame> frame(Group bars) {
        return bars.getChildren().stream().map(node -> {
            Line line = assertInstanceOf(Line.class, node);
            return new BarFrame(line.getStartY(), line.getEndY(), line.getOpacity());
        }).toList();
    }

    private record BarFrame(double top, double bottom, double opacity) {
    }
}
