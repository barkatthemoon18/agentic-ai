package com.fuad.assistant.skills.audio;

import com.fuad.audio.AudioControlIntent;
import com.fuad.enums.AudioAction;
import com.fuad.enums.AudioScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraniteAudioControlParserTest {
    @Test
    void shouldParseAbsoluteAndRelativeVolumeUsingClosedContract() {
        AudioControlIntent absolute = GraniteAudioControlParser.parseClassification(
                "set_volume|assistant|40");
        AudioControlIntent relative = GraniteAudioControlParser.parseClassification(
                "increase_volume|assistant|15");
        AudioControlIntent defaultRelative = GraniteAudioControlParser.parseClassification(
                "decrease_volume|assistant|default");

        assertEquals(AudioAction.SET_VOLUME, absolute.getAudioAction());
        assertEquals(40, absolute.getValue());
        assertEquals(AudioAction.INCREASE_VOLUME, relative.getAudioAction());
        assertEquals(15, relative.getValue());
        assertEquals(AudioAction.DECREASE_VOLUME, defaultRelative.getAudioAction());
        assertNull(defaultRelative.getValue());
    }

    @Test
    void shouldPreserveUnsupportedScopesForSkillAuthorization() {
        AudioControlIntent system = GraniteAudioControlParser.parseClassification("mute|system|none");
        AudioControlIntent application = GraniteAudioControlParser.parseClassification(
                "set_volume|application|30");

        assertEquals(AudioScope.SYSTEM, system.getAudioScope());
        assertEquals(AudioAction.MUTE, system.getAudioAction());
        assertEquals(AudioScope.APPLICATION, application.getAudioScope());
        assertEquals(30, application.getValue());
    }

    @Test
    void malformedOrOutOfRangeOutputShouldFailClosed() {
        assertUnsupported(GraniteAudioControlParser.parseClassification("set_volume|assistant|101"));
        assertUnsupported(GraniteAudioControlParser.parseClassification("increase_volume|assistant|-10"));
        assertUnsupported(GraniteAudioControlParser.parseClassification("mute|assistant|10"));
        assertUnsupported(GraniteAudioControlParser.parseClassification("```mute|assistant|none```"));
        assertUnsupported(GraniteAudioControlParser.parseClassification("anything else"));
    }

    private void assertUnsupported(AudioControlIntent intent) {
        assertEquals(AudioAction.UNSUPPORTED, intent.getAudioAction());
        assertEquals(AudioScope.UNKNOWN, intent.getAudioScope());
        assertNull(intent.getValue());
    }
}
