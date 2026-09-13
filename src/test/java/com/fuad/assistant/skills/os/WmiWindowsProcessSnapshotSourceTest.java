package com.fuad.assistant.skills.os;

import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class WmiWindowsProcessSnapshotSourceTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void shouldObserveTheCurrentProcessWithStableWmiInstanceIdentity() {
        ProcessSnapshotLookup lookup = new WmiWindowsProcessSnapshotSource()
                .snapshot(ProcessHandle.current().pid());

        assertEquals(ProcessSnapshotLookup.Status.FOUND, lookup.status(), lookup.detail());
        assertTrue(lookup.snapshot().creationTime().isPresent());
        assertTrue(lookup.snapshot().executablePath().isPresent());
    }

    @Test
    void shouldParseCimCreationTimeWithMicrosecondsAndOffset() {
        assertEquals(Instant.parse("2026-09-12T07:35:19.123456Z"),
                WmiWindowsProcessSnapshotSource.parseCreationTime(
                        "20260912043519.123456-180").orElseThrow());
        assertTrue(WmiWindowsProcessSnapshotSource.parseCreationTime(
                "20260912043519.******-180").isEmpty());
    }

    @Test
    void successfulEmptyLookupShouldBeNotFound() {
        WmiWindowsProcessSnapshotSource source = new WmiWindowsProcessSnapshotSource(pid -> List.of());

        assertEquals(ProcessSnapshotLookup.Status.NOT_FOUND, source.snapshot(42).status());
        assertEquals(ProcessSnapshotBatch.Status.COMPLETE, source.snapshotAll().status());
    }

    @Test
    void globalSnapshotShouldIgnoreTheSystemIdleProcess() {
        WmiWindowsProcessSnapshotSource source = new WmiWindowsProcessSnapshotSource(pid -> List.of(
                new WmiWindowsProcessSnapshotSource.WmiProcessRow(0, null, null,
                        "System Idle Process", null)));

        ProcessSnapshotBatch batch = source.snapshotAll();

        assertEquals(ProcessSnapshotBatch.Status.COMPLETE, batch.status());
        assertTrue(batch.snapshots().isEmpty());
    }

    @Test
    void timeoutShouldNeverBecomeNotFoundOrACompleteEmptyBatch() {
        WmiWindowsProcessSnapshotSource source = new WmiWindowsProcessSnapshotSource(pid -> {
            throw new TimeoutException("slow WMI");
        });

        ProcessSnapshotLookup lookup = source.snapshot(42);
        ProcessSnapshotBatch batch = source.snapshotAll();

        assertEquals(ProcessSnapshotLookup.Status.OBSERVATION_FAILED, lookup.status());
        assertEquals(ProcessObservationFailure.TIMEOUT, lookup.failure());
        assertEquals(ProcessSnapshotBatch.Status.OBSERVATION_FAILED, batch.status());
        assertEquals(ProcessObservationFailure.TIMEOUT, batch.failure());
    }

    @Test
    void accessDeniedShouldRemainAnObservationFailure() {
        WmiWindowsProcessSnapshotSource source = new WmiWindowsProcessSnapshotSource(pid -> {
            throw new COMException("denied", new HRESULT(WinError.E_ACCESSDENIED));
        });

        ProcessSnapshotLookup lookup = source.snapshot(42);

        assertEquals(ProcessSnapshotLookup.Status.OBSERVATION_FAILED, lookup.status());
        assertEquals(ProcessObservationFailure.ACCESS_DENIED, lookup.failure());
    }

    @Test
    void shouldCreateSnapshotEntirelyFromTheWmiRow() {
        WmiWindowsProcessSnapshotSource.WmiProcessRow row =
                new WmiWindowsProcessSnapshotSource.WmiProcessRow(42,
                        7,
                        "20260912043519.123456-180", "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
                        "firefox.exe", "\"C:\\Program Files\\Mozilla Firefox\\firefox.exe\" -new-window https://www.primevideo.com");
        WmiWindowsProcessSnapshotSource source = new WmiWindowsProcessSnapshotSource(pid -> List.of(row));

        WindowsProcessSnapshot snapshot = source.snapshot(42).snapshot();

        assertEquals(42, snapshot.pid());
        assertEquals(7, snapshot.parentProcessId());
        assertEquals(Instant.parse("2026-09-12T07:35:19.123456Z"), snapshot.creationTime().orElseThrow());
        assertEquals("firefox.exe", snapshot.executableName());
        assertEquals(List.of("-new-window", "https://www.primevideo.com"), snapshot.arguments());
        assertTrue(snapshot.argumentsAvailable());
    }
}
