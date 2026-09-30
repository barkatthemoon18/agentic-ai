package com.fuad.audio.output;

import java.util.Objects;
import java.util.OptionalDouble;

public record AudioOutputSnapshot(
        boolean available,
        String endpointId,
        String deviceName,
        OptionalDouble volume,
        boolean hardwareVolumeSupported) {

    public AudioOutputSnapshot {
        double value;

        endpointId = endpointId == null ? "" : endpointId;
        deviceName = deviceName == null ? "" : deviceName;
        volume = Objects.requireNonNull(volume, "volume must not be null");
        if (volume.isPresent()) {
            value = volume.getAsDouble();
            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("Volume must be between 0.0 and 1.0.");
            }
        }
    }

    public static AudioOutputSnapshot unavailable() {
        return new AudioOutputSnapshot(false, "", "", OptionalDouble.empty(), false);
    }
}
