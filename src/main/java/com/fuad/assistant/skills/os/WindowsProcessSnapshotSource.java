package com.fuad.assistant.skills.os;

interface WindowsProcessSnapshotSource {
    ProcessSnapshotBatch snapshotAll();
    ProcessSnapshotLookup snapshot(long pid);
}
