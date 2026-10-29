package com.fuad.bootstrap;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import static com.fuad.testsupport.Await.until;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaBootstrapTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void bootstrapShouldStartPollingAndPreserveIndependentProvidersWhenWindowsSessionFails(int failure) throws Exception {
        MediaSessionProvider sessions = mock(MediaSessionProvider.class);
        AudioOutputProvider output = mock(AudioOutputProvider.class);
        MediaEnrichmentProvider enrichment = mock(MediaEnrichmentProvider.class);
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
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
            assertEquals("LOSSLESS", controller.current().quality());
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
        when(sessions.current()).thenReturn(MediaSessionSnapshot.unavailable());
        when(output.current()).thenReturn(new AudioOutputSnapshot(true, "endpoint", "device", OptionalDouble.of(0.5), true));
        when(enrichment.current()).thenReturn(new MediaEnrichmentSnapshot("LOSSLESS", List.of()));
        Supplier<MediaSessionProvider> sessionFactory = failing == 0 ? () -> { throw new IllegalStateException("sessions"); } : () -> sessions;
        Supplier<AudioOutputProvider> outputFactory = failing == 1 ? () -> null : () -> output;
        Supplier<MediaEnrichmentProvider> enrichmentFactory = failing == 2 ? () -> { throw new UnsatisfiedLinkError("enrichment"); } : () -> enrichment;

        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            var controller = new MediaBootstrap(sessionFactory, outputFactory, enrichmentFactory).create(cleanup);
            if (failing == 0) {
                until(() -> controller.current().volume().isPresent());
            } else if (failing == 1) {
                until(() -> !controller.current().outputDevice().equals("device") && controller.current().quality().equals("LOSSLESS"));
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
