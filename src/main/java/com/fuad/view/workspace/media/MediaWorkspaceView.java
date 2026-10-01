package com.fuad.view.workspace.media;

import com.fuad.media.MediaTrack;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot.MediaQueueItem;
import com.fuad.presentation.media.MediaAction;
import com.fuad.presentation.media.MediaActionHandler;
import com.fuad.presentation.media.MediaWorkspaceSnapshot;
import com.fuad.view.icon.HudIcon;
import com.fuad.view.icon.HudIconView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class MediaWorkspaceView extends VBox {
    private static final double PROGRESS_WIDTH = 320.0;
    private static final double PROGRESS_HEIGHT = 5.0;
    private static final Duration REFRESH_INTERVAL = Duration.millis(500);
    private final Label title = new Label();
    private final Label artist = new Label();
    private final Label album = new Label();
    private final Label elapsed =  new Label();
    private final Label duration = new Label();
    private final Label volumeValue = new Label();
    private final Label output = new Label();
    private final Label quality = new Label();
    private final Label provider = new Label();
    private final Button playPauseButton = transportButton(HudIcon.PLAY);
    private final Region progressFill = new Region();
    private final Region volumeFill = new Region();
    private final StackPane cover = new StackPane();
    private final Label coverPlaceholder = new Label("MEDIA");
    private final ImageView coverImage = new ImageView();
    private final VBox queueList = new VBox(6.0);
    private final Supplier<MediaWorkspaceSnapshot> mediaWorkspaceSnapshotSupplier;
    private final MediaActionHandler mediaActionHandler;
    private final Timeline refreshTimeline;
    private int currentArtworkHash;

    public MediaWorkspaceView() {
        this(MediaWorkspaceSnapshot::unavailable, MediaActionHandler.unavailable());
    }

    public MediaWorkspaceView(Supplier<MediaWorkspaceSnapshot> mediaWorkspaceSnapshotSupplier, MediaActionHandler mediaActionHandler) {
        this.mediaWorkspaceSnapshotSupplier = Objects.requireNonNull(mediaWorkspaceSnapshotSupplier);
        this.mediaActionHandler = Objects.requireNonNull(mediaActionHandler);

        setSpacing(14.0);

        Label sectionTitle = new Label("MEDIA // PLAYER");
        sectionTitle.getStyleClass().add("workspace-view-title");
        provider.getStyleClass().add("media-provider");

        VBox identity = new VBox(2.0, sectionTitle, provider);
        HBox nowPlaying = createNowPlaying();
        HBox transport = createTransportControls();
        VBox audio = createAudioControls();

        VBox playerPanel = new VBox(16.0, nowPlaying, transport, audio);
        playerPanel.setPadding(new Insets(18.0));
        playerPanel.getStyleClass().add("media-player-panel");

        VBox queue = createQueue();
        queue.setPadding(new Insets(14.0));
        queue.getStyleClass().add("media-queue");

        getChildren().addAll(identity, playerPanel, queue);
        update(this.mediaWorkspaceSnapshotSupplier.get());

        refreshTimeline = new Timeline(new KeyFrame(REFRESH_INTERVAL, event -> update(mediaWorkspaceSnapshotSupplier.get())));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    public void update(MediaWorkspaceSnapshot snapshot) {
        double progress;
        double progressWidth;

        if (!snapshot.available() || snapshot.currentTrack().isEmpty()) {
            renderUnavailable();
            return;
        }
        MediaTrack track = snapshot.currentTrack().orElseThrow();

        renderArtwork(track);

        provider.setText(snapshot.sourceDisplayName().toUpperCase() + " // ACTIVE");
        title.setText(track.title());
        artist.setText(track.artist());
        album.setText(track.album());
        elapsed.setText(formatTime(snapshot.positionSeconds()));
        duration.setText(formatTime(track.durationSeconds()));
        if (snapshot.volume().isPresent()) {
            double volume = snapshot.volume().getAsDouble();
            volumeValue.setText("%.0f %%".formatted(volume * 100.0));
            double volumeWidth = 250.0 * volume;
            volumeFill.setMinWidth(volumeWidth);
            volumeFill.setPrefWidth(volumeWidth);
            volumeFill.setMaxWidth(volumeWidth);
        }
        else {
            volumeValue.setText("--");
            volumeFill.setMinWidth(0.0);
            volumeFill.setPrefWidth(0.0);
            volumeFill.setMaxWidth(0.0);
        }
        output.setText(displayValue(snapshot.outputDevice()));
        quality.setText(displayValue(snapshot.quality()));

        renderQueue(snapshot.queue());

        playPauseButton.setGraphic(new HudIconView(snapshot.playing() ? HudIcon.PAUSE : HudIcon.PLAY, 16.0));

        progress = track.durationSeconds() > 0.0 ? snapshot.positionSeconds() / track.durationSeconds() : 0.0;
        progressWidth = PROGRESS_WIDTH * Math.clamp(progress, 0.0, 1.0);
        progressFill.setMinWidth(progressWidth);
        progressFill.setPrefWidth(progressWidth);
        progressFill.setMaxWidth(progressWidth);
        if (snapshot.volume().isPresent()) {
            double volume = snapshot.volume().getAsDouble();
            double volumeWidth = 250.0 * volume;
            volumeFill.setMinWidth(volumeWidth);
            volumeFill.setPrefWidth(volumeWidth);
            volumeFill.setMaxWidth(volumeWidth);
        }
        else {
            volumeFill.setMinWidth(0.0);
            volumeFill.setPrefWidth(0.0);
            volumeFill.setMaxWidth(0.0);
        }
    }

    public void dispose() {
        refreshTimeline.stop();
    }

    private static String displayValue(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }

    private static String formatTime(double seconds) {
        long total = Math.max(0, Math.round(seconds));

        return "%02d:%02d".formatted(total / 60, total % 60);
    }

    private void renderArtwork(MediaTrack track) {
        int artworkHash;
        String artwork;

        artwork = track.artwork().filter(value -> !value.isBlank()).orElse(null);
        if (artwork == null) {
            clearArtwork();
            return;
        }
        artworkHash = artwork.hashCode();
        if (artworkHash == currentArtworkHash && coverImage.getImage() != null) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(artwork);
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError()) {
                clearArtwork();
                return;
            }
            coverImage.setImage(image);
            cover.getChildren().setAll(coverImage);
            currentArtworkHash = artworkHash;
        }
        catch (IllegalArgumentException e) {
            clearArtwork();
        }
    }

    private void clearArtwork() {
        coverImage.setImage(null);
        currentArtworkHash = 0;

        coverPlaceholder.setText("MEDIA");
        cover.getChildren().setAll(coverPlaceholder);
    }

    private void renderUnavailable() {
        title.setText("NO ACTIVE SESSION");
        artist.setText("--");
        album.setText("--");

        elapsed.setText("00:00");
        duration.setText("00:00");

        volumeValue.setText("--");
        output.setText("--");
        quality.setText("--");

        progressFill.setMinWidth(0.0);
        progressFill.setPrefWidth(0.0);
        progressFill.setMaxWidth(0.0);

        volumeFill.setMinWidth(0.0);
        volumeFill.setPrefWidth(0.0);
        volumeFill.setMaxWidth(0.0);

        playPauseButton.setGraphic(new HudIconView(HudIcon.PLAY, 16.0));

        clearArtwork();
        provider.setText("NO ACTIVE SESSION");

        queueList.getChildren().clear();
        Label emptyQueue = new Label("--");
        emptyQueue.getStyleClass().add("media-queue-row");
        queueList.getChildren().add(emptyQueue);
    }

    private HBox createNowPlaying(){
        cover.setPrefSize(176.0, 176.0);
        cover.setMinSize(176.0, 176.0);
        cover.setMaxSize(176.0, 176.0);
        cover.getStyleClass().add("media-cover");

        coverPlaceholder.getStyleClass().add("media-cover-placeholder");

        coverImage.setFitWidth(176.0);
        coverImage.setFitHeight(176.0);
        coverImage.setPreserveRatio(true);
        coverImage.setSmooth(true);

        cover.getChildren().setAll(coverPlaceholder);

        Label nowPlaying = new Label("NOW PLAYING");
        nowPlaying.getStyleClass().add("media-caption");

        title.getStyleClass().add("media-track-title");
        artist.getStyleClass().add("media-track-artist");
        album.getStyleClass().add("media-track-album");

        HBox progress = createProgressBar();
        VBox info = new VBox(7.0, nowPlaying, title, artist, album, progress);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox result = new HBox(22.0, cover, info);
        result.setAlignment(Pos.CENTER_LEFT);

        return result;
    }

    private HBox createProgressBar() {
        Region track = new Region();

        track.getStyleClass().add("media-progress-track");
        track.setMinSize(PROGRESS_WIDTH, PROGRESS_HEIGHT);
        track.setPrefSize(PROGRESS_WIDTH, PROGRESS_HEIGHT);
        track.setMaxSize(PROGRESS_WIDTH, PROGRESS_HEIGHT);

        progressFill.getStyleClass().add("media-progress-fill");
        progressFill.setMinHeight(PROGRESS_HEIGHT);
        progressFill.setPrefHeight(PROGRESS_HEIGHT);
        progressFill.setMaxHeight(PROGRESS_HEIGHT);

        StackPane bar = new StackPane(track, progressFill);
        bar.setMinSize(PROGRESS_WIDTH, PROGRESS_HEIGHT);
        bar.setPrefSize(PROGRESS_WIDTH, PROGRESS_HEIGHT);
        bar.setMaxSize(PROGRESS_WIDTH, PROGRESS_HEIGHT);
        bar.setAlignment(Pos.CENTER_LEFT);

        elapsed.getStyleClass().add("media-time");
        duration.getStyleClass().add("media-time");

        HBox result = new HBox(9.0, elapsed, bar, duration);
        result.setAlignment(Pos.CENTER_LEFT);

        return result;
    }

    private HBox createTransportControls() {
        Button previous = transportButton(HudIcon.PREVIOUS);
        Button next = transportButton(HudIcon.NEXT);

        playPauseButton.getStyleClass().add("media-transport-primary");
        HBox controls = new HBox(12.0, previous, playPauseButton, next);
        controls.setAlignment(Pos.CENTER);

        /* Actions */
        previous.setOnAction(event -> mediaActionHandler.submit(MediaAction.PREVIOUS));
        next.setOnAction(event -> mediaActionHandler.submit(MediaAction.NEXT));
        playPauseButton.setOnAction(event -> mediaActionHandler.submit(MediaAction.PLAY_PAUSE));

        return controls;
    }

    private Button transportButton(HudIcon symbol) {
        Button button = new Button();

        button.setGraphic(new HudIconView(symbol, 16.0));
        button.getStyleClass().add("media-transport");
        return button;
    }

    private VBox createAudioControls() {
        Label audioTitle = new Label("AUDIO // OUTPUT");
        audioTitle.getStyleClass().add("media-caption");

        Label volumeKey = new Label("VOLUME");
        volumeKey.getStyleClass().add("media-property-key");
        volumeValue.getStyleClass().add("media-property-value");

        StackPane volumeBar = createVolumeBar();

        HBox volumeRow = new HBox(12.0, volumeKey, volumeValue, volumeBar);
        volumeRow.setAlignment(Pos.CENTER_LEFT);

        Label outputKey = new Label("DEVICE");
        outputKey.getStyleClass().add("media-property-key");

        Label qualityKey = new Label("QUALITY");
        qualityKey.getStyleClass().add("media-property-key");

        output.getStyleClass().add("media-property-value");
        quality.getStyleClass().add("media-property-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox detailsRow = new HBox(10.0, outputKey, output, spacer, qualityKey, quality);
        detailsRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(9.0, audioTitle, volumeRow, detailsRow);
    }

    private StackPane createVolumeBar() {
        Region volumeTrack = new Region();
        volumeTrack.getStyleClass().add("media-volume-track");

        volumeTrack.setMinSize(250.0, 5.0);
        volumeTrack.setPrefSize(250.0, 5.0);
        volumeTrack.setMaxSize(250.0, 5.0);

        volumeFill.getStyleClass().add("media-volume-fill");

        StackPane volumeBar = new StackPane(volumeTrack, volumeFill);

        volumeBar.setAlignment(Pos.CENTER_LEFT);

        volumeBar.setMinSize(250.0, 5.0);
        volumeBar.setPrefSize(250.0, 5.0);
        volumeBar.setMaxSize(250.0, 5.0);

        volumeFill.setMinHeight(5.0);
        volumeFill.setPrefHeight(5.0);
        volumeFill.setMaxHeight(5.0);

        return volumeBar;
    }

    private VBox createQueue() {
        Label heading = new Label("QUEUE // NEXT");
        heading.getStyleClass().add("media-caption");

        return new VBox(8.0, heading, queueList);
    }

    private void renderQueue(List<MediaQueueItem> queue) {
        queueList.getChildren().clear();

        if (queue == null || queue.isEmpty()) {
            Label empty = new Label("--");
            empty.getStyleClass().add("media-queue-row");
            queueList.getChildren().add(empty);
            return;
        }
        int count = Math.min(queue.size(), 3);
        for (int i = 0; i < count; i++) {
            MediaQueueItem item = queue.get(i);
            String artist = item.displayArtist();
            String text = "%02d  %s%s".formatted(i + 1, item.title(), artist.isBlank() ? "" : "  //  " + artist);
            Label row = new Label(text);
            row.getStyleClass().add("media-queue-row");
            row.setMaxWidth(Double.MAX_VALUE);
            queueList.getChildren().add(row);
        }
    }
}
