package com.fuad.assistant.skills.general;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.local.LocalQwenException;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.GeneralSkill;
import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneralBackendRoutingTest {
    @Test
    void selectorShouldClassifyOnlyANewConversationAndHonorExplicitOverrides() {
        AtomicInteger classifications = new AtomicInteger();
        DefaultGeneralBackendSelector selector = new DefaultGeneralBackendSelector(command -> {
            classifications.incrementAndGet();
            return GeneralBackend.QWEN_LOCAL;
        });

        GeneralBackendDecision first = selector.select("¿Quién fue Alan Turing?",
                GeneralConversationState.empty());
        GeneralConversationState state = GeneralConversationState.empty().withActiveBranch(
                first.backend(), first.origin(), GeneralBranchState.empty());
        GeneralBackendDecision followUp = selector.select("¿Qué inventos hizo?", state);
        GeneralBackendDecision explicit = selector.select("Ahora profundiza usando GPT", state);
        GeneralBackendDecision explicitNatural = selector.select("Quiero que respondas con GPT", state);
        GeneralBackendDecision explicitLocal = selector.select("Vuelve a Qwen", state);
        GeneralBackendDecision conceptual = selector.select("¿Qué es GPT?", state);

        assertEquals(1, classifications.get());
        assertEquals(new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC), first);
        assertEquals(first, followUp);
        assertEquals(new GeneralBackendDecision(GeneralBackend.GPT, SelectionOrigin.EXPLICIT), explicit);
        assertEquals(explicit, explicitNatural);
        assertEquals(new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.EXPLICIT),
                explicitLocal);
        assertEquals(first, conceptual);
    }

    @Test
    void selectorShouldDefaultToAutomaticQwenWhenClassifierFails() {
        DefaultGeneralBackendSelector selector = new DefaultGeneralBackendSelector(command -> {
            throw new IllegalStateException("classifier offline");
        });

        assertEquals(new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC),
                selector.select("Explica RSA", GeneralConversationState.empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "No quiero que uses GPT",
            "No quiero que respondas con GPT",
            "No uses GPT",
            "No cambies a GPT",
            "Sin usar GPT",
            "No quiero que uses Qwen",
            "Sin usar el modelo local",
            "Prefiero Java; explícame qué es GPT"
    })
    void negatedAndConceptualModelMentionsShouldNotOverrideTheActiveBackend(String command) {
        DefaultGeneralBackendSelector selector = new DefaultGeneralBackendSelector(ignored -> {
            throw new AssertionError("classifier must not be called when a backend is active");
        });
        GeneralConversationState qwenState = GeneralConversationState.empty().withActiveBranch(
                GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC, GeneralBranchState.empty());

        assertEquals(new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC),
                selector.select(command, qwenState));
    }

    @Test
    void negatedModelMentionShouldPreserveAnActiveGptBackend() {
        DefaultGeneralBackendSelector selector = new DefaultGeneralBackendSelector(ignored -> {
            throw new AssertionError("classifier must not be called when a backend is active");
        });
        GeneralConversationState gptState = GeneralConversationState.empty().withActiveBranch(
                GeneralBackend.GPT, SelectionOrigin.EXPLICIT, GeneralBranchState.empty());

        assertEquals(new GeneralBackendDecision(GeneralBackend.GPT, SelectionOrigin.EXPLICIT),
                selector.select("No quiero que uses GPT", gptState));
    }

    @Test
    void negatedModelMentionWithoutContextShouldUseAutomaticClassification() {
        AtomicInteger classifications = new AtomicInteger();
        DefaultGeneralBackendSelector selector = new DefaultGeneralBackendSelector(command -> {
            classifications.incrementAndGet();
            return GeneralBackend.QWEN_LOCAL;
        });

        assertEquals(new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC),
                selector.select("No quiero que uses GPT", GeneralConversationState.empty()));
        assertEquals(1, classifications.get());
    }

    @Test
    void invalidClassifierOutputAfterRetryShouldUseAutomaticQwenFallback() {
        AtomicInteger attempts = new AtomicInteger();
        LocalGeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(request -> {
            attempts.incrementAndGet();
            return Optional.of("respuesta fuera de contrato");
        });
        DefaultGeneralBackendSelector selector = new DefaultGeneralBackendSelector(classifier);

        assertEquals(new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC),
                selector.select("Resume La Odisea", GeneralConversationState.empty()));
        assertEquals(2, attempts.get());
    }

    @Test
    void automaticUnavailableQwenShouldCommitOnlySuccessfulGptFallback() {
        GeneralEngine local = request -> {
            throw unavailable();
        };
        GeneralEngine gpt = request -> new GeneralEngineResult("respuesta GPT",
                new GeneralBranchState("gpt-1", List.of()));
        GeneralSkill skill = new GeneralSkill(gpt, local, (command, state) ->
                new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC));

        AssistantResult result = skill.execute("Explica RSA");

        assertEquals("respuesta GPT", result.getText());
        assertEquals(GeneralBackend.GPT,
                result.getGeneralConversationState().getActiveBackend().orElseThrow());
        assertEquals(SelectionOrigin.AUTOMATIC,
                result.getGeneralConversationState().getSelectionOrigin().orElseThrow());
        assertFalse(result.getGeneralConversationState().getBranch(GeneralBackend.QWEN_LOCAL).isPresent());
        assertEquals("gpt-1", result.getContinuationToken());
    }

    @Test
    void explicitUnavailableQwenShouldReturnPreserveWithoutChangingState() {
        GeneralBranchState gptBranch = new GeneralBranchState("gpt-old", List.of());
        GeneralConversationState state = GeneralConversationState.empty().withActiveBranch(
                GeneralBackend.GPT, SelectionOrigin.AUTOMATIC, gptBranch);
        ConversationSnapshot snapshot = snapshot("pregunta", "respuesta", state);
        GeneralSkill skill = new GeneralSkill(request -> {
            throw new AssertionError("GPT must not be called");
        }, request -> {
            throw unavailable();
        }, (command, ignored) -> new GeneralBackendDecision(
                GeneralBackend.QWEN_LOCAL, SelectionOrigin.EXPLICIT));

        AssistantResult result = skill.executeFollowUp("Vuelve a Qwen", snapshot);

        assertEquals(ConversationPolicy.PRESERVE, result.getConversationPolicyOverride());
        assertNull(result.getGeneralConversationState());
        assertSame(state, snapshot.getGeneralConversationState());
    }

    @Test
    void qwenFailureShouldNotBeHiddenByGpt() {
        AtomicInteger gptCalls = new AtomicInteger();
        GeneralSkill skill = new GeneralSkill(request -> {
            gptCalls.incrementAndGet();
            return new GeneralEngineResult("gpt", GeneralBranchState.empty());
        }, request -> {
            throw new LocalQwenException(LocalQwenException.Kind.FAILURE, "invalid response");
        }, (command, state) -> new GeneralBackendDecision(
                GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC));

        assertThrows(LocalQwenException.class, () -> skill.execute("Explica RSA"));
        assertEquals(0, gptCalls.get());
    }

    @Test
    void failedGptFallbackShouldLeaveTheOriginalSnapshotUntouched() {
        GeneralConversationState state = GeneralConversationState.empty().withActiveBranch(
                GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC,
                new GeneralBranchState(null, List.of(
                        new GeneralMessage(GeneralMessage.Role.USER, "Explica RSA"),
                        new GeneralMessage(GeneralMessage.Role.ASSISTANT, "RSA es..."))));
        ConversationSnapshot snapshot = snapshot("Explica RSA", "RSA es...", state);
        GeneralSkill skill = new GeneralSkill(request -> {
            throw new IllegalStateException("GPT offline");
        }, request -> {
            throw unavailable();
        }, (command, ignored) -> new GeneralBackendDecision(
                GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC));

        assertThrows(IllegalStateException.class,
                () -> skill.executeFollowUp("Explícalo mejor", snapshot));
        assertSame(state, snapshot.getGeneralConversationState());
        assertEquals(GeneralBackend.QWEN_LOCAL,
                snapshot.getGeneralConversationState().getActiveBackend().orElseThrow());
    }

    @Test
    void switchingBackendsShouldResumeAnExistingIndependentBranch() {
        GeneralBranchState qwenBranch = new GeneralBranchState(null, List.of(
                new GeneralMessage(GeneralMessage.Role.USER, "Explica RSA"),
                new GeneralMessage(GeneralMessage.Role.ASSISTANT, "RSA es...")));
        GeneralBranchState gptBranch = new GeneralBranchState("gpt-existing", List.of());
        GeneralConversationState state = new GeneralConversationState(
                GeneralBackend.GPT, SelectionOrigin.EXPLICIT,
                Map.of(GeneralBackend.QWEN_LOCAL, qwenBranch, GeneralBackend.GPT, gptBranch));
        ConversationSnapshot snapshot = snapshot("Profundiza con GPT", "Detalle GPT", state);
        GeneralEngine local = request -> {
            assertSame(qwenBranch, request.continuation());
            return new GeneralEngineResult("respuesta Qwen", request.continuation());
        };
        GeneralSkill skill = new GeneralSkill(request -> {
            throw new AssertionError("GPT must not be called");
        }, local, (command, ignored) -> new GeneralBackendDecision(
                GeneralBackend.QWEN_LOCAL, SelectionOrigin.EXPLICIT));

        AssistantResult result = skill.executeFollowUp("Vuelve a Qwen", snapshot);

        assertEquals(GeneralBackend.QWEN_LOCAL,
                result.getGeneralConversationState().getActiveBackend().orElseThrow());
        assertSame(qwenBranch, result.getGeneralConversationState()
                .getBranch(GeneralBackend.QWEN_LOCAL).orElseThrow());
    }

    private ConversationSnapshot snapshot(String user, String assistant,
                                          GeneralConversationState state) {
        return new ConversationSnapshot(Capability.GENERAL, user, assistant,
                state.getBranch(GeneralBackend.GPT).map(GeneralBranchState::continuationToken).orElse(null),
                null, state);
    }

    private LocalQwenException unavailable() {
        return new LocalQwenException(LocalQwenException.Kind.UNAVAILABLE, "offline");
    }
}
