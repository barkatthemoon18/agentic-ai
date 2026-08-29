package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.enums.OsAction;

import java.io.IOException;

public class OsCommandSkill implements Skill {
    private final OsCommandParser parser;
    private final ApplicationRegistry applicationRegistry;
    private final ApplicationController applicationController;
    private final OsCommandSafetyGuard safetyGuard;

    public OsCommandSkill(OsCommandParser parser, ApplicationRegistry applicationRegistry,
                          ApplicationController applicationController, OsCommandSafetyGuard safetyGuard) {
        this.parser = parser;
        this.applicationRegistry = applicationRegistry;
        this.applicationController = applicationController;
        this.safetyGuard = safetyGuard;
    }

    @Override
    public AssistantResult execute(String command) {
        if (!safetyGuard.canExecute(command)) {
            System.out.println("OS SAFETY -> REJECTED");
            return new AssistantResult("No interpreté eso como una orden inmediata.");
        }
        OsCommandIntent intent = parser.parse(command);
        if (intent.getAction() == OsAction.UNSUPPORTED) {
            return new AssistantResult("Ese comando del sistema todavía no está soportado");
        }
        ApplicationDefinition applicationDefinition = applicationRegistry.get(intent.getTarget()).orElse(null);
        if (applicationDefinition == null) {
            return new AssistantResult("No tengo registrada esa aplicación");
        }
        try {
            return switch (intent.getAction()) {
                case OPEN_APPLICATION -> open(applicationDefinition);
                case CLOSE_APPLICATION -> close(applicationDefinition);
                default -> new AssistantResult("Ese comando del sistema todavía no está soportado");
            };
        }
        catch (Exception e) {
            System.err.println("OS command failed: " + e.getMessage());
        }
        return new AssistantResult("No pude ejecutar esa acción");
    }

    private AssistantResult open(ApplicationDefinition applicationDefinition) throws IOException {
        applicationController.open(applicationDefinition);
        return new AssistantResult("Abriendo: " + applicationDefinition.getDisplayName() + ".");
    }

    private AssistantResult close(ApplicationDefinition applicationDefinition) {
        boolean closed = applicationController.close(applicationDefinition);
        if (!closed) {
            return new AssistantResult(applicationDefinition.getDisplayName() + " no está abierto");
        }
        return new AssistantResult("Cerrando: " + applicationDefinition.getDisplayName() + ".");
    }
}
