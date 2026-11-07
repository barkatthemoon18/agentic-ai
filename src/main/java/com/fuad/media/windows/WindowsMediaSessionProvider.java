package com.fuad.media.windows;

import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.MediaTrack;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaControlRequest;
import com.fuad.media.control.MediaTarget;
import org.endlesssource.mediainterface.SystemMediaFactory;
import org.endlesssource.mediainterface.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class WindowsMediaSessionProvider implements MediaSessionProvider {
    private final SystemMediaInterface systemMediaInterface;

    public WindowsMediaSessionProvider() {
        this(SystemMediaFactory.createSystemInterface(SystemMediaOptions.defaults().withEventDrivenEnabled(true)
                .withPositionUpdatesEnabled(true)));
    }

    public WindowsMediaSessionProvider(SystemMediaInterface systemMediaInterface) {
        this.systemMediaInterface = Objects.requireNonNull(systemMediaInterface, "systemMediaInterface must not be null");

        logSessions();
    }

    @Override
    public Optional<MediaPlaybackState> playbackState(String applicationName) {
        if (applicationName == null || applicationName.isBlank()) {
            System.out.println("applicationName must not be null or blank");
            return Optional.empty();
        }
        var states = systemMediaInterface.getAllSessions().stream()
                .filter(session -> matchesApplication(session.getApplicationName(), applicationName))
                .map(session -> toPlaybackState(session.getControls().getPlaybackState()))
                .toList();
        if (states.isEmpty()) {
            return Optional.empty();
        }
        if (states.contains(MediaPlaybackState.PLAYING)) {
            return Optional.of(MediaPlaybackState.PLAYING);
        }
        if (states.contains(MediaPlaybackState.UNKNOWN)) {
            return Optional.of(MediaPlaybackState.UNKNOWN);
        }
        if (states.contains(MediaPlaybackState.PAUSED)) {
            return Optional.of(MediaPlaybackState.PAUSED);
        }
        return Optional.of(MediaPlaybackState.STOPPED);
    }

    @Override
    public boolean execute(MediaControlRequest request) {
        Objects.requireNonNull(request);

        return resolveSession(request.target()).map(session -> executeOnSession(session, request.action()))
                .orElse(false);
    }

    @Override
    public MediaSessionSnapshot current() {
        return systemMediaInterface.getActiveSession().map(this::toSnapshot).orElseGet(MediaSessionSnapshot::unavailable);
    }

    @Override
    public boolean playPause() {
        return execute(MediaControlRequest.active(MediaAction.PLAY_PAUSE));
    }

    @Override
    public boolean pause() {
        return execute(MediaControlRequest.active(MediaAction.PAUSE));
    }

    @Override
    public boolean resume() {
        return execute(MediaControlRequest.active(MediaAction.RESUME));
    }

    @Override
    public boolean next() {
        return execute(MediaControlRequest.active(MediaAction.NEXT));
    }

    @Override
    public boolean previous() {
        return execute(MediaControlRequest.active(MediaAction.PREVIOUS));
    }

    @Override
    public void close() {
        systemMediaInterface.close();
    }

    private MediaSessionSnapshot toSnapshot(MediaSession session) {
        Optional<NowPlaying> nowPlaying = session.getNowPlaying();
        Optional<MediaTrack> track = nowPlaying.map(this::toTrack);
        double positionSeconds = nowPlaying.flatMap(NowPlaying::getPosition).map(WindowsMediaSessionProvider::toSeconds).orElse(0.0);
        MediaPlaybackState playbackState = toPlaybackState(session.getControls().getPlaybackState());
        return new MediaSessionSnapshot(true, session.getApplicationName(), track, positionSeconds, playbackState);
    }

    private MediaTrack toTrack(NowPlaying nowPlaying) {
        double durationSeconds = nowPlaying.getDuration().map(WindowsMediaSessionProvider::toSeconds).orElse(0.0);
        return new MediaTrack(nowPlaying.getTitle().orElse(""), nowPlaying.getArtist().orElse(""),
                nowPlaying.getAlbum().orElse(""), durationSeconds, nowPlaying.getArtwork());
    }

    private void logSessions() {
        var sessions = systemMediaInterface.getAllSessions();

        System.out.println("[MEDIA] sessions detected: " + sessions.size());
        for (var session : sessions) {
            System.out.println("[MEDIA] session=" + session.getSessionId() + " | app=" + session.getApplicationName() +
                    " | active=" + session.isActive() + " | state=" + session.getControls().getPlaybackState());
        }
    }

    private static MediaPlaybackState toPlaybackState(PlaybackState state) {
        if (state == null) {
            return MediaPlaybackState.UNKNOWN;
        }
        return switch (state) {
            case PLAYING -> MediaPlaybackState.PLAYING;
            case PAUSED -> MediaPlaybackState.PAUSED;
            case STOPPED -> MediaPlaybackState.STOPPED;
            case UNKNOWN -> MediaPlaybackState.UNKNOWN;
        };
    }

    private static double toSeconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private static boolean matchesApplication(String actual, String expected) {
        String source;
        String target;

        if (actual == null || expected == null) {
            return false;
        }
        source = normalizeApplicationName(actual);
        target = normalizeApplicationName(expected);
        return !source.isBlank() && source.equals(target);
    }

    private static String normalizeApplicationName(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);

        return normalized.endsWith(".exe") ? normalized.substring(0, normalized.length() - 4) : normalized;
    }

    private static boolean executeOnSession(MediaSession session, MediaAction action) {
        MediaTransportControls controls = session.getControls();

        return switch (action) {
            case NEXT -> controls.next();
            case PREVIOUS -> controls.previous();
            case PLAY_PAUSE -> controls.togglePlayPause();
            case PAUSE -> switch (controls.getPlaybackState()) {
                case PLAYING -> controls.togglePlayPause();
                case PAUSED -> true;
                case STOPPED, UNKNOWN -> false;
            };
            case RESUME -> switch (controls.getPlaybackState()) {
                case PLAYING -> true;
                case PAUSED -> controls.togglePlayPause();
                case STOPPED, UNKNOWN -> false;
            };
        };
    }

    private Optional<MediaSession> resolveSession(MediaTarget target) {
        return switch (target) {
            case MediaTarget.Active ignored -> systemMediaInterface.getActiveSession();
            case MediaTarget.Application application -> resolveApplicationSession(application.applicationName());
        };
    }

    private Optional<MediaSession> resolveApplicationSession(String applicationName) {
        List<MediaSession> matches = systemMediaInterface.getAllSessions().stream()
                .filter(session -> matchesApplication(session.getApplicationName(), applicationName))
                .limit(2).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
}