package com.fuad.assistant.skills.audio;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AudioControlIntent;
import com.fuad.enums.AudioAction;
import com.fuad.enums.AudioScope;

import java.util.Objects;

public class AudioControlSkill implements Skill {
    private final AudioControlParser parser;
    private final AssistantAudioController assistantAudioController;

    public AudioControlSkill(AudioControlParser parser, AssistantAudioController assistantAudioController) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.assistantAudioController = Objects.requireNonNull(assistantAudioController, "assistantAudioController");
    }

    @Override
    public AssistantResult execute(String command) {
        AudioControlIntent intent = Objects.requireNonNull(parser.parse(command), "parser result");
        if (intent.getAudioAction() == AudioAction.UNSUPPORTED) {
            return new AssistantResult("No interpreté ese control de audio.");
        }
        if (intent.getAudioScope() != AudioScope.ASSISTANT) {
            return unsupportedScope(intent.getAudioScope());
        }
        return switch (intent.getAudioAction()) {
            case SET_VOLUME -> setVolume(intent.getValue());
            case INCREASE_VOLUME -> increaseVolume(intent.getValue());
            case DECREASE_VOLUME -> decreaseVolume(intent.getValue());
            case MUTE -> {
                assistantAudioController.mute();
                yield new AssistantResult("Mi voz quedó silenciada.");
            }
            case UNMUTE -> {
                int volume = assistantAudioController.unmute();
                yield new AssistantResult("Mi voz fue restaurada al " + volume + " por ciento.");
            }
            case UNSUPPORTED -> throw new IllegalStateException("UNSUPPORTED action was already handled");
        };
    }

    private AssistantResult setVolume(Integer volume) {
        if (volume == null) {
            return new AssistantResult("No pude determinar el volumen.");
        }
        int effectiveVolume = assistantAudioController.setVolume(volume);
        return new AssistantResult("Volumen al " + effectiveVolume + " por ciento.");
    }

    private AssistantResult increaseVolume(Integer volume) {
        int value = volume == null ? assistantAudioController.increaseVolume() : assistantAudioController.increaseVolume(volume);
        return new AssistantResult("Volumen al " + value + " por ciento.");
    }

    private AssistantResult decreaseVolume(Integer volume) {
        int value = volume == null ? assistantAudioController.decreaseVolume() : assistantAudioController.decreaseVolume(volume);
        return new AssistantResult("Volumen al " + value + " por ciento.");
    }

    private AssistantResult unsupportedScope(AudioScope audioScope) {
        return switch (audioScope) {
            case SYSTEM -> new AssistantResult(
                    "Actualmente sólo puedo controlar el volumen de mi propia voz, no el de Windows.");
            case APPLICATION -> new AssistantResult(
                    "Actualmente sólo puedo controlar el volumen de mi propia voz, no el de aplicaciones.");
            case UNKNOWN -> new AssistantResult("No pude determinar qué audio quieres controlar.");
            case ASSISTANT -> throw new IllegalStateException("ASSISTANT scope is supported");
        };
    }
}
