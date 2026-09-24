package com.fuad.presentation;

import com.fuad.audio.*;
import com.fuad.config.AppConfig;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.tts.piper.PiperClient;
import com.fuad.tts.piper.PiperTtsEngine;
import javafx.application.Platform;

import java.util.Scanner;

/** Run from IntelliJ with the project root and Piper's Python environment. */
public final class JavaFxVisualOutputDemo {
    private static final String LONG_MESSAGE = ("Prueba de lectura y desplazamiento. "
            + "El panel conserva el mensaje completo y limita su altura al monitor. "
            + "Mantén el cursor dentro durante más de treinta segundos: debe permanecer visible. "
            + "Al salir, continúa el tiempo restante. Usa el scroll sin que se cierre.\n\n").repeat(12);

    public static void main(String[] args) {
        try (PiperClient client = new PiperClient()) {
            AudioDeviceInfo output = new AudioDeviceManager().getOutputDevices().stream()
                    .filter(device -> device.getName().contains("Altavoces")
                            && device.getName().contains("Focusrite"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontró la salida Altavoces Focusrite"));
            System.out.println("Salida: " + output.getName());
            client.start();
            AssistantAudioController controller = new AssistantAudioController();
            OutputPresentationPolicy policy = new OutputPresentationPolicy(AppConfig.TEXT_UI_VOLUME_THRESHOLD);
            AudioPipeline audio = new AudioPipeline(new PiperTtsEngine(client),
                    new AudioPlaybackService(), output, controller);
            try (JavaFxVisualOutput visual = new JavaFxVisualOutput(null, null, null, null, false)) {
                AssistantOutputCoordinator coordinator = new AssistantOutputCoordinator(controller, policy, audio, visual);
                runMenu(controller, policy, coordinator, visual);
            }
        }
        catch (Exception e) {
            System.err.println("Demo interrumpida: " + e.getMessage());
            e.printStackTrace();
        }
        finally {
            // Also handles overlay construction failure after toolkit startup.
            Platform.exit();
        }
    }

    private static void runMenu(AssistantAudioController controller, OutputPresentationPolicy policy,
                                AssistantOutputCoordinator coordinator, VisualOutput visual) throws InterruptedException {
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.println("""

                    1 — Mute al 40%: solo texto
                    2 — Volumen 10%: audio y texto con el umbral habitual de 20
                    3 — Recuperar volumen 40%: audio y ocultamiento
                    4 — Cero, luego unmute a 40% tras cinco segundos
                    5 — Volumen en el umbral: solo audio
                    6 — Texto extenso silenciado: scroll y pausa con el cursor
                    7 — Mensaje corto silenciado: comprobar reducción
                    8 — Mensaje tras cinco segundos: foco y monitor
                    9 — Ocultar tras cinco segundos, incluso con el cursor dentro
                    0 — Salir
                    """);
            System.out.print("Escenario: ");
            if (!input.hasNextLine()) {
                return;
            }
            String choice = input.nextLine().trim();
            switch (choice) {
                case "0" -> { return; }
                case "1" -> {
                    controller.setVolume(40);
                    controller.mute();
                    present(controller, policy, coordinator, "Mi voz quedó silenciada.");
                }
                case "2" -> {
                    controller.setVolume(10);
                    present(controller, policy, coordinator, "Esta respuesta acompaña mi voz a volumen bajo.");
                }
                case "3" -> {
                    controller.setVolume(40);
                    present(controller, policy, coordinator, "Mi voz vuelve al cuarenta por ciento.");
                }
                case "4" -> {
                    controller.setVolume(40);
                    controller.setVolume(0);
                    present(controller, policy, coordinator, "Volumen cero. Recuperaré mi voz en cinco segundos.");
                    Thread.sleep(5_000);
                    controller.unmute();
                    present(controller, policy, coordinator, "Recuperé el último volumen audible: cuarenta por ciento.");
                }
                case "5" -> {
                    controller.setVolume(policy.getTextUiVolumeThreshold());
                    present(controller, policy, coordinator, "En el umbral configurado, respondo solo por audio.");
                }
                case "6", "7", "8" -> {
                    controller.setVolume(40);
                    controller.mute();
                    if (choice.equals("8")) {
                        System.out.println("Vuelve al editor y escribe; mueve el puntero al monitor que quieras probar.");
                        Thread.sleep(5_000);
                    }
                    present(controller, policy, coordinator, choice.equals("6") ? LONG_MESSAGE
                            : "Mensaje corto. Comprueba tamaño, posición y que el editor conserve el foco.");
                }
                case "9" -> {
                    System.out.println("Coloca el cursor dentro del panel. Se ocultará en cinco segundos.");
                    Thread.sleep(5_000);
                    visual.hide();
                }
                default -> System.out.println("Selecciona una opción de 0 a 9.");
            }
        }
    }

    private static void present(AssistantAudioController controller, OutputPresentationPolicy policy,
                                AssistantOutputCoordinator coordinator, String text) {
        System.out.println("Salida esperada: " + policy.resolve(controller.getSnapshot()));
        // The main thread owns scenarios; JavaFX stays free during synthesis/playback.
        coordinator.present(text);
    }

    private JavaFxVisualOutputDemo() {
    }
}
