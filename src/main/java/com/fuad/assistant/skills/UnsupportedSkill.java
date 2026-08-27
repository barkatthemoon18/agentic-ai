package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.enums.Capability;
import com.fuad.enums.SkillType;

public class UnsupportedSkill implements Skill {
    private final Capability capability;

    public UnsupportedSkill(Capability capability) {
        this.capability = capability;
    }

    @Override
    public SkillType getType() {
        return SkillType.LOCAL_COMMAND;
    }

    @Override
    public AssistantResult execute(String command) {
        return new AssistantResult("La capacidad " + capability + " todavía no está implementada");
    }
}
