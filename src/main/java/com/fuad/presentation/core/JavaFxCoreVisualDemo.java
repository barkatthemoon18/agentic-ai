package com.fuad.presentation.core;

import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.interaction.DefaultInteractionDisplayResolver;
import com.fuad.telemetry.gpu.nvidia.NvidiaGpuTelemetryProvider;
import com.fuad.telemetry.host.oshi.OshiHostTelemetryProvider;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Control;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Scanner;

import static com.fuad.logging.LogCategory.PRESENTATION;

public class JavaFxCoreVisualDemo {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaFxCoreVisualDemo.class);

    public static void main(String[] args) throws Exception {
        LOGGER.info(PRESENTATION, "Application:");
        LOGGER.info(PRESENTATION, "{}", JavaFxCoreVisualDemo.class.getModule());

        LOGGER.info(PRESENTATION, "JavaFX Control:");
        LOGGER.info(PRESENTATION, "{}", Control.class.getModule());

        LOGGER.info(PRESENTATION, "JavaFX Graphics:");
        LOGGER.info(PRESENTATION, "{}", Canvas.class.getModule());

        var displayResolver = DefaultInteractionDisplayResolver.platformDefault(Path.of("config", "interaction-display.json"));
        try (JavaFxRuntime runtime = new JavaFxRuntime(); JavaFxCoreVisual visual = new JavaFxCoreVisual(runtime, displayResolver);
             RealCoreVisualSource source = new RealCoreVisualSource(new OshiHostTelemetryProvider(), new NvidiaGpuTelemetryProvider(), new RuntimeStatusCoordinator(), new AssistantVisualStateStore())) {
            visual.show();
            source.start(visual::update);
            LOGGER.info(PRESENTATION, """
                    
                    ARES // CORE VISUAL DEMO
                    
                    Mock telemetry activa.
                    El dashboard debería estar visible
                    en la pantalla configurada.
                    
                    Pulsa ENTER para cerrar.
                    """);
            new Scanner(System.in).nextLine();
        }
    }

    private JavaFxCoreVisualDemo() {
        /* Empty intentionally */
    }
}
