package com.fuad.assistant.routing;

import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRegistry;
import com.fuad.assistant.skills.SkillRoute;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.enums.Capability;

public class AiSkillRouter implements SkillRouter {
    private final SemanticRouter semanticRouter;
    private final SkillRegistry skillRegistry;

    public AiSkillRouter(SemanticRouter semanticRouter, SkillRegistry skillRegistry) {
        this.semanticRouter = semanticRouter;
        this.skillRegistry = skillRegistry;
    }

    @Override
    public SkillRoute route(String command) {
        Capability capability = semanticRouter.classify(command);
        System.out.println("CAPABILITY -> " + capability);
        return routeTo(capability);
    }

    @Override
    public SkillRoute routeTo(Capability capability) {
        Skill skill = skillRegistry.get(capability);
        return new SkillRoute(capability, skill);
    }
}
