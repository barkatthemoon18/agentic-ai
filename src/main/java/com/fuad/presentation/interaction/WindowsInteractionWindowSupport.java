package com.fuad.presentation.interaction;

import com.fuad.interaction.FocusRequirement;
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

final class WindowsInteractionWindowSupport {
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_NOACTIVATE = 0x08000000;

    private final NativeApi nativeApi;
    private final Consumer<String> diagnostics;

    static WindowsInteractionWindowSupport platformDefault() {
        Consumer<String> diagnostics = message ->
                System.err.println("Interaction window: " + message);
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return new WindowsInteractionWindowSupport(null, diagnostics);
        }
        try {
            return new WindowsInteractionWindowSupport(new JnaNativeApi(), diagnostics);
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("Win32 integration unavailable: " + safeMessage(e));
            return new WindowsInteractionWindowSupport(null, diagnostics);
        }
    }

    WindowsInteractionWindowSupport(NativeApi nativeApi, Consumer<String> diagnostics) {
        this.nativeApi = nativeApi;
        this.diagnostics = Objects.requireNonNull(diagnostics);
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

    WindowConfiguration configureAfterShow(String title, long processId,
                                            FocusRequirement focusRequirement,
                                            long previousForegroundWindow) {
        if (nativeApi == null) {
            return WindowConfiguration.unavailable();
        }
        NativeWindow window;
        try {
            window = findWindow(title, processId);
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("unable to locate interaction window: " + safeMessage(e));
            return WindowConfiguration.unavailable();
        }
        if (window == null) {
            diagnostics.accept("unable to locate interaction window");
            return WindowConfiguration.notLocated();
        }

        StyleUpdate update;
        try {
            update = setNoActivate(window.handle(),
                    focusRequirement == FocusRequirement.PASSIVE);
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("unable to configure interaction window: " + safeMessage(e));
            update = StyleUpdate.FAILED;
        }
        boolean passiveFallback = focusRequirement == FocusRequirement.PASSIVE
                && update == StyleUpdate.FAILED;
        boolean accidentallyForeground = false;
        try {
            accidentallyForeground = focusRequirement == FocusRequirement.PASSIVE
                    && isForeground(window.handle());
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("unable to inspect foreground window: " + safeMessage(e));
        }
        if (passiveFallback || accidentallyForeground) {
            restoreForegroundWindow(previousForegroundWindow, window.handle());
        }
        return new WindowConfiguration(window.handle(),
                focusRequirement == FocusRequirement.REQUIRED || passiveFallback,
                false);
    }

    void restoreForegroundWindow(long previousForegroundWindow,
                                 long interactionWindow) {
        if (nativeApi == null || previousForegroundWindow == 0
                || interactionWindow == 0) {
            return;
        }
        try {
            if (!isForeground(interactionWindow)) {
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

    private NativeWindow findWindow(String title, long processId) {
        return nativeApi.topLevelWindows().stream()
                .filter(window -> window.processId() == processId)
                .filter(window -> title.equals(window.title()))
                .findFirst().orElse(null);
    }

    private boolean isForeground(long handle) {
        return nativeApi.foregroundWindow() == handle;
    }

    private StyleUpdate setNoActivate(long handle, boolean enabled) {
        nativeApi.clearLastError();
        int existing = nativeApi.getWindowLong(handle, GWL_EXSTYLE);
        int readError = nativeApi.lastError();
        if (existing == 0 && readError != 0) {
            diagnostics.accept("GetWindowLong failed with error " + readError);
            return StyleUpdate.FAILED;
        }
        int updated = enabled ? existing | WS_EX_NOACTIVATE
                : existing & ~WS_EX_NOACTIVATE;
        if (updated == existing) {
            return StyleUpdate.UNCHANGED;
        }
        nativeApi.clearLastError();
        int previous = nativeApi.setWindowLong(handle, GWL_EXSTYLE, updated);
        int writeError = nativeApi.lastError();
        if (previous == 0 && writeError != 0) {
            diagnostics.accept("SetWindowLong failed with error " + writeError);
            return StyleUpdate.FAILED;
        }
        return StyleUpdate.APPLIED;
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
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

    record WindowConfiguration(long interactionWindow,
                               boolean restoreOnDismiss,
                               boolean retrySuggested) {
        static WindowConfiguration unavailable() {
            return new WindowConfiguration(0, false, false);
        }

        static WindowConfiguration notLocated() {
            return new WindowConfiguration(0, false, true);
        }
    }

    private enum StyleUpdate {
        APPLIED,
        UNCHANGED,
        FAILED
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
