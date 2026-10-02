package com.fuad.presentation;

import com.fuad.model.runtime.ComponentSnapshot;
import com.fuad.model.runtime.RuntimeComponent;

public class ConsoleVisualOutput implements VisualOutput {
    @Override
    public void show(VisualMessage visualMessage) {
        String state = visualMessage.audioSnapshot().muted() ? "MUTED" : "VOL " + visualMessage.audioSnapshot().volume() + "%";
        System.out.println("TEXT UI [" + state + "]:" + visualMessage.text());
        if (visualMessage.payload() instanceof com.fuad.assistant.skills.os.ApplicationCatalogPayload catalog) {
            catalog.items().forEach(item -> System.out.println("  - " + item.displayName()));
            System.out.println("  Página " + (catalog.pageIndex() + 1) + " de " + catalog.totalPages());
        }
        if (visualMessage.payload() instanceof com.fuad.assistant.skills.os.OpenApplicationsPayload open) {
            open.items().forEach(item -> System.out.println("  - " + item.displayName()));
            if (open.unverifiableCount() > 0) {
                System.out.println("  Estados no verificables: " + open.unverifiableCount());
            }
        }
    }

    @Override
    public void hide() {
        System.out.println("TEXT UI: hidden");
    }

    @Override
    public void showInfrastructureStatus(InfrastructureStatus status) {
        System.out.println("LMS RUNTIME [" + status.snapshot().state() + "]");
        for (RuntimeComponent component : RuntimeComponent.values()) {
            ComponentSnapshot snapshot = status.snapshot().component(component);
            System.out.println("  " + component + ": " + snapshot.state() + " - " + snapshot.detail());
        }
    }
}
