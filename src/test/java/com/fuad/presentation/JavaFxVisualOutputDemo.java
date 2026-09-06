package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class JavaFxVisualOutputDemo {

    public static void main(String[] args) {
        JavaFxVisualOutput visualOutput = new JavaFxVisualOutput();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        visualOutput.show(new VisualMessage(
                "Mi voz quedó silenciada.",
                new AssistantAudioSnapshot(40, true)));

        scheduler.schedule(
                () -> visualOutput.show(new VisualMessage(
                        "Esta respuesta aparece junto al audio porque el volumen de mi voz "
                                + "está por debajo del umbral configurado.",
                        new AssistantAudioSnapshot(10, false))),
                4,
                TimeUnit.SECONDS);

        scheduler.schedule(
                () -> visualOutput.show(new VisualMessage(
                        "Prueba de contenido extenso. El panel limita su altura y habilita "
                                + "desplazamiento vertical cuando el texto supera el espacio disponible. "
                                + "La siguiente sección repite contenido para comprobar visualmente el scroll. "
                                + "Ares conserva el mensaje completo, mantiene el encabezado visible y reinicia "
                                + "el temporizador cada vez que llega una nueva respuesta. ".repeat(4),
                        new AssistantAudioSnapshot(12, false))),
                8,
                TimeUnit.SECONDS);

        scheduler.schedule(visualOutput::hide, 18, TimeUnit.SECONDS);

        scheduler.schedule(
                () -> {
                    visualOutput.close();
                    scheduler.shutdown();
                },
                20,
                TimeUnit.SECONDS);
    }

    private JavaFxVisualOutputDemo() {
        // Utility class.
    }
}
