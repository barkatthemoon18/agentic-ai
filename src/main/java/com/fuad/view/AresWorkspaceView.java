package com.fuad.view;

import com.fuad.presentation.core.WorkspaceType;
import com.fuad.view.workspace.dev.DevWorkspaceView;
import com.fuad.view.workspace.files.FilesWorkspaceView;
import com.fuad.view.workspace.media.MediaWorkspaceView;
import com.fuad.view.workspace.system.SystemWorkspaceView;
import com.fuad.view.workspace.web.ResearchWorkspaceView;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class AresWorkspaceView extends VBox {
    private static final PseudoClass ACTIVE_CATEGORY = PseudoClass.getPseudoClass("active");
    private final Label workspaceSubtitle = new Label();
    private final HBox navigation = new HBox(7.0);
    private final StackPane contentHost = new StackPane();
    private final Map<WorkspaceType, Button> navigationButtons = new EnumMap<>(WorkspaceType.class);
    private final Map<WorkspaceType, Supplier<? extends Region>> factories = new EnumMap<>(WorkspaceType.class);
    private final Map<WorkspaceType, Region> instances = new  EnumMap<>(WorkspaceType.class);
    private WorkspaceType activeWorkspace = WorkspaceType.DEV;

    public AresWorkspaceView() {
        setSpacing(12.0);
        setPadding(new Insets(18.0));
        getStyleClass().addAll("core-panel", "ares-workspace");
        Label title = new Label("ARES // WORKSPACE");
        title.getStyleClass().add("core-hud-title");
        workspaceSubtitle.getStyleClass().add("workspace-subtitle");
        navigation.getStyleClass().add("workspace-tabs");
        contentHost.getStyleClass().add("workspace-content");
        contentHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        contentHost.setMinHeight(0.0);
        VBox.setVgrow(contentHost, Priority.ALWAYS);
        registerMockWorkspaces();
        createNavigation();
        getChildren().addAll(title, workspaceSubtitle, navigation, contentHost);
        showWorkspace(WorkspaceType.DEV);
    }

    public void showWorkspace(WorkspaceType workspaceType) {
        Supplier<? extends Region> supplier = factories.get(workspaceType);

        if (supplier == null) {
            return;
        }
        Region workspace = instances.computeIfAbsent(workspaceType, ignored -> supplier.get());
        workspace.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        workspace.setMinHeight(0.0);
        activeWorkspace = workspaceType;
        workspaceSubtitle.setText("WORKSPACE // " + workspaceType.getSubtitle().toUpperCase());
        navigationButtons.forEach((wsType, button) -> button.pseudoClassStateChanged(ACTIVE_CATEGORY,
                wsType == workspaceType));
        contentHost.getChildren().setAll(workspace);
    }

    private void registerMockWorkspaces() {
        factories.put(WorkspaceType.DEV, DevWorkspaceView::new);
        factories.put(WorkspaceType.MEDIA, MediaWorkspaceView::new);
        factories.put(WorkspaceType.FILES, FilesWorkspaceView::new);
        factories.put(WorkspaceType.SYSTEM, SystemWorkspaceView::new);
        factories.put(WorkspaceType.WEB, ResearchWorkspaceView::new);
    }

    private void createNavigation() {
        for (WorkspaceType workspaceType : WorkspaceType.values()) {
            Button button = new Button(workspaceType.getLabel());
            button.getStyleClass().add("workspace-tab");
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
            button.setOnAction(event -> showWorkspace(workspaceType));
            navigationButtons.put(workspaceType, button);
            navigation.getChildren().add(button);
        }
    }
}
