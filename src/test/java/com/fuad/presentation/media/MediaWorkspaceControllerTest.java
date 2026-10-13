package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.assistant.skills.media.LocalMediaControlParser;
import com.fuad.assistant.skills.media.MediaControlSkill;
import com.fuad.media.*;
import com.fuad.media.control.MediaAction;
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
    @EnumSource(value = MediaAction.class, names = {"NEXT", "PREVIOUS", "PLAY_PAUSE"})
    void actionsShouldDelegateAndRefreshEvenWhenTransportReturnsFalse(MediaAction action) throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            assertTrue(controller.submit(action));
            until(() -> controller.current().available());
            switch (action) {
                case NEXT -> verify(media).next();
                case PREVIOUS -> verify(media).previous();
                case PLAY_PAUSE -> verify(media).playPause();
            }
            verify(media).current();
            verify(media).playbackState("TIDAL");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "PAUSE, PLAYING, PAUSED, 1",
            "PAUSE, PAUSED, PAUSED, 0",
            "PAUSE, STOPPED, STOPPED, 0",
            "PAUSE, UNKNOWN, UNKNOWN, 0",
            "RESUME, PAUSED, PLAYING, 1",
            "RESUME, PLAYING, PLAYING, 0",
            "RESUME, STOPPED, STOPPED, 0",
            "RESUME, UNKNOWN, UNKNOWN, 0"
    })
    void pauseAndResumeShouldRespectPlaybackStateAndBeIdempotent(MediaAction action,
                                                               MediaPlaybackState initialState,
                                                               MediaPlaybackState expectedState,
                                                               int expectedToggles) throws Exception {
        try (MediaWorkspaceController controller = controller()) {
            AtomicReference<MediaSessionSnapshot> session = new AtomicReference<>(
                    new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, initialState));
            when(media.current()).thenAnswer(invocation -> session.get());
            assertTrue(controller.submit(MediaAction.NEXT));
            until(() -> controller.current().available());

            // Each submitted action refreshes the session after executing its transport command.
            CountDownLatch refreshed = new CountDownLatch(2);
            when(media.current()).thenAnswer(invocation -> {
                MediaSessionSnapshot snapshot = session.get();
                refreshed.countDown();
                return snapshot;
            });
            doAnswer(invocation -> {
                session.set(new MediaSessionSnapshot(true, "Spotify", Optional.of(track), 20, expectedState));
                return true;
            }).when(media).playPause();

            assertTrue(controller.submit(action));
            assertTrue(controller.submit(action));

            assertTrue(refreshed.await(3, TimeUnit.SECONDS), "Both actions should finish and refresh the session");
            until(() -> controller.current().playbackState() == expectedState);
            verify(media, times(expectedToggles)).playPause();
            verify(media, times(3)).current();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pausa la música", "Pausa TIDAL"})
    void aPlayerNameInTheCommandShouldStillPauseTheActiveSpotifySession(String command) throws Exception {
        OpenAIClient client = mock(OpenAIClient.class);
        try (MediaWorkspaceController controller = controller()) {
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
            verify(media).playPause();
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
}
