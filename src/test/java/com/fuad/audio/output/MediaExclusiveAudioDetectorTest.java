package com.fuad.audio.output;

import com.fuad.media.MediaPlaybackState;
import com.fuad.presentation.media.MediaWorkspaceSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaExclusiveAudioDetectorTest {
    @ParameterizedTest
    @CsvSource({"PLAYING,true", "UNKNOWN,true", "PAUSED,false", "STOPPED,false", "ABSENT,true"})
    void tidalProcessShouldReserveOutputUnlessExplicitlyPausedOrStopped(String state, boolean expected) {
        ProcessHandle process = process(java.nio.file.Path.of("music", "TiDaL.ExE").toString());
        Optional<MediaPlaybackState> playback = state.equals("ABSENT")
                ? Optional.empty() : Optional.of(MediaPlaybackState.valueOf(state));
        MediaWorkspaceSnapshot snapshot = new MediaWorkspaceSnapshot(true, "Spotify", Optional.empty(),
                0, MediaPlaybackState.PLAYING, OptionalDouble.empty(), "", "", List.of(), playback);
        try (var processes = mockStatic(ProcessHandle.class)) {
            processes.when(ProcessHandle::allProcesses).thenAnswer(invocation -> Stream.of(process));
            assertEquals(expected, new MediaExclusiveAudioDetector(() -> snapshot).isOutputReserved());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void otherOrUnobservableProcessesShouldNotReserveOutputOrReadMediaSnapshot() {
        Supplier<MediaWorkspaceSnapshot> supplier = mock(Supplier.class);
        ProcessHandle other = process(java.nio.file.Path.of("music", "tidal-helper.exe").toString());
        ProcessHandle unobservable = process(null);
        try (var processes = mockStatic(ProcessHandle.class)) {
            processes.when(ProcessHandle::allProcesses).thenAnswer(invocation -> Stream.of(other, unobservable));
            assertFalse(new MediaExclusiveAudioDetector(supplier).isOutputReserved());
            verifyNoInteractions(supplier);
        }
    }

    @Test
    void reservationShouldFollowPlaybackChangesWithoutCachingThem() {
        ProcessHandle process = process(java.nio.file.Path.of("music", "Tidal.exe").toString());
        java.util.concurrent.atomic.AtomicReference<MediaWorkspaceSnapshot> snapshot =
                new java.util.concurrent.atomic.AtomicReference<>(snapshot(MediaPlaybackState.PLAYING));
        try (var processes = mockStatic(ProcessHandle.class)) {
            processes.when(ProcessHandle::allProcesses).thenAnswer(invocation -> Stream.of(process));
            MediaExclusiveAudioDetector detector = new MediaExclusiveAudioDetector(snapshot::get);
            assertTrue(detector.isOutputReserved());
            snapshot.set(snapshot(MediaPlaybackState.PAUSED));
            assertFalse(detector.isOutputReserved());
            snapshot.set(snapshot(MediaPlaybackState.PLAYING));
            assertTrue(detector.isOutputReserved());
        }
    }

    private static MediaWorkspaceSnapshot snapshot(MediaPlaybackState state) {
        return new MediaWorkspaceSnapshot(true, "Tidal", null, 0, state,
                OptionalDouble.empty(), "", "", null, Optional.of(state));
    }

    private static ProcessHandle process(String command) {
        ProcessHandle process = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(process.info()).thenReturn(info);
        when(info.command()).thenReturn(Optional.ofNullable(command));
        return process;
    }
}
