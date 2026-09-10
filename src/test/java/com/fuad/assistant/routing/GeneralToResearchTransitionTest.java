package com.fuad.assistant.routing;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRegistry;
import com.fuad.assistant.skills.research.CurrentResearchSkill;
import com.fuad.assistant.skills.research.ResearchBackend;
import com.fuad.assistant.skills.research.ResearchBranchState;
import com.fuad.assistant.skills.research.ResearchEngineResult;
import com.fuad.assistant.skills.research.ResearchRequest;
import com.fuad.enums.ActivationType;
import com.fuad.enums.Capability;
import com.fuad.enums.ResearchDepth;
import com.fuad.pipeline.AssistantPipeline;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeneralToResearchTransitionTest {
    @Test
    void shouldEscalateWithVisibleContextAndProduceAResearchOwnedResult() {
        AtomicReference<ResearchRequest> captured = new AtomicReference<>();
        CurrentResearchSkill research = new CurrentResearchSkill(request -> {
            captured.set(request);
            return new ResearchEngineResult("Fuentes encontradas",
                    new ResearchBranchState("web-1", request.previousMessages()));
        }, request -> {
            throw new AssertionError("local research must not be called");
        }, query -> ResearchDepth.QUICK, (query, inherited) -> ResearchBackend.GPT_WEB);
        Skill general = command -> new AssistantResult("general");
        EnumMap<Capability, Skill> skills = new EnumMap<>(Capability.class);
        for (Capability capability : Capability.values()) {
            skills.put(capability, capability == Capability.CURRENT_RESEARCH
                    ? research
                    : capability == Capability.GENERAL
                    ? general
                    : command -> new AssistantResult(capability.name()));
        }
        AiSkillRouter router = new AiSkillRouter(command -> {
            throw new AssertionError("semantic router must not decide a follow-up transition");
        }, new SkillRegistry(skills));
        AssistantPipeline pipeline = new AssistantPipeline(router);
        ConversationSnapshot snapshot = new ConversationSnapshot(Capability.GENERAL,
                "¿Quién fue Alan Turing?", "Fue un matemático.", "general-token");

        AssistantExecutionResult result = pipeline.processFollowUp(
                new ActivationResult(true, ActivationType.CONTEXTUAL,
                        "Ahora búscalo en Internet y dime qué fuentes encuentras"), snapshot);

        assertEquals(Capability.CURRENT_RESEARCH, result.getCapability());
        assertEquals(ResearchBackend.GPT_WEB,
                result.getResponse().getResearchConversationState().getActiveBackend().orElseThrow());
        assertNull(captured.get().continuation().continuationToken());
        assertEquals(2, captured.get().previousMessages().size());
        assertEquals("¿Quién fue Alan Turing?", captured.get().previousMessages().getFirst().content());
    }
}
