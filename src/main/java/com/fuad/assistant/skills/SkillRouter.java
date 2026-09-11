package com.fuad.assistant.skills;

import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;

public interface SkillRouter {
    SkillRoute route(String command);
    SkillRoute routeTo(Capability capability);
    default SkillRoute routeFollowUp(String command, ConversationSnapshot snapshot) {
        return routeTo(snapshot.getOwner());
    }
}
