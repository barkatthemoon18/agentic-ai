package com.fuad.assistant.skills.os;

import com.sun.jna.Pointer;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JnaWindowService implements WindowService {
    private final User32 user32;
    private final User32Extra user32Extra;

    public JnaWindowService() {
        this(User32.INSTANCE, User32Extra.INSTANCE);
    }

    JnaWindowService(User32 user32, User32Extra user32Extra) {
        this.user32 = user32;
        this.user32Extra = user32Extra;
    }

    @Override
    public List<WindowHandle> visibleWindows(Set<Long> processIds) {
        List<WindowHandle> windows = new ArrayList<>();
        user32.EnumWindows((window, data) -> {
            if (!user32.IsWindowVisible(window)) return true;
            if (user32.GetWindowTextLength(window) == 0) return true;
            IntByReference processId = new IntByReference();
            user32.GetWindowThreadProcessId(window, processId);
            long pid = Integer.toUnsignedLong(processId.getValue());
            if (processIds.contains(pid)) {
                char[] title = new char[user32.GetWindowTextLength(window) + 1];
                user32.GetWindowText(window, title, title.length);
                char[] className = new char[256];
                user32.GetClassName(window, className, className.length);
                windows.add(new WindowHandle(Pointer.nativeValue(window.getPointer()), pid,
                        Native.toString(title), Native.toString(className)));
            }
            return true;
        }, null);
        return List.copyOf(windows);
    }

    @Override
    public CloseResult close(List<WindowHandle> windows, IdentityGuard identityGuard) {
        if (windows.isEmpty()) return CloseResult.SENT;
        if (windows.stream().anyMatch(window -> !isCurrent(window)) || !identityGuard.isValid()) {
            return CloseResult.IDENTITY_CHANGED;
        }
        for (WindowHandle window : windows) {
            user32.PostMessage(toNative(window), WinUser.WM_CLOSE, null, null);
        }
        return CloseResult.SENT;
    }

    @Override
    public FocusResult focus(WindowHandle window, IdentityGuard identityGuard) {
        WinDef.HWND nativeWindow = toNative(window);
        if (!isCurrent(window)) return FocusResult.IDENTITY_CHANGED;
        if (user32Extra.IsIconic(nativeWindow)) {
            if (!isCurrent(window) || !identityGuard.isValid()) return FocusResult.IDENTITY_CHANGED;
            user32.ShowWindow(nativeWindow, WinUser.SW_RESTORE);
        }
        if (!isCurrent(window) || !identityGuard.isValid()) return FocusResult.IDENTITY_CHANGED;
        user32.SetForegroundWindow(nativeWindow);
        long deadline = System.nanoTime() + 500_000_000L;
        do {
            WinDef.HWND foreground = user32.GetForegroundWindow();
            if (foreground != null && Pointer.nativeValue(foreground.getPointer()) == window.nativeHandle()) {
                return FocusResult.FOCUSED;
            }
            try {
                Thread.sleep(25);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FocusResult.REJECTED;
            }
        }
        while (System.nanoTime() < deadline);
        return FocusResult.REJECTED;
    }

    @Override
    public boolean isCurrent(WindowHandle window) {
        WinDef.HWND nativeWindow = toNative(window);
        if (!exists(window)) return false;
        IntByReference processId = new IntByReference();
        user32.GetWindowThreadProcessId(nativeWindow, processId);
        if (Integer.toUnsignedLong(processId.getValue()) != window.processId()) return false;
        char[] title = new char[Math.max(1, user32.GetWindowTextLength(nativeWindow) + 1)];
        user32.GetWindowText(nativeWindow, title, title.length);
        char[] className = new char[256];
        user32.GetClassName(nativeWindow, className, className.length);
        return window.title().equals(Native.toString(title))
                && window.className().equals(Native.toString(className));
    }

    @Override
    public boolean exists(WindowHandle window) {
        WinDef.HWND nativeWindow = toNative(window);
        if (!user32.IsWindow(nativeWindow)) return false;
        IntByReference processId = new IntByReference();
        user32.GetWindowThreadProcessId(nativeWindow, processId);
        return Integer.toUnsignedLong(processId.getValue()) == window.processId();
    }

    private WinDef.HWND toNative(WindowHandle window) {
        return new WinDef.HWND(Pointer.createConstant(window.nativeHandle()));
    }

    interface User32Extra extends StdCallLibrary {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean IsIconic(WinDef.HWND window);
    }
}
