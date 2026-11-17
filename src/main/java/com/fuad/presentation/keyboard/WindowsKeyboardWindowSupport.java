package com.fuad.presentation.keyboard;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WindowsKeyboardWindowSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowsKeyboardWindowSupport.class);
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    static final String WINDOW_TITLE = "ARES // INPUT SURFACE [VISUAL V1]";

    private WindowsKeyboardWindowSupport() {
        /* Empty intentionally */
    }

    static long captureForegroundWindow() {
        if (!isWindows()) {
            return 0;
        }
        try {
            HWND window = User32.INSTANCE.GetForegroundWindow();
            return window == null ? 0 : Pointer.nativeValue(window.getPointer());
        }
        catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Unable to capture foreground window", e);
            return 0;
        }
    }

    static void tryConfigureNonActivating(long previousForegroundWindow) {
        if (!isWindows()) {
            return;
        }
        try {
            User32 user32 = User32.INSTANCE;
            HWND[] matched = new HWND[1];
            long processId = ProcessHandle.current().pid();

            user32.EnumWindows((window, data) -> {
                IntByReference windowPid = new IntByReference();
                user32.GetWindowThreadProcessId(window, windowPid);
                if (Integer.toUnsignedLong(windowPid.getValue()) != processId) {
                    return true;
                }
                int length = user32.GetWindowTextLength(window);
                char[] title = new char[Math.max(1, length + 1)];
                user32.GetWindowText(window, title, title.length);
                if (WINDOW_TITLE.equals(Native.toString(title))) {
                    matched[0] = window;
                    return false;
                }
                return true;
            }, null);
            if (matched[0] == null) {
                LOGGER.warn("ARES keyboard HWND not found");
                return;
            }
            int current = user32.GetWindowLong(matched[0], GWL_EXSTYLE);
            int desired = current | WS_EX_NOACTIVATE;
            if (current != desired) {
                Native.setLastError(0);
                int previous = user32.SetWindowLong(matched[0], GWL_EXSTYLE, desired);
                if (previous == 0 && Native.getLastError() != 0) {
                    LOGGER.warn("Unable to configure non-activate style: {}", Native.getLastError());
                }
            }
            if (previousForegroundWindow != 0) {
                HWND currentForeground = user32.GetForegroundWindow();
                if (currentForeground != null && currentForeground.equals(matched[0])) {
                    HWND previous = new HWND(new Pointer(previousForegroundWindow));
                    if (user32.SetForegroundWindow(previous)) {
                        LOGGER.warn("Windows rejected foreground restoration");
                    }
                }
            }
        }
        catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Unable to configure keyboard window", e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }
}
