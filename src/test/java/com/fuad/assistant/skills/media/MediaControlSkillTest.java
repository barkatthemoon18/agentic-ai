package com.fuad.assistant.skills.media;

import com.fuad.assistant.AssistantResult;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionExecutor;
import com.fuad.media.control.MediaActionResult;
import com.fuad.media.control.MediaActionStatus;
import com.fuad.pipeline.ConversationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CompletableFuture;

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
        when(executor.executeAsync(MediaAction.PAUSE)).thenReturn(new CompletableFuture<>());

        AssistantResult result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> skill.execute("Pausa"));

        assertEquals("No pude confirmar el resultado del comando", result.text());
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
        when(executor.executeAsync(MediaAction.PAUSE)).thenReturn(new CompletableFuture<>());

        Thread.currentThread().interrupt();
        try {
            assertEquals("La operación multimedia fue interrumpida", skill.execute("Pausa").text());
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally {
            Thread.interrupted();
        }
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
    void shouldRequireBothDependencies() {
        assertThrows(NullPointerException.class, () -> new MediaControlSkill(null, executor));
        assertThrows(NullPointerException.class, () -> new MediaControlSkill(parser, null));
    }
}
