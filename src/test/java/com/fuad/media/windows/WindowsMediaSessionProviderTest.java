package com.fuad.media.windows;

import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionSnapshot;
import org.endlesssource.mediainterface.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowsMediaSessionProviderTest {
    private final SystemMediaInterface system = mock(SystemMediaInterface.class);

    @Test
    void activeSessionShouldMapMetadataArtworkAndFractionalSeconds() {
        MediaSession session = session("Tidal.exe", PlaybackState.PLAYING);
        NowPlaying playing = mock(NowPlaying.class);
        when(playing.getTitle()).thenReturn(Optional.of("Song"));
        when(playing.getArtist()).thenReturn(Optional.of("Artist"));
        when(playing.getAlbum()).thenReturn(Optional.of("Album"));
        when(playing.getArtwork()).thenReturn(Optional.of("artwork-uri"));
        when(playing.getDuration()).thenReturn(Optional.of(Duration.ofMillis(123456)));
        when(playing.getPosition()).thenReturn(Optional.of(Duration.ofMillis(1500)));
        when(session.getNowPlaying()).thenReturn(Optional.of(playing));
        when(system.getActiveSession()).thenReturn(Optional.of(session));

        MediaSessionSnapshot snapshot = new WindowsMediaSessionProvider(system).current();

        assertTrue(snapshot.available());
        assertEquals("Tidal.exe", snapshot.sourceApplication());
        assertEquals(1.5, snapshot.positionSeconds());
        assertEquals(MediaPlaybackState.PLAYING, snapshot.playbackState());
        var track = snapshot.currentTrack().orElseThrow();
        assertEquals("Song", track.title());
        assertEquals("Artist", track.artist());
        assertEquals("Album", track.album());
        assertEquals(123.456, track.durationSeconds(), 1e-9);
        assertEquals(Optional.of("artwork-uri"), track.artwork());
    }

    @Test
    void missingTrackAndMissingSessionShouldRemainDistinct() {
        MediaSession session = session("Tidal", null);
        when(system.getActiveSession()).thenReturn(Optional.of(session)).thenReturn(Optional.empty());
        WindowsMediaSessionProvider provider = new WindowsMediaSessionProvider(system);
        MediaSessionSnapshot snapshot = provider.current();
        assertTrue(snapshot.available());
        assertTrue(snapshot.currentTrack().isEmpty());
        assertEquals(0, snapshot.positionSeconds());
        assertEquals(MediaPlaybackState.UNKNOWN, snapshot.playbackState());
        assertEquals(MediaSessionSnapshot.unavailable(), provider.current());
    }

    @Test
    void missingTrackFieldsShouldUseEmptyMetadataAndZeroDuration() {
        MediaSession session = session("Tidal", PlaybackState.PAUSED);
        when(session.getNowPlaying()).thenReturn(Optional.of(mock(NowPlaying.class)));
        when(system.getActiveSession()).thenReturn(Optional.of(session));
        var snapshot = new WindowsMediaSessionProvider(system).current();
        var track = snapshot.currentTrack().orElseThrow();
        assertEquals("", track.title());
        assertEquals("", track.artist());
        assertEquals("", track.album());
        assertTrue(track.artwork().isEmpty());
        assertEquals(0, track.durationSeconds());
        assertEquals(0, snapshot.positionSeconds());
    }

    @ParameterizedTest
    @CsvSource({"PLAYING,UNKNOWN,PLAYING", "UNKNOWN,PAUSED,UNKNOWN", "PAUSED,STOPPED,PAUSED",
            "STOPPED,STOPPED,STOPPED", "STOPPED,PLAYING,PLAYING", "PAUSED,UNKNOWN,UNKNOWN"})
    void allMatchingSessionsShouldUsePlayingUnknownPausedStoppedPriority(String first, String second, String expected) {
        List<MediaSession> sessions = List.of(
                session("com.vendor.TIDAL.exe", PlaybackState.valueOf(first)),
                session(" tidal.EXE ", PlaybackState.valueOf(second)),
                session("Spotify", PlaybackState.PLAYING));
        when(system.getAllSessions()).thenReturn(sessions);
        WindowsMediaSessionProvider provider = new WindowsMediaSessionProvider(system);
        assertEquals(Optional.of(MediaPlaybackState.valueOf(expected)), provider.playbackState("TiDaL"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Spotify", "tidal-helper"})
    void absentOrNonmatchingApplicationShouldNotInventPlayback(String application) {
        MediaSession session = session("Tidal", PlaybackState.PLAYING);
        when(system.getAllSessions()).thenReturn(List.of(session));
        assertTrue(new WindowsMediaSessionProvider(system).playbackState(application).isEmpty());
    }

    @Test
    void nullNativePlaybackShouldBeUnknownAndActiveControlsShouldDelegate() {
        MediaSession session = session("Tidal", null);
        when(system.getAllSessions()).thenReturn(List.of(session));
        when(system.getActiveSession()).thenReturn(Optional.of(session));
        MediaTransportControls controls = session.getControls();
        when(controls.togglePlayPause()).thenReturn(true);
        when(controls.previous()).thenReturn(true);
        WindowsMediaSessionProvider provider = new WindowsMediaSessionProvider(system);

        assertEquals(Optional.of(MediaPlaybackState.UNKNOWN), provider.playbackState("TIDAL"));
        assertTrue(provider.playPause());
        assertTrue(provider.previous());
        assertFalse(provider.next());
        verify(controls).togglePlayPause();
        verify(controls).previous();
        verify(controls).next();
        provider.close();
        verify(system).close();
    }

    @Test
    void absentActiveSessionShouldRejectTransport() {
        WindowsMediaSessionProvider provider = new WindowsMediaSessionProvider(system);
        assertFalse(provider.playPause());
        assertFalse(provider.next());
        assertFalse(provider.previous());
    }

    private static MediaSession session(String application, PlaybackState state) {
        MediaSession session = mock(MediaSession.class);
        MediaTransportControls controls = mock(MediaTransportControls.class);
        when(session.getApplicationName()).thenReturn(application);
        when(session.getControls()).thenReturn(controls);
        when(controls.getPlaybackState()).thenReturn(state);
        return session;
    }
}
