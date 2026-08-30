package com.fuad.audio;

import com.fuad.enums.AudioAction;
import com.fuad.enums.AudioScope;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AudioControlIntent {
    AudioAction audioAction;
    AudioScope audioScope;
    Integer value;
}
