package com.fuad.view.workspace.tools;

import com.fuad.enums.OsAction;
import com.fuad.presentation.tools.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Objects;

public class ToolsWorkspaceView extends VBox {
    private static final double ACTION_WIDTH = 250.0;
    private static final double ACTION_HEIGHT = 112.0;
    private final ToolsActionHandler actionHandler;
    private final MoreToolsHandler moreToolsHandler;
    private final GridPane actionGrid = new GridPane();
    private final Label targetValue = new Label("INTELLIJ IDEA");
    private final Label typeValue = new Label("APPLICATION");
    private final Label voiceValue = new Label("\"ABRIR INTELLIJ\"");
    private final Label touchValue = new Label("READY");
    private final Label lastValue = new Label("NONE");

    public ToolsWorkspaceView() {
        this(ToolsActionHandler.unavailable());
    }

    public ToolsWorkspaceView(ToolsActionHandler actionHandler) {
        this(ToolsWorkspaceConfig.empty(), actionHandler, MoreToolsHandler.unavailable());
    }

    public ToolsWorkspaceView(ToolsWorkspaceConfig config, ToolsActionHandler actionHandler, MoreToolsHandler moreToolsHandler) {
        int i = 0;

        this.actionHandler = Objects.requireNonNull(actionHandler);
        this.moreToolsHandler = Objects.requireNonNull(moreToolsHandler);
        List<ToolApplication> featured = Objects.requireNonNull(config).featuredApplications();

        setSpacing(12.0);

        actionGrid.setHgap(12.0);
        actionGrid.setVgap(12.0);

        for (ToolApplication application : featured) {
            actionGrid.add(createApplicationButton(application), i % 2, i / 2);
            i++;
        }

        actionGrid.add(createMoreButton(), i % 2, i / 2);

        VBox context = createContextPanel();
        getChildren().addAll(actionGrid, context);

        if (!featured.isEmpty()) {
            previewApplication(featured.getFirst());
        }
    }

    private Button createApplicationButton(ToolApplication application) {
        Button button = createActionButton(symbolFor(application), application.displayName(), detailFor(application));
        button.setOnTouchPressed(event -> previewApplication(application));
        button.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (Boolean.TRUE.equals(focused)) {
                previewApplication(application);
            }
        });
        button.setOnAction(event -> {
            boolean accepted = actionHandler.submit(new ToolsActionRequest(OsAction.OPEN_APPLICATION, application.resolverTarget()));
            touchValue.setText(accepted ? "SUBMITTED" : "BUSY");
            if (accepted) {
                lastValue.setText(application.displayName().toUpperCase());
            }
        });
        return button;
    }

    private Button createMoreButton() {
        Button button = createActionButton("...", "More", "MORE TOOLS");
        button.setOnTouchPressed(event -> previewMore());
        button.setOnAction(event -> {
            boolean accepted = moreToolsHandler.open();
            touchValue.setText(accepted ? "BROWSING" : "UNAVAILABLE");
        });
        return button;
    }

    private Button createActionButton(String symbolText, String nameText, String detailText) {
        Label symbol = new Label(symbolText);
        Label name = new Label(nameText);
        Label detail = new Label(detailText);

        symbol.getStyleClass().add("core-action-icon");
        name.getStyleClass().add("core-action-name");
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

    private void previewApplication(ToolApplication application) {
        targetValue.setText(application.displayName().toUpperCase());
        typeValue.setText("APPLICATION");
        voiceValue.setText("\"ABRIR " + application.displayName().toUpperCase() + "\"");
    }

    private void previewMore() {
        targetValue.setText("MORE TOOLS");
        typeValue.setText("NAVIGATION");
        voiceValue.setText("TOUCH ONLY");
    }

    private static void addContextRow(GridPane grid, int row, String name, Label value) {
        Label key = new Label(name);
        key.getStyleClass().add("deck-context-key");
        value.getStyleClass().add("deck-context-value");
        grid.add(key, 0, row);
        grid.add(value, 1, row);
    }

    private static String symbolFor(ToolApplication application) {
        return switch (application.id()) {
            case "intellij-idea" -> "IJ";
            case "visual-studio-code" -> "VS";
            case "powershell" -> ">_";
            case "tidal" -> "TD";
            case "studio-one" -> "S1";
            default -> "AP";
        };
    }

    private static String detailFor(ToolApplication application) {
        return switch (application.id()) {
            case "intellij-idea" -> "JAVA / KOTLIN";
            case "visual-studio-code" -> "CODE EDITOR";
            case "powershell" -> "SHELL";
            case "tidal" -> "MUSIC";
            case "studio-one" -> "DAW";
            default -> "APPLICATION";
        };
    }
}
