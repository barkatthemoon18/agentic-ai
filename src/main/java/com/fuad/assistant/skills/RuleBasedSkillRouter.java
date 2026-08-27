package com.fuad.assistant.skills;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class RuleBasedSkillRouter implements SkillRouter {
    private static final List<String> EXPLAIN_LIST = List.of("explicame", "explica", "en detalle", "detalladamente",
            "profundiza", "profundamente", "desarrolla", "analiza", "haz un analisis");
    private static final List<String> LOCAL_COMMAND_LIST = List.of("que hora es", "dime la hora", "hora actual");
    private final Skill generalSkill;
    private final Skill explainSkill;
    private final Skill localSkill;

    public RuleBasedSkillRouter(Skill generalSkill, Skill explainSkill, Skill localSkill) {
        this.generalSkill = generalSkill;
        this.explainSkill = explainSkill;
        this.localSkill = localSkill;
    }

    @Override
    public Skill route(String command) {
        String normalized = normalize(command);
        if (requestLocalCommand(normalized)) {
            return localSkill;
        }
        if (requestExplanation(normalized)) {
            return explainSkill;
        }
        return generalSkill;
    }

    private boolean requestLocalCommand(String normalized) {
        return LOCAL_COMMAND_LIST.stream().anyMatch(normalized::contains);
    }

    private boolean requestExplanation(String command) {
        return EXPLAIN_LIST.stream().anyMatch(command::contains);
    }

    private String normalize(String command) {
        if (command == null) {
            return "";
        }
        String normalized = Normalizer.normalize(command, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
