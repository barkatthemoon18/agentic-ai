package com.fuad.assistant.skills;

import com.fuad.enums.Capability;

public interface SkillRouter {
    SkillRoute route(String command);
    SkillRoute routeTo(Capability capability);
}
