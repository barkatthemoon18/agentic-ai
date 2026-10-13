package com.fuad.assistant.skills.media;

import com.fuad.assistant.AssistantResult;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionHandler;
import com.fuad.pipeline.ConversationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaControlSkillTest {
    private final MediaControlParser parser = mock(MediaControlParser.class);
    private final MediaActionHandler handler = mock(MediaActionHandler.class);
    private final MediaControlSkill skill = new MediaControlSkill(parser, handler);

    @ParameterizedTest
    @CsvSource({
            "NEXT, NEXT, Siguiente canción, Siguiente.",
            "PREVIOUS, PREVIOUS, Pon la anterior, Anterior.",
            "PAUSE, PAUSE, Pausa la música, Reproducción pausada.",
            "RESUME, RESUME, Continúa la música, Reproducción reanudada."
    })
    void shouldSubmitTheRequestedActionOnceAndConfirmAcceptance(MediaControlIntent intent,
                                                               MediaAction action,
                                                               String command,
                                                               String response) {
        when(parser.parse(command)).thenReturn(intent);
        when(handler.submit(action)).thenReturn(true);

        AssistantResult result = skill.execute(command);

        assertEquals(new AssistantResult(response), result);
        verify(parser).parse(command);
        verify(handler).submit(action);
        verifyNoMoreInteractions(parser, handler);
    }

    @ParameterizedTest
    @CsvSource({"NEXT, NEXT", "PREVIOUS, PREVIOUS", "PAUSE, PAUSE", "RESUME, RESUME"})
    void rejectedActionsShouldReportFailureWithoutRetrying(MediaControlIntent intent, MediaAction action) {
        when(parser.parse("comando")).thenReturn(intent);
        when(handler.submit(action)).thenReturn(false);

        AssistantResult result = skill.execute("comando");

        assertEquals("No pude controlar la reproducción multimedia.", result.text());
        verify(handler).submit(action);
        verifyNoMoreInteractions(handler);
    }

    @Test
    void unsupportedRequestsShouldNeverSubmitAnAction() {
        when(parser.parse("Pon Comfortably Numb")).thenReturn(MediaControlIntent.UNSUPPORTED);

        AssistantResult result = skill.execute("Pon Comfortably Numb");

        assertEquals("No interpreté ese control multimedia.", result.text());
        verifyNoInteractions(handler);
    }

    @Test
    void unavailableHandlerShouldReportFailure() {
        MediaControlSkill unavailable = new MediaControlSkill(
                command -> MediaControlIntent.PAUSE, MediaActionHandler.unavailable());

        assertEquals("No pude controlar la reproducción multimedia.", unavailable.execute("Pausa").text());
    }

    @Test
    void missingParserResultShouldFailBeforeSubmittingAnAction() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> skill.execute("Pausa la música"));

        assertEquals("parser result cannot be null", error.getMessage());
        verifyNoInteractions(handler);
    }

    @Test
    void parserFailureShouldPropagateWithoutSubmittingAnAction() {
        IllegalStateException failure = new IllegalStateException("model unavailable");
        when(parser.parse("Pausa la música")).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> skill.execute("Pausa la música")));
        verifyNoInteractions(handler);
    }

    @Test
    void handlerFailureShouldPropagateWithoutRetrying() {
        IllegalStateException failure = new IllegalStateException("controller closed");
        when(parser.parse("Pausa la música")).thenReturn(MediaControlIntent.PAUSE);
        when(handler.submit(MediaAction.PAUSE)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> skill.execute("Pausa la música")));
        verify(handler).submit(MediaAction.PAUSE);
        verifyNoMoreInteractions(handler);
    }

    @ParameterizedTest
    @EnumSource(MediaControlIntent.class)
    void everyOutcomeShouldPreserveTheConversation(MediaControlIntent intent) {
        MediaControlSkill preserving = new MediaControlSkill(command -> intent, action -> true);

        assertEquals(ConversationPolicy.PRESERVE, preserving.getConversationPolicy());
        assertNull(preserving.execute("comando").conversationPolicyOverride());
    }

    @Test
    void shouldRequireBothDependencies() {
        assertThrows(NullPointerException.class, () -> new MediaControlSkill(null, handler));
        assertThrows(NullPointerException.class, () -> new MediaControlSkill(parser, null));
    }
}
