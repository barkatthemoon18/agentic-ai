package com.fuad.presentation.keyboard;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Contextual touch surface with optional host actions for voice and the Command Deck. */
public final class AresKeyboardView extends Pane {
    public static final double WIDTH = 1040;
    public static final double HEIGHT = 424;
    private static final double FRAME_INSET = 16;
    private static final double FRAME_RADIUS = 32;
    // Command Deck palette (ui/core-visual.css).
    private static final Color CYAN = Color.web("#78ecff");
    private static final Color ACTIVE = Color.web("#39d5ff");
    private static final Color LABEL = Color.web("#eaf8fc");
    private static final Color SECONDARY = Color.web("#b7d6df");
    private static final Color SOFT = Color.web("#7699a7");
    private static final Color AMBER = Color.web("#d9b06c");
    private static final double HUB_X = 158;
    private static final double HUB_Y = 216;
    private static final String[] TARGETS = {"WEB", "MUSIC", "FILE", "APPS"};
    private static final String[] ACTION_IDS = {"web", "music", "file", "apps"};
    private static final String[] ACTION_NAMES = {"Buscar en\nla Web", "Buscar\nmúsica", "Abrir\narchivo", "Abrir\naplicación"};
    private static final String[] MODEL_NAMES = {"Qwen 2.5 14B", "Phi 4 14B", "Gemma 2 9B", "Llama 3.2 11B"};
    private static final List<String> SHORTCUT_IDS = List.of(
            "mcp-deck", "mcp-unmute", "mcp-weather", "mcp-dj", "mcp-stub");

    private enum Mode { QUERY, MEDIA, FILES, SYSTEM }

    private Mode mode = Mode.QUERY;
    private String query = "";
    private String feedbackMessage = "";
    private int selectedAction;
    private int activeModel;
    private boolean voiceMuted = true;
    private String selectedShortcut = "";
    private int focusedShortcut = -1;
    private Text feedback;
    private Circle feedbackIndicator;
    private TextField queryField;
    private final Map<String, Runnable> actions = new HashMap<>();
    private final AresKeyboardTraceLayer traces = new AresKeyboardTraceLayer();
    private final Runnable onUnmute;
    private final Runnable onFocusDeck;

    public AresKeyboardView() {
        this(null, null);
    }

    public AresKeyboardView(Runnable onUnmute, Runnable onFocusDeck) {
        this.onUnmute = onUnmute;
        this.onFocusDeck = onFocusDeck;
        setPickOnBounds(false);
        setStyle("-fx-background-color: transparent;");
        getStylesheets().add(Objects.requireNonNull(AresKeyboardView.class.getResource("/ui/response.css"),
                "Missing resource: /ui/response.css").toExternalForm());
        // Contain interactive light at the panel edge while leaving the window halo free.
        traces.setClip(frameClip());
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setOnMouseMoved(event -> traces.respondToPointer(new Point2D(event.getX(), event.getY())));
        setOnMouseExited(event -> traces.clearPointer());
        render();
    }

    public void setAnimationsEnabled(boolean enabled) {
        traces.setAnimationsEnabled(enabled);
    }

    public boolean areAnimationsEnabled() {
        return traces.areAnimationsEnabled();
    }

    public void setAnimationTime(Duration time) {
        traces.jumpTo(time);
    }

    public Duration getAnimationTime() {
        return traces.getAnimationTime();
    }

    public void setVoiceMuted(boolean muted) {
        if (voiceMuted == muted) return;
        voiceMuted = muted;
        if (muted && selectedShortcut.equals("unmute")) selectedShortcut = "";
        feedbackMessage = muted ? "VOICE // MUTED" : "VOICE // ON";
        render();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String value) {
        query = Objects.requireNonNull(value, "query");
        if (queryField != null && !queryField.getText().equals(value)) {
            queryField.setText(value);
            queryField.positionCaret(value.length());
        }
    }

    public String getMode() {
        return mode.name();
    }

