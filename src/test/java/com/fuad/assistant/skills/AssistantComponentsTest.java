package com.fuad.assistant.skills;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.routing.AiSkillRouter;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.ActivationType;
import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;
import com.fuad.pipeline.AssistantPipeline;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantComponentsTest {

    @Test
    void registryShouldRequireEveryCapability() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkillRegistry(Map.of(Capability.GENERAL,
                        command -> new AssistantResult(command))));
    }

    @Test
    void routerShouldReturnCapabilityAndSkillSelectedSemantically() {
        Skill expected = command -> new AssistantResult("ok");
        SkillRegistry registry = completeRegistry(expected);
        AiSkillRouter router = new AiSkillRouter(command -> Capability.GENERAL, registry);

        SkillRoute route = router.route("hola");

        assertEquals(Capability.GENERAL, route.getCapability());
        assertSame(expected, route.getSkill());
    }

    @Test
    void routeToShouldResolveOwnerWithoutCallingSemanticRouter() {
        AtomicInteger classifications = new AtomicInteger();
        Skill researchSkill = command -> new AssistantResult("investigación");
        SkillRegistry registry = completeRegistryWith(Capability.CURRENT_RESEARCH, researchSkill);
        AiSkillRouter router = new AiSkillRouter(command -> {
            classifications.incrementAndGet();
            return Capability.GENERAL;
        }, registry);

        SkillRoute route = router.routeTo(Capability.CURRENT_RESEARCH);

        assertEquals(0, classifications.get());
        assertEquals(Capability.CURRENT_RESEARCH, route.getCapability());
        assertSame(researchSkill, route.getSkill());
    }

    @Test
    void pipelineShouldRejectInactiveActivationInBothEntryPoints() {
        TrackingSkillRouter router = new TrackingSkillRouter(
                Capability.GENERAL, ignored -> command -> new AssistantResult("ok"));
        AssistantPipeline pipeline = new AssistantPipeline(router);

        assertThrows(IllegalArgumentException.class, () -> pipeline.process(ActivationResult.none()));
        assertThrows(IllegalArgumentException.class,
                () -> pipeline.processFollowUp(ActivationResult.none(),
                        new ConversationSnapshot(
                                Capability.GENERAL, "pregunta", "respuesta", "token")));
        assertEquals(0, router.normalRoutes.get());
        assertEquals(0, router.ownerRoutes.get());
    }

    @Test
    void processShouldUseSemanticRouteAndReportHandlingCapability() {
        AtomicReference<String> executed = new AtomicReference<>();
        Skill skill = keepOpenSkill(executed);
        TrackingSkillRouter router = new TrackingSkillRouter(Capability.GENERAL, ignored -> skill);
        AssistantPipeline pipeline = new AssistantPipeline(router);

        AssistantExecutionResult result = pipeline.process(
                new ActivationResult(true, ActivationType.WAKE_WORD, "comando"));

        assertEquals(1, router.normalRoutes.get());
        assertEquals(0, router.ownerRoutes.get());
        assertEquals("comando", executed.get());
        assertEquals("respuesta", result.getResponse().getText());
        assertEquals(ConversationPolicy.KEEP_OPEN, result.getConversationPolicy());
        assertEquals(Capability.GENERAL, result.getCapability());
    }

    @Test
    void processFollowUpShouldRouteDirectlyToOwner() {
        AtomicReference<String> executed = new AtomicReference<>();
        AtomicReference<String> receivedToken = new AtomicReference<>();
        Skill researchSkill = new Skill() {
            @Override
            public AssistantResult execute(String command) {
                return execute(command, null);
            }

            @Override
            public AssistantResult execute(String command, String continuationToken) {
                executed.set(command);
                receivedToken.set(continuationToken);
                return new AssistantResult("respuesta", "token-nuevo");
            }

            @Override
            public ConversationPolicy getConversationPolicy() {
                return ConversationPolicy.KEEP_OPEN;
            }
        };
        TrackingSkillRouter router = new TrackingSkillRouter(
                Capability.GENERAL,
                capability -> capability == Capability.CURRENT_RESEARCH
                        ? researchSkill
                        : command -> new AssistantResult("incorrecta"));
        AssistantPipeline pipeline = new AssistantPipeline(router);
        ActivationResult followUp = new ActivationResult(
                true, ActivationType.CONTEXTUAL, "¿Y cuándo ocurrió?");
        ConversationSnapshot snapshot =
                new ConversationSnapshot(
                        Capability.CURRENT_RESEARCH, "tema", "respuesta", "token-anterior");

        AssistantExecutionResult result =
                pipeline.processFollowUp(followUp, snapshot);

        assertEquals(0, router.normalRoutes.get());
        assertEquals(1, router.ownerRoutes.get());
        assertEquals(Capability.CURRENT_RESEARCH, router.requestedOwner.get());
        assertEquals("¿Y cuándo ocurrió?", executed.get());
        assertEquals("token-anterior", receivedToken.get());
        assertEquals("token-nuevo", result.getResponse().getContinuationToken());
        assertEquals(Capability.CURRENT_RESEARCH, result.getCapability());
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

        };
        GeneralSkill skill = new GeneralSkill(engine);

        AssistantResult result = skill.execute("explica RSA");

        assertEquals("respuesta", result.getText());
        assertEquals("explica RSA", captured.get().getCommand());
        assertEquals(300, captured.get().getMaxOutputTokens());
        assertNull(captured.get().getContinuationToken());
        assertFalse(captured.get().getInstructions().isBlank());
        assertEquals(ConversationPolicy.KEEP_OPEN, skill.getConversationPolicy());
    }

    @Test
    void generalSkillShouldForwardExplicitContinuationToken() {
        AtomicReference<AssistantRequest> captured = new AtomicReference<>();
        AssistantEngine engine = request -> {
            captured.set(request);
            return new AssistantResult("respuesta", "token-siguiente");
        };
        GeneralSkill skill = new GeneralSkill(engine);

        AssistantResult result = skill.execute("continúa", "token-sesion");

        assertEquals("token-sesion", captured.get().getContinuationToken());
        assertEquals("token-siguiente", result.getContinuationToken());
    }

    @Test
    void sharedStatelessEngineShouldReceiveTheTokenFromEachConversationSnapshot() {
        List<String> receivedTokens = new ArrayList<>();
        AssistantEngine sharedEngine = request -> {
            receivedTokens.add(request.getContinuationToken());
            return new AssistantResult("respuesta", "siguiente-" + receivedTokens.size());
        };
        GeneralSkill sharedSkill = new GeneralSkill(sharedEngine);
        TrackingSkillRouter router = new TrackingSkillRouter(Capability.GENERAL, ignored -> sharedSkill);
        AssistantPipeline pipeline = new AssistantPipeline(router);
        ActivationResult followUp = new ActivationResult(true, ActivationType.CONTEXTUAL, "continúa");
        ConversationSnapshot sessionA = new ConversationSnapshot(
                Capability.GENERAL, "pregunta A", "respuesta A", "token-A");
        ConversationSnapshot sessionB = new ConversationSnapshot(
                Capability.GENERAL, "pregunta B", "respuesta B", "token-B");

        pipeline.processFollowUp(followUp, sessionA);
        pipeline.processFollowUp(followUp, sessionB);

        assertEquals(List.of("token-A", "token-B"), receivedTokens);
    }

    @Test
    void basicSkillsShouldReturnStableUserFacingResponses() {
        assertTrue(new SystemTimeSkill().execute("").getText().matches("Son las \\d{2}:\\d{2} horas\\."));
        assertEquals("La capacidad AUDIO_CONTROL todavía no está implementada",
                new UnsupportedSkill(Capability.AUDIO_CONTROL).execute("").getText());
    }

    private Skill keepOpenSkill(AtomicReference<String> executed) {
        return new Skill() {
            @Override
            public AssistantResult execute(String command) {
                executed.set(command);
                return new AssistantResult("respuesta");
            }

            @Override
            public ConversationPolicy getConversationPolicy() {
                return ConversationPolicy.KEEP_OPEN;
            }
        };
    }

    private SkillRegistry completeRegistry(Skill generalSkill) {
        return completeRegistryWith(Capability.GENERAL, generalSkill);
    }

    private SkillRegistry completeRegistryWith(Capability selectedCapability, Skill selectedSkill) {
        EnumMap<Capability, Skill> skills = new EnumMap<>(Capability.class);
        for (Capability capability : Capability.values()) {
            skills.put(capability, capability == selectedCapability
                    ? selectedSkill
                    : command -> new AssistantResult(capability.name()));
        }
        return new SkillRegistry(skills);
    }

    private static final class TrackingSkillRouter implements SkillRouter {
        private final Capability normalCapability;
        private final Function<Capability, Skill> skillResolver;
        private final AtomicInteger normalRoutes = new AtomicInteger();
        private final AtomicInteger ownerRoutes = new AtomicInteger();
        private final AtomicReference<Capability> requestedOwner = new AtomicReference<>();

        private TrackingSkillRouter(Capability normalCapability, Function<Capability, Skill> skillResolver) {
            this.normalCapability = normalCapability;
            this.skillResolver = skillResolver;
        }

        @Override
        public SkillRoute route(String command) {
            normalRoutes.incrementAndGet();
            return new SkillRoute(normalCapability, skillResolver.apply(normalCapability));
        }

        @Override
        public SkillRoute routeTo(Capability capability) {
            ownerRoutes.incrementAndGet();
            requestedOwner.set(capability);
            return new SkillRoute(capability, skillResolver.apply(capability));
        }
    }
}
