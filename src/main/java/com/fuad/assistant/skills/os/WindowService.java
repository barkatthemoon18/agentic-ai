package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Set;

public interface WindowService {
    List<WindowHandle> visibleWindows(Set<Long> processIds);
    CloseResult close(List<WindowHandle> windows, IdentityGuard identityGuard);
    FocusResult focus(WindowHandle window, IdentityGuard identityGuard);

    default boolean isCurrent(WindowHandle window) {
        return visibleWindows(Set.of(window.processId())).contains(window);
    }

    default boolean exists(WindowHandle window) {
        return visibleWindows(Set.of(window.processId())).stream()
                .anyMatch(current -> current.nativeHandle() == window.nativeHandle()
                        && current.processId() == window.processId());
    }

    record WindowHandle(long nativeHandle, long processId, String title, String className) {
        public WindowHandle(long nativeHandle, long processId) {
            this(nativeHandle, processId, "", "");
        }

        public WindowHandle {
            title = title == null ? "" : title;
            className = className == null ? "" : className;
        }
    }
    enum CloseResult { SENT, REJECTED, IDENTITY_CHANGED }
    enum FocusResult { FOCUSED, REJECTED, IDENTITY_CHANGED }

    @FunctionalInterface
    interface IdentityGuard {
        boolean isValid();
    }
}
