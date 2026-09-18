package com.fuad.view;

import com.fuad.view.deck.DeckAction;
import com.fuad.view.deck.DeckCategory;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AresWorkspaceView extends VBox {
    private static final double ACTION_WIDTH = 250.0;
    private static final double ACTION_HEIGHT = 112.0;
    private static final PseudoClass ACTIVE_CATEGORY = PseudoClass.getPseudoClass("active");
    private final Label toolsetLabel = new Label();
    private final HBox categoryBar = new HBox(7.0);
    private final GridPane actionGrid =  new GridPane();
    private final Label targetValue =  new Label("INTELLIJ IDEA");
    private final Label typeValue = new Label("APPLICATION");
    private final Label voiceValue = new Label();
    private final Label touchValue = new Label("READY");
    private final Label lastValue = new Label("NONE");
    private final Map<String, Button> categoryButtons = new LinkedHashMap<>();
    private final Map<String, DeckCategory> categories = createMockCategories();
    private String activeCategoryId = "dev";

    public AresWorkspaceView() {
        setSpacing(12.0);
        setPadding(new Insets(18.0));
        getStyleClass().addAll("core-panel", "quick-action-deck");
        Label title = new Label("COMMAND // DECK");
        title.getStyleClass().add("core-hud-title");
        toolsetLabel.getStyleClass().add("deck-toolset");
        categoryBar.getStyleClass().add("deck-categories");
        actionGrid.setHgap(12.0);
        actionGrid.setVgap(12.0);
        VBox context = createContextPanel();
        getChildren().addAll(title, toolsetLabel, categoryBar, actionGrid, context);
        createCategoryButtons();
        showCategory(activeCategoryId);
    }

    private void createCategoryButtons() {
        for (DeckCategory category : categories.values()) {
            Button button = new Button(category.label());
            button.getStyleClass().add("deck-category");
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
            button.setOnAction(event -> showCategory(category.id()));
            categoryButtons.put(category.id(), button);
            categoryBar.getChildren().add(button);
        }
    }

    private void showCategory(String categoryId) {
        DeckCategory category = categories.get(categoryId);
        if (category == null) {
            return;
        }
        activeCategoryId = categoryId;
        toolsetLabel.setText("TOOLSET // " + category.subtitle().toUpperCase());
        updateCategorySelection();
        actionGrid.getChildren().clear();
        List<DeckAction> actions = category.actions();
        for (int i = 0; i < actions.size(); i++) {
            Button button = createActionButton(actions.get(i));
            int column = i % 2;
            int row = i / 2;
            actionGrid.add(button, column, row);
        }
        voiceValue.setText(actions.isEmpty() ? "-" : "\"" + actions.getFirst().voiceHint() + "\"");
    }

    private void updateCategorySelection() {
        categoryButtons.forEach((id, button) -> {
            button.pseudoClassStateChanged(ACTIVE_CATEGORY, id.equalsIgnoreCase(activeCategoryId));
        });
    }

    private Button createActionButton(DeckAction action) {
        Label symbol = new Label(action.symbol());
        symbol.getStyleClass().add("core-action-icon");
        Label name = new Label(action.name());
        name.getStyleClass().add("core-action-name");
        Label detail = new Label(action.detail());
        detail.getStyleClass().add("core-action-detail");

        Region accent = new Region();
        accent.getStyleClass().add("core-action-accent");
        accent.setPrefHeight(2.0);
        accent.setMaxWidth(54.0);

        VBox content = new VBox(5.0, accent, symbol, name, detail);
        content.setAlignment(Pos.CENTER);
        Button button = new Button();
        button.setGraphic(content);
        button.setPrefSize(ACTION_WIDTH, ACTION_HEIGHT);
        button.setMinSize(ACTION_WIDTH, ACTION_HEIGHT);
        button.getStyleClass().add("core-action");
        button.setOnTouchPressed(event -> previewAction(action));
        button.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (Boolean.TRUE.equals(focused)) {
                previewAction(action);
            }
        });
        button.setOnAction(event -> {
            lastValue.setText(action.name().toUpperCase());
            System.out.println("Mock action: " + action.id());
        });
        return button;
    }

    private VBox createContextPanel() {
        Label title = new Label("ACTION // CONTEXT");
        title.getStyleClass().add("deck-context-title");
        GridPane values = new GridPane();
        values.setHgap(18.0);
        values.setVgap(7.0);
        addContextRow(values, 0, "TARGET", targetValue);
        addContextRow(values, 1, "TYPE", typeValue);
        addContextRow(values, 2, "VOICE", voiceValue);
        addContextRow(values, 3, "TOUCH", touchValue);
        addContextRow(values, 4, "LAST", lastValue);
        VBox context = new VBox(10.0, title, values);
        context.setPadding(new Insets(15.0, 15.0, 22.0, 15.0));
        context.getStyleClass().add("deck-context");
        targetValue.getStyleClass().add("deck-context-target");
        context.setPrefHeight(145.0);
        context.setMinHeight(145.0);
        context.setMaxHeight(145.0);
        return context;
    }

    private static void addContextRow(GridPane grid, int row, String name, Label value) {
        Label key = new Label(name);
        key.getStyleClass().add("deck-context-key");
        value.getStyleClass().add("deck-context-value");
        grid.add(key, 0, row);
        grid.add(value, 1, row);
    }

    private void previewAction(DeckAction action) {
        targetValue.setText(action.name().toUpperCase());
        typeValue.setText("APPLICATION");
        voiceValue.setText("\"" + action.voiceHint() + "\"");
    }

    private static Map<String, DeckCategory> createMockCategories() {
        Map<String, DeckCategory> result = new LinkedHashMap<>();

        result.put("dev", new DeckCategory("dev", "DEV", "Development", List.of(
                new DeckAction(
                        "open-intellij",
                        "IJ",
                        "IntelliJ IDEA",
                        "JAVA / KOTLIN",
                        "ABRIR INTELLIJ"
                ),
                new DeckAction(
                        "open-vscode",
                        "VS",
                        "VS Code",
                        "CODE EDITOR",
                        "ABRIR VS CODE"
                ),
                new DeckAction(
                        "open-terminal",
                        ">_",
                        "Terminal",
                        "POWERSHELL",
                        "ABRIR TERMINAL"
                ),
                new DeckAction(
                        "open-github",
                        "GH",
                        "GitHub",
                        "REPOSITORIES",
                        "ABRIR GITHUB"
                ),
                new DeckAction(
                        "open-explorer",
                        "EX",
                        "Explorer",
                        "PROJECT FILES",
                        "ABRIR EXPLORADOR"
                ),
                new DeckAction(
                        "more-dev",
                        "···",
                        "Más",
                        "ALL TOOLS",
                        "MOSTRAR MÁS"
                )
        )));
        result.put("media", new DeckCategory("media", "MEDIA", "Media", List.of()));
        result.put("files",  new DeckCategory("files", "FILES", "Files", List.of()));
        result.put("system",  new DeckCategory("system", "SYSTEM", "System", List.of()));
        result.put("web",  new DeckCategory("web", "WEB", "Web", List.of()));
        return result;
    }
}
