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

/** Visual smoke test that does not start speech, models, or the assistant pipeline. */
public final class JavaFxInteractionPresenterDemo {

    public static void main(String[] args) {
        try (JavaFxRuntime runtime = new JavaFxRuntime();
             JavaFxInteractionPresenter presenter = new JavaFxInteractionPresenter(runtime,
                     () -> Optional.of(new ResolvedInteractionDisplay(
                             "demo", Screen.getPrimary(), "visual demo")))) {
            runMenu(presenter);
        }
    }

    private static void runMenu(JavaFxInteractionPresenter presenter) {
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
                case "0" -> { return; }
                case "1" -> showChoice(presenter, 2);
                case "2" -> showChoice(presenter, 10);
                case "3" -> showConfirmation(presenter);
                case "4" -> showText(presenter);
                default -> System.out.println("Selecciona una opción de 0 a 4.");
            }
        }
    }

    private static void showChoice(JavaFxInteractionPresenter presenter, int count) {
        List<ChoiceOption> options = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new ChoiceOption("app-" + index,
                        index == 0 ? "Visual Studio Code" : "Aplicación " + (index + 1),
                        Optional.of(index == 0 ? "Editor de código" : "Detalle opcional"),
                        List.of()))
                .toList();
        ChoiceRequest request = new ChoiceRequest(Optional.empty(),
                "Se encontraron varias aplicaciones compatibles",
                Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                FocusRequirement.PASSIVE, options);
        UUID sessionId = UUID.randomUUID();
        presenter.present(sessionId, request, responder(presenter));
    }

    private static void showConfirmation(JavaFxInteractionPresenter presenter) {
        ConfirmationRequest request = new ConfirmationRequest(Optional.empty(),
                "¿Quieres continuar con esta acción?",
                Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                FocusRequirement.PASSIVE, "CONTINUAR", "VOLVER");
        presenter.present(UUID.randomUUID(), request, responder(presenter));
    }

    private static void showText(JavaFxInteractionPresenter presenter) {
        TextInputRequest request = new TextInputRequest(Optional.empty(),
                "Escribe el nombre que quieres utilizar",
                Set.of(InputModality.TOUCH), Optional.empty(),
                FocusRequirement.REQUIRED, "", "Nombre", false, 120);
        presenter.present(UUID.randomUUID(), request, responder(presenter));
    }

    private static <T> InteractionResponder<T> responder(
            JavaFxInteractionPresenter presenter) {
        return new InteractionResponder<>() {
            @Override
            public void visible(UUID sessionId) {
                System.out.println("Visible: " + sessionId);
            }

            @Override
            public void submit(UUID sessionId, T value, InputModality modality) {
                System.out.println("Seleccionado: " + value + " mediante " + modality);
                presenter.dismiss(sessionId);
            }

            @Override
            public void cancel(UUID sessionId, InputModality modality) {
                System.out.println("Cancelado mediante " + modality);
                presenter.dismiss(sessionId);
            }

            @Override
            public void unavailable(UUID sessionId, String reason) {
                System.err.println("No disponible: " + reason);
                presenter.dismiss(sessionId);
            }
        };
    }

    private JavaFxInteractionPresenterDemo() {
    }
}
