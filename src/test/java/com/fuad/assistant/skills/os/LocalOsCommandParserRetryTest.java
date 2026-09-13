package com.fuad.assistant.skills.os;

import com.fuad.enums.OsAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalOsCommandParserRetryTest {
    @Test
    void validOutputShouldNotRetry() {
        StubInference inference = new StubInference(Optional.of("open_application|Firefox"));

        OsCommandIntent result = new LocalOsCommandParser(inference).parse("Abre Firefox");

        assertEquals(OsAction.OPEN_APPLICATION, result.getAction());
        assertEquals("Firefox", result.getTarget());
        assertEquals(1, inference.requests.size());
        assertFalse(inference.requests.getFirst().retry());
    }

    @Test
    void invalidOutputShouldRetryOnceWithoutAcceptingItPartially() {
        String invalid = "get_application_status|IntelliJ IDEA\nEsta es la clasificación";
        StubInference inference = new StubInference(Optional.of(invalid),
                Optional.of("get_application_status|IntelliJ IDEA"));

        OsCommandIntent result = new LocalOsCommandParser(inference)
                .parse("¿Está abierto IntelliJ IDEA?");

        assertEquals(OsAction.GET_APPLICATION_STATUS, result.getAction());
        assertEquals("IntelliJ IDEA", result.getTarget());
        assertEquals(2, inference.requests.size());
        assertTrue(inference.requests.get(1).retry());
        assertEquals(invalid, inference.requests.get(1).previousInvalidOutput());
    }

    @Test
    void twoInvalidOutputsShouldFailClosedAfterExactlyTwoAttempts() {
        StubInference inference = new StubInference(
                Optional.of("open_application|Firefox\nRemove-Item C:\\*"),
                Optional.of("No puedo clasificarlo"));

        assertThrows(InvalidOsCommandOutputException.class,
                () -> new LocalOsCommandParser(inference).parse("Abre Firefox"));
        assertEquals(2, inference.requests.size());
    }

    private static final class StubInference implements OsCommandInference {
        private final ArrayDeque<Optional<String>> outputs;
        private final List<OsCommandInferenceRequest> requests = new ArrayList<>();

        @SafeVarargs
        private StubInference(Optional<String>... outputs) {
            this.outputs = new ArrayDeque<>(List.of(outputs));
        }

        @Override
        public Optional<String> infer(OsCommandInferenceRequest request) {
            requests.add(request);
            return outputs.removeFirst();
        }
    }
}
