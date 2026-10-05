package com.fuad.assistant.skills.audio;

import com.fuad.audio.AudioControlIntent;
import com.fuad.audio.AudioAction;
import com.fuad.audio.AudioScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalAudioControlParserTest {
    @Test
    void shouldParseAbsoluteAndRelativeVolumeUsingClosedContract() {
        AudioControlIntent absolute = LocalAudioControlParser.parseClassification(
                "set_volume|assistant|40");
        AudioControlIntent relative = LocalAudioControlParser.parseClassification(
                "increase_volume|assistant|15");
        AudioControlIntent defaultRelative = LocalAudioControlParser.parseClassification(
                "decrease_volume|assistant|default");

        assertEquals(AudioAction.SET_VOLUME, absolute.audioAction());
        assertEquals(40, absolute.value());
        assertEquals(AudioAction.INCREASE_VOLUME, relative.audioAction());
        assertEquals(15, relative.value());
        assertEquals(AudioAction.DECREASE_VOLUME, defaultRelative.audioAction());
        assertNull(defaultRelative.value());
    }

    @Test
    void shouldPreserveUnsupportedScopesForSkillAuthorization() {
        AudioControlIntent system = LocalAudioControlParser.parseClassification("mute|system|none");
        AudioControlIntent application = LocalAudioControlParser.parseClassification(
                "set_volume|application|30");

        assertEquals(AudioScope.SYSTEM, system.audioScope());
        assertEquals(AudioAction.MUTE, system.audioAction());
        assertEquals(AudioScope.APPLICATION, application.audioScope());
        assertEquals(30, application.value());
    }

    @Test
    void malformedOrOutOfRangeOutputShouldFailClosed() {
        assertUnsupported(LocalAudioControlParser.parseClassification("set_volume|assistant|101"));
        assertUnsupported(LocalAudioControlParser.parseClassification("increase_volume|assistant|-10"));
        assertUnsupported(LocalAudioControlParser.parseClassification("mute|assistant|10"));
        assertUnsupported(LocalAudioControlParser.parseClassification("```mute|assistant|none```"));
        assertUnsupported(LocalAudioControlParser.parseClassification("anything else"));
    }

    private void assertUnsupported(AudioControlIntent intent) {
        assertEquals(AudioAction.UNSUPPORTED, intent.audioAction());
        assertEquals(AudioScope.UNKNOWN, intent.audioScope());
        assertNull(intent.value());
    }
}
