package com.fuad.assistant.routing;

import com.fuad.enums.Capability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraniteSemanticRouterTest {

    @Test
    void shouldAcceptAllowedLabelWithTrailingModelExplanation() {
        assertEquals(Capability.SYSTEM_TIME,
                GraniteSemanticRouter.parseClassification("system-time (si se pregunta por la hora)"));
        assertEquals(Capability.OS_COMMAND,
                GraniteSemanticRouter.parseClassification("os-command: acción local"));
    }

    @Test
    void shouldRejectUnknownOrEmbeddedLabels() {
        assertThrows(IllegalArgumentException.class,
                () -> GraniteSemanticRouter.parseClassification("respuesta: general"));
        assertThrows(IllegalArgumentException.class,
                () -> GraniteSemanticRouter.parseClassification("anything-else"));
    }
}
