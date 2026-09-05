package com.fuad.assistant.skills.audio;

import com.fuad.assistant.AssistantResult;
import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AudioControlIntent;
import com.fuad.enums.AudioAction;
import com.fuad.enums.AudioScope;
import com.fuad.enums.ConversationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioControlSkillTest {
    @Test
    void shouldExecuteAbsoluteAndRelativeAssistantVolume() {
        AssistantAudioController controller = new AssistantAudioController();
        AudioControlSkill absolute = skill(controller,
                new AudioControlIntent(AudioAction.SET_VOLUME, AudioScope.ASSISTANT, 40));

        AssistantResult setResult = absolute.execute("irrelevant");
        assertEquals(40, controller.getVolume());
        assertEquals("Volumen al 40 por ciento.", setResult.getText());

        AudioControlSkill relative = skill(controller,
                new AudioControlIntent(AudioAction.INCREASE_VOLUME, AudioScope.ASSISTANT, 15));
        assertEquals("Volumen al 55 por ciento.", relative.execute("irrelevant").getText());
    }

    @Test
    void unsupportedScopeShouldNeverMutateAssistantVolume() {
        AssistantAudioController controller = new AssistantAudioController();
        controller.setVolume(40);
        AudioControlSkill skill = skill(controller,
                new AudioControlIntent(AudioAction.MUTE, AudioScope.APPLICATION, null));

        AssistantResult result = skill.execute("Silencia Spotify");

        assertEquals(40, controller.getVolume());
        assertEquals(0.4f, controller.getGain());
        assertTrue(result.getText().contains("aplicaciones"));
    }

    @Test
    void muteShouldBeImmediateAndUnmuteShouldRestoreAudibleVolume() {
        AssistantAudioController controller = new AssistantAudioController();
        controller.setVolume(35);
        AudioControlSkill mute = skill(controller,
                new AudioControlIntent(AudioAction.MUTE, AudioScope.ASSISTANT, null));

        assertEquals("Mi voz quedó silenciada.", mute.execute("Silencia tu voz").getText());
        assertEquals(0.0f, controller.getGain());

        controller.setVolume(0);
        AudioControlSkill unmute = skill(controller,
                new AudioControlIntent(AudioAction.UNMUTE, AudioScope.ASSISTANT, null));
        assertEquals("Mi voz fue restaurada al 35 por ciento.",
                unmute.execute("Vuelve a hablar").getText());
        assertEquals(0.35f, controller.getGain());
    }

    @Test
    void shouldPreserveConversationByDefault() {
        AudioControlSkill skill = skill(new AssistantAudioController(),
                AudioControlIntent.unsupported(AudioScope.ASSISTANT));

        assertEquals(ConversationPolicy.PRESERVE, skill.getConversationPolicy());
    }

    @Test
    void unsupportedIntentShouldNotBeReportedAsAnUnsupportedTarget() {
        AssistantAudioController controller = new AssistantAudioController();
        AudioControlSkill skill = skill(controller,
                AudioControlIntent.unsupported(AudioScope.APPLICATION));

        assertEquals("No interpreté ese control de audio.", skill.execute("diagnóstico").getText());
        assertEquals(1.0f, controller.getGain());
    }

    private AudioControlSkill skill(AssistantAudioController controller, AudioControlIntent intent) {
        return new AudioControlSkill(command -> intent, controller);
    }
}
