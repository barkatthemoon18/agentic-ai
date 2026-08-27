package com.fuad.enums;

import lombok.Getter;

@Getter
public enum Capability {
    SYSTEM_TIME("system-time"),
    AUDIO_CONTROL("audio-control"),
    OS_COMMAND("os-command"),
    CURRENT_RESEARCH("current-research"),
    GENERAL("general");

    private final String value;

    Capability(String value) {
        this.value = value;
    }

    public static Capability fromValue(String value) {
        for (Capability skillType : Capability.values()) {
            if (skillType.value.equalsIgnoreCase(value.trim())) {
                return skillType;
            }
        }
        throw new IllegalArgumentException("Unknown routing skill: " + value);
    }
}
