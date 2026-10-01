package com.fuad.telemetry.gpu.nvidia;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

public interface NvmlLibrary extends Library {
    int NVML_SUCCESS = 0;
    int NVML_TEMPERATURE_GPU = 0;

    int nvmlInit_v2();
    int nvmlShutdown();
    int nvmlDeviceGetHandleByIndex_v2(int index, PointerByReference device);
    int nvmlDeviceGetUtilizationRates(Pointer device, NvmlUtilization utilization);
    int nvmlDeviceGetMemoryInfo(Pointer device, NvmlMemory memory);
    int nvmlDeviceGetTemperature(Pointer device, int sensorType, IntByReference temperature);
    String nvmlErrorString(int result);

    @Structure.FieldOrder({
        "gpu",
        "memory"
    })
    class NvmlUtilization extends Structure {
        public int gpu;
        public int memory;
    }

    @Structure.FieldOrder({
        "total",
        "free",
        "used"
    })
    class NvmlMemory extends Structure {
        public long total;
        public long free;
        public long used;
    }
}
