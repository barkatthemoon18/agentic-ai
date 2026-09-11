package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.local.LocalQwenException;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.general.GeneralBackend;
import com.fuad.assistant.skills.general.GeneralBackendDecision;
import com.fuad.assistant.skills.general.GeneralBackendSelector;
import com.fuad.assistant.skills.general.GeneralBranchState;
import com.fuad.assistant.skills.general.GeneralConversationState;
import com.fuad.assistant.skills.general.GeneralEngine;
import com.fuad.assistant.skills.general.GeneralEngineResult;
import com.fuad.assistant.skills.general.GeneralMessage;
import com.fuad.assistant.skills.general.GeneralRequest;
import com.fuad.assistant.skills.general.GptGeneralEngine;
import com.fuad.assistant.skills.general.SelectionOrigin;
import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GeneralSkill implements Skill {
    private static final String INSTRUCTIONS = "Responde de forma breve, natural y conversacional. " +
            "Normalmente responde en 1 a 3 frases. No añadas contexto, listas o antecedentes que el usuario no haya solicitado. " +
            "Si el usuario solicita explícitamente mayor detalle, adapta la extensión de la respuesta. " +
            "La respuesta será reproducida mediante voz, por lo que evita formato innecesario.";
    private static final String LOCAL_UNAVAILABLE = "El modelo local no está disponible en este momento.";
    private final GeneralEngine gptEngine;
    private final GeneralEngine localEngine;
    private final GeneralBackendSelector backendSelector;

    public GeneralSkill(AssistantEngine assistantEngine) {
        this(new GptGeneralEngine(assistantEngine), request -> {
            throw new IllegalStateException("Local engine is not configured");
        }, (command, state) -> new GeneralBackendDecision(GeneralBackend.GPT,
                state == null || state.getSelectionOrigin().isEmpty()
                        ? SelectionOrigin.AUTOMATIC
                        : state.getSelectionOrigin().orElseThrow()));
    }

    public GeneralSkill(GeneralEngine gptEngine, GeneralEngine localEngine,
                        GeneralBackendSelector backendSelector) {
        this.gptEngine = Objects.requireNonNull(gptEngine, "gptEngine cannot be null");
        this.localEngine = Objects.requireNonNull(localEngine, "localEngine cannot be null");
        this.backendSelector = Objects.requireNonNull(backendSelector, "backendSelector cannot be null");
    }

    @Override
    public AssistantResult execute(String command) {
        return executeInternal(command, null);
    }

    @Override
    public AssistantResult execute(String command, String continuationToken) {
        if (continuationToken == null || continuationToken.isBlank()) {
            return executeInternal(command, null);
        }
        GeneralConversationState state = GeneralConversationState.empty().withActiveBranch(
                GeneralBackend.GPT, SelectionOrigin.AUTOMATIC,
                new GeneralBranchState(continuationToken, List.of()));
        ConversationSnapshot snapshot = new ConversationSnapshot(Capability.GENERAL,
                command, "Contexto anterior no disponible", continuationToken, null, state);
        return executeInternal(command, snapshot);
    }

    @Override
    public AssistantResult executeFollowUp(String command, ConversationSnapshot snapshot) {
        return executeInternal(command, Objects.requireNonNull(snapshot, "snapshot cannot be null"));
    }

    private AssistantResult executeInternal(String command, ConversationSnapshot snapshot) {
        GeneralConversationState originalState = stateFrom(snapshot);
        GeneralBackendDecision decision = backendSelector.select(command, originalState);
        // TODO: when resuming an existing backend branch, consider supplying the last
        // visible exchange as transient handoff context without merging branch histories.
        GeneralBranchState branch = originalState.getBranch(decision.backend())
                .orElseGet(() -> seedBranch(snapshot));

        try {
            return successfulResult(decision.backend(), decision.origin(), originalState,
                    engine(decision.backend()).respond(request(command, branch)));
        }
        catch (LocalQwenException e) {
            if (decision.backend() != GeneralBackend.QWEN_LOCAL || !e.isUnavailable()) {
                throw e;
            }
            if (decision.origin() == SelectionOrigin.EXPLICIT) {
                return AssistantResult.preserveConversation(LOCAL_UNAVAILABLE);
            }

            GeneralBranchState gptBranch = originalState.getBranch(GeneralBackend.GPT)
                    .orElseGet(() -> seedBranch(snapshot));
            GeneralEngineResult fallback = gptEngine.respond(request(command, gptBranch));
            return successfulResult(GeneralBackend.GPT, SelectionOrigin.AUTOMATIC,
                    originalState, fallback);
        }
    }

    private AssistantResult successfulResult(GeneralBackend backend, SelectionOrigin origin,
                                             GeneralConversationState originalState,
                                             GeneralEngineResult result) {
        GeneralConversationState updated = originalState.withActiveBranch(
                backend, origin, result.continuation());
        return new AssistantResult(result.text(), result.continuation().continuationToken(),
                null, updated, null);
    }

    private GeneralRequest request(String command, GeneralBranchState branch) {
        return new GeneralRequest(command, INSTRUCTIONS, 300, branch);
    }

    private GeneralConversationState stateFrom(ConversationSnapshot snapshot) {
        if (snapshot == null || snapshot.getOwner() != Capability.GENERAL) {
            return GeneralConversationState.empty();
        }
        if (snapshot.getGeneralConversationState() != null) {
            return snapshot.getGeneralConversationState();
        }
        String token = snapshot.getContinuationToken();
        if (token != null && !token.isBlank()) {
            return GeneralConversationState.empty().withActiveBranch(GeneralBackend.GPT,
                    SelectionOrigin.AUTOMATIC, new GeneralBranchState(token, List.of()));
        }
        return GeneralConversationState.empty();
    }

    private GeneralBranchState seedBranch(ConversationSnapshot snapshot) {
        if (snapshot == null) {
            return GeneralBranchState.empty();
        }
        List<GeneralMessage> messages = new ArrayList<>();
        messages.add(new GeneralMessage(GeneralMessage.Role.USER, snapshot.getPreviousUserText()));
        messages.add(new GeneralMessage(GeneralMessage.Role.ASSISTANT,
                snapshot.getPreviousAssistantText()));
        return new GeneralBranchState(null, messages);
    }

    private GeneralEngine engine(GeneralBackend backend) {
        return backend == GeneralBackend.GPT ? gptEngine : localEngine;
    }

    @Override
    public ConversationPolicy getConversationPolicy() {
        return ConversationPolicy.KEEP_OPEN;
    }
}
