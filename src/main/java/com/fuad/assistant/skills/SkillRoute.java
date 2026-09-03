package com.fuad.assistant.skills;

import com.fuad.enums.Capability;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SkillRoute {
    Capability capability;
    Skill skill;
}
