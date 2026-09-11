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
    }

    @Override
    public void hide() {
        System.out.println("TEXT UI: hidden");
    }
}
