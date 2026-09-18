package com.fuad.presentation.core;

import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.interaction.InteractionDisplayResolver;
import com.fuad.presentation.interaction.ResolvedInteractionDisplay;
import com.fuad.view.CoreDashboardView;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.NonNull;

import java.net.URL;

public class JavaFxCoreVisual implements CoreVisual {
    private final JavaFxRuntime javaFxRuntime;
    private final InteractionDisplayResolver interactionDisplayResolver;
    private Stage stage;
    private CoreDashboardView dashboardView;

    public JavaFxCoreVisual(@NonNull JavaFxRuntime javaFxRuntime, @NonNull InteractionDisplayResolver interactionDisplayResolver) {
        this.javaFxRuntime = javaFxRuntime;
        this.interactionDisplayResolver =  interactionDisplayResolver;

        javaFxRuntime.runAndWait(this::createStage);
    }

    @Override
    public void show() {
        javaFxRuntime.runLater(stage::show);
    }

    @Override
    public void update(CoreVisualSnapshot visualSnapshot) {
        javaFxRuntime.runLater(() -> dashboardView.update(visualSnapshot));
    }

    @Override
    public void close() {
        javaFxRuntime.runAndWait(stage::close);
    }

    private void createStage() {
        ResolvedInteractionDisplay interactionDisplay = interactionDisplayResolver.resolve().orElseThrow(() ->
                new IllegalStateException("Core display unavailable"));
        Rectangle2D bounds = interactionDisplay.screen().getVisualBounds();
        dashboardView = new CoreDashboardView();
        Scene scene = new Scene(dashboardView, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.rgb(4, 13, 22));
        URL css = JavaFxCoreVisual.class.getResource("/ui/core-visual.css");
        if (css == null) {
            throw new IllegalStateException("Missing /ui/core-visual.css");
        }
        scene.getStylesheets().add(css.toExternalForm());
        stage = new Stage(StageStyle.UNDECORATED);
        stage.setTitle("Ares Core Visual");
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setResizable(false);
        stage.setAlwaysOnTop(false);
        stage.setScene(scene);
    }
}