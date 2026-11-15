package com.fuad.view.workspace.tools;

import com.fuad.pipeline.OsAction;
import com.fuad.presentation.tools.*;
import com.fuad.terminal.PowershellSession;
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
    private final TerminalWorkspaceView terminalView;
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
        this(config, actionHandler, moreToolsHandler, new PowershellSession());
    }


    public ToolsWorkspaceView(ToolsWorkspaceConfig config, ToolsActionHandler actionHandler, MoreToolsHandler moreToolsHandler,
                              PowershellSession terminalSession) {
        int i = 0;

        this.actionHandler = Objects.requireNonNull(actionHandler);
        this.moreToolsHandler = Objects.requireNonNull(moreToolsHandler);
        this.terminalView = new TerminalWorkspaceView(Objects.requireNonNull(terminalSession));
        List<ToolApplication> featured = Objects.requireNonNull(config).featuredApplications();

        setSpacing(12.0);
        setFillWidth(true);
        setMinHeight(0.0);

        actionGrid.setHgap(12.0);
        actionGrid.setVgap(12.0);

        for (ToolApplication application : featured) {
            actionGrid.add(createApplicationButton(application), i % 2, i / 2);
            i++;
        }

        actionGrid.add(createMoreButton(), i % 2, i / 2);

        VBox context = createContextPanel();
        getChildren().addAll(actionGrid, context, terminalView);
        VBox.setVgrow(terminalView, Priority.ALWAYS);

        if (!featured.isEmpty()) {
            previewApplication(featured.getFirst());
        }
    }

    public void dispose() {
        terminalView.dispose();
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
            previewApplication(application);
            if ("powershell".equals(application.id()) && terminalView.focusInput()) {
                touchValue.setText("FOCUSED");
                lastValue.setText("POWERSHELL");
                return;
            }
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
        values.getStyleClass().add("deck-context-grid");
        values.setHgap(8.0);
        values.setVgap(8.0);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3.0);
            column.setHgrow(Priority.ALWAYS);
            values.getColumnConstraints().add(column);
        }

        addContextCell(values, 0, 0, "TARGET", targetValue);
        addContextCell(values, 1, 0, "TYPE", typeValue);
        addContextCell(values, 2, 0, "VOICE", voiceValue);
        addContextCell(values, 0, 1, "TOUCH", touchValue);
        addContextCell(values, 1, 1, "LAST", lastValue);

        VBox context = new VBox(10.0, title, values);
        context.setPadding(new Insets(12.0));
        context.getStyleClass().add("deck-context");

        targetValue.getStyleClass().add("deck-context-target");

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

    private static void addContextCell(GridPane grid, int column, int row, String name, Label value) {
        Label key = new Label(name);
        key.getStyleClass().add("deck-context-key");

        value.getStyleClass().add("deck-context-value");
        value.setWrapText(true);
        value.setMinWidth(0.0);
        value.setMaxWidth(Double.MAX_VALUE);

        VBox cell = new VBox(3.0, key, value);
        cell.getStyleClass().add("deck-context-cell");
        cell.setMinWidth(0.0);
        cell.setMaxWidth(Double.MAX_VALUE);

        grid.add(cell, column, row);
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
