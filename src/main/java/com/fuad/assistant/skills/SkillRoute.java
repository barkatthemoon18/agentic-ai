package com.fuad.assistant.skills;

import com.fuad.enums.Capability;

public record SkillRoute(
        Capability capability,
        Skill skill) {
}
