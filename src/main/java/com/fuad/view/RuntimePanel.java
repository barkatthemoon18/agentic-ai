package com.fuad.view;

import com.fuad.presentation.core.CoreVisualSnapshot;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

public class RuntimePanel extends GridPane {
    private final Label phiState = stateLabel();
    private final Label qwenState = stateLabel();
    private final Label sttState = stateLabel();
    private final Label ttsState = stateLabel();

    public RuntimePanel() {
        setHgap(18.0);
        setVgap(9.0);
        setPadding(new Insets(18.0));

        getStyleClass().addAll("core-panel", "runtime-panel");
        Label title = new Label("ARES // RUNTIME");
        title.getStyleClass().add("core-hud-title");
        add(title, 0,0, 2, 1);
        addRuntimeRow(1, "PHI ROUTER", phiState);
        addRuntimeRow(2, "QWEN", qwenState);
        addRuntimeRow(3, "STT", sttState);
        addRuntimeRow(4, "TTS", ttsState);
    }

    public void update(CoreVisualSnapshot visualSnapshot) {
        var runtime = visualSnapshot.runtimeSnapshot();

        updateState(phiState, runtime.phiState());
        updateState(qwenState, runtime.qwenState());
        updateState(sttState, runtime.sttState());
        updateState(ttsState, runtime.ttsState());
    }

    private void addRuntimeRow(int row, String name, Label state) {
        Label label = new Label(name);
        label.getStyleClass().add("core-runtime-name");
        add(label, 0, row);
        add(state, 1, row);
    }

    private static Label stateLabel() {
        Label label = new Label();

        label.getStyleClass().add("core-runtime-state");
        return label;
    }

    private static void updateState(Label label, String state) {
        label.setText("● " + state);
    }
}