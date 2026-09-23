package com.fuad.view;

import com.fuad.presentation.core.CoreVisualSnapshot;
import com.fuad.presentation.core.RuntimeVisualState;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class RuntimePanel extends VBox {
    private static final PseudoClass READY = PseudoClass.getPseudoClass("ready");
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");
    public static final PseudoClass DEGRADED = PseudoClass.getPseudoClass("degraded");
    public static final PseudoClass OFFLINE = PseudoClass.getPseudoClass("offline");
    private final Label phiState = stateLabel();
    private final Label qwenState = stateLabel();
    private final Label sttState = stateLabel();
    private final Label ttsState = stateLabel();

    public RuntimePanel() {
        setSpacing(7.0);
        setPadding(new Insets(16.0));
        setFillWidth(true);

        getStyleClass().addAll("core-panel", "runtime-panel");

        Label title = new Label("ARES // RUNTIME");
        Label subtitle = new Label("LOCAL // PIPELINE");
        title.getStyleClass().add("core-hud-title");
        subtitle.getStyleClass().add("runtime-subtitle");

        VBox header = new VBox(2.0, title, subtitle);

        Label inferenceTitle = sectionTitle("INFERENCE");
        Label voiceTitle = sectionTitle("VOICE PIPELINE");

        getChildren().addAll(header, inferenceTitle, createRuntimeRow("PHI ROUTER", "ROUTING", phiState),
                createRuntimeRow("QWEN MAIN", "REASONING", qwenState), voiceTitle, createRuntimeRow("STT", "TRANSCRIPTION", sttState),
                createRuntimeRow("TTS", "SPEECH OUTPUT", ttsState));
    }

    public void update(CoreVisualSnapshot visualSnapshot) {
        var runtime = visualSnapshot.runtimeSnapshot();

        updateState(phiState, runtime.phiState());
        updateState(qwenState, runtime.qwenState());
        updateState(sttState, runtime.sttState());
        updateState(ttsState, runtime.ttsState());
    }

    private HBox createRuntimeRow(String name, String role, Label state) {
        Label nameLabel = new Label(name);
        Label roleLabel = new Label("// " + role);

        nameLabel.getStyleClass().add("runtime-name");
        roleLabel.getStyleClass().add("runtime-role");

        HBox identity = new HBox(7.0, nameLabel, roleLabel);
        identity.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10.0, identity, spacer, state);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("runtime-row");
        return row;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);

        label.getStyleClass().add("runtime-section-title");
        return label;
    }

    private static Label stateLabel() {
        Label label = new Label();

        label.getStyleClass().add("runtime-state");
        return label;
    }

    private static void updateState(Label label, RuntimeVisualState state) {
        clearStatePseudoClasses(label);

        switch (state) {
            case READY -> label.pseudoClassStateChanged(READY, true);
            case CHECKING,
                 LOADING -> label.pseudoClassStateChanged(LOADING, true);
            case RETRY_WAIT -> label.pseudoClassStateChanged(DEGRADED, true);
            case FAILED,
                 OFFLINE -> label.pseudoClassStateChanged(OFFLINE, true);
        }
        label.setText("● " + state.name());
    }

    private static void clearStatePseudoClasses(Label label) {
        label.pseudoClassStateChanged(READY, false);
        label.pseudoClassStateChanged(ACTIVE, false);
        label.pseudoClassStateChanged(LOADING, false);
        label.pseudoClassStateChanged(DEGRADED, false);
        label.pseudoClassStateChanged(OFFLINE, false);
    }
}