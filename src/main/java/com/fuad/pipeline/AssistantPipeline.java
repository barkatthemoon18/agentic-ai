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
        return execute(activationResult, skillRoute, null);
    }

    public AssistantExecutionResult processFollowUp(ActivationResult activationResult, ConversationSnapshot conversationSnapshot) {
        validateActivation(activationResult);
        Objects.requireNonNull(conversationSnapshot, "conversationSnapshot cannot be null");
        SkillRoute skillRoute = skillRouter.routeTo(conversationSnapshot.getOwner());
        return execute(activationResult, skillRoute, conversationSnapshot.getContinuationToken());
    }

    private AssistantExecutionResult execute(ActivationResult activationResult, SkillRoute skillRoute, String continuationToken) {
        Skill skill = skillRoute.getSkill();
        System.out.println("SKILL -> " + skill.getClass().getSimpleName());
        AssistantResult response = skill.execute(activationResult.getCommand(), continuationToken);
        return new AssistantExecutionResult(response, skill.getConversationPolicy(), skillRoute.getCapability());
    }

    private void validateActivation(ActivationResult activationResult) {
        if (!activationResult.isActivated()) {
            throw new IllegalArgumentException("Activation result is not activated");
        }
    }
}
