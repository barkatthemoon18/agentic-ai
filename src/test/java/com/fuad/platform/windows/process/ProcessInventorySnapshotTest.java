package com.fuad.platform.windows.process;

import com.fuad.platform.windows.process.ProcessInventorySnapshot.ProcessEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessInventorySnapshotTest {

    @Test
    void processEntryShouldValidateArguments() {
        assertThrows(NullPointerException.class, () -> new ProcessEntry(1L, null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessEntry(-1L, "app.exe"));
        assertThrows(IllegalArgumentException.class, () -> new ProcessEntry(1L, ""));
        assertThrows(IllegalArgumentException.class, () -> new ProcessEntry(1L, "   "));

        ProcessEntry entry = new ProcessEntry(42L, "  test.exe  ");
        assertEquals(42L, entry.pid());
        assertEquals("test.exe", entry.executableName());
    }

    @Test
    void snapshotShouldBeImmutable() {
        List<ProcessEntry> entries = new ArrayList<>();
        entries.add(new ProcessEntry(1L, "explorer.exe"));

        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.completed(entries);
        assertTrue(snapshot.complete());
        assertEquals(1, snapshot.processes().size());

        entries.add(new ProcessEntry(2L, "notepad.exe"));
        assertEquals(1, snapshot.processes().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.processes().add(new ProcessEntry(3L, "cmd.exe")));
    }

    @Test
    void incompleteFactoryShouldSetCompleteFalse() {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.incomplete(List.of());
        assertFalse(snapshot.complete());
        assertTrue(snapshot.processes().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TIDAL.exe", "tidal.exe", "TiDaL.ExE", "  tidal.exe  "})
    void containsExecutableShouldMatchCaseInsensitivelyAndTrim(String search) {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.completed(List.of(
                new ProcessEntry(100L, "TIDAL.exe"),
                new ProcessEntry(101L, "chrome.exe")
        ));

        assertTrue(snapshot.containsExecutable(search));
        assertFalse(snapshot.containsExecutable("spotify.exe"));
    }

    @Test
    void containsExecutableShouldValidateInput() {
        ProcessInventorySnapshot snapshot = ProcessInventorySnapshot.completed(List.of());

        assertThrows(NullPointerException.class, () -> snapshot.containsExecutable(null));
        assertThrows(IllegalArgumentException.class, () -> snapshot.containsExecutable(""));
        assertThrows(IllegalArgumentException.class, () -> snapshot.containsExecutable("   "));
    }
}
