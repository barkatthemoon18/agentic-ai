package com.fuad.presentation.core;

import com.fuad.pipeline.VoiceInputController;
import com.fuad.pipeline.VoiceSignalSnapshot;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.interaction.InteractionDisplayResolver;
import com.fuad.presentation.interaction.ResolvedInteractionDisplay;
import com.fuad.view.CoreDashboardView;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.Objects;
import java.util.function.Supplier;

public class JavaFxCoreVisual implements CoreVisual {
    private final JavaFxRuntime javaFxRuntime;
    private final InteractionDisplayResolver interactionDisplayResolver;
    private final Supplier<VoiceSignalSnapshot> voiceSignalSupplier;
    private final VoiceInputController voiceInputController;
    private Stage stage;
    private CoreDashboardView dashboardView;

    public JavaFxCoreVisual(JavaFxRuntime javaFxRuntime, InteractionDisplayResolver interactionDisplayResolver) {
        this(javaFxRuntime, interactionDisplayResolver, VoiceSignalSnapshot::silence, null);
    }

    public JavaFxCoreVisual(JavaFxRuntime javaFxRuntime, InteractionDisplayResolver interactionDisplayResolver, Supplier<VoiceSignalSnapshot> voiceSignalSupplier,
                            VoiceInputController voiceInputController) {
        this.javaFxRuntime = Objects.requireNonNull(javaFxRuntime);
        this.interactionDisplayResolver =  Objects.requireNonNull(interactionDisplayResolver);
        this.voiceSignalSupplier = Objects.requireNonNull(voiceSignalSupplier);
        this.voiceInputController = Objects.requireNonNull(voiceInputController);

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
        dashboardView = new CoreDashboardView(voiceSignalSupplier, voiceInputController);
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