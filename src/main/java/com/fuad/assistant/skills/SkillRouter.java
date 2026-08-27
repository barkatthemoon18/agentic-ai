package com.fuad.assistant.skills;

public interface SkillRouter {
    Skill route(String command);
}
