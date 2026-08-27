package com.fuad.assistant.routing;

import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.enums.Capability;

public class AiSkillRouter implements SkillRouter {
    private final SemanticRouter semanticRouter;
    private final Skill systemTimeSkill;
    private final Skill generalSkill;

    public AiSkillRouter(SemanticRouter semanticRouter, Skill systemTimeSkill, Skill generalSkill) {
        this.semanticRouter = semanticRouter;
        this.systemTimeSkill = systemTimeSkill;
        this.generalSkill = generalSkill;
    }

    @Override
    public Skill route(String command) {
        Capability capability = semanticRouter.classify(command);
        System.out.println("CAPABILITY -> " + capability);
        return switch (capability) {
            case SYSTEM_TIME -> systemTimeSkill;
            case GENERAL -> generalSkill;
            case AUDIO_CONTROL, OS_COMMAND, CURRENT_RESEARCH -> generalSkill;
        };
    }
}
