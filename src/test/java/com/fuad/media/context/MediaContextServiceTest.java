package com.fuad.media.context;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.audio.output.AudioReservation;
import com.fuad.audio.output.MediaContextAudioReservationDetector;
import com.fuad.logging.LogCategory;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.MediaTrack;
import com.fuad.media.control.*;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import com.fuad.platform.windows.process.ProcessInventorySnapshot;
import com.fuad.platform.windows.process.ProcessInventorySnapshot.ProcessEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.fuad.testsupport.Await.until;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaContextServiceTest {
    private MediaSessionProvider sessions;
    private AudioOutputProvider audio;
    private MediaEnrichmentProvider enrichment;
    private MediaContextService service;

    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        sessions = mock(MediaSessionProvider.class);
        audio = mock(AudioOutputProvider.class);
        enrichment = mock(MediaEnrichmentProvider.class);

        logger = (Logger) LoggerFactory.getLogger(MediaContextService.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        capturedEvents = new ListAppender<>();
        capturedEvents.start();
        logger.addAppender(capturedEvents);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (service != null) {
            service.close();
        }
        if (logger != null && capturedEvents != null) {
            logger.detachAppender(capturedEvents);
            capturedEvents.stop();
            logger.setLevel(originalLevel);
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

    // 1. Una lectura de sesiones falla -> Se invalida solo su muestra
    @Test
    void sessionReadFailureShouldInvalidateOnlyItsSample() throws Exception {
        MediaTrack track = new MediaTrack("Song", "Artist", "Album", 180);
        MediaSessionSnapshot okSession = new MediaSessionSnapshot(true, "TIDAL", Optional.of(track), 10.0, MediaPlaybackState.PLAYING);
        AudioOutputSnapshot okAudio = new AudioOutputSnapshot(true, "dev-1", "Speakers", OptionalDouble.of(0.8), false);
        MediaEnrichmentSnapshot okEnrichment = new MediaEnrichmentSnapshot("HI_RES_LOSSLESS", List.of());

        when(sessions.current()).thenReturn(okSession);
        when(sessions.playbackState("TIDAL")).thenReturn(Optional.of(MediaPlaybackState.PLAYING));
        when(audio.current()).thenReturn(okAudio);
        when(enrichment.current()).thenReturn(okEnrichment);

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.RUNNING);
        service.start();

        until(() -> service.current().activeSession().readSucceeded()
                && service.current().audioOutput().readSucceeded()
                && service.current().tidalProcess().readSucceeded());

        // Now session read fails, but other providers continue functioning
        when(sessions.current()).thenThrow(new RuntimeException("Session IPC failed"));

        until(() -> !service.current().activeSession().readSucceeded());

        MediaContextSnapshot snapshot = service.current();
        // Only active session sample is invalidated
        assertFalse(snapshot.activeSession().readSucceeded());
        assertEquals("TIDAL", snapshot.activeSession().value().sourceApplication());

        // Other samples remain valid
        assertTrue(snapshot.audioOutput().readSucceeded());
        assertTrue(snapshot.tidalProcess().readSucceeded());
        assertTrue(snapshot.tidalPlayback().readSucceeded());
        assertTrue(snapshot.tidalEnrichment().readSucceeded());
    }

    // 2. Falla el enriquecimiento TIDAL -> No afecta el inventario de procesos ni TTS
    @Test
    void tidalEnrichmentFailureShouldNotAffectProcessInventoryOrTts() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
        AudioOutputSnapshot okAudio = new AudioOutputSnapshot(true, "dev-1", "Speakers", OptionalDouble.of(0.5), false);

        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.of(MediaPlaybackState.PAUSED));
        when(audio.current()).thenReturn(okAudio);
        when(enrichment.current()).thenThrow(new RuntimeException("TIDAL enrichment service unavailable"));

        service = new MediaContextService(sessions, audio, enrichment, fixedClock, () -> ProcessPresence.NOT_RUNNING);
        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(service, fixedClock);
        service.start();

        until(() -> service.current().tidalProcess().readSucceeded()
                && !service.current().tidalEnrichment().readSucceeded());

        MediaContextSnapshot snapshot = service.current();
        // Enrichment failed
        assertFalse(snapshot.tidalEnrichment().readSucceeded());

        // Process inventory is NOT affected
        assertTrue(snapshot.tidalProcess().readSucceeded());
        assertEquals(ProcessPresence.NOT_RUNNING, snapshot.tidalProcess().value());

        // TTS is NOT blocked (audio output is available for TTS)
        assertFalse(detector.isOutputReserved());
        assertEquals(AudioReservation.AVAILABLE, detector.reservation());
    }

    // 3. El proceso TIDAL deja de detectarse de forma fiable -> TTS permanece bloqueado
    @Test
    void unreliableTidalProcessDetectionShouldKeepTtsBlockedWhenPresenceUnknownOrProbeFails() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
        AtomicReference<ProcessPresence> presenceRef = new AtomicReference<>(ProcessPresence.UNKNOWN);

        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());
        when(enrichment.current()).thenReturn(MediaEnrichmentSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, fixedClock, presenceRef::get);
        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(service, fixedClock);
        service.start();

        until(() -> service.current().tidalProcess().readSucceeded());

        // Presence is UNKNOWN -> TTS remains blocked
        assertEquals(ProcessPresence.UNKNOWN, service.current().tidalProcess().value());
        assertTrue(detector.isOutputReserved());
        assertEquals(AudioReservation.UNKNOWN, detector.reservation());

        // When probe throws / fails to detect reliably -> sample failed -> TTS remains blocked
        service.close();
        service = new MediaContextService(sessions, audio, enrichment, fixedClock, () -> {
            throw new RuntimeException("Process scan failed");
        });
        MediaContextAudioReservationDetector failedDetector = new MediaContextAudioReservationDetector(service, fixedClock);
        service.start();

        until(() -> !service.current().tidalProcess().readSucceeded());
        assertTrue(failedDetector.isOutputReserved());
        assertEquals(AudioReservation.UNKNOWN, failedDetector.reservation());
    }

    // 4. Un comando se cancela mientras está encolado -> No llega al provider
    @Test
    void commandCancelledWhileQueuedShouldNotReachProvider() throws Exception {
        CountDownLatch firstRunning = new CountDownLatch(1);
        CountDownLatch letFirstProceed = new CountDownLatch(1);

        when(sessions.playPause()).thenAnswer(invocation -> {
            firstRunning.countDown();
            letFirstProceed.await(5, TimeUnit.SECONDS);
            return true;
        });
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        // Occupy coreExecutor with the first command
        service.executeAsync(MediaAction.PLAY_PAUSE);
        assertTrue(firstRunning.await(5, TimeUnit.SECONDS));

        // Submit second command while coreExecutor is busy
        CompletableFuture<MediaActionResult> queuedFuture =
                service.executeAsync(MediaAction.NEXT).toCompletableFuture();

        // Cancel the second command while it's waiting in the queue
        queuedFuture.cancel(true);

        // Allow the first command to finish so coreExecutor picks up the second task
        letFirstProceed.countDown();

        // Wait for queue processing
        Thread.sleep(150);

        // The second command was cancelled before runAction started, so it never reaches provider
        verify(sessions, never()).next();
        assertTrue(queuedFuture.isCancelled());
    }

    // 5. La sesión activa cambia entre dos comandos -> Cada comando resuelve la sesión vigente
    @Test
    void activeSessionChangesBetweenCommandsShouldResolveCurrentSessionForEachCommand() throws Exception {
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        when(sessions.next()).thenReturn(true);
        when(sessions.previous()).thenReturn(true);

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        MediaActionResult first = service.executeAsync(MediaAction.NEXT).toCompletableFuture().get(3, TimeUnit.SECONDS);
        MediaActionResult second = service.executeAsync(MediaAction.PREVIOUS).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(MediaActionStatus.ACKNOWLEDGED, first.status());
        assertEquals(MediaActionStatus.ACKNOWLEDGED, second.status());
        verify(sessions).next();
        verify(sessions).previous();
    }

    // 6. Una aplicación desaparece antes de ejecutar -> REJECTED
    @Test
    void applicationDisappearingBeforeExecutionShouldReturnRejected() throws Exception {
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        MediaControlRequest request = MediaControlRequest.application(MediaAction.PAUSE, "Spotify");
        // Application disappeared before execution -> session cannot be resolved -> provider returns false
        when(sessions.execute(request)).thenReturn(false);

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        MediaActionResult result = service.executeAsync(request).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(MediaActionStatus.REJECTED, result.status());
        assertEquals(MediaAction.PAUSE, result.action());
        assertFalse(result.successful());
    }

    // 7. Dos sesiones coinciden con el mismo destino -> REJECTED
    @Test
    void twoSessionsMatchingSameDestinationShouldReturnRejected() throws Exception {
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        MediaControlRequest request = MediaControlRequest.application(MediaAction.PAUSE, "TIDAL");
        // Ambiguous destination matches multiple sessions -> provider returns false
        when(sessions.execute(request)).thenReturn(false);

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        MediaActionResult result = service.executeAsync(request).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(MediaActionStatus.REJECTED, result.status());
        assertEquals(MediaAction.PAUSE, result.action());
        assertFalse(result.successful());
    }

    // 8. Se cierra ARES con comandos pendientes -> Los pendientes terminan como CLOSED
    @Test
    void closingAresWithPendingCommandsShouldCompleteThemAsClosed() throws Exception {
        CountDownLatch workerHolding = new CountDownLatch(1);
        CountDownLatch letWorkerFinish = new CountDownLatch(1);

        when(sessions.playPause()).thenAnswer(invocation -> {
            workerHolding.countDown();
            letWorkerFinish.await(5, TimeUnit.SECONDS);
            return true;
        });
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        // Occupy worker
        service.executeAsync(MediaAction.PLAY_PAUSE);
        assertTrue(workerHolding.await(5, TimeUnit.SECONDS));

        // Submit pending commands while worker is busy
        CompletableFuture<MediaActionResult> pending1 =
                service.executeAsync(MediaAction.NEXT).toCompletableFuture();
        CompletableFuture<MediaActionResult> pending2 =
                service.executeAsync(MediaControlRequest.application(MediaAction.PREVIOUS, "Spotify")).toCompletableFuture();

        // Close service while commands are pending
        CompletableFuture<Void> closeFuture = CompletableFuture.runAsync(() -> {
            try {
                service.close();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Pending commands must complete as CLOSED
        MediaActionResult result1 = pending1.get(3, TimeUnit.SECONDS);
        MediaActionResult result2 = pending2.get(3, TimeUnit.SECONDS);

        assertEquals(MediaActionStatus.CLOSED, result1.status());
        assertEquals(MediaAction.NEXT, result1.action());
        assertEquals(MediaActionStatus.CLOSED, result2.status());
        assertEquals(MediaAction.PREVIOUS, result2.action());

        letWorkerFinish.countDown();
        closeFuture.get(5, TimeUnit.SECONDS);
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

    // --- Tests for detectTidalProcess() and resolveTidalPresence() ---

    @ParameterizedTest
    @ValueSource(strings = {"TIDAL.exe", "tidal.exe", "TiDaL.ExE"})
    void resolveTidalPresenceShouldDetectRunningProcess(String executableName) {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.completed(List.of(
                new ProcessEntry(100L, "explorer.exe"),
                new ProcessEntry(101L, executableName)
        ));

        assertEquals(ProcessPresence.RUNNING, MediaContextService.resolveTidalPresence(snapshot));
    }

    @Test
    void resolveTidalPresenceShouldReturnRunningEvenIfSnapshotIsIncomplete() {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.incomplete(List.of(
                new ProcessEntry(100L, "explorer.exe"),
                new ProcessEntry(101L, "TIDAL.exe")
        ));

        assertEquals(ProcessPresence.RUNNING, MediaContextService.resolveTidalPresence(snapshot));
    }

    @Test
    void resolveTidalPresenceShouldReturnNotRunningWhenAllProcessesInspectedWithoutTidal() {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.completed(List.of(
                new ProcessEntry(100L, "Spotify.exe"),
                new ProcessEntry(101L, "chrome.exe")
        ));

        assertEquals(ProcessPresence.NOT_RUNNING, MediaContextService.resolveTidalPresence(snapshot));
    }

    @Test
    void resolveTidalPresenceShouldReturnNotRunningWhenProcessSnapshotIsEmpty() {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.completed(List.of());

        assertEquals(ProcessPresence.NOT_RUNNING, MediaContextService.resolveTidalPresence(snapshot));
    }

    @Test
    void resolveTidalPresenceShouldReturnUnknownWhenSnapshotIsIncompleteWithoutTidal() {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.incomplete(List.of(
                new ProcessEntry(100L, "notepad.exe")
        ));

        assertEquals(ProcessPresence.UNKNOWN, MediaContextService.resolveTidalPresence(snapshot));
    }

    @Test
    void resolveTidalPresenceShouldReturnUnknownWhenSnapshotIsEmptyAndIncomplete() {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.incomplete(List.of());

        assertEquals(ProcessPresence.UNKNOWN, MediaContextService.resolveTidalPresence(snapshot));
    }

    @Test
    void detectTidalProcessShouldReturnNonNullPresence() {
        ProcessPresence presence = MediaContextService.detectTidalProcess();
        assertNotNull(presence);
        assertTrue(presence == ProcessPresence.RUNNING
                || presence == ProcessPresence.NOT_RUNNING
                || presence == ProcessPresence.UNKNOWN);
    }

    // --- Tests for action terminal event logging ---

    @Test
    void singleActionShouldGenerateExactlyOneTerminalLogEvent() throws Exception {
        when(sessions.next()).thenReturn(true);
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        MediaActionResult result = service.executeAsync(MediaAction.NEXT).toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(MediaActionStatus.ACKNOWLEDGED, result.status());

        until(() -> capturedEvents.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("event=MEDIA_ACTION")));

        List<ILoggingEvent> actionLogs = capturedEvents.list.stream()
                .filter(event -> event.getFormattedMessage().contains("event=MEDIA_ACTION"))
                .toList();

        assertEquals(1, actionLogs.size(), "Una acción genera exactamente un evento terminal");
        ILoggingEvent event = actionLogs.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(List.of(LogCategory.MEDIA), event.getMarkerList());
        assertEquals("event=MEDIA_ACTION action=NEXT, target=ACTIVE, status=ACKNOWLEDGED", event.getFormattedMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "PAUSE, false, REJECTED",
            "PREVIOUS, true, ACKNOWLEDGED"
    })
    void rejectedOrAcknowledgedActionShouldGenerateExactlyOneTerminalEvent(MediaAction action, boolean ack, String expectedStatus) throws Exception {
        when(sessions.pause()).thenReturn(ack);
        when(sessions.previous()).thenReturn(ack);
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(sessions.playbackState(anyString())).thenReturn(Optional.empty());
        when(audio.current()).thenReturn(AudioOutputSnapshot.unavailable());

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);

        MediaActionResult result = service.executeAsync(action).toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(MediaActionStatus.valueOf(expectedStatus), result.status());

        until(() -> capturedEvents.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("event=MEDIA_ACTION")));

        List<ILoggingEvent> actionLogs = capturedEvents.list.stream()
                .filter(event -> event.getFormattedMessage().contains("event=MEDIA_ACTION"))
                .toList();

        assertEquals(1, actionLogs.size(), "Una acción genera exactamente un evento terminal");
        ILoggingEvent event = actionLogs.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(List.of(LogCategory.MEDIA), event.getMarkerList());
        assertTrue(event.getFormattedMessage().contains("action=" + action));
        assertTrue(event.getFormattedMessage().contains("status=" + expectedStatus));
    }

    // --- Tests for session change registration ---

    @Test
    void sessionChangeShouldBeLoggedExactlyOnce() throws Exception {
        MediaTrack track1 = new MediaTrack("Song 1", "Artist 1", "Album 1", 180);
        MediaTrack track2 = new MediaTrack("Song 2", "Artist 2", "Album 2", 200);

        MediaSessionSnapshot spotifySession = new MediaSessionSnapshot(true, "Spotify", Optional.of(track1), 10.0, MediaPlaybackState.PLAYING);
        MediaSessionSnapshot tidalSession = new MediaSessionSnapshot(true, "TIDAL", Optional.of(track2), 20.0, MediaPlaybackState.PLAYING);
        AudioOutputSnapshot okAudio = new AudioOutputSnapshot(true, "dev-1", "Speakers", OptionalDouble.of(0.8), false);

        AtomicReference<MediaSessionSnapshot> currentSession = new AtomicReference<>(spotifySession);
        when(sessions.current()).thenAnswer(inv -> currentSession.get());
        when(sessions.playbackState(anyString())).thenReturn(Optional.of(MediaPlaybackState.PLAYING));
        when(audio.current()).thenReturn(okAudio);
        when(enrichment.current()).thenReturn(new MediaEnrichmentSnapshot("LOSSLESS", List.of()));

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.RUNNING);
        service.start();

        // Wait until initial session (Spotify) is observed
        until(() -> service.current().activeSession().readSucceeded()
                && "Spotify".equals(service.current().activeSession().value().sourceApplication()));

        // At this point lastObservedSession was initialized to Spotify (no change log yet)
        List<ILoggingEvent> initialChangeLogs = capturedEvents.list.stream()
                .filter(event -> event.getFormattedMessage().contains("SESION_CHANGED")
                        || event.getFormattedMessage().contains("SESSION_CHANGED"))
                .toList();
        assertTrue(initialChangeLogs.isEmpty());

        // Change session from Spotify to TIDAL
        currentSession.set(tidalSession);

        until(() -> "TIDAL".equals(service.current().activeSession().value().sourceApplication())
                && capturedEvents.list.stream().anyMatch(event ->
                (event.getFormattedMessage().contains("SESION_CHANGED")
                        || event.getFormattedMessage().contains("SESSION_CHANGED"))
                        && event.getFormattedMessage().contains("from=Spotify to=TIDAL")));

        // Wait to allow multiple core refresh cycles while still TIDAL
        Thread.sleep(1200);

        List<ILoggingEvent> changeLogs = capturedEvents.list.stream()
                .filter(event -> event.getFormattedMessage().contains("SESION_CHANGED")
                        || event.getFormattedMessage().contains("SESSION_CHANGED"))
                .toList();

        // Cambio de sesión registrado una sola vez
        assertEquals(1, changeLogs.size(), "El cambio de sesión a TIDAL debe registrarse exactamente una vez");
        ILoggingEvent event = changeLogs.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(List.of(LogCategory.MEDIA), event.getMarkerList());
        assertTrue(event.getFormattedMessage().contains("from=Spotify to=TIDAL"));
    }

    // --- Tests for provider failure and recovery logging ---

    @Test
    void repeatedProviderFailureShouldLogSingleWarnAndRecoveryShouldLogSingleInfo() throws Exception {
        MediaTrack track = new MediaTrack("Song", "Artist", "Album", 180);
        MediaSessionSnapshot okSession = new MediaSessionSnapshot(true, "TIDAL", Optional.of(track), 10.0, MediaPlaybackState.PLAYING);
        AudioOutputSnapshot okAudio = new AudioOutputSnapshot(true, "dev-1", "Speakers", OptionalDouble.of(0.8), false);
        MediaEnrichmentSnapshot okEnrichment = new MediaEnrichmentSnapshot("LOSSLESS", List.of());

        AtomicBoolean failing = new AtomicBoolean(true);
        when(sessions.current()).thenAnswer(inv -> {
            if (failing.get()) {
                throw new RuntimeException("Session provider crashed");
            }
            return okSession;
        });
        when(sessions.playbackState(anyString())).thenReturn(Optional.of(MediaPlaybackState.PLAYING));
        when(audio.current()).thenReturn(okAudio);
        when(enrichment.current()).thenReturn(okEnrichment);

        service = new MediaContextService(sessions, audio, enrichment, Clock.systemUTC(), () -> ProcessPresence.RUNNING);
        service.start();

        // Wait until at least one failure cycle completes
        until(() -> capturedEvents.list.stream().anyMatch(e ->
                e.getFormattedMessage().contains("event=MEDIA_PROVIDER_FAILURE")
                        && e.getFormattedMessage().contains("component=active session")
                        && e.getFormattedMessage().contains("state=FAILED")));

        // Let core refresh tick several more times while still failing (repeated failure)
        Thread.sleep(1200);

        List<ILoggingEvent> failureLogs = capturedEvents.list.stream()
                .filter(e -> e.getFormattedMessage().contains("event=MEDIA_PROVIDER_FAILURE")
                        && e.getFormattedMessage().contains("component=active session")
                        && e.getFormattedMessage().contains("state=FAILED"))
                .toList();

        // Fallo repetido -> un WARN
        assertEquals(1, failureLogs.size(), "El fallo repetido debe generar un solo WARN");
        ILoggingEvent failureEvent = failureLogs.getFirst();
        assertEquals(Level.WARN, failureEvent.getLevel());
        assertEquals(List.of(LogCategory.MEDIA), failureEvent.getMarkerList());
        assertTrue(failureEvent.getFormattedMessage().contains("cause=RuntimeException"));

        // Now recover
        failing.set(false);

        // Wait until recovery cycle completes
        until(() -> capturedEvents.list.stream().anyMatch(e ->
                e.getFormattedMessage().contains("event=MEDIA_PROVIDER_FAILURE")
                        && e.getFormattedMessage().contains("component=active session")
                        && e.getFormattedMessage().contains("state=RECOVERED")));

        // Let core refresh tick several more times while recovered
        Thread.sleep(1200);

        List<ILoggingEvent> recoveryLogs = capturedEvents.list.stream()
                .filter(e -> e.getFormattedMessage().contains("event=MEDIA_PROVIDER_FAILURE")
                        && e.getFormattedMessage().contains("component=active session")
                        && e.getFormattedMessage().contains("state=RECOVERED"))
                .toList();

        // Recuperación -> un INFO
        assertEquals(1, recoveryLogs.size(), "La recuperación debe generar un solo INFO");
        ILoggingEvent recoveryEvent = recoveryLogs.getFirst();
        assertEquals(Level.INFO, recoveryEvent.getLevel());
        assertEquals(List.of(LogCategory.MEDIA), recoveryEvent.getMarkerList());
    }
}
