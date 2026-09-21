package com.fuad.view.workspace.dev;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;

public class DevWorkspaceView extends VBox {
    private static final double ACTION_WIDTH = 250.0;
    private static final double ACTION_HEIGHT = 112.0;
    private final GridPane actionGrid = new GridPane();
    private final Label targetValue = new Label("INTELLIJ IDEA");
    private final Label typeValue = new Label("APPLICATION");
    private final Label voiceValue = new Label("\"ABRIR INTELLIJ\"");
    private final Label touchValue = new Label("READY");
    private final Label lastValue = new Label("NONE");
    private final List<QuickAction> actions = createMockActions();

    public DevWorkspaceView() {
        setSpacing(12.0);

        actionGrid.setHgap(12.0);
        actionGrid.setVgap(12.0);

        for (int i = 0; i < actions.size(); i++) {
            QuickAction action = actions.get(i);
            actionGrid.add(createActionButton(action), i % 2, i / 2);
        }
        VBox context = createContextPanel();
        getChildren().addAll(actionGrid, context);
        previewAction(actions.getFirst());
    }

    private Button createActionButton(QuickAction action) {
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

    private void previewAction(QuickAction action) {
        targetValue.setText(action.name().toUpperCase());
        typeValue.setText(action.kind().name());
        voiceValue.setText("\"" + action.voiceHint() + "\"");
    }

    private static List<QuickAction> createMockActions() {
        return List.of(
                new QuickAction(
                        "open-intellij",
                        "IJ",
                        "IntelliJ IDEA",
                        "JAVA / KOTLIN",
                        "ABRIR INTELLIJ",
                        QuickActionKind.APPLICATION
                ),
                new QuickAction(
                        "open-vscode",
                        "VS",
                        "VS Code",
                        "CODE EDITOR",
                        "ABRIR VS CODE",
                        QuickActionKind.APPLICATION
                ),
                new QuickAction(
                        "open-terminal",
                        ">_",
                        "Terminal",
                        "POWERSHELL",
                        "ABRIR TERMINAL",
                        QuickActionKind.APPLICATION
                ),
                new QuickAction(
                        "open-github",
                        "GH",
                        "GitHub",
                        "REPOSITORIES",
                        "ABRIR GITHUB",
                        QuickActionKind.APPLICATION
                ),
                new QuickAction(
                        "open-explorer",
                        "EX",
                        "Explorer",
                        "PROJECT FILES",
                        "ABRIR EXPLORADOR",
                        QuickActionKind.APPLICATION
                ),
                new QuickAction(
                        "more-dev",
                        "···",
                        "Más",
                        "ALL TOOLS",
                        "MOSTRAR MÁS",
                        QuickActionKind.NAVIGATION
                )
        );
    }
}
