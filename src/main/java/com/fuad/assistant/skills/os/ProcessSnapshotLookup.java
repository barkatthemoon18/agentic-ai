package com.fuad.assistant.skills.os;

record ProcessSnapshotLookup(Status status, WindowsProcessSnapshot snapshot,
                             ProcessObservationFailure failure, String detail) {
    enum Status { FOUND, NOT_FOUND, OBSERVATION_FAILED }

    static ProcessSnapshotLookup found(WindowsProcessSnapshot snapshot) {
        return new ProcessSnapshotLookup(Status.FOUND, snapshot, null, null);
    }

    static ProcessSnapshotLookup notFound() {
        return new ProcessSnapshotLookup(Status.NOT_FOUND, null, null, null);
    }

    static ProcessSnapshotLookup failed(ProcessObservationFailure failure, String detail) {
        return new ProcessSnapshotLookup(Status.OBSERVATION_FAILED, null, failure, detail);
    }
}
