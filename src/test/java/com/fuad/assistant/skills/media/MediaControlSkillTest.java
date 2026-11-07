package com.fuad.assistant.skills.media;

import com.fuad.assistant.AssistantResult;
import com.fuad.media.control.*;
import com.fuad.pipeline.ConversationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaControlSkillTest {
    private final MediaControlParser parser = mock(MediaControlParser.class);
    private final MediaActionExecutor executor = mock(MediaActionExecutor.class);
    private final MediaControlSkill skill = new MediaControlSkill(parser, executor);

    private static CompletableFuture<MediaActionResult> completed(MediaAction action, MediaActionStatus status) {
        return CompletableFuture.completedFuture(new MediaActionResult(action, status));
    }

    @ParameterizedTest
    @CsvSource({
            "NEXT, NEXT, Siguiente canción, Siguiente.",
            "PREVIOUS, PREVIOUS, Pon la anterior, Anterior.",
            "PAUSE, PAUSE, Pausa la música, Reproducción pausada.",
            "RESUME, RESUME, Continúa la música, Reproducción reanudada."
    })
    void shouldExecuteTheRequestedActionOnceAndConfirmOnlyWhenAcknowledged(MediaControlIntent intent,
                                                                          MediaAction action,
                                                                          String command,
                                                                          String response) {
        when(parser.parse(command)).thenReturn(intent);
        when(executor.executeAsync(action)).thenReturn(completed(action, MediaActionStatus.ACKNOWLEDGED));

        AssistantResult result = skill.execute(command);

        assertEquals(new AssistantResult(response), result);
        verify(parser).parse(command);
        verify(executor).executeAsync(action);
        verifyNoMoreInteractions(parser, executor);
    }

    @Test
    void pauseTidalShouldInvokeExecuteAsyncWithMediaControlRequestAndNeverMediaAction() {
        when(parser.parse("Pausa TIDAL")).thenReturn(MediaControlIntent.PAUSE);
        when(executor.executeAsync(any(MediaControlRequest.class)))
                .thenReturn(completed(MediaAction.PAUSE, MediaActionStatus.ACKNOWLEDGED));

        AssistantResult result = skill.execute("Pausa TIDAL");

        assertEquals(new AssistantResult("Reproducción pausada."), result);
        verify(executor).executeAsync(new MediaControlRequest(MediaAction.PAUSE, MediaTarget.application("TIDAL")));
        verify(executor, never()).executeAsync(any(MediaAction.class));
    }

    @ParameterizedTest
    @CsvSource({
            "Pausa Spotify, PAUSE, PAUSE, Spotify, Reproducción pausada.",
            "Reanuda VLC, RESUME, RESUME, VLC, Reproducción reanudada.",
            "Siguiente canción en Spotify, NEXT, NEXT, Spotify, Siguiente."
    })
    void applicationTargetedCommandsShouldInvokeExecuteAsyncWithMediaControlRequest(
            String command,
            MediaControlIntent intent,
            MediaAction action,
            String appName,
            String expectedResponse) {
        when(parser.parse(command)).thenReturn(intent);
        when(executor.executeAsync(any(MediaControlRequest.class)))
                .thenReturn(completed(action, MediaActionStatus.ACKNOWLEDGED));

        AssistantResult result = skill.execute(command);

        assertEquals(new AssistantResult(expectedResponse), result);
        verify(executor).executeAsync(new MediaControlRequest(action, MediaTarget.application(appName)));
        verify(executor, never()).executeAsync(any(MediaAction.class));
    }

    @Test
    void previousTrackWithApplicationTargetShouldExecuteWhenParserRecognizesAction() {
        String command = "Anterior canción de TIDAL";
        when(parser.parse(command)).thenReturn(MediaControlIntent.PREVIOUS);

        MediaControlCommandDetector mockDetector = mock(MediaControlCommandDetector.class);
        when(mockDetector.detect("anterior cancion")).thenReturn(Optional.of(MediaAction.PREVIOUS));
        MediaCommandTargetResolver targetResolver = new MediaCommandTargetResolver(mockDetector);
        MediaControlSkill skillWithCustomResolver = new MediaControlSkill(parser, executor, targetResolver);

        when(executor.executeAsync(any(MediaControlRequest.class)))
                .thenReturn(completed(MediaAction.PREVIOUS, MediaActionStatus.ACKNOWLEDGED));

        AssistantResult result = skillWithCustomResolver.execute(command);

        assertEquals(new AssistantResult("Anterior."), result);
        verify(executor).executeAsync(new MediaControlRequest(MediaAction.PREVIOUS, MediaTarget.application("TIDAL")));
        verify(executor, never()).executeAsync(any(MediaAction.class));
    }

    @Test
    void pauseMusicShouldInvokeExecuteAsyncWithMediaActionAndNeverMediaControlRequest() {
        when(parser.parse("Pausa la música")).thenReturn(MediaControlIntent.PAUSE);
        when(executor.executeAsync(MediaAction.PAUSE))
                .thenReturn(completed(MediaAction.PAUSE, MediaActionStatus.ACKNOWLEDGED));

        AssistantResult result = skill.execute("Pausa la música");

        assertEquals(new AssistantResult("Reproducción pausada."), result);
        verify(executor).executeAsync(MediaAction.PAUSE);
        verify(executor, never()).executeAsync(any(MediaControlRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Pausa Deezer",
            "Siguiente canción en Deezer",
            "Pausa Deezer en Spotify",
            "Pausa Spotify en TIDAL",
            "Pausa Spotify y TIDAL",
            "No pauses TIDAL",
            "Pausa TIDAL cuando termine"
    })
    void rejectedCommandsShouldReportUnidentifiablePlayerWithoutCallingExecutor(String command) {
        when(parser.parse(command)).thenReturn(MediaControlIntent.PAUSE);

        AssistantResult result = skill.execute(command);

        assertEquals("No pude identificar de forma segura el reproductor", result.text());
        verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @CsvSource({
            "REJECTED, No pude aplicar el control multimedia",
            "FAILED, Ocurrió un error al controlar la reproducción",
            "CLOSED, El control multimedia no está disponible"
    })
    void nonAcknowledgedOutcomesShouldReportTheirOwnMessageWithoutRetrying(MediaActionStatus status, String message) {
        when(parser.parse("comando")).thenReturn(MediaControlIntent.PAUSE);
        when(executor.executeAsync(MediaAction.PAUSE)).thenReturn(completed(MediaAction.PAUSE, status));

        AssistantResult result = skill.execute("comando");

        assertEquals(message, result.text());
        verify(executor).executeAsync(MediaAction.PAUSE);
        verifyNoMoreInteractions(executor);
    }

    @Test
    void unsupportedRequestsShouldNeverExecuteAnAction() {
        when(parser.parse("Pon Comfortably Numb")).thenReturn(MediaControlIntent.UNSUPPORTED);

        AssistantResult result = skill.execute("Pon Comfortably Numb");

        assertEquals("No interpreté ese control multimedia.", result.text());
        verifyNoInteractions(executor);
    }

    @Test
    void unavailableExecutorShouldReportThatMediaControlIsClosed() {
        MediaControlSkill unavailable = new MediaControlSkill(
                command -> MediaControlIntent.PAUSE, MediaActionExecutor.unavailable());

        assertEquals("El control multimedia no está disponible", unavailable.execute("Pausa").text());
    }

    @Test
    void unresolvedActionShouldTimeOutWithoutClaimingSuccess() {
        when(parser.parse("Pausa")).thenReturn(MediaControlIntent.PAUSE);
        CompletableFuture<MediaActionResult> future = new CompletableFuture<>();
        when(executor.executeAsync(MediaAction.PAUSE)).thenReturn(future);

        AssistantResult result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> skill.execute("Pausa"));

        assertEquals("No pude confirmar el resultado del comando", result.text());
        assertTrue(future.isCancelled());
    }

    @Test
    void exceptionallyCompletedActionShouldReportExecutionError() {
        when(parser.parse("Pausa")).thenReturn(MediaControlIntent.PAUSE);
        when(executor.executeAsync(MediaAction.PAUSE))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        assertEquals("Ocurrió un error al ejecutar el comando", skill.execute("Pausa").text());
    }

    @Test
    void interruptedWaitShouldRestoreTheInterruptFlag() {
        when(parser.parse("Pausa")).thenReturn(MediaControlIntent.PAUSE);
        CompletableFuture<MediaActionResult> future = new CompletableFuture<>();
        when(executor.executeAsync(MediaAction.PAUSE)).thenReturn(future);

        Thread.currentThread().interrupt();
        try {
            assertEquals("La operación multimedia fue interrumpida", skill.execute("Pausa").text());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(future.isCancelled());
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedThreadShouldCancelExecutionAndRestoreInterruptFlag() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        CompletableFuture<MediaActionResult> future = new CompletableFuture<>();
        when(parser.parse("Pausa")).thenReturn(MediaControlIntent.PAUSE);
        when(executor.executeAsync(MediaAction.PAUSE)).thenAnswer(inv -> {
            waiting.countDown();
            return future;
        });

        AtomicReference<AssistantResult> result = new AtomicReference<>();
        AtomicBoolean threadInterrupted = new AtomicBoolean();

        Thread thread = new Thread(() -> {
            result.set(skill.execute("Pausa"));
            threadInterrupted.set(Thread.currentThread().isInterrupted());
        });

        thread.start();
        assertTrue(waiting.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);
        thread.interrupt();
        thread.join(2000);

        assertFalse(thread.isAlive());
        assertTrue(threadInterrupted.get());
        assertTrue(future.isCancelled());
        assertEquals("La operación multimedia fue interrumpida", result.get().text());
    }

    @Test
    void missingParserResultShouldFailBeforeExecutingAnAction() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> skill.execute("Pausa la música"));

        assertEquals("parser result cannot be null", error.getMessage());
        verifyNoInteractions(executor);
    }

    @Test
    void parserFailureShouldPropagateWithoutExecutingAnAction() {
        IllegalStateException failure = new IllegalStateException("model unavailable");
        when(parser.parse("Pausa la música")).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> skill.execute("Pausa la música")));
        verifyNoInteractions(executor);
    }

    @Test
    void synchronousExecutorFailureShouldPropagateWithoutRetrying() {
        IllegalStateException failure = new IllegalStateException("controller closed");
        when(parser.parse("Pausa la música")).thenReturn(MediaControlIntent.PAUSE);
        when(executor.executeAsync(MediaAction.PAUSE)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> skill.execute("Pausa la música")));
        verify(executor).executeAsync(MediaAction.PAUSE);
        verifyNoMoreInteractions(executor);
    }

    @ParameterizedTest
    @EnumSource(MediaControlIntent.class)
    void everyOutcomeShouldPreserveTheConversation(MediaControlIntent intent) {
        MediaControlSkill preserving = new MediaControlSkill(command -> intent,
                action -> completed(action, MediaActionStatus.ACKNOWLEDGED));

        assertEquals(ConversationPolicy.PRESERVE, preserving.getConversationPolicy());
        assertNull(preserving.execute("comando").conversationPolicyOverride());
    }

    @Test
    void shouldRequireDependencies() {
        assertThrows(NullPointerException.class, () -> new MediaControlSkill(null, executor));
        assertThrows(NullPointerException.class, () -> new MediaControlSkill(parser, null));
        assertThrows(NullPointerException.class, () -> new MediaControlSkill(parser, executor, null));
    }
}