    public void setMode(String value) {
        mode = Mode.valueOf(Objects.requireNonNull(value, "mode").toUpperCase(Locale.ROOT));
        selectedAction = mode.ordinal();
        selectedShortcut = "";
        feedbackMessage = "";
        render();
    }

    /** Also used by the demo's interaction checks; invokes the same callback as a touch. */
    public void activate(String id) {
        Runnable action = actions.get(id);
        if (action == null) {
            throw new IllegalArgumentException("Unknown keyboard control: " + id);
        }
        Node control = lookup("#" + id);
        if (control == null || !control.isDisabled()) action.run();
    }

    private void render() {
        Node focused = getScene() == null ? null : getScene().getFocusOwner();
        String focusedId = focused == null ? null : focused.getId();
        int caret = queryField == null ? 0 : queryField.getCaretPosition();
        actions.clear();
        getChildren().clear();
        drawFrame();
        drawHeader();
        drawRadialCluster();
        drawInput();
        drawExecute();
        drawActions();
        drawSuggestions();
        drawModels();
        getChildren().add(traces);
        drawFooter();
        if (focusedId != null) {
            Node replacement = lookup("#" + focusedId);
            if (replacement != null) {
                replacement.requestFocus();
                if (replacement == queryField) queryField.positionCaret(Math.min(caret, query.length()));
            }
        }
    }

    private static Rectangle frameClip() {
        return roundedRect(FRAME_INSET, FRAME_INSET, WIDTH - FRAME_INSET * 2,
                HEIGHT - FRAME_INSET * 2, FRAME_RADIUS * 2);
    }

    private void drawFrame() {
        Region frame = new Region();
        frame.setId("keyboard-window-frame");
        frame.getStyleClass().add("ares-frame");
        frame.setStyle("-fx-background-radius: " + FRAME_RADIUS + "px; -fx-border-radius: " + FRAME_RADIUS + "px;");
        frame.setManaged(false);
        frame.resizeRelocate(FRAME_INSET, FRAME_INSET, WIDTH - FRAME_INSET * 2, HEIGHT - FRAME_INSET * 2);
        // The rounded background also keeps hover stable in the gaps between controls.
        frame.setPickOnBounds(false);
        getChildren().add(frame);
        outline(new Path(new MoveTo(269, 117), new CubicCurveTo(288, 178, 288, 283, 274, 342)), 0.56, 0.9);
        outline(new Path(new MoveTo(731, 96), new CubicCurveTo(713, 173, 713, 281, 731, 346)), 0.56, 0.9);
        outline(new Path(new MoveTo(292, 102), new CubicCurveTo(422, 65, 574, 69, 708, 93)), 0.28, 0.7);
        outline(new Path(new MoveTo(291, 350), new CubicCurveTo(430, 330, 571, 332, 706, 349)), 0.32, 0.7);
        slashes(290, 378, 3, 0.42);
        slashes(951, 383, 3, 0.42);
    }

    /** Keeps radial reflections and controls inside their compact compartment. */
    static Path radialClip() {
        Path clip = new Path(new MoveTo(0, 68), new LineTo(278, 68), new LineTo(278, 358),
                new LineTo(0, 358), new ClosePath());
        clip.setFill(Color.BLACK);
        clip.setStroke(null);
        return clip;
    }

    private void drawHeader() {
        Text title = text("ARES  //  INPUT", 84, 61, 19, CYAN, true);
        title.setEffect(glow(6, 0.35));
        text("CONTEXTUAL", 85, 77, 9, SOFT, true);
        Circle dot = new Circle(570, 53, 3.5, voiceMuted ? AMBER : ACTIVE);
        dot.setMouseTransparent(true);
        getChildren().add(dot);
        Text voiceStatus = text(voiceMuted ? "VOICE // MUTED" : "VOICE // ON", 581, 58, 12,
                voiceMuted ? AMBER : ACTIVE, true);
        voiceStatus.setId("voice-status");
        text(TARGETS[selectedAction], 305, 77, 11, CYAN, true);
    }

