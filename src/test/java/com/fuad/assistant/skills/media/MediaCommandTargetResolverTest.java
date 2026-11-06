package com.fuad.assistant.skills.media;

import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaControlCommandDetector;
import com.fuad.media.control.MediaTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaCommandTargetResolverTest {
    private final MediaCommandTargetResolver resolver = new MediaCommandTargetResolver();

    @ParameterizedTest
    @CsvSource({
            "Pausa TIDAL, TIDAL",
            "Pausa Spotify, Spotify",
            "Reanuda VLC, VLC",
            "Pon en pausa Spotify, Spotify",
            "Continúa TIDAL, TIDAL",
            "Sigue reproduciendo VLC, VLC",
            "Pausa spotify.exe, Spotify"
    })
    void directPlayerNamesShouldResolveToApplicationTarget(String command, String expectedApp) {
        assertEquals(Optional.of(MediaTarget.application(expectedApp)), resolver.resolve(command));
    }

    @ParameterizedTest
    @CsvSource({
            "Siguiente canción en Spotify, Spotify",
            "Pausa la música en TIDAL, TIDAL",
            "Pausa la canción de Spotify, Spotify",
            "Reanuda la reproducción desde Spotify, Spotify",
            "Canción anterior de TIDAL, TIDAL",
            "Salta esta pista en VLC, VLC"
    })
    void scopedKnownPlayersShouldResolveToApplicationTarget(String command, String expectedApp) {
        assertEquals(Optional.of(MediaTarget.application(expectedApp)), resolver.resolve(command));
    }

    @Test
    void scopedPreviousCommandShouldResolveToApplicationTargetWhenParserRecognizesAction() {
        MediaControlCommandDetector mockDetector = mock(MediaControlCommandDetector.class);
        when(mockDetector.detect("anterior cancion")).thenReturn(Optional.of(MediaAction.PREVIOUS));

        MediaCommandTargetResolver resolverWithMockDetector = new MediaCommandTargetResolver(mockDetector);

        assertEquals(Optional.of(MediaTarget.application("TIDAL")),
                resolverWithMockDetector.resolve("Anterior canción de TIDAL"));
    }

    @Test
    void scopedPreviousCommandShouldBeRejectedWhenParserDoesNotRecognizeAction() {
        assertTrue(resolver.resolve("Anterior canción de TIDAL").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Pausa la música",
            "Pausa la canción",
            "Pon en pausa la reproducción",
            "Continúa la música",
            "Sigue reproduciendo la música",
            "Reanuda la pista",
            "Siguiente canción",
            "Canción anterior",
            "Ponlo en pausa",
            "Dale de nuevo"
    })
    void genericCommandsShouldAddressActiveSession(String command) {
        assertEquals(Optional.of(MediaTarget.active()), resolver.resolve(command));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Pausa Deezer",
            "Siguiente canción en Deezer",
            "Pausa Deezer en Spotify",
            "Pausa Spotify en TIDAL",
            "Pausa Spotify y TIDAL",
            "No pauses TIDAL",
            "Pausa TIDAL cuando termine",
            "Pausa YouTube",
            "Pausa Winamp",
            "Pausa la música en YouTube",
            "Pausa en Apple Music",
            "Pausa la música en TIDAL y Spotify",
            "No pauses la música",
            "Si estoy hablando pausa Spotify",
            "Mañana pausa Spotify"
    })
    void unknownOrUnsafeRequestsShouldBeRejected(String command) {
        assertTrue(resolver.resolve(command).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void emptyOrNullRequestsShouldBeInconclusive(String command) {
        assertTrue(resolver.resolve(command).isEmpty());
    }

    @Test
    void shouldRequireActionDetector() {
        assertThrows(NullPointerException.class, () -> new MediaCommandTargetResolver(null));
    }
}
