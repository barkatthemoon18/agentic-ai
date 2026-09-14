package com.fuad.presentation;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

final class WindowsOverlayOwnerSupport {
    static final int GWL_EXSTYLE = -20;
    static final int WS_EX_NOACTIVATE = 0x08000000;

    private final NativeApi nativeApi;
    private final Consumer<String> diagnostics;

    static WindowsOverlayOwnerSupport platformDefault() {
        Consumer<String> diagnostics = message ->
                System.err.println("JavaFX overlay owner: " + message);
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return new WindowsOverlayOwnerSupport(null, diagnostics);
        }
        try {
            return new WindowsOverlayOwnerSupport(new JnaNativeApi(), diagnostics);
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("Win32 integration unavailable: " + safeMessage(e));
            return new WindowsOverlayOwnerSupport(null, diagnostics);
        }
    }

    WindowsOverlayOwnerSupport(NativeApi nativeApi, Consumer<String> diagnostics) {
        this.nativeApi = nativeApi;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    long captureForegroundWindow() {
        if (nativeApi == null) {
            return 0;
        }
        try {
            return nativeApi.foregroundWindow();
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("unable to capture foreground window: " + safeMessage(e));
            return 0;
        }
    }

    void configureAfterShow(String ownerTitle, long processId, long previousForegroundWindow) {
        Objects.requireNonNull(ownerTitle, "ownerTitle must not be null");
        if (nativeApi == null) {
            return;
        }
        try {
            NativeWindow owner = nativeApi.topLevelWindows().stream()
                    .filter(window -> window.processId() == processId)
                    .filter(window -> ownerTitle.equals(window.title()))
                    .findFirst()
                    .orElse(null);
            if (owner == null) {
                diagnostics.accept("unable to locate the native owner window");
            }
            else {
                addNoActivate(owner.handle());
            }
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("unable to configure the native owner window: " + safeMessage(e));
        }
        finally {
            restoreForegroundWindow(previousForegroundWindow);
        }
    }

    private void addNoActivate(long handle) {
        Integer existingStyle = readExtendedStyle(handle, "read");
        if (existingStyle == null) {
            return;
        }
        int updatedStyle = existingStyle | WS_EX_NOACTIVATE;

        nativeApi.clearLastError();
        int previousStyle = nativeApi.setWindowLong(handle, GWL_EXSTYLE, updatedStyle);
        int writeError = nativeApi.lastError();
        if (previousStyle == 0 && writeError != 0) {
            diagnostics.accept("SetWindowLong(GWL_EXSTYLE) failed with error " + writeError);
            return;
        }

        Integer confirmedStyle = readExtendedStyle(handle, "verify");
        if (confirmedStyle != null && (confirmedStyle & WS_EX_NOACTIVATE) == 0) {
            diagnostics.accept("WS_EX_NOACTIVATE was not present after SetWindowLong");
        }
    }

    private Integer readExtendedStyle(long handle, String operation) {
        nativeApi.clearLastError();
        int style = nativeApi.getWindowLong(handle, GWL_EXSTYLE);
        int error = nativeApi.lastError();
        if (style == 0 && error != 0) {
            diagnostics.accept("GetWindowLong(GWL_EXSTYLE) " + operation
                    + " failed with error " + error);
            return null;
        }
        return style;
    }

    private void restoreForegroundWindow(long previousForegroundWindow) {
        if (previousForegroundWindow == 0) {
            return;
        }
        try {
            if (nativeApi.foregroundWindow() == previousForegroundWindow) {
                return;
            }
            if (!nativeApi.setForegroundWindow(previousForegroundWindow)) {
                diagnostics.accept("Windows rejected foreground-window restoration");
            }
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("unable to restore foreground window: " + safeMessage(e));
        }
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    interface NativeApi {
        long foregroundWindow();

        List<NativeWindow> topLevelWindows();

        int getWindowLong(long handle, int index);

        int setWindowLong(long handle, int index, int value);

        boolean setForegroundWindow(long handle);

        void clearLastError();

        int lastError();
    }

    record NativeWindow(long handle, long processId, String title) {
    }

    private static final class JnaNativeApi implements NativeApi {
        private final User32 user32 = User32.INSTANCE;

        @Override
        public long foregroundWindow() {
            return nativeHandle(user32.GetForegroundWindow());
        }

        @Override
        public List<NativeWindow> topLevelWindows() {
            List<NativeWindow> windows = new ArrayList<>();
            user32.EnumWindows((window, data) -> {
                IntByReference processId = new IntByReference();
                user32.GetWindowThreadProcessId(window, processId);
                int titleLength = user32.GetWindowTextLength(window);
                char[] title = new char[Math.max(1, titleLength + 1)];
                user32.GetWindowText(window, title, title.length);
                windows.add(new NativeWindow(nativeHandle(window),
                        Integer.toUnsignedLong(processId.getValue()), Native.toString(title)));
                return true;
            }, null);
            return List.copyOf(windows);
        }

        @Override
        public int getWindowLong(long handle, int index) {
            return user32.GetWindowLong(toNative(handle), index);
        }

        @Override
        public int setWindowLong(long handle, int index, int value) {
            return user32.SetWindowLong(toNative(handle), index, value);
        }

        @Override
        public boolean setForegroundWindow(long handle) {
            return user32.SetForegroundWindow(toNative(handle));
        }

        @Override
        public void clearLastError() {
            Native.setLastError(0);
        }

        @Override
        public int lastError() {
            return Native.getLastError();
        }

        private static WinDef.HWND toNative(long handle) {
            return new WinDef.HWND(Pointer.createConstant(handle));
        }

        private static long nativeHandle(WinDef.HWND window) {
            return window == null ? 0 : Pointer.nativeValue(window.getPointer());
        }
    }
}
