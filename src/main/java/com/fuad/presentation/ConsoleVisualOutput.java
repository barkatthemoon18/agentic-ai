package com.fuad.presentation;

public class ConsoleVisualOutput implements VisualOutput {
    @Override
    public void show(VisualMessage visualMessage) {
        String state = visualMessage.getAudioSnapshot().isMuted() ? "MUTED" : "VOL " + visualMessage.getAudioSnapshot().getVolume() + "%";
        System.out.println("TEXT UI [" + state + "]:" + visualMessage.getText());
        if (visualMessage.getPayload() instanceof com.fuad.assistant.skills.os.ApplicationCatalogPayload catalog) {
            catalog.items().forEach(item -> System.out.println("  - " + item.displayName()));
            System.out.println("  Página " + (catalog.pageIndex() + 1) + " de " + catalog.totalPages());
        }
        if (visualMessage.getPayload() instanceof com.fuad.assistant.skills.os.OpenApplicationsPayload open) {
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
}
