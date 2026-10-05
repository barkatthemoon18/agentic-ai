package com.fuad.audio;

import javax.sound.sampled.Mixer;

public record AudioDeviceInfo(
        Mixer.Info info,
        String name,
        String description,
        String vendor) {
}