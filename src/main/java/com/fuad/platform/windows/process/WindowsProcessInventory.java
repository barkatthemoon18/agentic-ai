package com.fuad.platform.windows.process;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.*;

import java.util.ArrayList;
import java.util.List;

public class WindowsProcessInventory implements ProcessInventory{
    @Override
    public ProcessInventorySnapshot snapshot() {
        if (!Platform.isWindows()) {
            return ProcessInventorySnapshot.incomplete(List.of());
        }
        try {
            return enumerate();
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("WIN32 PROCESS -> enumeration unavailable");
        }
        return ProcessInventorySnapshot.incomplete(List.of());
    }

    private static ProcessInventorySnapshot enumerate() {
        Kernel32 kernel = Kernel32.INSTANCE;
        WinNT.HANDLE handle = kernel.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));

        if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            return ProcessInventorySnapshot.incomplete(List.of());
        }
        try {
            return readProcess(kernel, handle);
        }
        finally {
            if (!kernel.CloseHandle(handle)) {
                System.err.println("WIN32 PROCESS -> unable to close snapshot handle");
            }
        }
    }

    private static ProcessInventorySnapshot readProcess(Kernel32 kernel, WinNT.HANDLE handle) {
        String executable;
        long pid;
        int error;

        List<ProcessInventorySnapshot.ProcessEntry> entries = new ArrayList<>();
        Tlhelp32.PROCESSENTRY32 entry = new Tlhelp32.PROCESSENTRY32();
        if (!kernel.Process32First(handle, entry)) {
            return ProcessInventorySnapshot.incomplete(entries);
        }
        do {
            executable = Native.toString(entry.szExeFile);
            if (!executable.isBlank()) {
                pid = Integer.toUnsignedLong(entry.th32ProcessID.intValue());
                entries.add(new ProcessInventorySnapshot.ProcessEntry(pid, executable));
            }
        } while (kernel.Process32Next(handle, entry));
        error = kernel.GetLastError();
        if (error == WinError.ERROR_NO_MORE_FILES) {
            return ProcessInventorySnapshot.completed(entries);
        }
        return ProcessInventorySnapshot.incomplete(entries);
    }
}
