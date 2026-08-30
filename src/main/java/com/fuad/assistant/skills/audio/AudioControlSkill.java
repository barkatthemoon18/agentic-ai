package com.fuad.assistant.skills.audio;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AudioControlIntent;

public class AudioControlSkill implements Skill {
    private final AudioControlParser parser;
    private final AssistantAudioController assistantAudioController;

    public AudioControlSkill(AudioControlParser parser, AssistantAudioController assistantAudioController) {
        this.parser = parser;
        this.assistantAudioController = assistantAudioController;
    }

    @Override
    public AssistantResult execute(String command) {
        AudioControlIntent intent = parser.parse(command);
        return switch (intent.getAudioAction()) {
            case SET_VOLUME -> setVolume(intent.getValue());
            case INCREASE_VOLUME -> increaseVolume(intent.getValue());
            case DECREASE_VOLUME -> decreaseVolume(intent.getValue());
            case MUTE -> {
                assistantAudioController.mute();
                yield new AssistantResult("Silenciando mi voz");
            }
            case UNMUTE -> {
                assistantAudioController.unmute();
                yield new AssistantResult("Audio restaurado");
            }
            case UNSUPPORTED -> new AssistantResult("No interpreté ese control de audio");
        };
    }

    private AssistantResult setVolume(Integer volume) {
        if (volume == null) {
            return new AssistantResult("No pude determinar el volumen");
        }
        assistantAudioController.setVolume(volume);
        return new AssistantResult("Volumen al " + volume + " por ciento");
    }

    private AssistantResult increaseVolume(Integer volume) {
        int value = volume == null ? assistantAudioController.increaseVolume() : assistantAudioController.increaseVolume(volume);
        return new AssistantResult("Volumen al " + value + " por ciento");
    }

    private AssistantResult decreaseVolume(Integer volume) {
        int value = volume == null ? assistantAudioController.decreaseVolume() : assistantAudioController.decreaseVolume(volume);
        return new AssistantResult("Volumen al " + value + " por ciento");
    }
}
