package com.fuad.view.workspace.tools;

import com.fuad.terminal.AresPtyConnector;
import com.fuad.terminal.AresTerminalSettingsProvider;
import com.fuad.terminal.PowershellSession;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Objects;

public final class TerminalWorkspaceView extends VBox {
    private final Label status = new Label("OFFLINE");
    private JediTermFxWidget widget;

    public TerminalWorkspaceView(PowershellSession session) {
        Objects.requireNonNull(session);

        setSpacing(8.0);
        setMinHeight(0.0);
        getStyleClass().add("ares-terminal");

         Label title = new Label("TERMINAL // POWERSHELL");
         title.getStyleClass().add("ares-terminal-title");
         status.getStyleClass().add("ares-terminal-status");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10.0, title, spacer, status);
        header.setAlignment(Pos.CENTER_LEFT);

        getChildren().add(header);
        session.process().ifPresentOrElse(process -> {
            if (!process.isAlive()) {
                showUnavailable("PowerShell session terminated");
                return;
            }
            widget = new JediTermFxWidget(70, 14, new AresTerminalSettingsProvider());
            widget.setTtyConnector(new AresPtyConnector(process));
            Pane terminalPane = widget.getPane();
            terminalPane.setMinSize(0.0, 0.0);
            terminalPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            VBox.setVgrow(terminalPane, Priority.ALWAYS);
            getChildren().add(terminalPane);

            widget.addListener(terminalWidget -> Platform.runLater(() -> status.setText("CLOSED")));
            widget.start();

            status.setText("READY");
        }, () -> showUnavailable(session.error().orElse("Terminal unavailable")));
    }

    public boolean focusInput() {
        if (widget == null || !widget.getTtyConnector().isConnected()) {
            return false;
        }
        widget.getPreferredFocusableNode().requestFocus();
        return true;
    }

    public void dispose() {
        if (widget != null) {
            widget.close();
            widget = null;
        }
    }

    private void showUnavailable(String message) {
        Label error = new Label(message);
        error.setWrapText(true);
        error.getStyleClass().add("ares-terminal-error");
        getChildren().add(error);
        status.setText("UNAVAILABLE");
    }
}
