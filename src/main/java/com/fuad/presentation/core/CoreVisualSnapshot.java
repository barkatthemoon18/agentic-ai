package com.fuad.presentation.core;


public record CoreVisualSnapshot(
        AssistantVisualState assistantVisualState,
        SystemSnapshot systemSnapshot,
        GpuSnapshot gpuSnapshot,
        NetworkSnapshot networkSnapshot,
        RuntimeSnapshot runtimeSnapshot) {

    public record SystemSnapshot(
            double cpuUsage,
            double ramUsedGb,
            double ramTotalGb) {
        /* Empty intentionally */
    }

    public record GpuSnapshot(
            double usage,
            double vramUsedGb,
            double vramTotalGb,
            double temperature) {
        /* Empty intentionally */
    }

    public record NetworkSnapshot(
            String localIp,
            double downloadMbps,
            double uploadMbps) {
        /* Empty intentionally */
    }

    public record RuntimeSnapshot(
        String phiState,
        String qwenState,
        String sttState,
        String ttsState) {
        /* Empty intentionally */
    }
}

