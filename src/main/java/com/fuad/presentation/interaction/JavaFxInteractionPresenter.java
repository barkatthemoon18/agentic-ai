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
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
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
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class JavaFxInteractionPresenter implements InteractionPresenter {
    private static final String WINDOW_TITLE = "Ares Interaction Surface";
    private static final double ENTRANCE_OFFSET = 12.0;

    private final JavaFxRuntime fxRuntime;
    private final InteractionDisplayResolver displayResolver;
    private final WindowsInteractionWindowSupport windowSupport;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ListChangeListener<Screen> screenListener =
            change -> checkDisplayStillAvailable();
    private Stage stage;
    private StackPane host;
    private RenderedSession rendered;
    private EventHandler<WindowEvent> shownHandler;
    private Animation activeAnimation;
    private UUID animationSessionId;
    private PauseTransition configurationRetry;

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
        if (!closed.get()) {
            fxRuntime.runLater(() -> dismissInternal(sessionId));
        }
    }

    private void createStage() {
        host = new StackPane();
        host.getStyleClass().add("interaction-root");
        host.setPadding(new Insets(InteractionGeometry.HALO_PADDING));
        host.setPickOnBounds(false);

        Scene scene = new Scene(host);
        scene.setFill(Color.TRANSPARENT);
        URL stylesheet = JavaFxInteractionPresenter.class.getResource("/ui/interaction.css");
        if (stylesheet == null) {
            throw new IllegalStateException("Missing resource: /ui/interaction.css");
        }
        scene.getStylesheets().add(stylesheet.toExternalForm());

        stage = new Stage(StageStyle.TRANSPARENT);
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
            finishSession(rendered);
        }

        Rectangle2D visualBounds = display.screen().getVisualBounds();
        InteractionGeometry.Surface surface = surfaceOf(request);
        boolean compact = InteractionGeometry.calculate(visualBounds, surface, 0.0).compact();
        View view = render(request, sessionId, responder, compact);
        double panelWidth = InteractionGeometry.panelWidth(visualBounds, surface);
        prepareForMeasurement(view, panelWidth);
        host.getChildren().setAll(view.frame);
        host.applyCss();
        host.layout();
        double naturalHeight = Math.ceil(view.frame.prefHeight(panelWidth));
        InteractionGeometry.Layout geometry = InteractionGeometry.calculate(
                visualBounds, surface, naturalHeight);
        applyGeometry(view, geometry);

        FocusRequirement effectiveFocus = request instanceof TextInputRequest
                ? FocusRequirement.REQUIRED : request.focusRequirement();
        long foregroundWindow = windowSupport.captureForegroundWindow();
        RenderedSession session = new RenderedSession(sessionId, display.id(),
                foregroundWindow, () -> responder.unavailable(sessionId,
                "Dedicated interaction display was disconnected"));
        rendered = session;

        shownHandler = new EventHandler<>() {
            @Override
            public void handle(WindowEvent event) {
                stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
                if (shownHandler == this) {
                    shownHandler = null;
                }
                if (rendered != session) {
                    return;
                }
                fxRuntime.runLater(() -> configureVisibleSession(session, view,
                        responder, effectiveFocus, 0));
            }
        };
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, shownHandler);
        try {
            stage.show();
            playEntrance(session, view.frame);
        }
        catch (RuntimeException e) {
            finishSession(session);
            responder.unavailable(sessionId,
                    "Unable to show interaction surface: " + e.getMessage());
        }
    }

    private <T> void configureVisibleSession(RenderedSession session, View view,
                                             InteractionResponder<T> responder,
                                             FocusRequirement focusRequirement,
                                             int attempt) {
        if (rendered != session || !stage.isShowing()) {
            return;
        }
        WindowsInteractionWindowSupport.WindowConfiguration configuration =
                windowSupport.configureAfterShow(WINDOW_TITLE,
                        ProcessHandle.current().pid(), focusRequirement,
                        session.previousForegroundWindow);
        if (configuration.retrySuggested() && attempt < 4) {
            PauseTransition retry = new PauseTransition(Duration.millis(50.0));
            configurationRetry = retry;
            retry.setOnFinished(event -> {
                if (configurationRetry != retry || rendered != session) {
                    return;
                }
                configurationRetry = null;
                configureVisibleSession(session, view, responder,
                        focusRequirement, attempt + 1);
            });
            retry.play();
            return;
        }
        session.interactionWindow = configuration.interactionWindow();
        session.restoreOnDismiss = configuration.restoreOnDismiss();
        responder.visible(session.sessionId);
        if (focusRequirement == FocusRequirement.REQUIRED) {
            stage.requestFocus();
            if (view.focusTarget != null) {
                view.focusTarget.requestFocus();
            }
        }
    }

    private void prepareForMeasurement(View view, double panelWidth) {
        view.frame.setMinWidth(panelWidth);
        view.frame.setPrefWidth(panelWidth);
        view.frame.setMaxWidth(panelWidth);
        view.contentScroll.setPrefViewportWidth(Math.max(0.0, panelWidth - 40.0));
        double contentHeight = Math.max(0.0,
                view.content.prefHeight(Math.max(0.0, panelWidth - 40.0)));
        view.contentScroll.setPrefViewportHeight(contentHeight);
    }

    private void applyGeometry(View view, InteractionGeometry.Layout geometry) {
        Rectangle2D bounds = geometry.stageBounds();
        view.frame.setMinHeight(0.0);
        view.frame.setPrefHeight(geometry.panelHeight());
        view.frame.setMaxHeight(geometry.panelHeight());
        view.contentScroll.setMinHeight(0.0);
        host.setPrefSize(bounds.getWidth(), bounds.getHeight());
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        host.resize(bounds.getWidth(), bounds.getHeight());
        host.layout();
    }

    private <T> View render(InteractionRequest<T> request, UUID sessionId,
                           InteractionResponder<T> responder, boolean compact) {
        Label title = decorativeLabel("ARES // INTERACTION", "interaction-title");
        Label status = decorativeLabel(modalityStatus(request.modalities()),
                "interaction-status");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(12.0, title, headerSpacer, status);
        header.setAlignment(Pos.CENTER_LEFT);

        Region separator = new Region();
        separator.getStyleClass().add("interaction-separator");
        separator.setMouseTransparent(true);
        separator.setPickOnBounds(false);

        Label prompt = new Label(request.prompt());
        prompt.setWrapText(true);
        prompt.setMaxWidth(Double.MAX_VALUE);
        prompt.getStyleClass().add("interaction-prompt");

        Content content = switch (request) {
            case ChoiceRequest choice -> renderChoice(choice, sessionId,
                    castResponder(responder));
            case ConfirmationRequest confirmation -> renderConfirmation(
                    confirmation, sessionId, castResponder(responder));
            case TextInputRequest text -> renderText(text, sessionId,
                    castResponder(responder), compact);
        };

        ScrollPane contentScroll = new ScrollPane(content.root);
        contentScroll.getStyleClass().add("interaction-content-scroll");
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        contentScroll.setPannable(true);
        contentScroll.setMinSize(0.0, 0.0);

        Label hint = decorativeLabel(interactionHint(request), "interaction-hint");
        Button cancel = new Button("CANCELAR");
        cancel.getStyleClass().addAll("interaction-button", "interaction-cancel");
        cancel.setOnAction(event -> responder.cancel(sessionId, InputModality.TOUCH));
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(12.0, hint, footerSpacer, cancel);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox frame = new VBox(10.0, header, separator, prompt, contentScroll, footer);
        frame.setPadding(new Insets(18.0, 20.0, 16.0, 20.0));
        frame.getStyleClass().add("interaction-frame");
        if (compact) {
            frame.getStyleClass().add("interaction-compact");
        }
        VBox.setVgrow(contentScroll, Priority.ALWAYS);
        return new View(frame, contentScroll, content.root, content.focusTarget);
    }

    private Content renderChoice(ChoiceRequest request, UUID sessionId,
                                 InteractionResponder<String> responder) {
        VBox choices = new VBox(10.0);
        choices.getStyleClass().add("interaction-choices");
        double rowHeight = request.options().size() <= 4 ? 92.0 : 72.0;
        for (int index = 0; index < request.options().size(); index++) {
            ChoiceOption option = request.options().get(index);
            Label ordinal = decorativeLabel(String.format(Locale.ROOT, "%02d", index + 1),
                    "interaction-choice-index");
            Label initials = decorativeLabel(monogram(option.label()),
                    "interaction-choice-monogram");
            StackPane monogram = new StackPane(initials);
            monogram.getStyleClass().add("interaction-choice-monogram-box");
            monogram.setMouseTransparent(true);
            monogram.setPickOnBounds(false);

            Label name = decorativeLabel(option.label(), "interaction-choice-name");
            name.setMaxWidth(Double.MAX_VALUE);
            VBox labels = new VBox(3.0, name);
            labels.setAlignment(Pos.CENTER_LEFT);
            option.detail().ifPresent(detail -> {
                Label description = decorativeLabel(detail, "interaction-choice-detail");
                description.setMaxWidth(Double.MAX_VALUE);
                labels.getChildren().add(description);
            });
            labels.setMouseTransparent(true);
            labels.setPickOnBounds(false);
            HBox.setHgrow(labels, Priority.ALWAYS);

            Label chevron = decorativeLabel("›", "interaction-choice-chevron");
            HBox graphic = new HBox(14.0, ordinal, monogram, labels, chevron);
            graphic.setAlignment(Pos.CENTER_LEFT);
            graphic.setMouseTransparent(true);
            graphic.setPickOnBounds(false);
            graphic.setMaxWidth(Double.MAX_VALUE);

            Button button = new Button();
            button.setGraphic(graphic);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(rowHeight);
            button.setPrefHeight(rowHeight);
            button.setAccessibleText(option.detail()
                    .map(detail -> option.label() + ", " + detail)
                    .orElse(option.label()));
            button.getStyleClass().addAll("interaction-button", "interaction-choice");
            button.setOnAction(event -> responder.submit(sessionId,
                    option.id(), InputModality.TOUCH));
            choices.getChildren().add(button);
        }
        return new Content(choices, null);
    }

    private Content renderConfirmation(ConfirmationRequest request,
                                       UUID sessionId,
                                       InteractionResponder<Boolean> responder) {
        Button reject = actionButton(request.rejectLabel(), "interaction-reject",
                () -> responder.submit(sessionId, false, InputModality.TOUCH));
        Button confirm = actionButton(request.confirmLabel(), "interaction-confirm",
                () -> responder.submit(sessionId, true, InputModality.TOUCH));
        HBox.setHgrow(reject, Priority.ALWAYS);
        HBox.setHgrow(confirm, Priority.ALWAYS);
        reject.setMaxWidth(Double.MAX_VALUE);
        confirm.setMaxWidth(Double.MAX_VALUE);
        HBox actions = new HBox(12.0, reject, confirm);
        actions.setAlignment(Pos.CENTER);
        return new Content(actions, null);
    }

    private Content renderText(TextInputRequest request, UUID sessionId,
                               InteractionResponder<String> responder,
                               boolean compact) {
        TextArea editor = new TextArea(request.initialValue());
        editor.setPromptText(request.placeholder());
        editor.setWrapText(true);
        editor.setPrefHeight(compact ? 96.0 : 140.0);
        editor.setMinHeight(compact ? 80.0 : 112.0);
        editor.getStyleClass().add("interaction-editor");
        editor.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= request.maxLength() ? change : null));

        VBox keyboard = createKeyboard(editor, compact);
        Button send = actionButton("ENVIAR", "interaction-confirm", () -> {
            String value = editor.getText().trim();
            if (request.allowBlank() || !value.isEmpty()) {
                responder.submit(sessionId, value, InputModality.TOUCH);
            }
        });
        send.disableProperty().bind(editor.textProperty().isEmpty().and(
                new javafx.beans.property.SimpleBooleanProperty(!request.allowBlank())));
        HBox submit = new HBox(send);
        submit.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(compact ? 10.0 : 14.0, editor, keyboard, submit);
        return new Content(content, editor);
    }

    private VBox createKeyboard(TextArea editor, boolean compact) {
        double gap = compact ? 6.0 : 8.0;
        VBox keyboard = new VBox(gap);
        keyboard.setAlignment(Pos.CENTER);
        AtomicBoolean shifted = new AtomicBoolean(false);
        AtomicBoolean symbols = new AtomicBoolean(false);
        AtomicReference<Runnable> rebuild = new AtomicReference<>();
        rebuild.set(() -> {
            GridPane keys = new GridPane();
            keys.setHgap(gap);
            keys.setVgap(gap);
            keys.setAlignment(Pos.CENTER);
            if (symbols.get()) {
                addKeyboardRow(keys, 0,
                        List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), editor, compact);
                addKeyboardRow(keys, 1,
                        List.of("@", "#", "$", "%", "&", "*", "(", ")", "-", "+"), editor, compact);
                addKeyboardRow(keys, 2,
                        List.of("/", "\\", ":", ";", "¿", "?", "¡", "!", "_", "."), editor, compact);
            }
            else {
                boolean upper = shifted.get();
                addKeyboardRow(keys, 0, letters(
                        List.of("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), upper), editor, compact);
                addKeyboardRow(keys, 1, letters(
                        List.of("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"), upper), editor, compact);
                addKeyboardRow(keys, 2, letters(
                        List.of("z", "x", "c", "v", "b", "n", "m", "á", "é", "í", "ó", "ú"), upper), editor, compact);
            }
            Button mode = key(symbols.get() ? "ABC" : "123", compact, () -> {
                symbols.set(!symbols.get());
                rebuild.get().run();
            });
            Button shift = key(shifted.get() ? "⇩" : "⇧", compact, () -> {
                shifted.set(!shifted.get());
                rebuild.get().run();
            });
            shift.setDisable(symbols.get());
            Button clear = key("LIMPIAR", compact, editor::clear);
            Button space = key("ESPACIO", compact, () -> append(editor, " "));
            space.getStyleClass().add("interaction-space");
            Button backspace = key("⌫", compact, () -> {
                int caret = editor.getCaretPosition();
                if (caret > 0) {
                    editor.deleteText(caret - 1, caret);
                }
            });
            HBox controls = new HBox(gap, mode, shift, clear, space, backspace);
            controls.setAlignment(Pos.CENTER);
            keyboard.getChildren().setAll(keys, controls);
        });
        rebuild.get().run();
        return keyboard;
    }

    private List<String> letters(List<String> keys, boolean uppercase) {
        return uppercase ? keys.stream().map(value -> value.toUpperCase(Locale.ROOT)).toList()
                : keys;
    }

    private void addKeyboardRow(GridPane keyboard, int row, List<String> keys,
                                TextArea editor, boolean compact) {
        for (int column = 0; column < keys.size(); column++) {
            String value = keys.get(column);
            keyboard.add(key(value, compact, () -> append(editor, value)), column, row);
        }
    }

    private void append(TextArea editor, String value) {
        editor.replaceSelection(value);
    }

    private Button key(String text, boolean compact, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("interaction-button", "interaction-key");
        button.setMinSize(compact ? 48.0 : 58.0, compact ? 48.0 : 56.0);
        button.setPrefHeight(compact ? 48.0 : 56.0);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button actionButton(String text, String role, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("interaction-button", "interaction-action", role);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void playEntrance(RenderedSession session, Node node) {
        stopAnimation();
        node.setOpacity(0.0);
        node.setTranslateX(ENTRANCE_OFFSET);
        FadeTransition fade = new FadeTransition(Duration.millis(160.0), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        TranslateTransition movement = new TranslateTransition(Duration.millis(160.0), node);
        movement.setFromX(ENTRANCE_OFFSET);
        movement.setToX(0.0);
        ParallelTransition animation = new ParallelTransition(fade, movement);
        registerAnimation(session, node, animation, false);
        animation.play();
    }

    private void playExit(RenderedSession session, Node node) {
        stopAnimation();
        FadeTransition fade = new FadeTransition(Duration.millis(130.0), node);
        fade.setFromValue(node.getOpacity());
        fade.setToValue(0.0);
        TranslateTransition movement = new TranslateTransition(Duration.millis(130.0), node);
        movement.setFromX(node.getTranslateX());
        movement.setToX(ENTRANCE_OFFSET);
        ParallelTransition animation = new ParallelTransition(fade, movement);
        registerAnimation(session, node, animation, true);
        animation.play();
    }

    private void registerAnimation(RenderedSession session, Node node,
                                   ParallelTransition animation, boolean exits) {
        activeAnimation = animation;
        animationSessionId = session.sessionId;
        animation.setOnFinished(event -> {
            if (activeAnimation != animation || rendered != session
                    || !session.sessionId.equals(animationSessionId)) {
                return;
            }
            activeAnimation = null;
            animationSessionId = null;
            if (exits) {
                finishSession(session);
            }
            else {
                node.setOpacity(1.0);
                node.setTranslateX(0.0);
            }
        });
    }

    private void stopAnimation() {
        if (activeAnimation != null) {
            activeAnimation.stop();
            activeAnimation = null;
            animationSessionId = null;
        }
        if (!host.getChildren().isEmpty()) {
            Node node = host.getChildren().getFirst();
            node.setOpacity(1.0);
            node.setTranslateX(0.0);
        }
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
        if (rendered == null || !rendered.sessionId.equals(sessionId)) {
            return;
        }
        if (sessionId.equals(animationSessionId)) {
            stopAnimation();
        }
        clearShownHandler();
        Node node = host.getChildren().isEmpty() ? null : host.getChildren().getFirst();
        if (node == null || !stage.isShowing()) {
            finishSession(rendered);
        }
        else {
            playExit(rendered, node);
        }
    }

    private void finishSession(RenderedSession session) {
        if (rendered != session) {
            return;
        }
        stopAnimation();
        stopConfigurationRetry();
        clearShownHandler();
        if (session.restoreOnDismiss) {
            windowSupport.restoreForegroundWindow(session.previousForegroundWindow,
                    session.interactionWindow);
        }
        rendered = null;
        stage.hide();
        host.getChildren().clear();
    }

    private void stopConfigurationRetry() {
        if (configurationRetry != null) {
            configurationRetry.stop();
            configurationRetry = null;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        fxRuntime.runAndWait(() -> {
            Screen.getScreens().removeListener(screenListener);
            if (rendered != null) {
                finishSession(rendered);
            }
            else {
                stopAnimation();
                stopConfigurationRetry();
                clearShownHandler();
                stage.hide();
                host.getChildren().clear();
            }
            stage.close();
        });
    }

    private void clearShownHandler() {
        if (shownHandler != null) {
            stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, shownHandler);
            shownHandler = null;
        }
    }

    static String monogram(String label) {
        Objects.requireNonNull(label, "label must not be null");
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        label.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                token.appendCodePoint(codePoint);
            }
            else if (!token.isEmpty()) {
                tokens.add(token.toString());
                token.setLength(0);
            }
        });
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        if (tokens.isEmpty()) {
            return "--";
        }
        String raw = tokens.size() == 1
                ? firstCodePoints(tokens.getFirst(), 2)
                : firstCodePoints(tokens.getFirst(), 1)
                + firstCodePoints(tokens.getLast(), 1);
        return firstCodePoints(raw.toUpperCase(Locale.ROOT), 2);
    }

    private static String firstCodePoints(String value, int count) {
        StringBuilder result = new StringBuilder();
        value.codePoints().limit(count).forEach(result::appendCodePoint);
        return result.toString();
    }

    private static InteractionGeometry.Surface surfaceOf(InteractionRequest<?> request) {
        return switch (request) {
            case ChoiceRequest ignored -> InteractionGeometry.Surface.CHOICE;
            case ConfirmationRequest ignored -> InteractionGeometry.Surface.CONFIRMATION;
            case TextInputRequest ignored -> InteractionGeometry.Surface.FREE_TEXT;
        };
    }

    private static String modalityStatus(Set<InputModality> modalities) {
        return modalities.contains(InputModality.VOICE) ? "●  VOZ + TÁCTIL" : "●  TÁCTIL";
    }

    private static String interactionHint(InteractionRequest<?> request) {
        boolean voice = request.modalities().contains(InputModality.VOICE);
        return switch (request) {
            case ChoiceRequest ignored -> voice
                    ? "DI «PRIMERA» · TOCA UNA OPCIÓN" : "TOCA UNA OPCIÓN";
            case ConfirmationRequest ignored -> voice
                    ? "DI «SÍ» O «NO» · TOCA UNA ACCIÓN" : "TOCA UNA ACCIÓN";
            case TextInputRequest ignored -> "ESCRIBE O USA EL TECLADO TÁCTIL";
        };
    }

    private static Label decorativeLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMouseTransparent(true);
        label.setFocusTraversable(false);
        label.setPickOnBounds(false);
        return label;
    }

    @SuppressWarnings("unchecked")
    private static <T> InteractionResponder<T> castResponder(InteractionResponder<?> responder) {
        return (InteractionResponder<T>) responder;
    }

    private record Content(Region root, Node focusTarget) {
    }

    private record View(VBox frame, ScrollPane contentScroll,
                        Region content, Node focusTarget) {
    }

    private static final class RenderedSession {
        private final UUID sessionId;
        private final String displayId;
        private final long previousForegroundWindow;
        private final Runnable unavailable;
        private long interactionWindow;
        private boolean restoreOnDismiss;

        private RenderedSession(UUID sessionId, String displayId,
                                long previousForegroundWindow,
                                Runnable unavailable) {
            this.sessionId = sessionId;
            this.displayId = displayId;
            this.previousForegroundWindow = previousForegroundWindow;
            this.unavailable = unavailable;
        }
    }
}
