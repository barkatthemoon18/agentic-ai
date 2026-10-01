package com.fuad.telemetry.gpu.nvidia;

import com.fuad.telemetry.gpu.GpuTelemetryProvider;
import com.fuad.telemetry.gpu.GpuTelemetrySnapshot;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

public final class NvidiaGpuTelemetryProvider implements GpuTelemetryProvider {
    private static final double BYTES_PER_GB = Math.pow(1024, 3);
    private final NvmlLibrary nvml;
    private final Pointer device;
    private boolean closed;

    public NvidiaGpuTelemetryProvider() {
        PointerByReference deviceReference;

        nvml = NvmlLoader.load();
        check(nvml.nvmlInit_v2(), "nvmlInit_v2");
        deviceReference = new PointerByReference();

        try {
            check(nvml.nvmlDeviceGetHandleByIndex_v2(0, deviceReference), "nvmlDeviceGetHandleByIndex_v2");
            device = deviceReference.getValue();
            if (device == null) {
                throw new IllegalStateException("NVML returned a null GPU handle");
            }
        }
        catch (RuntimeException e) {
            nvml.nvmlShutdown();
            throw e;
        }
    }

    @Override
    public synchronized GpuTelemetrySnapshot sample() {
        if (closed) {
            return GpuTelemetrySnapshot.unavailable();
        }
        try {
            NvmlLibrary.NvmlUtilization utilization = new NvmlLibrary.NvmlUtilization();
            NvmlLibrary.NvmlMemory memory = new NvmlLibrary.NvmlMemory();
            IntByReference temperature = new IntByReference();

            check(nvml.nvmlDeviceGetUtilizationRates(device, utilization), "nvmlDeviceGetUtilizationRates");
            utilization.read();
            check(nvml.nvmlDeviceGetMemoryInfo(device, memory), "nvmlDeviceGetMemoryInfo");
            memory.read();
            check(nvml.nvmlDeviceGetTemperature(device, NvmlLibrary.NVML_TEMPERATURE_GPU, temperature), "nvmlDeviceGetTemperature");
            return new GpuTelemetrySnapshot(true, utilization.gpu, memory.used / BYTES_PER_GB,
                    memory.total / BYTES_PER_GB, temperature.getValue());
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("NVML telemetry unavailable: " + e.getMessage());
        }
        return GpuTelemetrySnapshot.unavailable();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        int result = nvml.nvmlShutdown();
        if (result != NvmlLibrary.NVML_SUCCESS) {
            System.err.println("nvmlShutdown failed: " + nvml.nvmlErrorString(result));
        }
    }

    private void check(int result, String operation) {
        if (result == NvmlLibrary.NVML_SUCCESS) {
            return;
        }
        throw new IllegalStateException(operation + " failed: " + nvml.nvmlErrorString(result) + " [" + result + "]");
    }
}
