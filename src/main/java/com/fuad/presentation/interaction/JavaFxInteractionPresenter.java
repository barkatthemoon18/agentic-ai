package com.fuad.presentation.interaction;

import com.fuad.interaction.ChoiceOption;
import com.fuad.interaction.ChoiceRequest;
import com.fuad.interaction.ConfirmationRequest;
import com.fuad.interaction.FocusRequirement;
import com.fuad.interaction.InputModality;
import com.fuad.interaction.InteractionPresenter;
import com.fuad.interaction.InteractionRequest;
import com.fuad.interaction.InteractionResponder;
import com.fuad.interaction.TextInputRequest;
import com.fuad.presentation.JavaFxRuntime;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class JavaFxInteractionPresenter implements InteractionPresenter {
    private static final String WINDOW_TITLE = "Ares Interaction Surface";

    private final JavaFxRuntime fxRuntime;
    private final InteractionDisplayResolver displayResolver;
    private final WindowsInteractionWindowSupport windowSupport;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ListChangeListener<Screen> screenListener = change -> checkDisplayStillAvailable();
    private Stage stage;
    private StackPane host;
    private RenderedSession rendered;
    private EventHandler<WindowEvent> shownHandler;

    public JavaFxInteractionPresenter(JavaFxRuntime fxRuntime,
                                      InteractionDisplayResolver displayResolver) {
        this(fxRuntime, displayResolver, WindowsInteractionWindowSupport.platformDefault());
    }

    JavaFxInteractionPresenter(JavaFxRuntime fxRuntime,
                               InteractionDisplayResolver displayResolver,
                               WindowsInteractionWindowSupport windowSupport) {
        this.fxRuntime = Objects.requireNonNull(fxRuntime);
        this.displayResolver = Objects.requireNonNull(displayResolver);
        this.windowSupport = Objects.requireNonNull(windowSupport);
        fxRuntime.runAndWait(this::createStage);
    }

    @Override
    public <T> void present(UUID sessionId, InteractionRequest<T> request,
                            InteractionResponder<T> responder) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(request);
        Objects.requireNonNull(responder);
        if (closed.get()) {
            responder.unavailable(sessionId, "Interaction presenter is closed");
            return;
        }
        fxRuntime.runLater(() -> presentInternal(sessionId, request, responder));
    }

    @Override
    public void dismiss(UUID sessionId) {
        Objects.requireNonNull(sessionId);
        if (closed.get()) {
            return;
        }
        fxRuntime.runLater(() -> dismissInternal(sessionId));
    }

    private void createStage() {
        host = new StackPane();
        host.getStyleClass().add("interaction-root");
        Scene scene = new Scene(host, Color.rgb(3, 12, 20));
        URL stylesheet = JavaFxInteractionPresenter.class.getResource("/ui/interaction.css");
        if (stylesheet == null) {
            throw new IllegalStateException("Missing resource: /ui/interaction.css");
        }
        scene.getStylesheets().add(stylesheet.toExternalForm());
        stage = new Stage(StageStyle.UNDECORATED);
        stage.setTitle(WINDOW_TITLE);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setScene(scene);
        Screen.getScreens().addListener(screenListener);
    }

    private <T> void presentInternal(UUID sessionId, InteractionRequest<T> request,
                                     InteractionResponder<T> responder) {
        if (closed.get()) {
            responder.unavailable(sessionId, "Interaction presenter is closed");
            return;
        }
        ResolvedInteractionDisplay display = displayResolver.resolve().orElse(null);
        if (display == null) {
            responder.unavailable(sessionId, "Dedicated interaction display is unavailable");
            return;
        }
        if (rendered != null) {
            dismissInternal(rendered.sessionId);
        }
        View<T> view = render(request, sessionId, responder);
        FocusRequirement effectiveFocus = request instanceof TextInputRequest
                ? FocusRequirement.REQUIRED : request.focusRequirement();
        long foregroundWindow = windowSupport.captureForegroundWindow();
        rendered = new RenderedSession(sessionId, display.id(), foregroundWindow,
                () -> responder.unavailable(sessionId,
                        "Dedicated interaction display was disconnected"));
        host.getChildren().setAll(view.root);
        Rectangle2D bounds = display.screen().getBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());

        shownHandler = new EventHandler<>() {
            @Override
            public void handle(WindowEvent event) {
                stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
                if (shownHandler == this) {
                    shownHandler = null;
                }
                if (rendered == null || !rendered.sessionId.equals(sessionId)) {
                    return;
                }
                windowSupport.configureAfterShow(WINDOW_TITLE,
                        ProcessHandle.current().pid(), effectiveFocus,
                        foregroundWindow);
                responder.visible(sessionId);
                if (effectiveFocus == FocusRequirement.REQUIRED) {
                    stage.requestFocus();
                    if (view.focusTarget != null) {
                        view.focusTarget.requestFocus();
                    }
                }
            }
        };
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, shownHandler);
        try {
            stage.show();
        }
        catch (RuntimeException e) {
            clearShownHandler();
            responder.unavailable(sessionId, "Unable to show interaction surface: " + e.getMessage());
        }
    }

    private <T> View<T> render(InteractionRequest<T> request, UUID sessionId,
                               InteractionResponder<T> responder) {
        Label title = new Label("ARES // INTERACTION");
        title.getStyleClass().add("interaction-title");
        Label prompt = new Label(request.prompt());
        prompt.setWrapText(true);
        prompt.getStyleClass().add("interaction-prompt");

        View<T> content = switch (request) {
            case ChoiceRequest choice -> castView(renderChoice(choice, sessionId,
                    castResponder(responder)));
            case ConfirmationRequest confirmation -> castView(renderConfirmation(
                    confirmation, sessionId, castResponder(responder)));
            case TextInputRequest text -> castView(renderText(text, sessionId,
                    castResponder(responder)));
        };

        Button cancel = new Button("CANCELAR");
        cancel.getStyleClass().addAll("interaction-button", "interaction-cancel");
        cancel.setOnAction(event -> responder.cancel(sessionId, InputModality.TOUCH));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(spacer, cancel);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox frame = new VBox(28, title, prompt, content.root, footer);
        frame.setPadding(new Insets(48, 64, 42, 64));
        frame.getStyleClass().add("interaction-frame");
        VBox.setVgrow(content.root, Priority.ALWAYS);
        return new View<>(frame, content.focusTarget);
    }

    private View<String> renderChoice(ChoiceRequest request, UUID sessionId,
                                      InteractionResponder<String> responder) {
        FlowPane choices = new FlowPane(22, 22);
        choices.setAlignment(Pos.CENTER);
        for (ChoiceOption option : request.options()) {
            VBox labels = new VBox(6);
            labels.setAlignment(Pos.CENTER);
            Label name = new Label(option.label());
            name.getStyleClass().add("interaction-choice-name");
            labels.getChildren().add(name);
            option.detail().ifPresent(detail -> {
                Label description = new Label(detail);
                description.getStyleClass().add("interaction-choice-detail");
                labels.getChildren().add(description);
            });
            Button button = new Button();
            button.setGraphic(labels);
            button.getStyleClass().addAll("interaction-button", "interaction-choice");
            button.setOnAction(event -> responder.submit(sessionId,
                    option.id(), InputModality.TOUCH));
            choices.getChildren().add(button);
        }
        BorderPane container = new BorderPane(choices);
        return new View<>(container, null);
    }

    private View<Boolean> renderConfirmation(ConfirmationRequest request, UUID sessionId,
                                             InteractionResponder<Boolean> responder) {
        Button reject = actionButton(request.rejectLabel(),
                () -> responder.submit(sessionId, false, InputModality.TOUCH));
        Button confirm = actionButton(request.confirmLabel(),
                () -> responder.submit(sessionId, true, InputModality.TOUCH));
        HBox actions = new HBox(36, reject, confirm);
        actions.setAlignment(Pos.CENTER);
        return new View<>(actions, null);
    }

    private View<String> renderText(TextInputRequest request, UUID sessionId,
                                    InteractionResponder<String> responder) {
        TextArea editor = new TextArea(request.initialValue());
        editor.setPromptText(request.placeholder());
        editor.setWrapText(true);
        editor.getStyleClass().add("interaction-editor");
        editor.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= request.maxLength() ? change : null));

        VBox keyboard = createKeyboard(editor);

        Button send = actionButton("ENVIAR", () -> {
            String value = editor.getText().trim();
            if (request.allowBlank() || !value.isEmpty()) {
                responder.submit(sessionId, value, InputModality.TOUCH);
            }
        });
        send.disableProperty().bind(editor.textProperty().isEmpty().and(
                new javafx.beans.property.SimpleBooleanProperty(!request.allowBlank())));
        HBox submit = new HBox(send);
        submit.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(18, editor, keyboard, submit);
        VBox.setVgrow(editor, Priority.ALWAYS);
        return new View<>(content, editor);
    }

    private VBox createKeyboard(TextArea editor) {
        VBox keyboard = new VBox(10);
        keyboard.setAlignment(Pos.CENTER);
        AtomicBoolean shifted = new AtomicBoolean(false);
        AtomicBoolean symbols = new AtomicBoolean(false);
        AtomicReference<Runnable> rebuild = new AtomicReference<>();
        rebuild.set(() -> {
            GridPane keys = new GridPane();
            keys.setHgap(10);
            keys.setVgap(10);
            keys.setAlignment(Pos.CENTER);
            if (symbols.get()) {
                addKeyboardRow(keys, 0, List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), editor);
                addKeyboardRow(keys, 1, List.of("@", "#", "$", "%", "&", "*", "(", ")", "-", "+"), editor);
                addKeyboardRow(keys, 2, List.of("/", "\\", ":", ";", "¿", "?", "¡", "!", "_", "."), editor);
            }
            else {
                boolean upper = shifted.get();
                addKeyboardRow(keys, 0, letters(List.of("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), upper), editor);
                addKeyboardRow(keys, 1, letters(List.of("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"), upper), editor);
                addKeyboardRow(keys, 2, letters(List.of("z", "x", "c", "v", "b", "n", "m", "á", "é", "í", "ó", "ú"), upper), editor);
            }
            Button mode = key(symbols.get() ? "ABC" : "123", () -> {
                symbols.set(!symbols.get());
                rebuild.get().run();
            });
            Button shift = key(shifted.get() ? "⇩" : "⇧", () -> {
                shifted.set(!shifted.get());
                rebuild.get().run();
            });
            shift.setDisable(symbols.get());
            Button space = key("ESPACIO", () -> append(editor, " "));
            space.getStyleClass().add("interaction-space");
            Button backspace = key("⌫", () -> {
                int caret = editor.getCaretPosition();
                if (caret > 0) {
                    editor.deleteText(caret - 1, caret);
                }
            });
            Button clear = key("LIMPIAR", editor::clear);
            HBox controls = new HBox(10, mode, shift, clear, space, backspace);
            controls.setAlignment(Pos.CENTER);
            keyboard.getChildren().setAll(keys, controls);
        });
        rebuild.get().run();
        return keyboard;
    }

    private List<String> letters(List<String> keys, boolean uppercase) {
        return uppercase ? keys.stream().map(value -> value.toUpperCase(java.util.Locale.ROOT)).toList()
                : keys;
    }

    private void addKeyboardRow(GridPane keyboard, int row, List<String> keys,
                                TextArea editor) {
        for (int column = 0; column < keys.size(); column++) {
            String value = keys.get(column);
            keyboard.add(key(value, () -> append(editor, value)), column, row);
        }
    }

    private void append(TextArea editor, String value) {
        editor.replaceSelection(value);
    }

    private Button key(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("interaction-button", "interaction-key");
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("interaction-button", "interaction-action");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void checkDisplayStillAvailable() {
        if (rendered == null) {
            return;
        }
        ResolvedInteractionDisplay resolved = displayResolver.resolve().orElse(null);
        if (resolved == null || !rendered.displayId.equals(resolved.id())) {
            rendered.unavailable.run();
        }
    }

    private void dismissInternal(UUID sessionId) {
        // Re-check on the JavaFX thread so a delayed dismiss cannot hide a newer session.
        if (rendered == null || !rendered.sessionId.equals(sessionId)) {
            return;
        }
        RenderedSession dismissed = rendered;
        rendered = null;
        clearShownHandler();
        stage.hide();
        host.getChildren().clear();
        windowSupport.restoreForegroundWindow(dismissed.previousForegroundWindow);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        fxRuntime.runAndWait(() -> {
            Screen.getScreens().removeListener(screenListener);
            clearShownHandler();
            if (rendered != null) {
                RenderedSession dismissed = rendered;
                rendered = null;
                windowSupport.restoreForegroundWindow(dismissed.previousForegroundWindow);
            }
            stage.hide();
            stage.close();
            host.getChildren().clear();
        });
    }

    private void clearShownHandler() {
        if (shownHandler == null) {
            return;
        }
        stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, shownHandler);
        shownHandler = null;
    }

    @SuppressWarnings("unchecked")
    private static <T> InteractionResponder<T> castResponder(InteractionResponder<?> responder) {
        return (InteractionResponder<T>) responder;
    }

    @SuppressWarnings("unchecked")
    private static <T> View<T> castView(View<?> view) {
        return (View<T>) view;
    }

    private record View<T>(Node root, Node focusTarget) {
    }

    private record RenderedSession(UUID sessionId, String displayId,
                                   long previousForegroundWindow,
                                   Runnable unavailable) {
    }
}
