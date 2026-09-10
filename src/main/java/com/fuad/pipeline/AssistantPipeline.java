package com.fuad.pipeline;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRoute;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.enums.Capability;

import java.util.Objects;

public class AssistantPipeline {
    private final SkillRouter skillRouter;

    public AssistantPipeline(SkillRouter skillRouter) {
        this.skillRouter = Objects.requireNonNull(skillRouter, "skillRouter cannot be null");
    }

    public AssistantExecutionResult process(ActivationResult activationResult) {
        validateActivation(activationResult);
        SkillRoute skillRoute = skillRouter.route(activationResult.getCommand());
        return execute(activationResult, skillRoute, (String) null);
    }

    public AssistantExecutionResult processFollowUp(ActivationResult activationResult, ConversationSnapshot conversationSnapshot) {
        validateActivation(activationResult);
        Objects.requireNonNull(conversationSnapshot, "conversationSnapshot cannot be null");
        SkillRoute skillRoute = skillRouter.routeFollowUp(activationResult.getCommand(), conversationSnapshot);
        return execute(activationResult, skillRoute, conversationSnapshot);
    }

    private AssistantExecutionResult execute(ActivationResult activationResult, SkillRoute skillRoute, String continuationToken) {
        Skill skill = skillRoute.getSkill();
        System.out.println("SKILL -> " + skill.getClass().getSimpleName());
        AssistantResult response = skill.execute(activationResult.getCommand(), continuationToken);
        return executionResult(response, skill, skillRoute);
    }

    private AssistantExecutionResult execute(ActivationResult activationResult, SkillRoute skillRoute,
                                             ConversationSnapshot conversationSnapshot) {
        Skill skill = skillRoute.getSkill();
        System.out.println("SKILL -> " + skill.getClass().getSimpleName());
        AssistantResult response = skill.executeFollowUp(activationResult.getCommand(), conversationSnapshot);
        return executionResult(response, skill, skillRoute);
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
