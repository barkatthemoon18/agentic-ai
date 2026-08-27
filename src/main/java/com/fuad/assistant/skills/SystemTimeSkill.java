package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.enums.SkillType;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class SystemTimeSkill implements Skill {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public SkillType getType() {
        return SkillType.LOCAL_COMMAND;
    }

    @Override
    public AssistantResult execute(String command) {
        String time = LocalTime.now().format(TIME_FORMAT);
        return new AssistantResult("Son las " + time + "horas.");
    }
}
