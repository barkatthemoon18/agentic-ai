package com.fuad.assistant.skills.audio;

import com.fuad.audio.AudioControlIntent;

public interface AudioControlParser {
    AudioControlIntent parse(String command);
}
