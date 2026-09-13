package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantPayload;

import java.util.List;

public record OpenApplicationsPayload(List<OpenApplicationItem> items, int unverifiableCount)
        implements AssistantPayload {
    public OpenApplicationsPayload {
        items = items == null ? List.of() : List.copyOf(items);
        if (unverifiableCount < 0) throw new IllegalArgumentException("unverifiableCount cannot be negative");
    }
}
