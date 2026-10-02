package com.fuad.assistant;

import com.fuad.enums.ResearchDepth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssistantRequestTest {
    @Test
    void canonicalConstructorShouldRetainRequiredAndOptionalFields() {
        assertThrows(NullPointerException.class, () -> new AssistantRequest(null, "instructions", 100, null));
        assertThrows(NullPointerException.class, () -> new AssistantRequest("command", null, 100, null));
        assertThrows(NullPointerException.class, () -> new AssistantRequest("command", "instructions", 100, null, null));
        AssistantRequest request = new AssistantRequest("command", "instructions", 100, null);
        assertNull(request.continuationToken());
        assertEquals(ResearchDepth.NONE, request.researchDepth());
    }
}
