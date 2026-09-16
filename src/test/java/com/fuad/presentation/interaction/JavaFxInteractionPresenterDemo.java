package com.fuad.presentation.interaction;

import com.fuad.interaction.ChoiceOption;
import com.fuad.interaction.ChoiceRequest;
import com.fuad.interaction.ConfirmationRequest;
import com.fuad.interaction.FocusRequirement;
import com.fuad.interaction.InputModality;
import com.fuad.interaction.InteractionResponder;
import com.fuad.interaction.TextInputRequest;
import com.fuad.presentation.JavaFxRuntime;
import javafx.stage.Screen;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/** Visual smoke test that does not start speech, models, or the assistant pipeline. */
public final class JavaFxInteractionPresenterDemo {

    public static void main(String[] args) {
        System.out.println("Application:");
        System.out.println(JavaFxInteractionPresenterDemo.class.getModule());

        System.out.println("JavaFX Control:");
        System.out.println(javafx.scene.control.Control.class.getModule());

        System.out.println("JavaFX Paint:");
        System.out.println(javafx.scene.paint.Paint.class.getModule());

        System.out.println(javafx.scene.control.Control.class.getProtectionDomain().getCodeSource());
        System.out.println(javafx.scene.paint.Paint.class.getProtectionDomain().getCodeSource());

        try (JavaFxRuntime runtime = new JavaFxRuntime()) {
            runMenu(runtime);
        }
    }

    private static void runMenu(JavaFxRuntime runtime) {
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.println("""

                    1 — CHOICE con dos opciones
                    2 — CHOICE con diez opciones y scroll
                    3 — CONFIRMATION
                    4 — FREE_TEXT
                    0 — Salir
                    """);
            System.out.print("Escenario: ");
            if (!input.hasNextLine()) {
                return;
            }
            switch (input.nextLine().trim()) {
                case "0" -> {
                    return;
                }
                case "1" -> runScenario(runtime, (presenter, completion) -> showChoice(presenter, completion, 2));
                case "2" -> runScenario(runtime, (presenter, completion) -> showChoice(presenter, completion, 10));
                case "3" -> runScenario(runtime, JavaFxInteractionPresenterDemo::showConfirmation);
                case "4" -> runScenario(runtime, JavaFxInteractionPresenterDemo::showText);
                default -> System.out.println("Selecciona una opción de 0 a 4");
            }
        }
    }

    @FunctionalInterface
    private interface Scenario {
        void show(JavaFxInteractionPresenter presenter, CompletableFuture<Void> completion);
    }

    private static void runScenario(JavaFxRuntime runtime,Scenario scenario) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try (JavaFxInteractionPresenter presenter = new JavaFxInteractionPresenter(runtime, () -> Optional.of(new ResolvedInteractionDisplay("demo", Screen.getPrimary(), "visual demo")))) {
            scenario.show(presenter, completion);
            completion.join();
        }
    }

    private static void showChoice(JavaFxInteractionPresenter presenter, CompletableFuture<Void> completion, int count) {
        List<ChoiceOption> options = IntStream.range(0, count).mapToObj(index ->
                        new ChoiceOption(
                                "app-" + index,
                                index == 0
                                        ? "Visual Studio Code"
                                        : "Aplicación " + (index + 1),
                                Optional.of(
                                        index == 0
                                                ? "Editor de código"
                                                : "Detalle opcional"
                                ),
                                List.of()
                        )
                )
                .toList();
        ChoiceRequest request = new ChoiceRequest(
                Optional.empty(),
                "Se encontraron varias aplicaciones compatibles",
                Set.of(InputModality.TOUCH, InputModality.VOICE),
                Optional.empty(),
                FocusRequirement.PASSIVE,
                options
        );

        presenter.present(
                UUID.randomUUID(),
                request,
                responder(completion)
        );
    }

    private static void showConfirmation(JavaFxInteractionPresenter presenter, CompletableFuture<Void> completion) {
        ConfirmationRequest request = new ConfirmationRequest(
                Optional.empty(),
                "¿Quieres continuar con esta acción?",
                Set.of(InputModality.TOUCH, InputModality.VOICE),
                Optional.empty(),
                FocusRequirement.PASSIVE,
                "CONTINUAR",
                "VOLVER"
        );

        presenter.present(
                UUID.randomUUID(),
                request,
                responder(completion)
        );
    }

    private static void showText(JavaFxInteractionPresenter presenter, CompletableFuture<Void> completion) {
        TextInputRequest request = new TextInputRequest(
                Optional.empty(),
                "Escribe el nombre que quieres utilizar",
                Set.of(InputModality.TOUCH),
                Optional.empty(),
                FocusRequirement.REQUIRED,
                "",
                "Nombre",
                false,
                120
        );

        presenter.present(
                UUID.randomUUID(),
                request,
                responder(completion)
        );
    }

    private static <T> InteractionResponder<T> responder(CompletableFuture<Void> completion) {
        return new InteractionResponder<>() {

            @Override
            public void visible(UUID sessionId) {
                System.out.println("Visible: " + sessionId);
            }

            @Override
            public void submit(
                    UUID sessionId,
                    T value,
                    InputModality modality
            ) {
                System.out.println(
                        "Seleccionado: "
                                + value
                                + " mediante "
                                + modality
                );

                completion.complete(null);
            }

            @Override
            public void cancel(
                    UUID sessionId,
                    InputModality modality
            ) {
                System.out.println(
                        "Cancelado mediante " + modality
                );

                completion.complete(null);
            }

            @Override
            public void unavailable(
                    UUID sessionId,
                    String reason
            ) {
                System.err.println(
                        "No disponible: " + reason
                );

                completion.complete(null);
            }
        };
    }

    private JavaFxInteractionPresenterDemo() {
        /* Private constructor. Empty intentionally */
    }
}
