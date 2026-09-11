package com.fuad.assistant.skills.research;

import com.fuad.enums.ResearchDepth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalResearchDepthClassifierTest {

    @Test
    void shouldParseContractLabelsAndCommonModelDecoration() {
        assertEquals(ResearchDepth.QUICK, LocalResearchDepthClassifier.parse("quick"));
        assertEquals(ResearchDepth.DEEP, LocalResearchDepthClassifier.parse("```text\ndeep\n```"));
    }

    @Test
    void shouldRejectOutputOutsideContract() {
        assertThrows(IllegalStateException.class,
                () -> LocalResearchDepthClassifier.parse("medium"));
    }
}
