package com.fuad.interaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ChoiceOption(String id, String label, Optional<String> detail,
                           List<String> voiceAliases) {
    public ChoiceOption {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(label, "label must not be null");
        id = id.trim();
        label = label.trim();
        if (id.isEmpty() || label.isEmpty()) {
            throw new IllegalArgumentException("choice id and label must not be blank");
        }
        detail = detail == null ? Optional.empty()
                : detail.map(String::trim).filter(value -> !value.isEmpty());
        voiceAliases = voiceAliases == null ? List.of()
                : voiceAliases.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    public ChoiceOption(String id, String label, List<String> voiceAliases) {
        this(id, label, Optional.empty(), voiceAliases);
    }
}
