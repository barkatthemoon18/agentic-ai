package com.fuad.presentation;

import com.fuad.model.runtime.ComponentSnapshot;
import com.fuad.model.runtime.RuntimeComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fuad.logging.LogCategory.MODEL;
import static com.fuad.logging.LogCategory.PRESENTATION;

public class ConsoleVisualOutput implements VisualOutput {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleVisualOutput.class);

    @Override
    public void show(VisualMessage visualMessage) {
        String state = visualMessage.audioSnapshot().muted() ? "MUTED" : "VOL " + visualMessage.audioSnapshot().volume() + "%";
        LOGGER.info(PRESENTATION, "TEXT UI [{}]:{}", state, visualMessage.text());
        if (visualMessage.payload() instanceof com.fuad.assistant.skills.os.ApplicationCatalogPayload catalog) {
            catalog.items().forEach(item -> LOGGER.info(PRESENTATION, "  - {}", item.displayName()));
            LOGGER.info(PRESENTATION, "  Página {} de {}", (catalog.pageIndex() + 1), catalog.totalPages());
        }
        if (visualMessage.payload() instanceof com.fuad.assistant.skills.os.OpenApplicationsPayload open) {
            open.items().forEach(item -> LOGGER.info(PRESENTATION, "  - {}", item.displayName()));
            if (open.unverifiableCount() > 0) {
                LOGGER.info(PRESENTATION, "  Estados no verificables: {}", open.unverifiableCount());
            }
        }
    }

    @Override
    public void hide() {
        LOGGER.info(PRESENTATION, "TEXT UI: hidden");
    }

    @Override
    public void showInfrastructureStatus(InfrastructureStatus status) {
        LOGGER.info(MODEL, "LMS RUNTIME [{}]", status.snapshot().state());
        for (RuntimeComponent component : RuntimeComponent.values()) {
            ComponentSnapshot snapshot = status.snapshot().component(component);
            LOGGER.info(MODEL, "  {}: {} - {}", component, snapshot.state(), snapshot.detail());
        }
    }
}
