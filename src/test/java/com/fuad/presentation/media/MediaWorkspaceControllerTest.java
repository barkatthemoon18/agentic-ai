package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.assistant.skills.media.LocalMediaControlParser;
import com.fuad.assistant.skills.media.MediaControlSkill;
import com.fuad.media.*;
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

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.fuad.testsupport.Await.until;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaWorkspaceControllerTest {
    private final MediaSessionProvider media = mock(MediaSessionProvider.class);
    private final AudioOutputProvider output = mock(AudioOutputProvider.class);
    private final MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);
    private final MediaTrack track = new MediaTrack("Song", "Artist", "Album", 120);

    private MediaWorkspaceController controller() {
        when(media.current()).thenReturn(new MediaSessionSnapshot(true, "Spotify", Optional.of(track),
                20, MediaPlaybackState.PLAYING));
        when(media.playbackState("TIDAL")).thenReturn(Optional.of(MediaPlaybackState.PAUSED));
        when(output.current()).thenReturn(new AudioOutputSnapshot(true, "endpoint", "Speakers",
                OptionalDouble.of(0.5), true));
        when(enrichment.current()).thenReturn(new MediaEnrichmentSnapshot("LOSSLESS",
                List.of(new MediaQueueItem("Next song", List.of("Guest")))));
        return new MediaWorkspaceController(media, output, enrichment);
    }

    @Test
    void pollingShouldComposeIndependentProvidersAndKeepTidalStateSeparate() throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            assertFalse(controller.current().available());
            controller.start();
            until(() -> controller.current().available());
            MediaWorkspaceSnapshot snapshot = controller.current();
            assertSame(track, snapshot.currentTrack().orElseThrow());
            assertEquals("Spotify", snapshot.sourceApplication());
            assertEquals(20, snapshot.positionSeconds());
            assertTrue(snapshot.playing());
            assertEquals(Optional.of(MediaPlaybackState.PAUSED), snapshot.mediaPlaybackState());
            assertEquals(0.5, snapshot.volume().orElseThrow());
            assertEquals("Speakers", snapshot.outputDevice());
            assertEquals("LOSSLESS", snapshot.quality());
            assertEquals("Next song", snapshot.queue().getFirst().title());
        }
    }

    @ParameterizedTest
    @EnumSource(MediaAction.class)
    void actionsShouldDelegateAndRefreshEvenWhenTransportReturnsFalse(MediaAction action) throws Exception {
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
            verify(enrichment).current();
            if (action != MediaAction.PLAY_PAUSE) {
                verify(media, never()).playPause();
            }
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
                    new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, cachedState));
            CountDownLatch refreshed = new CountDownLatch(3);
            when(media.current()).thenAnswer(invocation -> {
                refreshed.countDown();
                return session.get();
            });
            if (action == MediaAction.PAUSE) {
                when(media.pause()).thenAnswer(invocation -> {
                    session.set(new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, expectedState));
                    return true;
                });
            } else {
                when(media.resume()).thenAnswer(invocation -> {
                    session.set(new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, expectedState));
                    return true;
                });
            }

            // Publish a cached state before the active session changes independently.
            assertTrue(controller.submit(MediaAction.NEXT));
            until(() -> controller.current().available());
            assertEquals(cachedState, controller.current().playbackState());
            MediaPlaybackState activeState = action == MediaAction.PAUSE ? MediaPlaybackState.PLAYING : MediaPlaybackState.PAUSED;
            session.set(new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, activeState));

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
    void transportExceptionShouldRefreshAllProvidersAndAllowFollowingActions(MediaAction action) throws Exception {
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
                    new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, MediaPlaybackState.PLAYING),
                    new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 30, MediaPlaybackState.PLAYING));
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
            assertEquals("LOSSLESS", snapshot.quality());
            assertEquals("Next song", snapshot.queue().getFirst().title());
            assertEquals(Optional.of(MediaPlaybackState.PAUSED), snapshot.mediaPlaybackState());
            until(() -> controller.current().positionSeconds() == 30);
            verify(media, times(2)).current();
            verify(media, times(2)).playbackState("TIDAL");
            verify(output, times(2)).current();
            verify(enrichment, times(2)).current();
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
                return new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, MediaPlaybackState.PAUSED);
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
            switch (failure) {
                case MEDIA -> when(media.current()).thenThrow(new IllegalStateException("media"))
                        .thenReturn(new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, MediaPlaybackState.PLAYING));
                case AUDIO -> when(output.current()).thenThrow(new IllegalStateException("audio"))
                        .thenReturn(new AudioOutputSnapshot(true, "endpoint", "Speakers", OptionalDouble.of(0.5), true));
                case ENRICHMENT -> when(enrichment.current()).thenThrow(new IllegalStateException("enrichment"))
                        .thenReturn(new MediaEnrichmentSnapshot("LOSSLESS", List.of()));
                case TIDAL -> when(media.playbackState("TIDAL")).thenThrow(new IllegalStateException("tidal"))
                        .thenReturn(Optional.of(MediaPlaybackState.PAUSED));
            }
            // The next action is an executor barrier: the first snapshot has been published before it runs.
            java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<MediaWorkspaceSnapshot> failed = new java.util.concurrent.atomic.AtomicReference<>();
            doAnswer(invocation -> {
                failed.set(controller.current());
                barrier.countDown();
                return false;
            }).when(media).next();
            // Finish stubbing before the executor starts invoking this shared mock.
            controller.submit(MediaAction.PLAY_PAUSE);
            controller.submit(MediaAction.NEXT);
            assertTrue(barrier.await(3, java.util.concurrent.TimeUnit.SECONDS));
            MediaWorkspaceSnapshot snapshot = failed.get();
            assertEquals(failure != Failure.MEDIA, snapshot.available());
            assertEquals(failure != Failure.AUDIO, snapshot.volume().isPresent());
            assertEquals(failure == Failure.ENRICHMENT ? "" : "LOSSLESS", snapshot.quality());
            assertEquals(failure != Failure.TIDAL, snapshot.mediaPlaybackState().isPresent());
            assertEquals(failure != Failure.ENRICHMENT, !snapshot.queue().isEmpty());
            until(() -> controller.current().available() && controller.current().volume().isPresent()
                    && controller.current().quality().equals("LOSSLESS") && controller.current().mediaPlaybackState().isPresent());
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
    void optionalProvidersShouldDefaultToUnknownDeviceAndNoEnrichment() throws Exception {
        when(media.current()).thenReturn(new MediaSessionSnapshot(true, "Tidal", Optional.of(track),
                20, MediaPlaybackState.PLAYING));
        try (MediaWorkspaceController controller = new MediaWorkspaceController(media)) {
            assertTrue(controller.submit(MediaAction.NEXT));
            until(() -> controller.current().available());
            assertTrue(controller.current().volume().isEmpty());
            assertEquals("", controller.current().outputDevice());
            assertEquals("", controller.current().quality());
            assertTrue(controller.current().queue().isEmpty());
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
    void shouldRejectNullActionsAndProviders() throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            assertThrows(NullPointerException.class, () -> controller.submit(null));
            assertThrows(NullPointerException.class, () -> controller.executeAsync(null));
        }
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController(null, output, enrichment));
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController(media, null, enrichment));
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceController(media, output, null));
    }
}
