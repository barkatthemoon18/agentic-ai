package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Set;

public interface WindowService {
    List<WindowHandle> visibleWindows(Set<Long> processIds);
    boolean close(WindowHandle window);
    FocusResult focus(WindowHandle window);

    record WindowHandle(long nativeHandle, long processId) { }
    enum FocusResult { FOCUSED, REJECTED }
}
