package com.fuad.assistant.skills.media;

import com.fuad.config.AppConfig;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalMediaControlParserTest {
    private final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
    private final LocalMediaControlParser parser = new LocalMediaControlParser(client, " test-model ");

    @ParameterizedTest
    @CsvSource({
            "Siguiente canción., NEXT", "Pon la siguiente canción., NEXT",
            "Salta esta canción, NEXT", "Canción anterior, PREVIOUS",
            "Pausa la música, PAUSE", "Pausa TIDAL, PAUSE",
            "Reanuda la reproducción, RESUME", "Continúa la música, RESUME"
    })
    void completeCommandsShouldBypassTheModel(String command, MediaControlIntent expected) {
        assertEquals(expected, parser.parse(command));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "No pauses la música", "¿Qué canción está sonando?", "Sube el volumen de TIDAL",
            "Cierra Spotify", "Activa shuffle", "Pon Comfortably Numb"
    })
    void explicitContradictionsShouldBeUnsupportedWithoutModelInference(String command) {
        assertEquals(MediaControlIntent.UNSUPPORTED, parser.parse(command));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pasa esta", "dale", "sigue", "vuelve", "pon otra", "Vuelve atrás"})
    void incompleteRequestsShouldRemainAvailableForSemanticParsing(String command) {
        respond("next");

        assertEquals(MediaControlIntent.NEXT, parser.parse(command));
        verify(client.chat().completions()).create(any(ChatCompletionCreateParams.class));
    }

    @ParameterizedTest
    @CsvSource({
            "next, NEXT",
            "previous, PREVIOUS",
            "pause, PAUSE",
            "resume, RESUME",
            "unsupported, UNSUPPORTED",
            "' PaUsE ', PAUSE"
    })
    void shouldMapSupportedModelLabels(String output, MediaControlIntent expected) {
        respond(output);

        assertEquals(expected, parser.parse("solicitud multimedia"));
        verify(client.chat().completions()).create(any(ChatCompletionCreateParams.class));
        verifyNoMoreInteractions(client.chat().completions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"```text\nnext\n```", "\"next\"", "**next**", "next\nExplicación adicional"})
    void shouldUseTheSharedFirstContractLineNormalization(String output) {
        respond(output);

        assertEquals(MediaControlIntent.NEXT, parser.parse("Pasa a otra de las pistas"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "\n\t", "play_pause", "stop", "shuffle", "repeat", "seek",
            "mute", "set_volume|assistant|40", "os-command", "next_track",
            "pause.", "next please", "La acción es pause", "{\"action\":\"pause\"}"
    })
    void missingUnknownOrMalformedModelOutputShouldBeUnsupported(String output) {
        respond(output);

        assertEquals(MediaControlIntent.UNSUPPORTED, parser.parse("solicitud multimedia"));
        verify(client.chat().completions()).create(any(ChatCompletionCreateParams.class));
        verifyNoMoreInteractions(client.chat().completions());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n", "\t", " \r\n\t "})
    void blankCommandsShouldBeUnsupportedWithoutCallingTheModel(String command) {
        assertEquals(MediaControlIntent.UNSUPPORTED, parser.parse(command));

        verifyNoInteractions(client);
    }

    @Test
    void emptyChoicesShouldBeUnsupported() {
        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of());
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(completion);

        assertEquals(MediaControlIntent.UNSUPPORTED, parser.parse("Haz que la música siga sonando"));
    }

    @Test
    void shouldOnlyUseTheFirstChoice() {
        ChatCompletion.Choice first = choice("unsupported");
        ChatCompletion.Choice second = choice("pause");
        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(first, second));
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(completion);

        assertEquals(MediaControlIntent.UNSUPPORTED, parser.parse("Haz que la música siga sonando"));
        verifyNoInteractions(second);
    }

    @Test
    void shouldSendATrimmedCommandWithADeterministicRestrictedRequest() {
        respond("pause");

        assertEquals(MediaControlIntent.PAUSE, parser.parse(" \nPon la música en pausa\t "));
        ChatCompletionCreateParams params = captured();

        assertEquals(ChatModel.of("test-model"), params.model());
        assertEquals(Optional.of(0.0), params.temperature());
        assertEquals(Optional.of(8L), params.maxCompletionTokens());
        assertEquals(2, params.messages().size());
        String system = params.messages().getFirst().asSystem().content().asText();
        for (String label : List.of("next", "previous", "pause", "resume", "unsupported")) {
            assertTrue(system.contains(label), "Missing label: " + label);
        }
        assertEquals("""
                Clasifica la siguiente solicitud multimedia.

                <query>
                Pon la música en pausa
                </query>

                Devuelve únicamente:
                next, previous, pause, resume o unsupported.
                """, params.messages().getLast().asUser().content().asText());
    }

    @Test
    void defaultConstructorShouldUseTheConfiguredLocalModel() {
        respond("resume");

        assertEquals(MediaControlIntent.RESUME, new LocalMediaControlParser(client).parse("Continúa"));
        assertEquals(ChatModel.of(AppConfig.LOCAL_MODEL_ID.trim()), captured().model());
    }

    @Test
    void providerFailureShouldPropagateWithoutRetrying() {
        IllegalStateException failure = new IllegalStateException("model unavailable");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> parser.parse("Haz que la música siga sonando")));
        verify(client.chat().completions()).create(any(ChatCompletionCreateParams.class));
        verifyNoMoreInteractions(client.chat().completions());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankModelIdentifiers(String model) {
        if (model == null) {
            assertThrows(NullPointerException.class, () -> new LocalMediaControlParser(client, model));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new LocalMediaControlParser(client, model));
        }
        verifyNoInteractions(client);
    }

    @Test
    void shouldRequireAClient() {
        assertThrows(NullPointerException.class, () -> new LocalMediaControlParser(null, "model"));
    }

    private void respond(String output) {
        ChatCompletion.Choice choice = choice(output);
        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(choice));
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(completion);
    }

    private ChatCompletion.Choice choice(String output) {
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);
        when(message.content()).thenReturn(Optional.ofNullable(output));
        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
        when(choice.message()).thenReturn(message);
        return choice;
    }

    private ChatCompletionCreateParams captured() {
        ArgumentCaptor<ChatCompletionCreateParams> params = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions()).create(params.capture());
        return params.getValue();
    }
}
