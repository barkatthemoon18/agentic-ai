package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.enums.SkillType;

public interface Skill {
    SkillType getType();
    AssistantResult execute(String command);
}
