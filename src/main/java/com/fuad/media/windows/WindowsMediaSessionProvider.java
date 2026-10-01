package com.fuad.media.windows;

import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.MediaTrack;
import org.endlesssource.mediainterface.SystemMediaFactory;
import org.endlesssource.mediainterface.api.*;

import java.time.Duration;
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
    public MediaSessionSnapshot current() {
        return systemMediaInterface.getActiveSession().map(this::toSnapshot).orElseGet(MediaSessionSnapshot::unavailable);
    }

    @Override
    public boolean playPause() {
        return activeControls().map(MediaTransportControls::togglePlayPause).orElse(false);
    }

    @Override
    public boolean next() {
        return activeControls().map(MediaTransportControls::next).orElse(false);
    }

    @Override
    public boolean previous() {
        return activeControls().map(MediaTransportControls::previous).orElse(false);
    }

    @Override
    public void close() {
        systemMediaInterface.close();
    }

    private Optional<MediaTransportControls> activeControls() {
        return systemMediaInterface.getActiveSession().map(MediaSession::getControls);
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
            System.out.println("[MEDIA] session=" + session.getSessionId() + " | app=" + session.getApplicationName() + " | active=" + session.isActive() + " | state=" + session.getControls().getPlaybackState());
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
}
