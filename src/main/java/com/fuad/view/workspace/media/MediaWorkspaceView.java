package com.fuad.view.workspace.media;

import com.fuad.view.icon.HudIcon;
import com.fuad.view.icon.HudIconView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;

public class MediaWorkspaceView extends VBox {
    private final Label title = new Label();
    private final Label artist = new Label();
    private final Label album = new Label();
    private final Label elapsed =  new Label();
    private final Label duration = new Label();
    private final Label volumeValue = new Label();
    private final Label output = new Label();
    private final Label quality = new Label();
    private final Button playPauseButton = transportButton(HudIcon.PLAY);
    private final Region progressFill = new Region();
    private final Region volumeFill = new Region();

    public MediaWorkspaceView() {
        setSpacing(14.0);

        Label sectionTitle = new Label("MEDIA // PLAYER");
        Label provider = new Label("TIDAL PLAYER // MOCK");
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

        getChildren().addAll(identity, nowPlaying, transport, audio, queue);
        update(mockSnapshot());
    }

    public void update(MediaPlayerSnapshot snapshot) {
        double progress;
        MediaTrack track = snapshot.currentTrack();

        title.setText(track.title());
        artist.setText(track.artist());
        album.setText(track.album());
        elapsed.setText(formatTime(snapshot.positionSeconds()));
        duration.setText(formatTime(track.durationSeconds()));
        volumeValue.setText("%.0f %%".formatted(snapshot.volume() * 100.0));
        output.setText(snapshot.outputDevice());
        quality.setText(snapshot.quality());
        playPauseButton.setGraphic(new HudIconView(snapshot.playing() ? HudIcon.PAUSE : HudIcon.PLAY, 16.0));

        progress = snapshot.positionSeconds() / track.durationSeconds();
        progressFill.setPrefWidth(320.0 * Math.clamp(progress, 0.0, 1.0));
        volumeFill.setMinWidth(250.0 * Math.clamp(snapshot.volume(), 0.0, 1.0));
        volumeFill.setPrefWidth(250.0 * Math.clamp(snapshot.volume(), 0.0, 1.0));
        volumeFill.setMaxWidth(250.0 * Math.clamp(snapshot.volume(), 0.0, 1.0));
    }

    private static void addProperty(GridPane gridPane, int row, String name, Label value) {
        Label key = new Label(name);

        key.getStyleClass().add("media-property-key");
        value.getStyleClass().add("media-property-value");

        gridPane.add(key, 0, row);
        gridPane.add(value, 1, row);
    }

    private static String formatTime(double seconds) {
        long total = Math.max(0, Math.round(seconds));

        return "%02d:%02d".formatted(total / 60, total % 60);
    }

    private static MediaPlayerSnapshot mockSnapshot() {
        return new MediaPlayerSnapshot(new MediaTrack("Instant Crush", "Daft Punk", "Random Access Memories", 337),
                134,
                true,
                0.62,
                "FOCUSRITE USB",
                "MAX / LOSSLESS");
    }

    private HBox createNowPlaying(){
        StackPane cover = new StackPane();

        cover.setPrefSize(176.0, 176.0);
        cover.setMinSize(176.0, 176.0);
        cover.setMaxSize(176.0, 176.0);
        cover.getStyleClass().add("media-cover");

        Label coverText = new Label("TIDAL");
        coverText.getStyleClass().add("media-cover-placeholder");
        cover.getChildren().add(coverText);
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
        track.setMaxWidth(Double.MAX_VALUE);
        track.setMinHeight(5.0);
        track.setPrefWidth(5.0);
        track.setMaxHeight(5.0);

        progressFill.getStyleClass().add("media-progress-fill");

        StackPane bar = new StackPane(track, progressFill);
        bar.setMinHeight(5.0);
        bar.setPrefHeight(5.0);
        bar.setMaxHeight(5.0);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefWidth(320.0);
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

        VBox list = new VBox(6.0);

        List<MediaTrack> mockQueue = List.of(
                new MediaTrack(
                        "Giorgio by Moroder",
                        "Daft Punk",
                        "Random Access Memories",
                        544
                ),
                new MediaTrack(
                        "Within",
                        "Daft Punk",
                        "Random Access Memories",
                        228
                ),
                new MediaTrack(
                        "Touch",
                        "Daft Punk",
                        "Random Access Memories",
                        498
                ));
        for (int i = 0; i < mockQueue.size(); i++) {
            MediaTrack track = mockQueue.get(i);
            Label row = new Label("%02d  %-28s  %s".formatted(i + 1, track.title(), formatTime(track.durationSeconds())));
            row.getStyleClass().add("media-queue-row");
            list.getChildren().add(row);
        }
        return new VBox(8.0, heading, list);
    }
}
