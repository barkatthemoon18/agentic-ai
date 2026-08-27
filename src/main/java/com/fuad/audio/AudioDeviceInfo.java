package com.fuad.audio;

import lombok.*;

import javax.sound.sampled.Mixer;

@Getter
@Setter
@AllArgsConstructor
public class AudioDeviceInfo {
    Mixer.Info info;
    String name;
    String description;
    String vendor;
}