package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;

public interface Skill {
    AssistantResult execute(String command);
}
