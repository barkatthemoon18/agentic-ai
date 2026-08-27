package com.fuad.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AudioDeviceManager {
    public  List<AudioDeviceInfo> getInputDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            Line.Info[] targetLines =  mixer.getTargetLineInfo();
            if (targetLines.length == 0) {
                continue;
            }
            devices.add(new AudioDeviceInfo(info, info.getName(), info.getDescription(), info.getVendor()));
        }
        return devices;
    }

    public List<AudioDeviceInfo> getOutputDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getSourceLineInfo().length == 0) {
                continue;
            }
            devices.add(new AudioDeviceInfo(info, info.getName(), info.getDescription(), info.getVendor()));
        }
        return devices;
    }

    public Optional<AudioDeviceInfo> findInputDevice(String name) {
        return getInputDevices().stream().filter(device -> device.getName().toLowerCase()
                .contains(name.toLowerCase())).findFirst();
    }
}
