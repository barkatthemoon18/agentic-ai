package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantPayload;

import java.util.List;
import java.util.UUID;

public record ApplicationCatalogPayload(UUID sessionId, String filter, int pageIndex, int pageSize,
                                        int totalCount, int totalPages, List<ApplicationListItem> items)
        implements AssistantPayload {
    public ApplicationCatalogPayload {
        items = List.copyOf(items);
    }
}
