package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class SystemTimeSkill implements Skill {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public AssistantResult execute(String command) {
        String time = LocalTime.now().format(TIME_FORMAT);
        return new AssistantResult("Son las " + time + " horas.");
    }
}
