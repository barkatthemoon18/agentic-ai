package com.fuad.assistant.skills.os;

import java.util.List;

record ProcessSnapshotBatch(Status status, List<WindowsProcessSnapshot> snapshots,
                            ProcessObservationFailure failure, String detail) {
    enum Status { COMPLETE, OBSERVATION_FAILED }

    ProcessSnapshotBatch {
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    }

    static ProcessSnapshotBatch complete(List<WindowsProcessSnapshot> snapshots) {
        return new ProcessSnapshotBatch(Status.COMPLETE, snapshots, null, null);
    }

    static ProcessSnapshotBatch failed(ProcessObservationFailure failure, String detail) {
        return new ProcessSnapshotBatch(Status.OBSERVATION_FAILED, List.of(), failure, detail);
    }
}
