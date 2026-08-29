package com.fuad.assistant.skills.os;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ApplicationDefinition {
    private final String id;
    private final String displayName;
    private final List<String> openCommand;
    private final String processName;
}
