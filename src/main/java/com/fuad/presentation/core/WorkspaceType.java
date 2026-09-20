package com.fuad.presentation.core;

import lombok.Getter;

@Getter
public enum WorkspaceType {
    DEV("DEV", "Development"),
    MEDIA("MEDIA", "Media"),
    FILES("FILES", "Files"),
    SYSTEM("SYSTEM", "System"),
    WEB("WEB", "Research");

    private final String label;
    private final String subtitle;

    WorkspaceType(String label, String subtitle) {
        this.label = label;
        this.subtitle = subtitle;
    }
}