    private void drawRadialCluster() {
        radialGuide(125, 35, 323, 0.48);
        radialGuide(64, 40, 325, 0.45);
        radialGuide(25, -168, 176, 0.40);
        String[] names = {"UNMUTE", "DECK", "WEATHER", "DJ", "MCP"};
        String[] ids = {"unmute", "deck", "weather", "dj", "stub"};
        double[][] angles = {{215, 264}, {267, 323}, {142, 212}, {88, 139}, {35, 85}};
        for (int i = 0; i < names.length; i++) {
            int shortcut = i;
            double start = angles[i][0];
            double end = angles[i][1];
            Point2D center = KeyboardGeometry.polar(HUB_X, HUB_Y, 94, (start + end) / 2);
            boolean active = i == 0 ? !voiceMuted : selectedShortcut.equals(ids[i]);
            addButton("mcp-" + ids[i], KeyboardGeometry.annularKey(HUB_X, HUB_Y, 68, 120, start, end),
                    new Group(AresKeyboardIcons.icon(names[i], 25, CYAN)), center, active, () -> {
                        switch (shortcut) {
                            case 0 -> unmute();
                            case 1 -> focusDeck();
                            case 2 -> prepareShortcut("weather", Mode.QUERY, "Clima en Talca", "CLIMA / WEATHER // consulta preparada");
                            case 3 -> prepareShortcut("dj", Mode.MEDIA, "Buscar en TIDAL", "DJ / TIDAL // música");
                            case 4 -> prepareShortcut("stub", Mode.SYSTEM, "Ver integraciones MCP", "MCP STUB // integraciones");
                            default -> throw new IllegalStateException("Unknown MCP shortcut");
                        }
                    });
        }
        String[] directions = {"UP", "RIGHT", "DOWN", "LEFT"};
        for (int i = 0; i < directions.length; i++) {
            String name = directions[i];
            double middle = -90 + i * 90;
            Point2D center = KeyboardGeometry.polar(HUB_X, HUB_Y, 44, middle);
            Runnable callback = switch (name) {
                case "UP" -> () -> focusShortcut(-1);
                case "DOWN" -> () -> focusShortcut(1);
                case "LEFT" -> () -> selectAction(selectedAction - 1);
                default -> () -> selectAction(selectedAction + 1);
            };
            addButton("nav-" + name.toLowerCase(Locale.ROOT),
                    KeyboardGeometry.annularKey(HUB_X, HUB_Y, 29, 59, middle - 40, middle + 40),
                    new Group(AresKeyboardIcons.icon(name, 16, CYAN)), center, false, callback);
        }
        addButton("nav-focus", new Circle(HUB_X, HUB_Y, 22),
                new Group(AresKeyboardIcons.icon("FOCUS", 20, CYAN)), new Point2D(HUB_X, HUB_Y), false,
                () -> { queryField.requestFocus(); queryField.positionCaret(query.length()); });
    }

