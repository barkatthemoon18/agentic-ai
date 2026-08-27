package com.fuad.pipeline;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRouter;

public class AssistantPipeline {
    private final AssistantEngine assistantEngine;
    private final SkillRouter skillRouter;

    public AssistantPipeline(AssistantEngine assistantEngine, SkillRouter skillRouter) {
        this.assistantEngine = assistantEngine;
        this.skillRouter = skillRouter;
    }

    public AssistantResult process(ActivationResult activationResult) {
        if (!activationResult.isActivated()) {
            throw new IllegalArgumentException("Activation result is not activated");
        }
        Skill skill = skillRouter.route(activationResult.getCommand());
        System.out.println("SKILL -> " + skill.getType());
        return skill.execute(activationResult.getCommand());
    }

    public void resetConversation() {
        assistantEngine.resetConversation();
    }
}
