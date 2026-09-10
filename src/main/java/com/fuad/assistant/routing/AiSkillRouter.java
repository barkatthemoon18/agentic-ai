package com.fuad.assistant.routing;

import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillRegistry;
import com.fuad.assistant.skills.SkillRoute;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.enums.Capability;

import java.util.Objects;

public class AiSkillRouter implements SkillRouter {
    private final SemanticRouter semanticRouter;
    private final SkillRegistry skillRegistry;
    private final ResearchEscalationDetector researchEscalationDetector;

    public AiSkillRouter(SemanticRouter semanticRouter, SkillRegistry skillRegistry) {
        this(semanticRouter, skillRegistry, new ResearchEscalationDetector());
    }

    public AiSkillRouter(SemanticRouter semanticRouter, SkillRegistry skillRegistry,
                         ResearchEscalationDetector researchEscalationDetector) {
        this.semanticRouter = Objects.requireNonNull(semanticRouter, "semanticRouter cannot be null");
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry cannot be null");
        this.researchEscalationDetector = Objects.requireNonNull(researchEscalationDetector,
                "researchEscalationDetector cannot be null");
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

    @Override
    public SkillRoute routeFollowUp(String command, ConversationSnapshot snapshot) {
        if (snapshot.getOwner() == Capability.GENERAL
                && researchEscalationDetector.shouldEscalate(command)) {
            System.out.println("CAPABILITY TRANSITION -> CURRENT_RESEARCH");
            return routeTo(Capability.CURRENT_RESEARCH);
        }
        return routeTo(snapshot.getOwner());
    }
}
