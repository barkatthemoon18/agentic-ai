package com.fuad.presentation.core;

import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.interaction.DefaultInteractionDisplayResolver;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Control;

import java.nio.file.Path;
import java.util.Scanner;

public class JavaFxCoreVisualDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("Application:");
        System.out.println(JavaFxCoreVisualDemo.class.getModule());

        System.out.println("JavaFX Control:");
        System.out.println(Control.class.getModule());

        System.out.println("JavaFX Graphics:");
        System.out.println(Canvas.class.getModule());

        var displayResolver = DefaultInteractionDisplayResolver.platformDefault(Path.of("config", "interaction-display.json"));
        try (JavaFxRuntime runtime = new JavaFxRuntime(); JavaFxCoreVisual visual = new JavaFxCoreVisual(runtime, displayResolver);
             MockCoreVisualSource mock = new MockCoreVisualSource()) {
            visual.show();
            mock.start(visual::update);
            System.out.println("""
                    
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
