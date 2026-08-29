package com.fuad.assistant.skills;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.*;
import com.fuad.assistant.routing.AiSkillRouter;
import com.fuad.enums.ActivationType;
import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;
import com.fuad.pipeline.AssistantPipeline;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AssistantComponentsTest {
    @Test
    void registryShouldRequireEveryCapability() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkillRegistry(Map.of(Capability.GENERAL, command -> new AssistantResult(command))));
    }

    @Test
    void routerShouldReturnSkillSelectedBySemanticCapability() {
        Skill expected = command -> new AssistantResult("ok");
        SkillRegistry registry = completeRegistry(expected);
        AiSkillRouter router = new AiSkillRouter(command -> Capability.GENERAL, registry);

        assertSame(expected, router.route("hola"));
    }

    @Test
    void pipelineShouldRejectInactiveActivation() {
        AssistantPipeline pipeline = new AssistantPipeline(noOpEngine(), command -> cmd -> new AssistantResult("ok"));

        assertThrows(IllegalArgumentException.class, () -> pipeline.process(ActivationResult.none()));
    }

    @Test
    void pipelineShouldRouteExecuteAndReturnConversationPolicy() {
        AtomicReference<String> routed = new AtomicReference<>();
        AtomicReference<String> executed = new AtomicReference<>();
        Skill skill = new Skill() {
            @Override
            public AssistantResult execute(String command) {
                executed.set(command);
                return new AssistantResult("respuesta");
            }

            @Override
            public ConversationPolicy conversationPolicy() {
                return ConversationPolicy.KEEP_OPEN;
            }
        };
        AssistantPipeline pipeline = new AssistantPipeline(noOpEngine(), command -> {
            routed.set(command);
            return skill;
        });

        AssistantExecutionResult result = pipeline.process(
                new ActivationResult(true, ActivationType.WAKE_WORD, "comando"));

        assertEquals("comando", routed.get());
        assertEquals("comando", executed.get());
        assertEquals("respuesta", result.getResponse().getText());
        assertEquals(ConversationPolicy.KEEP_OPEN, result.getConversationPolicy());
    }

    @Test
    void pipelineShouldDelegateConversationReset() {
        AtomicBoolean reset = new AtomicBoolean();
        AssistantEngine engine = new AssistantEngine() {
            @Override public AssistantResult process(AssistantRequest request) { return new AssistantResult(""); }
            @Override public void resetConversation() { reset.set(true); }
        };
        AssistantPipeline pipeline = new AssistantPipeline(engine, command -> cmd -> new AssistantResult(""));

        pipeline.resetConversation();

        assertTrue(reset.get());
    }

    @Test
    void generalSkillShouldBuildExpectedAssistantRequestAndKeepConversationOpen() {
        AtomicReference<AssistantRequest> captured = new AtomicReference<>();
        AssistantEngine engine = new AssistantEngine() {
            @Override
            public AssistantResult process(AssistantRequest request) {
                captured.set(request);
                return new AssistantResult("respuesta");
            }
            @Override public void resetConversation() { }
        };
        GeneralSkill skill = new GeneralSkill(engine);

        AssistantResult result = skill.execute("explica RSA");

        assertEquals("respuesta", result.getText());
        assertEquals("explica RSA", captured.get().getCommand());
        assertEquals(300, captured.get().getMaxOutputTokens());
        assertFalse(captured.get().getInstructions().isBlank());
        assertEquals(ConversationPolicy.KEEP_OPEN, skill.conversationPolicy());
    }

    @Test
    void basicSkillsShouldReturnStableUserFacingResponses() {
        assertTrue(new SystemTimeSkill().execute("").getText()
                .matches("Son las \\d{2}:\\d{2} horas\\."));
        assertEquals("La capacidad AUDIO_CONTROL todavía no está implementada",
                new UnsupportedSkill(Capability.AUDIO_CONTROL).execute("").getText());
    }

    private SkillRegistry completeRegistry(Skill general) {
        EnumMap<Capability, Skill> skills = new EnumMap<>(Capability.class);
        for (Capability capability : Capability.values()) {
            skills.put(capability, capability == Capability.GENERAL
                    ? general
                    : command -> new AssistantResult(capability.name()));
        }
        return new SkillRegistry(skills);
    }

    private AssistantEngine noOpEngine() {
        return new AssistantEngine() {
            @Override public AssistantResult process(AssistantRequest request) { return new AssistantResult(""); }
            @Override public void resetConversation() { }
        };
    }
}
