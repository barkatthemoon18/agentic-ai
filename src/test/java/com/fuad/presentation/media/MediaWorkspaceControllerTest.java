package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.assistant.skills.media.LocalMediaControlParser;
import com.fuad.assistant.skills.media.MediaControlSkill;
import com.fuad.media.*;
import com.fuad.media.context.MediaContextService;
import com.fuad.media.context.ProcessPresence;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionResult;
import com.fuad.media.control.MediaActionStatus;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot.MediaQueueItem;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.fuad.testsupport.Await.until;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaWorkspaceControllerTest {
    private final MediaSessionProvider media = mock(MediaSessionProvider.class);
    private final AudioOutputProvider output = mock(AudioOutputProvider.class);
    private final MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);
    private final MediaTrack track = new MediaTrack("Song", "Artist", "Album", 120);

    private MediaSessionSnapshot session(String source, double position, MediaPlaybackState state) {
        return new MediaSessionSnapshot(true, source, Optional.of(track), position, state);
    }

    private MediaWorkspaceController controller() {
        when(media.current()).thenReturn(session("Spotify", 20, MediaPlaybackState.PLAYING));
        when(media.playbackState("TIDAL")).thenReturn(Optional.of(MediaPlaybackState.PAUSED));
        when(output.current()).thenReturn(new AudioOutputSnapshot(true, "endpoint", "Speakers",
                OptionalDouble.of(0.5), true));
        when(enrichment.current()).thenReturn(new MediaEnrichmentSnapshot("LOSSLESS",
                List.of(new MediaQueueItem("Next song", List.of("Guest")))));
        // The process probe is injected so tests never enumerate real OS processes.
        return new MediaWorkspaceController(new MediaContextService(media, output, enrichment,
                Clock.systemUTC(), () -> ProcessPresence.UNKNOWN));
    }

    @Test
    void pollingShouldComposeIndependentProvidersAndKeepTidalStateSeparate() throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            assertFalse(controller.current().available());
            controller.start();
            until(() -> controller.current().available() && controller.current().volume().isPresent()
                    && controller.current().mediaPlaybackState().isPresent());
            MediaWorkspaceSnapshot snapshot = controller.current();
            assertSame(track, snapshot.currentTrack().orElseThrow());
            assertEquals("Spotify", snapshot.sourceApplication());
            assertEquals(20, snapshot.positionSeconds());
            assertTrue(snapshot.playing());
            assertEquals(Optional.of(MediaPlaybackState.PAUSED), snapshot.mediaPlaybackState());
            assertEquals(0.5, snapshot.volume().orElseThrow());
            assertEquals("Speakers", snapshot.outputDevice());
        }
    }

    @Test
    void tidalEnrichmentShouldOnlyBeExposedWhileTidalIsTheActiveSession() throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            AtomicReference<MediaSessionSnapshot> active = new AtomicReference<>(
                    session("Spotify", 20, MediaPlaybackState.PLAYING));
            when(media.current()).thenAnswer(invocation -> active.get());

            controller.start();
            verify(enrichment, timeout(3000).atLeastOnce()).current();
            until(() -> controller.current().available());
            assertEquals("", controller.current().quality());
            assertTrue(controller.current().queue().isEmpty());

            active.set(session("TIDAL.exe", 21, MediaPlaybackState.PLAYING));
            until(() -> controller.current().quality().equals("LOSSLESS"));
            assertEquals("Next song", controller.current().queue().getFirst().title());

            active.set(session("Spotify", 22, MediaPlaybackState.PLAYING));
            until(() -> controller.current().quality().isEmpty());
            assertTrue(controller.current().queue().isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(MediaAction.class)
    void actionsShouldDelegateAndRefreshTheCoreContextEvenWhenTransportReturnsFalse(MediaAction action) throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            assertTrue(controller.submit(action));
            until(() -> controller.current().available());
            switch (action) {
                case NEXT -> verify(media).next();
                case PREVIOUS -> verify(media).previous();
                case PLAY_PAUSE -> verify(media).playPause();
                case PAUSE -> verify(media).pause();
                case RESUME -> verify(media).resume();
            }
            verify(media).current();
            verify(media).playbackState("TIDAL");
            verify(output).current();
            if (action != MediaAction.PLAY_PAUSE) {
                verify(media, never()).playPause();
            }
            // Enrichment lives on its own scheduler: actions must not block on (or trigger) it.
            verify(enrichment, never()).current();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "PAUSE, PLAYING, PAUSED",
            "PAUSE, PAUSED, PAUSED",
            "PAUSE, STOPPED, PAUSED",
            "PAUSE, UNKNOWN, PAUSED",
            "RESUME, PAUSED, PLAYING",
            "RESUME, PLAYING, PLAYING",
            "RESUME, STOPPED, PLAYING",
            "RESUME, UNKNOWN, PLAYING"
    })
    void pauseAndResumeShouldDelegateRegardlessOfCachedPlaybackState(MediaAction action,
                                                                    MediaPlaybackState cachedState,
                                                                    MediaPlaybackState expectedState) throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            AtomicReference<MediaSessionSnapshot> session = new AtomicReference<>(
                    session("Spotify", 20, cachedState));
            CountDownLatch refreshed = new CountDownLatch(3);
            when(media.current()).thenAnswer(invocation -> {
                refreshed.countDown();
                return session.get();
            });
            if (action == MediaAction.PAUSE) {
                when(media.pause()).thenAnswer(invocation -> {
                    session.set(session("Spotify", 20, expectedState));
                    return true;
                });
            } else {
                when(media.resume()).thenAnswer(invocation -> {
                    session.set(session("Spotify", 20, expectedState));
                    return true;
                });
            }

            // Publish a cached state before the active session changes independently.
            assertTrue(controller.submit(MediaAction.NEXT));
            until(() -> controller.current().available());
            assertEquals(cachedState, controller.current().playbackState());
            MediaPlaybackState activeState = action == MediaAction.PAUSE ? MediaPlaybackState.PLAYING : MediaPlaybackState.PAUSED;
            session.set(session("Spotify", 20, activeState));

            assertTrue(controller.submit(action));
            assertTrue(controller.submit(action));

            assertTrue(refreshed.await(3, TimeUnit.SECONDS), "Both actions should finish and refresh the session");
            until(() -> controller.current().playbackState() == expectedState);
            if (action == MediaAction.PAUSE) {
                verify(media, times(2)).pause();
                verify(media, never()).resume();
            } else {
                verify(media, times(2)).resume();
                verify(media, never()).pause();
            }
            verify(media, never()).playPause();
            verify(media, times(3)).current();
        }
    }

    @ParameterizedTest
    @EnumSource(MediaAction.class)
    void transportExceptionShouldRefreshCoreProvidersAndAllowFollowingActions(MediaAction action) throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            IllegalStateException failure = new IllegalStateException("transport");
            switch (action) {
                case NEXT -> when(media.next()).thenThrow(failure);
                case PREVIOUS -> when(media.previous()).thenThrow(failure);
                case PLAY_PAUSE -> when(media.playPause()).thenThrow(failure);
                case PAUSE -> when(media.pause()).thenThrow(failure);
                case RESUME -> when(media.resume()).thenThrow(failure);
            }
            when(media.current()).thenReturn(
                    session("Spotify", 20, MediaPlaybackState.PLAYING),
                    session("Spotify", 30, MediaPlaybackState.PLAYING));
            CountDownLatch followingAction = new CountDownLatch(1);
            AtomicReference<MediaWorkspaceSnapshot> refreshedAfterFailure = new AtomicReference<>();
            var barrier = doAnswer(invocation -> {
                refreshedAfterFailure.set(controller.current());
                followingAction.countDown();
                return true;
            }).when(media);
            MediaAction nextAction = action == MediaAction.NEXT ? MediaAction.PREVIOUS : MediaAction.NEXT;
            if (nextAction == MediaAction.PREVIOUS) {
                barrier.previous();
            } else {
                barrier.next();
            }

            assertTrue(controller.submit(action));
            assertTrue(controller.submit(nextAction));

            assertTrue(followingAction.await(3, TimeUnit.SECONDS), "The executor should continue after a transport exception");
            MediaWorkspaceSnapshot snapshot = refreshedAfterFailure.get();
            assertTrue(snapshot.available());
            assertSame(track, snapshot.currentTrack().orElseThrow());
            assertEquals(20, snapshot.positionSeconds());
            assertEquals("Speakers", snapshot.outputDevice());
            assertEquals(0.5, snapshot.volume().orElseThrow());
            assertEquals(Optional.of(MediaPlaybackState.PAUSED), snapshot.mediaPlaybackState());
            until(() -> controller.current().positionSeconds() == 30);
            verify(media, times(2)).current();
            verify(media, times(2)).playbackState("TIDAL");
            verify(output, times(2)).current();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pausa la música", "Pausa TIDAL"})
    void aPlayerNameInTheCommandShouldStillPauseTheActiveSpotifySession(String command) throws Exception {
        OpenAIClient client = mock(OpenAIClient.class);
        try (MediaWorkspaceController controller = controller()) {
            when(media.pause()).thenReturn(true);
            assertTrue(controller.submit(MediaAction.NEXT));
            until(() -> controller.current().available());
            CountDownLatch refreshed = new CountDownLatch(1);
            when(media.current()).thenAnswer(invocation -> {
                refreshed.countDown();
                return session("Spotify", 20, MediaPlaybackState.PAUSED);
            });
            MediaControlSkill skill = new MediaControlSkill(new LocalMediaControlParser(client, "model"), controller);

            assertEquals("Reproducción pausada.", skill.execute(command).text());

            assertTrue(refreshed.await(3, TimeUnit.SECONDS));
            until(() -> controller.current().playbackState() == MediaPlaybackState.PAUSED);
            assertEquals("Spotify", controller.current().sourceApplication());
            verify(media).pause();
            verify(media, never()).playPause();
            verifyNoInteractions(client);
        }
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void providerFailureShouldPreserveOtherProvidersAndRecover(Failure failure) throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            AtomicBoolean healthy = new AtomicBoolean();
            // TIDAL must be the active session so its enrichment is exposed when everything is healthy.
            when(media.current()).thenAnswer(invocation -> {
                if (failure == Failure.MEDIA && !healthy.get()) throw new IllegalStateException("media");
                return session("TIDAL", 20, MediaPlaybackState.PLAYING);
            });
            when(media.playbackState("TIDAL")).thenAnswer(invocation -> {
                if (failure == Failure.TIDAL && !healthy.get()) throw new IllegalStateException("tidal");
                return Optional.of(MediaPlaybackState.PAUSED);
            });
            when(output.current()).thenAnswer(invocation -> {
                if (failure == Failure.AUDIO && !healthy.get()) throw new IllegalStateException("audio");
                return new AudioOutputSnapshot(true, "endpoint", "Speakers", OptionalDouble.of(0.5), true);
            });
            when(enrichment.current()).thenAnswer(invocation -> {
                if (failure == Failure.ENRICHMENT && !healthy.get()) throw new IllegalStateException("enrichment");
                return new MediaEnrichmentSnapshot("LOSSLESS", List.of(new MediaQueueItem("Next song", List.of())));
            });

            controller.start();

            // Degraded: only the failing provider (and what depends on it) is missing.
            // Enrichment is gated by the active TIDAL session, so a media failure hides it too.
            boolean mediaOk = failure != Failure.MEDIA;
            boolean audioOk = failure != Failure.AUDIO;
            boolean tidalOk = failure != Failure.TIDAL;
            boolean enrichmentOk = failure != Failure.ENRICHMENT && mediaOk;
            until(() -> {
                MediaWorkspaceSnapshot s = controller.current();
                return s.available() == mediaOk
                        && s.volume().isPresent() == audioOk
                        && s.mediaPlaybackState().isPresent() == tidalOk
                        && s.quality().equals(enrichmentOk ? "LOSSLESS" : "")
                        && !s.queue().isEmpty() == enrichmentOk;
            });

            healthy.set(true);
            until(() -> {
                MediaWorkspaceSnapshot s = controller.current();
                return s.available() && s.volume().isPresent() && s.quality().equals("LOSSLESS")
                        && s.mediaPlaybackState().isPresent();
            });
        }
    }

    @Test
    void closeShouldReleaseEveryProviderEvenWhenOneFailsAndRejectNewActions() throws Exception {
        MediaWorkspaceController controller = controller();
        doThrow(new IllegalStateException("close")).when(media).close();
        assertDoesNotThrow(controller::close);
        verify(media).close();
        verify(output).close();
        verify(enrichment).close();
        assertFalse(controller.submit(MediaAction.NEXT));
        verify(media, never()).next();
    }

    @Test
    void closeShouldInvalidateThePublishedContextSoStaleDataIsNeverExposed() throws Exception {
        MediaWorkspaceController controller = controller();
        controller.start();
        until(() -> controller.current().available() && controller.current().volume().isPresent());

        controller.close();

        MediaWorkspaceSnapshot snapshot = controller.current();
        assertFalse(snapshot.available());
        assertTrue(snapshot.volume().isEmpty());
        assertEquals("", snapshot.quality());
        assertTrue(snapshot.mediaPlaybackState().isEmpty());
        assertFalse(controller.contextProvider().current().activeSession().readSucceeded());
        assertFalse(controller.contextProvider().current().audioOutput().readSucceeded());
    }

    @Test
    void closeShouldStopPollingAllProviders() throws Exception {
        MediaWorkspaceController controller = controller();
        controller.start();
        until(() -> controller.current().available());
        controller.close();

        clearInvocations(media, output, enrichment);
        Thread.sleep(700);

        verify(media, never()).current();
        verify(output, never()).current();
        verify(enrichment, never()).current();
    }

    @Test
    void optionalProvidersShouldDefaultToUnknownDeviceAndNoEnrichment() throws Exception {
        when(media.current()).thenReturn(session("Tidal", 20, MediaPlaybackState.PLAYING));
        try (MediaWorkspaceController controller = new MediaWorkspaceController(media)) {
            assertTrue(controller.submit(MediaAction.NEXT));
            until(() -> controller.current().available());
            assertTrue(controller.current().volume().isEmpty());
            assertEquals("", controller.current().outputDevice());
            assertEquals("", controller.current().quality());
            assertTrue(controller.current().queue().isEmpty());
        }
    }

    @Test
    void contextProviderShouldExposeTheServiceBackingTheController() throws Exception {
        MediaContextService service = new MediaContextService(media, output, enrichment,
                Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        try (MediaWorkspaceController controller = new MediaWorkspaceController(service)) {
            assertSame(service, controller.contextProvider());
        }
    }

    @Test
    void controllerShouldMapTheServiceContextThroughTheInjectedMapper() throws Exception {
        MediaContextService service = new MediaContextService(media, output, enrichment,
                Clock.systemUTC(), () -> ProcessPresence.UNKNOWN);
        MediaWorkspaceMapper mapper = mock(MediaWorkspaceMapper.class);
        MediaWorkspaceSnapshot mapped = MediaWorkspaceSnapshot.unavailable();
        when(mapper.map(any())).thenReturn(mapped);
        try (MediaWorkspaceController controller = new MediaWorkspaceController(service, mapper)) {
            assertSame(mapped, controller.current());
            verify(mapper).map(service.current());
        }
    }

    enum Failure { MEDIA, AUDIO, ENRICHMENT, TIDAL }

    private static MediaActionResult await(CompletableFuture<MediaActionResult> future) throws Exception {
        return future.get(3, TimeUnit.SECONDS);
    }

    @Test
    void rejectedActionShouldBeReportedByTheSkillInsteadOfConfirmed() throws Exception {
        OpenAIClient client = mock(OpenAIClient.class);
        try (MediaWorkspaceController controller = controller()) {
            when(media.pause()).thenReturn(false);
            MediaControlSkill skill = new MediaControlSkill(new LocalMediaControlParser(client, "model"), controller);

            assertEquals("No pude aplicar el control multimedia", skill.execute("Pausa la música").text());

            verify(media).pause();
            verifyNoInteractions(client);
        }
    }

    @ParameterizedTest
    @CsvSource({"true, ACKNOWLEDGED", "false, REJECTED"})
    void executeAsyncShouldReportWhetherTheTransportAcceptedTheAction(boolean accepted, MediaActionStatus expected)
            throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            when(media.pause()).thenReturn(accepted);

            MediaActionResult result = await(controller.executeAsync(MediaAction.PAUSE).toCompletableFuture());

            assertEquals(new MediaActionResult(MediaAction.PAUSE, expected), result);
            assertEquals(accepted, result.successful());
            until(() -> controller.current().available());
        }
    }

    @ParameterizedTest
    @EnumSource(MediaAction.class)
    void executeAsyncShouldReportFailureOnTransportExceptionAndKeepServingActions(MediaAction action) throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            IllegalStateException failure = new IllegalStateException("transport");
            switch (action) {
                case NEXT -> when(media.next()).thenThrow(failure);
                case PREVIOUS -> when(media.previous()).thenThrow(failure);
                case PLAY_PAUSE -> when(media.playPause()).thenThrow(failure);
                case PAUSE -> when(media.pause()).thenThrow(failure);
                case RESUME -> when(media.resume()).thenThrow(failure);
            }
            MediaAction other = action == MediaAction.NEXT ? MediaAction.PREVIOUS : MediaAction.NEXT;
            if (other == MediaAction.PREVIOUS) {
                when(media.previous()).thenReturn(true);
            } else {
                when(media.next()).thenReturn(true);
            }

            MediaActionResult failed = await(controller.executeAsync(action).toCompletableFuture());
            MediaActionResult following = await(controller.executeAsync(other).toCompletableFuture());

            assertEquals(new MediaActionResult(action, MediaActionStatus.FAILED), failed);
            assertFalse(failed.successful());
            assertEquals(MediaActionStatus.ACKNOWLEDGED, following.status());
        }
    }

    @Test
    void actionsAfterCloseShouldBeClosedWithoutTouchingTheProvider() throws Exception {
        MediaWorkspaceController controller = controller();
        controller.close();

        MediaActionResult result = await(controller.executeAsync(MediaAction.NEXT).toCompletableFuture());

        assertEquals(new MediaActionResult(MediaAction.NEXT, MediaActionStatus.CLOSED), result);
        assertFalse(controller.submit(MediaAction.PAUSE));
        verify(media, never()).next();
        verify(media, never()).pause();
        assertDoesNotThrow(controller::close);
        verify(media, times(1)).close();
    }

    @Test
    void closeShouldCompleteRunningAndQueuedActionsAsClosedAndNeverRunTheQueuedOne() throws Exception {
        MediaWorkspaceController controller = controller();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(media.next()).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return true;
        });
        CompletableFuture<MediaActionResult> running = controller.executeAsync(MediaAction.NEXT).toCompletableFuture();
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        CompletableFuture<MediaActionResult> queued = controller.executeAsync(MediaAction.PAUSE).toCompletableFuture();

        CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> {
            try {
                controller.close();
            }
            catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        try {
            assertEquals(MediaActionStatus.CLOSED, await(queued).status());
            assertEquals(MediaActionStatus.CLOSED, await(running).status());
        }
        finally {
            release.countDown();
        }
        closing.get(10, TimeUnit.SECONDS);
        verify(media, never()).pause();
        verify(media).close();
        verify(output).close();
        verify(enrichment).close();
    }

    @Test
    void closingFromTheMediaWorkerShouldNotDeadlockAndStillReleaseProviders() throws Exception {
        MediaWorkspaceController controller = controller();
        when(media.next()).thenAnswer(invocation -> {
            controller.close();
            return true;
        });

        MediaActionResult result = await(controller.executeAsync(MediaAction.NEXT).toCompletableFuture());

        assertEquals(MediaActionStatus.CLOSED, result.status());
        verify(media, timeout(3000)).close();
        verify(output, timeout(3000)).close();
        verify(enrichment, timeout(3000)).close();
    }

    @Test
    void startShouldBeIdempotentAndRejectedAfterClose() throws Exception {
        MediaWorkspaceController controller = controller();
        controller.start();
        assertDoesNotThrow(controller::start);
        until(() -> controller.current().available());
        controller.close();

        assertThrows(IllegalStateException.class, controller::start);
    }

    @Test
    void shouldRejectNullActionsAndDependencies() throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            assertThrows(NullPointerException.class, () -> controller.submit(null));
            assertThrows(NullPointerException.class, () -> controller.executeAsync(null));
        }
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController(null, output, enrichment));
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController(media, null, enrichment));
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController(media, output, null));
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController((MediaContextService) null));
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController(
                new MediaContextService(media, output, enrichment), null));
    }
}
