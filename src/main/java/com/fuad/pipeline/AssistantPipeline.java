package com.fuad.pipeline;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRoute;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.enums.Capability;

public class AssistantPipeline {
    private final AssistantEngine assistantEngine;
    private final SkillRouter skillRouter;

    public AssistantPipeline(AssistantEngine assistantEngine, SkillRouter skillRouter) {
        this.assistantEngine = assistantEngine;
        this.skillRouter = skillRouter;
    }

    public AssistantExecutionResult process(ActivationResult activationResult) {
        validateActivation(activationResult);
        SkillRoute skillRoute = skillRouter.route(activationResult.getCommand());
        return execute(activationResult, skillRoute);
    }

    public AssistantExecutionResult processFollowUp(ActivationResult activationResult, Capability owner) {
        validateActivation(activationResult);
        SkillRoute skillRoute = skillRouter.routeTo(owner);
        return execute(activationResult, skillRoute);
    }

    public void resetConversation() {
        assistantEngine.resetConversation();
    }

    private AssistantExecutionResult execute(ActivationResult activationResult, SkillRoute skillRoute) {
        Skill skill = skillRoute.getSkill();
        System.out.println("SKILL -> " + skill.getClass().getSimpleName());
        AssistantResult response = skill.execute(activationResult.getCommand());
        return new AssistantExecutionResult(response, skill.getConversationPolicy(), skillRoute.getCapability());
    }

    private void validateActivation(ActivationResult activationResult) {
        if (!activationResult.isActivated()) {
            throw new IllegalArgumentException("Activation result is not activated");
        }
    }
}
