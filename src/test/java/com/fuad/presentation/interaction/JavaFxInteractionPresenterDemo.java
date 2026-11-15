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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static com.fuad.logging.LogCategory.INTERACTION;

/** Visual smoke test that does not start speech, models, or the assistant pipeline. */
public final class JavaFxInteractionPresenterDemo {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaFxInteractionPresenterDemo.class);

    public static void main(String[] args) {
        LOGGER.info(INTERACTION, "Application:");
        LOGGER.info(INTERACTION, "{}", JavaFxInteractionPresenterDemo.class.getModule());

        LOGGER.info(INTERACTION, "JavaFX Control:");
        LOGGER.info(INTERACTION, "{}", javafx.scene.control.Control.class.getModule());

        LOGGER.info(INTERACTION, "JavaFX Paint:");
        LOGGER.info(INTERACTION, "{}", javafx.scene.paint.Paint.class.getModule());

        LOGGER.info(INTERACTION, "{}", javafx.scene.control.Control.class.getProtectionDomain().getCodeSource());
        LOGGER.info(INTERACTION, "{}", javafx.scene.paint.Paint.class.getProtectionDomain().getCodeSource());

        try (JavaFxRuntime runtime = new JavaFxRuntime()) {
            runMenu(runtime);
        }
    }

    private static void runMenu(JavaFxRuntime runtime) {
        Scanner input = new Scanner(System.in);
        while (true) {
            LOGGER.info(INTERACTION, """

                    1 — CHOICE con dos opciones
                    2 — CHOICE con diez opciones y scroll
                    3 — CONFIRMATION
                    4 — FREE_TEXT
                    0 — Salir
                    """);
            LOGGER.info(INTERACTION, "Escenario: ");
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
                default -> LOGGER.info(INTERACTION, "Selecciona una opción de 0 a 4");
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
                LOGGER.info(INTERACTION, "Visible: {}", sessionId);
            }

            @Override
            public void submit(UUID sessionId, T value, InputModality modality) {
                LOGGER.info(INTERACTION, "Seleccionado: {} mediante {}", value, modality);

                completion.complete(null);
            }

            @Override
            public void cancel(
                    UUID sessionId,
                    InputModality modality
            ) {
                LOGGER.info(INTERACTION, "Cancelado mediante {}", modality);

                completion.complete(null);
            }

            @Override
            public void unavailable(
                    UUID sessionId,
                    String reason
            ) {
                LOGGER.warn(INTERACTION, "No disponible: {}", reason);

                completion.complete(null);
            }
        };
    }

    private JavaFxInteractionPresenterDemo() {
        /* Private constructor. Empty intentionally */
    }
}
