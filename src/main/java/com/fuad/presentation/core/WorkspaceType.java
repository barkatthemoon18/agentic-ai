package com.fuad.presentation.core;

public enum WorkspaceType {
    TOOLS("TOOLS", "Tools"),
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

    public String label() {
        return label;
    }

    public String subtitle() {
        return subtitle;
    }
}
