package com.fuad.telemetry.host.oshi;

import com.fuad.telemetry.host.HostTelemetryProvider;
import com.fuad.telemetry.host.HostTelemetrySnapshot;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.util.Comparator;
import java.util.List;

public class OshiHostTelemetryProvider implements HostTelemetryProvider {
    public static final double BITS_PER_GB = Math.pow(1024.0, 3);
    public static final double BITS_PER_MEGABIT = 1_000_000.0;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final NetworkIF network;
    private long[] previousCpuTicks;
    private long previousRxBytes;
    private long previousTxBytes;
    private long previousNetworkSampleNanos;

    public OshiHostTelemetryProvider() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
        processor = hardwareAbstractionLayer.getProcessor();
        memory = hardwareAbstractionLayer.getMemory();
        network = resolveNetworkInterface(hardwareAbstractionLayer.getNetworkIFs());
        previousCpuTicks = processor.getSystemCpuLoadTicks();
        if (network != null) {
            network.updateAttributes();
            previousRxBytes = network.getBytesRecv();
            previousTxBytes = network.getBytesSent();
            previousNetworkSampleNanos = System.nanoTime();
        }
    }

    @Override
    public synchronized HostTelemetrySnapshot sample() {
        double cpuUsage = sampleCpuUsage();
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        double ramTotalGb = totalMemory / BITS_PER_GB;
        double ramUsedGb = (totalMemory - availableMemory) / BITS_PER_GB;
        NetworkSample networkSample = sampleNetwork();
        return new HostTelemetrySnapshot(cpuUsage, ramUsedGb, ramTotalGb, networkSample.localIp(),
                networkSample.downloadMbps(), networkSample.uploadMbps());
    }

    @Override
    public synchronized void close() throws Exception {
        HostTelemetryProvider.super.close();
    }

    private double sampleCpuUsage() {
        try {
            double load = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks);
            previousCpuTicks = processor.getSystemCpuLoadTicks();
            return Math.clamp(load * 100.0, 0.0, 100.0);
        }
        catch (IllegalArgumentException e) {
            previousCpuTicks = processor.getSystemCpuLoadTicks();
            return 0.0;
        }
    }

    private NetworkSample sampleNetwork() {
        if (network == null) {
            return NetworkSample.unavailable();
        }
        network.updateAttributes();
        long now = System.nanoTime();
        long currentRx = network.getBytesRecv();
        long currentTx = network.getBytesSent();
        double seconds = (now - previousNetworkSampleNanos) / 1_000_000_000.0;
        double downloadMbps = 0.0;
        double uploadMbps = 0.0;

        if (seconds > 0.0) {
            downloadMbps = Math.max(0L, currentRx - previousRxBytes) * 8.0 / seconds / BITS_PER_MEGABIT;
            uploadMbps = Math.max(0L, currentTx - previousTxBytes) * 8.0 / seconds / BITS_PER_MEGABIT;
        }
        previousRxBytes = currentRx;
        previousTxBytes = currentTx;
        previousNetworkSampleNanos = now;
        String localIp = network.getIPv4addr().length > 0 ? network.getIPv4addr()[0] : "-";
        return new NetworkSample(localIp, downloadMbps, uploadMbps);
    }


    private static NetworkIF resolveNetworkInterface(List<NetworkIF> interfaces) {
        return interfaces.stream().filter(network -> network.getIPv4addr().length > 0)
                .filter(network -> !network.isKnownVmMacAddr())
                .max(Comparator.comparingLong(network -> network.getBytesRecv() + network.getBytesSent()))
                .orElse(null);
    }

    private record NetworkSample(String localIp, double downloadMbps, double uploadMbps) {
        static NetworkSample unavailable() {
            return new NetworkSample("-", 0.0, 0.0);
        }
    }
}
