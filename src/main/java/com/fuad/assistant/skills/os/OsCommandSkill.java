package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.enums.OsAction;

public class OsCommandSkill implements Skill {
    private final OsCommandParser parser;
    private final ApplicationRegistry applicationRegistry;

    public OsCommandSkill(OsCommandParser parser, ApplicationRegistry applicationRegistry) {
        this.parser = parser;
        this.applicationRegistry = applicationRegistry;
    }

    @Override
    public AssistantResult execute(String command) {
        OsCommandIntent intent = parser.parse(command);
        if (intent.getAction() != OsAction.OPEN_APPLICATION) {
            return new AssistantResult("Ese comando del sistema todavía no está soportado");
        }
        try {
            boolean opened = applicationRegistry.open(intent.getTarget());
            if (!opened) {
                return new AssistantResult("No tengo registrada esa aplicación");
            }
            return new AssistantResult("Abriendo Spotify");
        }
        catch (Exception e) {
            System.err.println("Error opening application: " + e.getMessage());
            return new AssistantResult("No pude abrir Spotify");
        }
    }
}
