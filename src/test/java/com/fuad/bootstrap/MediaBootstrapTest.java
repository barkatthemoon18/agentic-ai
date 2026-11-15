package com.fuad.bootstrap;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import static com.fuad.testsupport.Await.until;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaBootstrapTest {
    @Test
    void defaultConstructorShouldInstantiateWithoutTriggeringProviders() {
        assertDoesNotThrow(() -> {
            new MediaBootstrap();
        });
    }

    @Test
    void constructorShouldRejectNullSuppliers() {
        MediaSessionProvider sessions = mock(MediaSessionProvider.class);
        AudioOutputProvider output = mock(AudioOutputProvider.class);
        MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);

        assertThrows(NullPointerException.class, () -> new MediaBootstrap(null, () -> output, () -> enrichment));
        assertThrows(NullPointerException.class, () -> new MediaBootstrap(() -> sessions, null, () -> enrichment));
        assertThrows(NullPointerException.class, () -> new MediaBootstrap(() -> sessions, () -> output, null));
    }

    @Test
    void createShouldRejectNullCleanup() {
        MediaBootstrap bootstrap = new MediaBootstrap(
                MediaSessionProvider::unavailable,
                AudioOutputProvider::unavailable,
                MediaEnrichmentProvider::unavailable);

        assertThrows(NullPointerException.class, () -> bootstrap.create(null));
    }

    @Test
    void shutdownDuringConstructionShouldReleaseAcquiredProviders() {
        MediaSessionProvider sessions = mock(MediaSessionProvider.class);
        AudioOutputProvider output = mock(AudioOutputProvider.class);
        MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);
        ResourceCleanup cleanup = new ResourceCleanup();
        cleanup.close();

        MediaBootstrap bootstrap = new MediaBootstrap(() -> sessions, () -> output, () -> enrichment);
        assertThrows(IllegalStateException.class, () -> bootstrap.create(cleanup));

        verify(sessions).close();
        verify(output).close();
        verify(enrichment).close();
    }

    @Test
    void createShouldExposeActiveContextProvider() throws Exception {
        MediaSessionProvider sessions = mock(MediaSessionProvider.class);
        AudioOutputProvider output = mock(AudioOutputProvider.class);
        MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(output.current()).thenReturn(AudioOutputSnapshot.unavailable());
        when(enrichment.current()).thenReturn(MediaEnrichmentSnapshot.unavailable());

        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            var controller = new MediaBootstrap(() -> sessions, () -> output, () -> enrichment).create(cleanup);
            assertNotNull(controller.contextProvider());
            assertNotNull(controller.contextProvider().current());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void bootstrapShouldStartPollingAndPreserveIndependentProvidersWhenWindowsSessionFails(int failure) throws Exception {
        MediaSessionProvider sessions = mock(MediaSessionProvider.class);
        AudioOutputProvider output = mock(AudioOutputProvider.class);
        MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);
        when(sessions.current()).thenReturn(new MediaSessionSnapshot(true, "TIDAL", Optional.empty(), 0, MediaPlaybackState.PLAYING));
        when(output.current()).thenReturn(new AudioOutputSnapshot(true, "endpoint", "device", OptionalDouble.of(0.5), true));
        when(enrichment.current()).thenReturn(new MediaEnrichmentSnapshot("LOSSLESS", List.of()));
        Supplier<MediaSessionProvider> factory = () -> {
            if (failure == 1) throw new IllegalStateException("unavailable");
            if (failure == 2) throw new UnsatisfiedLinkError("unavailable");
            return sessions;
        };
        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            var controller = new MediaBootstrap(factory, () -> output, () -> enrichment).create(cleanup);
            until(() -> controller.current().volume().isPresent());
            assertEquals("device", controller.current().outputDevice());
            // Enrichment is only exposed while TIDAL is the active session, which needs the session provider.
            if (failure == 0) {
                until(() -> controller.current().quality().equals("LOSSLESS"));
            } else {
                assertEquals("", controller.current().quality());
            }
        }
        verify(output).close();
        verify(enrichment).close();
        verify(sessions, times(failure == 0 ? 1 : 0)).close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void bootstrapShouldFallBackToUnavailableForAnyProviderThatFailsOrReturnsNull(int failing) throws Exception {
        MediaSessionProvider sessions = mock(MediaSessionProvider.class);
        AudioOutputProvider output = mock(AudioOutputProvider.class);
        MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);
        when(sessions.current()).thenReturn(new MediaSessionSnapshot(true, "TIDAL", Optional.empty(), 0, MediaPlaybackState.PLAYING));
        when(output.current()).thenReturn(new AudioOutputSnapshot(true, "endpoint", "device", OptionalDouble.of(0.5), true));
        when(enrichment.current()).thenReturn(new MediaEnrichmentSnapshot("LOSSLESS", List.of()));
        Supplier<MediaSessionProvider> sessionFactory = failing == 0 ? () -> { throw new IllegalStateException("sessions"); } : () -> sessions;
        Supplier<AudioOutputProvider> outputFactory = failing == 1 ? () -> null : () -> output;
        Supplier<MediaEnrichmentProvider> enrichmentFactory = failing == 2 ? () -> { throw new UnsatisfiedLinkError("enrichment"); } : () -> enrichment;

        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            var controller = new MediaBootstrap(sessionFactory, outputFactory, enrichmentFactory).create(cleanup);
            if (failing == 0) {
                until(() -> controller.current().volume().isPresent());
                assertEquals("", controller.current().quality());
            } else if (failing == 1) {
                until(() -> controller.current().quality().equals("LOSSLESS"));
                assertEquals("", controller.current().outputDevice());
                assertTrue(controller.current().volume().isEmpty());
            } else {
                until(() -> controller.current().outputDevice().equals("device"));
                assertEquals("", controller.current().quality());
            }
        }
        verify(sessions, times(failing == 0 ? 0 : 1)).close();
        verify(output, times(failing == 1 ? 0 : 1)).close();
        verify(enrichment, times(failing == 2 ? 0 : 1)).close();
    }
}
