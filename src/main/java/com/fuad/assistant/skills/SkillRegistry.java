package com.fuad.assistant.skills;

import com.fuad.enums.Capability;

import java.util.Map;

public class SkillRegistry {
    private final Map<Capability, Skill> skills;

    public SkillRegistry(Skill generalSkill, Skill systemTimeSkill, Skill audioControlSkill, Skill osCommandSkill,
                         Skill currentResearchSkill) {
        skills = Map.of(Capability.GENERAL, generalSkill, Capability.SYSTEM_TIME, systemTimeSkill,
                Capability.AUDIO_CONTROL, audioControlSkill, Capability.OS_COMMAND, osCommandSkill,
                Capability.CURRENT_RESEARCH, currentResearchSkill);
    }

    public Skill get(Capability capability) {
        return skills.getOrDefault(capability, skills.get(Capability.GENERAL));
    }
}
