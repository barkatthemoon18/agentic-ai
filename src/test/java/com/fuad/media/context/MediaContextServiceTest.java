package com.fuad.media.context;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.MediaTrack;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionResult;
import com.fuad.media.control.MediaActionStatus;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.fuad.testsupport.Await.until;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaContextServiceTest {
    private MediaSessionProvider sessions;
    private AudioOutputProvider audio;
    private MediaEnrichmentProvider enrichment;
    private MediaContextService service;

    @BeforeEach
    void setUp() {
        sessions = mock(MediaSessionProvider.class);
        audio = mock(AudioOutputProvider.class);
        enrichment = mock(MediaEnrichmentProvider.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void constructorShouldValidateArguments() {
        Clock clock = Clock.systemUTC();
        assertThrows(NullPointerException.class, () -> new MediaContextService(null, audio, enrichment, clock, () -> ProcessPresence.UNKNOWN));
        assertThrows(NullPointerException.class, () -> new MediaContextService(sessions, null, enrichment, clock, () -> ProcessPresence.UNKNOWN));
        assertThrows(NullPointerException.class, () -> new MediaContextService(sessions, audio, null, clock, () -> ProcessPresence.UNKNOWN));
        assertThrows(NullPointerException.class, () -> new MediaContextService(sessions, audio, enrichment, null, () -> ProcessPresence.UNKNOWN));
        assertThrows(NullPointerException.class, () -> new MediaContextService(sessions, audio, enrichment, clock, null));

        assertDoesNotThrow(() -> new MediaContextService(sessions, audio, enrichment));
    }

    @Test
    void initialSnapshotShouldBeUnavailable() {
        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.RUNNING);
        MediaContextSnapshot initial = service.current();

        assertNotNull(initial);
        assertFalse(initial.activeSession().readSucceeded());
        assertFalse(initial.tidalPlayback().readSucceeded());
        assertFalse(initial.tidalEnrichment().readSucceeded());
        assertFalse(initial.audioOutput().readSucceeded());
        assertFalse(initial.tidalProcess().readSucceeded());
    }

    @Test
    void startShouldPeriodicallyRefreshCoreAndEnrichmentSnapshots() throws Exception {
        MediaTrack track = new MediaTrack("Song", "Artist", "Album", 180);
        MediaSessionSnapshot sessionSnapshot = new MediaSessionSnapshot(true, "TIDAL", Optional.of(track), 10.0, MediaPlaybackState.PLAYING);
        AudioOutputSnapshot audioSnapshot = new AudioOutputSnapshot(true, "dev-1", "Headphones", OptionalDouble.of(0.8), false);
        MediaEnrichmentSnapshot enrichmentSnapshot = new MediaEnrichmentSnapshot("HI_RES_LOSSLESS", List.of());

        when(sessions.current()).thenReturn(sessionSnapshot);
        when(sessions.playbackState("TIDAL")).thenReturn(Optional.of(MediaPlaybackState.PLAYING));
        when(audio.current()).thenReturn(audioSnapshot);
        when(enrichment.current()).thenReturn(enrichmentSnapshot);

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.RUNNING);
        service.start();

        until(() -> service.current().activeSession().readSucceeded()
                && service.current().tidalEnrichment().readSucceeded());

        MediaContextSnapshot snapshot = service.current();
        assertTrue(snapshot.activeSession().readSucceeded());
        assertEquals("TIDAL", snapshot.activeSession().value().sourceApplication());
        assertTrue(snapshot.tidalPlayback().readSucceeded());
        assertEquals(Optional.of(MediaPlaybackState.PLAYING), snapshot.tidalPlayback().value());
        assertTrue(snapshot.audioOutput().readSucceeded());
        assertEquals("Headphones", snapshot.audioOutput().value().deviceName());
        assertTrue(snapshot.tidalProcess().readSucceeded());
        assertEquals(ProcessPresence.RUNNING, snapshot.tidalProcess().value());
        assertTrue(snapshot.tidalEnrichment().readSucceeded());
        assertEquals("HI_RES_LOSSLESS", snapshot.tidalEnrichment().value().quality());
    }

    @Test
    void startShouldBeIdempotent() {
        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        assertDoesNotThrow(() -> {
            service.start();
            service.start();
        });
    }

    @Test
    void startAfterCloseShouldThrowIllegalStateException() throws Exception {
        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        service.close();
        assertThrows(IllegalStateException.class, () -> service.start());
    }

    @Test
    void providerFailureShouldPreservePriorValueAndMarkSampleFailed() throws Exception {
        MediaSessionSnapshot okSession = new MediaSessionSnapshot(true, "Spotify", Optional.empty(), 0, MediaPlaybackState.PAUSED);
        AudioOutputSnapshot okAudio = new AudioOutputSnapshot(true, "dev-1", "Speakers", OptionalDouble.of(0.5), false);
        MediaEnrichmentSnapshot okEnrichment = new MediaEnrichmentSnapshot("LOSSLESS", List.of());

        when(sessions.current()).thenReturn(okSession);
        when(sessions.playbackState("TIDAL")).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(okAudio);
        when(enrichment.current()).thenReturn(okEnrichment);

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.NOT_RUNNING);
        service.start();

        until(() -> service.current().activeSession().readSucceeded());

        // Now inject failures into sessions and enrichment
        when(sessions.current()).thenThrow(new RuntimeException("IPC failed"));
        when(enrichment.current()).thenThrow(new UnsatisfiedLinkError("Native library missing"));

        until(() -> !service.current().activeSession().readSucceeded()
                && !service.current().tidalEnrichment().readSucceeded());

        MediaContextSnapshot failedSnapshot = service.current();
        // Values are preserved from prior read
        assertEquals("Spotify", failedSnapshot.activeSession().value().sourceApplication());
        assertFalse(failedSnapshot.activeSession().readSucceeded());
        assertEquals("LOSSLESS", failedSnapshot.tidalEnrichment().value().quality());
        assertFalse(failedSnapshot.tidalEnrichment().readSucceeded());
        // Audio continues working
        assertTrue(failedSnapshot.audioOutput().readSucceeded());
    }

    @Test
    void processProbeShouldThrottleReadsWithinThreeSeconds() throws Exception {
        AtomicInteger probeCalls = new AtomicInteger();
        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> {
            probeCalls.incrementAndGet();
            return ProcessPresence.RUNNING;
        });
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service.start();

        until(() -> probeCalls.get() >= 1);
        int initialCalls = probeCalls.get();
        // Wait 1.2 seconds: core polls at 500ms delay, so core ticks multiple times, but process probe should throttle
        Thread.sleep(1200);
        assertEquals(initialCalls, probeCalls.get());
    }

    @ParameterizedTest
    @EnumSource(MediaAction.class)
    void executeAsyncShouldDispatchToCorrectSessionMethodAndAcknowledge(MediaAction action) throws Exception {
        when(sessions.previous()).thenReturn(true);
        when(sessions.next()).thenReturn(true);
        when(sessions.pause()).thenReturn(true);
        when(sessions.resume()).thenReturn(true);
        when(sessions.playPause()).thenReturn(true);

        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        MediaActionResult result = service.executeAsync(action).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(action, result.action());
        assertEquals(MediaActionStatus.ACKNOWLEDGED, result.status());

        switch (action) {
            case PREVIOUS -> verify(sessions).previous();
            case NEXT -> verify(sessions).next();
            case PAUSE -> verify(sessions).pause();
            case RESUME -> verify(sessions).resume();
            case PLAY_PAUSE -> verify(sessions).playPause();
        }
    }

    @Test
    void executeAsyncShouldReturnRejectedWhenSessionReturnsFalse() throws Exception {
        when(sessions.pause()).thenReturn(false);
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        MediaActionResult result = service.executeAsync(MediaAction.PAUSE).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(MediaActionStatus.REJECTED, result.status());
    }

    @Test
    void executeAsyncShouldReturnFailedWhenSessionThrows() throws Exception {
        when(sessions.playPause()).thenThrow(new RuntimeException("Crash"));
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        MediaActionResult result = service.executeAsync(MediaAction.PLAY_PAUSE).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(MediaActionStatus.FAILED, result.status());
    }

    @Test
    void submitShouldReturnFalseAndExecuteAsyncShouldCompleteClosedWhenClosed() throws Exception {
        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        service.close();

        assertFalse(service.submit(MediaAction.NEXT));
        MediaActionResult result = service.executeAsync(MediaAction.NEXT).toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(MediaActionStatus.CLOSED, result.status());
    }

    @Test
    void closeShouldBeIdempotentAndCloseProvidersSafely() throws Exception {
        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        service.start();

        service.close();
        service.close();

        verify(sessions, atLeastOnce()).close();
        verify(audio, atLeastOnce()).close();
        verify(enrichment, atLeastOnce()).close();

        MediaContextSnapshot closedSnapshot = service.current();
        assertFalse(closedSnapshot.activeSession().readSucceeded());
        assertFalse(closedSnapshot.tidalPlayback().readSucceeded());
        assertFalse(closedSnapshot.tidalEnrichment().readSucceeded());
        assertFalse(closedSnapshot.audioOutput().readSucceeded());
        assertFalse(closedSnapshot.tidalProcess().readSucceeded());
    }

    @Test
    void closeShouldSurviveProviderCloseFailures() throws Exception {
        doThrow(new RuntimeException("Session close fail")).when(sessions).close();
        doThrow(new UnsatisfiedLinkError("Linkage error on close")).when(audio).close();

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        assertDoesNotThrow(() -> service.close());
    }

    // --- Tests for detectTidalProcess() ---

    @ParameterizedTest
    @ValueSource(strings = {"TIDAL.exe", "tidal.exe", "TiDaL.ExE", "C:\\Users\\User\\AppData\\Local\\TIDAL\\TIDAL.exe", "/opt/tidal/tidal.exe"})
    void detectTidalProcessShouldDetectRunningProcess(String executablePath) {
        ProcessHandle tidal = mockProcess(executablePath);
        ProcessHandle other = mockProcess("C:\\Windows\\explorer.exe");

        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(other, tidal));
            assertEquals(ProcessPresence.RUNNING, MediaContextService.detectTidalProcess());
        }
    }

    @Test
    void detectTidalProcessShouldReturnNotRunningWhenAllProcessesInspectedWithoutTidal() {
        ProcessHandle p1 = mockProcess("C:\\Program Files\\Spotify\\Spotify.exe");
        ProcessHandle p2 = mockProcess("C:\\Program Files\\Google\\Chrome\\chrome.exe");

        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(p1, p2));
            assertEquals(ProcessPresence.NOT_RUNNING, MediaContextService.detectTidalProcess());
        }
    }

    @Test
    void detectTidalProcessShouldReturnNotRunningWhenProcessStreamIsEmpty() {
        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());
            assertEquals(ProcessPresence.NOT_RUNNING, MediaContextService.detectTidalProcess());
        }
    }

    @Test
    void detectTidalProcessShouldReturnUnknownWhenProcessHasEmptyCommand() {
        ProcessHandle p1 = mockProcess(null); // empty command info
        ProcessHandle p2 = mockProcess("C:\\Windows\\notepad.exe");

        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(p1, p2));
            assertEquals(ProcessPresence.UNKNOWN, MediaContextService.detectTidalProcess());
        }
    }

    @Test
    void detectTidalProcessShouldReturnUnknownWhenProcessInfoThrowsRuntimeException() {
        ProcessHandle p1 = mock(ProcessHandle.class);
        when(p1.info()).thenThrow(new SecurityException("Access denied"));
        ProcessHandle p2 = mockProcess("C:\\Windows\\notepad.exe");

        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(p1, p2));
            assertEquals(ProcessPresence.UNKNOWN, MediaContextService.detectTidalProcess());
        }
    }

    @Test
    void detectTidalProcessShouldReturnUnknownWhenProcessInfoThrowsLinkageError() {
        ProcessHandle p1 = mock(ProcessHandle.class);
        when(p1.info()).thenThrow(new UnsatisfiedLinkError("Native failure"));

        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(p1));
            assertEquals(ProcessPresence.UNKNOWN, MediaContextService.detectTidalProcess());
        }
    }

    @Test
    void detectTidalProcessShouldReturnRunningEvenIfEarlierProcessWasIncomplete() {
        ProcessHandle incomplete = mockProcess(null);
        ProcessHandle tidal = mockProcess("C:\\TIDAL\\TIDAL.exe");

        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(incomplete, tidal));
            assertEquals(ProcessPresence.RUNNING, MediaContextService.detectTidalProcess());
        }
    }

    @Test
    void detectTidalProcessShouldReturnUnknownWhenAllProcessesThrows() {
        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenThrow(new RuntimeException("OS failure"));
            assertEquals(ProcessPresence.UNKNOWN, MediaContextService.detectTidalProcess());
        }

        try (var mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(ProcessHandle::allProcesses).thenThrow(new UnsatisfiedLinkError("OS linkage"));
            assertEquals(ProcessPresence.UNKNOWN, MediaContextService.detectTidalProcess());
        }
    }

    private static ProcessHandle mockProcess(String command) {
        ProcessHandle handle = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(handle.info()).thenReturn(info);
        when(info.command()).thenReturn(Optional.ofNullable(command));
        return handle;
    }
}
