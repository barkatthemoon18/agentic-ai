package com.fuad.presentation;

public class ConsoleVisualOutput implements VisualOutput {
    @Override
    public void show(VisualMessage visualMessage) {
        String state = visualMessage.getAudioSnapshot().isMuted() ? "MUTED" : "VOL " + visualMessage.getAudioSnapshot().getVolume() + "%";
        System.out.println("TEXT UI [" + state + "]:" + visualMessage.getText());
    }

    @Override
    public void hide() {
        System.out.println("TEXT UI: hidden");
    }
}
