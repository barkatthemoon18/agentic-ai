package com.fuad.assistant.skills.media;

@FunctionalInterface
public interface MediaControlParser {
    MediaControlIntent parse(String command);
}
