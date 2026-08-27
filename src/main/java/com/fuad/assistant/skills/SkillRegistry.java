package com.fuad.assistant.skills;

import com.fuad.enums.Capability;

import java.util.EnumMap;
import java.util.Map;

public class SkillRegistry {
    private final Map<Capability, Skill> skills;

    public SkillRegistry(Map<Capability, Skill> skills) {
        EnumMap<Capability, Skill> registry = new EnumMap<>(Capability.class);
        registry.putAll(skills);
        for (Capability capability : Capability.values()) {
            if (!registry.containsKey(capability)) {
                throw new IllegalArgumentException("Missing skill for capability: " + capability);
            }
        }
        this.skills = Map.copyOf(registry);
    }

    public Skill get(Capability capability) {
        Skill skill = skills.get(capability);

        if (skill == null) {
            throw new IllegalStateException("No skill registered for capability: " + capability);
        }
        return skill;
    }
}
