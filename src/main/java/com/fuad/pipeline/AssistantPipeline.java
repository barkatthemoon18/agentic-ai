package com.fuad.pipeline;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.AssistantTurn;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRoute;
import com.fuad.assistant.skills.SkillExecution;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.enums.Capability;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class AssistantPipeline {
    private final AssistantExecutionLifecycleListener executionLifecycleListener;
    private final SkillRouter skillRouter;

    public AssistantPipeline(SkillRouter skillRouter) {
        this(skillRouter, AssistantExecutionLifecycleListener.noop());
    }

    public AssistantPipeline(SkillRouter skillRouter, AssistantExecutionLifecycleListener executionLifecycleListener) {
        this.skillRouter = Objects.requireNonNull(skillRouter, "skillRouter cannot be null");
        this.executionLifecycleListener = Objects.requireNonNull(executionLifecycleListener, "listener cannot be null");
    }

    public AssistantExecutionResult process(ActivationResult activationResult) {
        return completed(processTurn(activationResult));
    }

    public AssistantExecutionResult processFollowUp(ActivationResult activationResult, ConversationSnapshot conversationSnapshot) {
        return completed(processFollowUpTurn(activationResult, conversationSnapshot));
    }

    public AssistantTurn processTurn(ActivationResult activationResult) {
        validateActivation(activationResult);
        SkillRoute skillRoute = skillRouter.route(activationResult.getCommand());
        Skill skill = skillRoute.getSkill();
        System.out.println("SKILL -> " + skill.getClass().getSimpleName());
        return execute(() -> skill.executeTurn(activationResult.getCommand()), skill, skillRoute);
    }

    public AssistantTurn processFollowUpTurn(ActivationResult activationResult,
                                             ConversationSnapshot conversationSnapshot) {
        validateActivation(activationResult);
        Objects.requireNonNull(conversationSnapshot, "conversationSnapshot cannot be null");
        SkillRoute skillRoute = skillRouter.routeFollowUp(activationResult.getCommand(), conversationSnapshot);
        Skill skill = skillRoute.getSkill();
        System.out.println("SKILL -> " + skill.getClass().getSimpleName());
        return execute(() -> skill.executeFollowUpTurn(activationResult.getCommand(), conversationSnapshot), skill, skillRoute);
    }

    public AssistantTurn processDirectTurn(Capability capability, Supplier<SkillExecution> execution) {
        Objects.requireNonNull(capability, "capability cannot be null");
        Objects.requireNonNull(execution, "execution cannot be null");

        SkillRoute route = skillRouter.routeTo(capability);
        Skill skill = route.getSkill();

        System.out.println("SKILL -> " + skill.getClass().getSimpleName() + " [DIRECT]");

        return execute(execution, skill, route);
    }

    private AssistantTurn execute(Supplier<SkillExecution> action, Skill skill, SkillRoute skillRoute) {
        UUID executionId = UUID.randomUUID();

        executionLifecycleListener.onExecutionStarted(executionId, skillRoute.getCapability());
        try {
            SkillExecution execution = action.get();
            return map(execution, skill, skillRoute, executionId);
        }
        catch (RuntimeException e) {
            executionLifecycleListener.onExecutionCompleted(executionId, skillRoute.getCapability());
            throw e;
        }
    }

    private AssistantTurn map(SkillExecution execution, Skill skill, SkillRoute route, UUID executionId) {
        return switch (execution) {
            case SkillExecution.Completed completed -> {
                executionLifecycleListener.onExecutionCompleted(executionId, route.getCapability());
                yield new AssistantTurn.Completed(executionResult(completed.result(), skill, route));
            }
            case SkillExecution.Async async -> new AssistantTurn.Async(async.stage().whenComplete((result, failure) ->
                        executionLifecycleListener.onExecutionCompleted(executionId, route.getCapability()))
                        .thenApply(result -> executionResult(result, skill, route)));
            case SkillExecution.AwaitingInteraction<?> awaiting -> {
                executionLifecycleListener.onExecutionCompleted(executionId, route.getCapability());
                yield mapAwaiting(awaiting, skill, route);
            }
        };
    }

    private <T> AssistantTurn mapAwaiting(SkillExecution.AwaitingInteraction<T> awaiting,
                                          Skill skill, SkillRoute route) {
        return new AssistantTurn.AwaitingInteraction<>(awaiting.request(),
                result -> execute(() -> awaiting.continuation().apply(result), skill, route));
    }

    private AssistantExecutionResult completed(AssistantTurn turn) {
        if (turn instanceof AssistantTurn.Completed(AssistantExecutionResult result)) {
            return result;
        }
        throw new IllegalStateException("Interactive or asynchronous turn requires processTurn");
    }

    private AssistantExecutionResult executionResult(AssistantResult response, Skill skill, SkillRoute route) {
        var effectivePolicy = response.getConversationPolicyOverride() == null
                ? skill.getConversationPolicy()
                : response.getConversationPolicyOverride();
        return new AssistantExecutionResult(response, effectivePolicy, route.getCapability());
    }

    private void validateActivation(ActivationResult activationResult) {
        if (!activationResult.isActivated()) {
            throw new IllegalArgumentException("Activation result is not activated");
        }
    }
}
