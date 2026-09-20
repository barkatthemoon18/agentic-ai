package com.fuad.view.workspace.web;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class ResearchWorkspaceView extends VBox {

    public ResearchWorkspaceView() {
        Label title = new Label("WEB // RESEARCH");
        title.getStyleClass().add("workspace-view-title");
        Label mock = new Label("NO ACTIVE RESEARCH");
        mock.getStyleClass().add("workspace-placeholder");
        getChildren().addAll(title, mock);
    }
}