    private void drawInput() {
        Path input = KeyboardGeometry.bowedPanel(300, 99, 394, 66, 8, 8, 8, 12);
        panel(input, 0.9, 1.3, 7);
        drawWave();
        icon("QUERY", 21, 320, 133, CYAN);
        queryField = new TextField(query);
        queryField.setId("query-input");
        queryField.setAccessibleText("Consulta o instrucción");
        queryField.setPromptText(switch (mode) {
            case QUERY -> "Escribe una consulta…";
            case MEDIA -> "Busca música…";
            case FILES -> "Busca un archivo…";
            case SYSTEM -> "Aplicación o comando…";
        });
        queryField.setFont(Font.font("JetBrains Mono", 16));
        queryField.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; "
                + "-fx-border-width: 0; -fx-padding: 0; -fx-text-fill: #eaf8fc; "
                + "-fx-prompt-text-fill: #7699a7; -fx-highlight-fill: rgba(57,213,255,0.24); "
                + "-fx-highlight-text-fill: #eaf8fc;");
        queryField.setManaged(false);
        queryField.resizeRelocate(344, 115, 326, 36);
        queryField.textProperty().addListener((observable, previous, next) -> query = next);
        queryField.setOnAction(event -> {
            traces.pulseAt(new Point2D(497, 202));
            execute();
        });
        queryField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                back();
                event.consume();
            } else if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
                focusShortcut(event.getCode() == KeyCode.UP ? -1 : 1);
                event.consume();
            }
        });
        getChildren().add(queryField);
    }

    private void drawWave() {
        Canvas wave = new Canvas(340, 18);
        GraphicsContext gc = wave.getGraphicsContext2D();
        for (int line = 0; line < 5; line++) {
            gc.setStroke(ACTIVE.deriveColor(0, 1, 1, 0.09 + line * 0.012));
            gc.setLineWidth(0.6);
            gc.beginPath();
            for (int x = 0; x <= 340; x += 4) {
                double envelope = Math.sin(Math.PI * x / 340.0);
                double y = 9 + Math.sin(x / 40.0 + line * 0.3) * envelope * (2 + line * 0.7);
                if (x == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
            }
            gc.stroke();
        }
        wave.setLayoutX(327);
        wave.setLayoutY(151);
        wave.setMouseTransparent(true);
        getChildren().add(wave);
    }

    private void drawExecute() {
        Path aura = executeShape();
        aura.setFill(null);
        aura.setStroke(ACTIVE.deriveColor(0, 1, 1, 0.7));
        aura.setStrokeWidth(2.8);
        aura.setEffect(glow(12, 0.65));
        aura.setMouseTransparent(true);
        getChildren().add(aura);
        addButton("execute", executeShape(), new Group(AresKeyboardIcons.icon("EXECUTE", 29, LABEL)),
                new Point2D(497, 202), true, this::execute);
        outline(new Path(new MoveTo(298, 183), new CubicCurveTo(284, 194, 284, 209, 298, 221)), 0.56, 1);
        outline(new Path(new MoveTo(696, 183), new CubicCurveTo(710, 194, 710, 209, 696, 221)), 0.56, 1);
    }

    private static Path executeShape() {
        return new Path(new MoveTo(328, 180), new QuadCurveTo(497, 174, 666, 180),
                new QuadCurveTo(672, 180, 677, 185), new LineTo(691, 199),
                new QuadCurveTo(694, 202, 691, 205), new LineTo(677, 219),
                new QuadCurveTo(672, 224, 666, 224), new QuadCurveTo(497, 218, 328, 224),
                new QuadCurveTo(322, 224, 317, 219), new LineTo(303, 205),
                new QuadCurveTo(300, 202, 303, 199), new LineTo(317, 185),
                new QuadCurveTo(322, 180, 328, 180), new ClosePath());
    }

    private void drawActions() {
        double left = 302;
        double width = 390;
        double gap = 10;
        double keyWidth = (width - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            int action = i;
            double x = left + i * (keyWidth + gap);
            double centerX = x + keyWidth / 2;
            double centerY = 262 + KeyboardGeometry.curveY(centerX, left, width, 8);
            Path shape = KeyboardGeometry.curvedKey(x, 235, keyWidth, 54, left, width, 8);
            Group content = new Group(AresKeyboardIcons.icon(TARGETS[i], 27,
                    selectedAction == i ? CYAN : SECONDARY));
            if (i == selectedAction) {
                Line underline = new Line(-12, 21, 12, 21);
                underline.setStroke(CYAN);
                underline.setStrokeWidth(2);
                underline.setEffect(glow(4, 0.6));
                content.getChildren().add(underline);
            }
            addButton("action-" + ACTION_IDS[i], shape, content, new Point2D(centerX, centerY),
                    selectedAction == i, () -> selectAction(action));
        }
    }

    private String[] suggestions() {
        return switch (mode) {
            case QUERY -> new String[]{"Buscar en Google", "Buscar en TIDAL", "Abrir descargas", "Ejecutar comando"};
            case MEDIA -> new String[]{"Mi música en TIDAL", "Jazz para trabajar", "Álbumes favoritos", "Mi playlist diaria"};
            case FILES -> new String[]{"Abrir descargas", "Mis documentos", "Archivos recientes", "Buscar un archivo"};
            case SYSTEM -> new String[]{"Abrir navegador", "Abrir terminal", "Configuración", "Estado del sistema"};
        };
    }

    private void drawSuggestions() {
        String[] symbols = switch (mode) {
            case QUERY -> new String[]{"QUERY", "MUSIC", "FILE", "TERMINAL"};
            case MEDIA -> new String[]{"MUSIC", "PLAY", "LAYERS", "DJ"};
            case FILES -> new String[]{"DOWNLOAD", "FILE", "LAYERS", "QUERY"};
            case SYSTEM -> new String[]{"WEB", "TERMINAL", "MANAGE", "MEMORY"};
        };
        for (int i = 0; i < 4; i++) {
            int suggestion = i;
            double x = 302 + i * 100;
            double y = 309 + KeyboardGeometry.curveY(x + 45, 302, 390, 6);
            addButton("suggestion-" + i, roundedRect(x, y, 90, 31, 25),
                    new Group(AresKeyboardIcons.icon(symbols[i], 18, CYAN)),
                    new Point2D(x + 45, y + 15.5), false, () -> chooseSuggestion(suggestion));
        }
    }

    private void drawModels() {
        icon("MEMORY", 22, 756, 57, CYAN);
        Text title = text("Estado LLMs", 776, 63, 18, CYAN, true);
        title.setId("llm-status-title");
        for (int i = 0; i < MODEL_NAMES.length; i++) {
            int model = i;
            boolean active = activeModel == i;
            double x = 746 + (i % 2) * 134;
            double y = 89 + (i / 2) * 133;
            double cx = x + 61;
            Rectangle card = roundedRect(x, y, 122, 119, 18);
            panel(card, active ? 0.78 : 0.25, active ? 1.2 : 0.8, active ? 5 : 0);
            icon("MODEL", 28, cx, y + 26, active ? CYAN : SOFT);
            Text name = centered(MODEL_NAMES[i], cx, y + 56, 13, LABEL, true);
            name.setId("model-name-" + i);
            name.setMouseTransparent(true);
            getChildren().add(name);
            Group toggle = new Group();
            // The illuminated thumb and its Stop icon indicate the active model.
            SVGPath play = AresKeyboardIcons.icon("PLAY", 13, active ? SOFT : CYAN);
            play.setLayoutX(-16);
            SVGPath stop = AresKeyboardIcons.icon("STOP", 12, active ? CYAN : SOFT);
            stop.setLayoutX(16);
            Rectangle thumb = roundedRect(active ? 1 : -31, -15, 30, 30, 24);
            thumb.setFill(ACTIVE.deriveColor(0, 1, 1, active ? 0.24 : 0.08));
            thumb.setStroke(active ? CYAN : SOFT);
            thumb.setStrokeWidth(0.9);
            toggle.getChildren().addAll(thumb, play, stop);
            addButton("model-toggle-" + i, roundedRect(cx - 34, y + 75, 68, 34, 30),
                    toggle, new Point2D(cx, y + 92), active, () -> {
                        activeModel = activeModel == model ? -1 : model;
                        feedbackMessage = MODEL_NAMES[model] + (activeModel == model ? " activo" : " en standby") + " · simulación";
                        render();
                    });
        }
    }

    private void drawFooter() {
        feedbackIndicator = new Circle(290, 357, 2.5, CYAN);
        feedbackIndicator.setEffect(glow(5, 0.65));
        feedbackIndicator.setMouseTransparent(true);
        getChildren().add(feedbackIndicator);
        feedback = text("", 303, 361, 10, SOFT, false);
        feedback.setId("keyboard-feedback");
        updateFeedback();
        getChildren().add(traces.communicationBars());
        text("DEMO", 755, 377, 9, SOFT, true);
    }

    private void updateFeedback() {
        feedback.setText(shorten(feedbackMessage, 60));
        feedback.setVisible(!feedbackMessage.isBlank());
        feedbackIndicator.setVisible(!feedbackMessage.isBlank());
    }

    private void focusShortcut(int direction) {
        focusedShortcut = focusedShortcut < 0
                ? (direction > 0 ? 0 : SHORTCUT_IDS.size() - 1)
                : Math.floorMod(focusedShortcut + direction, SHORTCUT_IDS.size());
        lookup("#" + SHORTCUT_IDS.get(focusedShortcut)).requestFocus();
    }

    private void selectAction(int index) {
        selectedAction = Math.floorMod(index, 4);
        mode = Mode.values()[selectedAction];
        selectedShortcut = "";
        feedbackMessage = "Acción seleccionada // " + ACTION_NAMES[selectedAction].replace('\n', ' ');
        render();
    }

    private void unmute() {
        if (onUnmute != null) onUnmute.run();
        voiceMuted = false;
        selectedShortcut = "unmute";
        feedbackMessage = onUnmute == null ? "VOICE // ON · demo" : "VOICE // ON";
        render();
    }

    private void focusDeck() {
        selectedShortcut = "deck";
        feedbackMessage = onFocusDeck == null ? "COMMAND DECK // foco de consulta · demo" : "COMMAND DECK // foco y cursor";
        render();
        if (onFocusDeck != null) onFocusDeck.run();
        else {
            queryField.requestFocus();
            queryField.positionCaret(query.length());
        }
    }

    private void prepareShortcut(String shortcut, Mode context, String defaultQuery, String message) {
        mode = context;
        selectedAction = context.ordinal();
        selectedShortcut = shortcut;
        if (query.isBlank()) setQuery(defaultQuery);
        feedbackMessage = message;
        render();
        queryField.requestFocus();
        queryField.positionCaret(query.length());
    }

    private void chooseSuggestion(int index) {
        String value = suggestions()[index];
        if (mode == Mode.QUERY) {
            selectedAction = index;
            mode = Mode.values()[index];
        }
        setQuery(value);
        feedbackMessage = "Sugerencia lista · pulsa Play";
        render();
        queryField.requestFocus();
        queryField.positionCaret(query.length());
    }

    private void execute() {
        if (query.isBlank()) {
            feedbackMessage = "Escribe una consulta o elige una sugerencia";
            updateFeedback();
            queryField.requestFocus();
            return;
        }
        feedbackMessage = "DEMO // " + TARGETS[selectedAction] + " · " + query.strip();
        updateFeedback();
    }

    private void home() {
        mode = Mode.QUERY;
        selectedAction = 0;
        selectedShortcut = "";
        query = "";
        feedbackMessage = "Inicio · selecciona una acción";
        render();
    }

    private void back() {
        if (!query.isEmpty()) {
            setQuery("");
            feedbackMessage = "Consulta borrada";
            updateFeedback();
        } else {
            home();
        }
    }

    private void addButton(String id, Shape shape, Group content, Point2D center, boolean active, Runnable click) {
        int shortcut = SHORTCUT_IDS.indexOf(id);
        Runnable interaction = () -> {
            if (shortcut >= 0) focusedShortcut = shortcut;
            traces.pulseAt(center);
            click.run();
        };
        Paint fill = surfaceFill(active);
        Color stroke = CYAN.deriveColor(0, 1, 1, active ? 1 : 0.60);
        shape.setFill(fill);
        shape.setStroke(stroke);
        shape.setStrokeWidth(active ? 1.8 : 1.0);
        shape.setEffect(glow(active ? 13 : 3, active ? 0.55 : 0.14));
        content.setLayoutX(center.getX());
        content.setLayoutY(center.getY());
        content.setMouseTransparent(true);
        Group button = new Group(shape, content) {
            @Override
            public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
                if (attribute == AccessibleAttribute.SELECTED && id.startsWith("model-toggle-")) {
                    return active;
                }
                return super.queryAccessibleAttribute(attribute, parameters);
            }

            @Override
            public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
                if (action == AccessibleAction.FIRE) {
                    if (!isDisabled()) interaction.run();
                }
                else super.executeAccessibleAction(action, parameters);
            }
        };
        button.setId(id);
        button.setPickOnBounds(false);
        button.setCursor(Cursor.HAND);
        button.setFocusTraversable(true);
        button.setAccessibleRole(id.startsWith("model-toggle-") ? AccessibleRole.TOGGLE_BUTTON : AccessibleRole.BUTTON);
        button.setAccessibleText(accessibleName(id));
        Tooltip tooltip = new Tooltip(button.getAccessibleText());
        tooltip.setStyle("-fx-font-family: 'JetBrains Mono'; -fx-text-fill: #eaf8fc; -fx-background-color: #071c29;");
        tooltip.setShowDelay(Duration.millis(250));
        Tooltip.install(button, tooltip);
        Runnable resting = () -> {
            boolean highlighted = button.isHover() || button.isFocused();
            shape.setFill(highlighted ? surfaceFill(true) : fill);
            shape.setStroke(highlighted ? LABEL : stroke);
            shape.setEffect(glow(highlighted ? 16 : active ? 13 : 3, highlighted ? 0.7 : active ? 0.55 : 0.14));
        };
        button.setOnMouseEntered(event -> {
            resting.run();
            traces.highlightAt(center);
        });
        button.setOnMouseExited(event -> resting.run());
        button.focusedProperty().addListener((observable, previous, next) -> {
            resting.run();
            if (next) {
                if (shortcut >= 0) focusedShortcut = shortcut;
                traces.highlightAt(center);
            }
        });
        button.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) shape.setFill(ACTIVE.deriveColor(0, 1, 1, 0.24));
        });
        button.setOnMouseReleased(event -> resting.run());
        button.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                button.requestFocus();
                interaction.run();
                event.consume();
            }
        });
        button.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                interaction.run();
            } else if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
                focusShortcut(event.getCode() == KeyCode.UP ? -1 : 1);
            } else if (event.getCode() == KeyCode.LEFT) {
                selectAction(selectedAction - 1);
            } else if (event.getCode() == KeyCode.RIGHT) {
                selectAction(selectedAction + 1);
            } else if (event.getCode() == KeyCode.ESCAPE) {
                back();
            } else return;
            event.consume();
        });
        actions.put(id, interaction);
        getChildren().add(button);
    }

    private String accessibleName(String id) {
        if (id.startsWith("action-")) {
            for (int i = 0; i < ACTION_IDS.length; i++) {
                if (id.equals("action-" + ACTION_IDS[i])) return ACTION_NAMES[i].replace('\n', ' ');
            }
        }
        if (id.startsWith("suggestion-")) return suggestions()[Integer.parseInt(id.substring(11))];
        if (id.startsWith("model-toggle-")) {
            int model = Integer.parseInt(id.substring(13));
            return (activeModel == model ? "Detener " : "Activar ") + MODEL_NAMES[model]
                    + (activeModel == model ? " · activo" : " · standby") + " · demostración";
        }
        return switch (id) {
            case "mcp-unmute" -> "UNMUTE · habilitar entrada de voz";
            case "mcp-deck" -> "DECK · enfocar Command Deck y cursor";
            case "mcp-weather" -> "CLIMA / WEATHER";
            case "mcp-dj" -> "DJ · TIDAL";
            case "mcp-stub" -> "MCP STUB · integraciones";
            case "nav-focus" -> "Enfocar consulta";
            case "nav-up" -> "Enfocar botón izquierdo anterior";
            case "nav-down" -> "Enfocar botón izquierdo siguiente";
            case "nav-right" -> "Seleccionar acción siguiente";
            case "nav-left" -> "Seleccionar acción anterior";
            case "execute" -> "Cursar consulta en demo";
            default -> id.replace('-', ' ');
        };
    }

    private void panel(Shape shape, double opacity, double strokeWidth, double halo) {
        shape.setFill(surfaceFill(false));
        shape.setStroke(CYAN.deriveColor(0, 1, 1, opacity));
        shape.setStrokeWidth(strokeWidth);
        if (halo > 0) shape.setEffect(glow(halo, 0.45));
        shape.setMouseTransparent(true);
        getChildren().add(shape);
    }

    private static Paint surfaceFill(boolean active) {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, active ? ACTIVE.deriveColor(0, 1, 1, 0.16) : Color.web("#071c29")),
                new Stop(1, Color.web("#04101b")));
    }

    private void radialGuide(double radius, double start, double end, double opacity) {
        Point2D a = KeyboardGeometry.polar(HUB_X, HUB_Y, radius, start);
        Point2D b = KeyboardGeometry.polar(HUB_X, HUB_Y, radius, end);
        Path guide = new Path(new MoveTo(a.getX(), a.getY()),
                new ArcTo(radius, radius, 0, b.getX(), b.getY(), end - start > 180, true));
        guide.setClip(radialClip());
        outline(guide, opacity, 0.7);
    }

    private void outline(Path path, double opacity, double width) {
        path.setFill(null);
        path.setStroke(CYAN.deriveColor(0, 1, 1, opacity));
        path.setStrokeWidth(width);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setMouseTransparent(true);
        getChildren().add(path);
    }

    private void slashes(double x, double y, int count, double opacity) {
        for (int i = 0; i < count; i++) {
            Path slash = new Path(new MoveTo(x + i * 12, y), new LineTo(x + i * 12 + 8, y - 8),
                    new LineTo(x + i * 12 + 14, y - 8), new LineTo(x + i * 12 + 6, y), new ClosePath());
            slash.setFill(CYAN.deriveColor(0, 1, 1, opacity));
            slash.setStroke(null);
            slash.setMouseTransparent(true);
            getChildren().add(slash);
        }
    }

    private void icon(String name, double size, double x, double y, Color color) {
        SVGPath symbol = AresKeyboardIcons.icon(name, size, color);
        symbol.setLayoutX(x);
        symbol.setLayoutY(y);
        symbol.setMouseTransparent(true);
        getChildren().add(symbol);
    }

    private static Rectangle roundedRect(double x, double y, double width, double height, double radius) {
        Rectangle rectangle = new Rectangle(x, y, width, height);
        rectangle.setArcWidth(radius);
        rectangle.setArcHeight(radius);
        return rectangle;
    }

    private static DropShadow glow(double radius, double opacity) {
        return new DropShadow(radius, ACTIVE.deriveColor(0, 1, 1, opacity));
    }

    private static Text centered(String value, double x, double y, double size, Color color, boolean bold) {
        Text node = new Text(value);
        node.setFont(Font.font("JetBrains Mono", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        node.setFill(color);
        centerText(node, x, y);
        return node;
    }

    private static void centerText(Text node, double x, double y) {
        Bounds bounds = node.getLayoutBounds();
        node.setX(node.getX() + x - (bounds.getMinX() + bounds.getMaxX()) / 2);
        node.setY(node.getY() + y - (bounds.getMinY() + bounds.getMaxY()) / 2);
    }

    private Text text(String value, double x, double y, double size, Color color, boolean bold) {
        Text node = new Text(x, y, value);
        node.setFont(Font.font("JetBrains Mono", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        node.setFill(color);
        node.setMouseTransparent(true);
        getChildren().add(node);
        return node;
    }

    private static String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
