package com.fuad.view.workspace.system;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class SystemWorkspaceView extends VBox {

    public SystemWorkspaceView() {
        Label title = new Label("SYSTEM // CONTROL");
        title.getStyleClass().add("workspace-view-title");
        Label mock = new Label("AUDIO · NETWORK · DISPLAYS · POWER");
        mock.getStyleClass().add("workspace-placeholder");
        getChildren().addAll(title, mock);
    }
}
