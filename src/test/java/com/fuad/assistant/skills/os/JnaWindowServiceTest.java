package com.fuad.assistant.skills.os;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class JnaWindowServiceTest {
    @Test
    void shouldRevalidateAfterRestoreAndImmediatelyBeforeSetForegroundWindow() {
        User32 user32 = mock(User32.class);
        JnaWindowService.User32Extra extra = mock(JnaWindowService.User32Extra.class);
        prepareOwnedWindow(user32, 10);
        when(extra.IsIconic(any())).thenReturn(true);
        AtomicInteger validations = new AtomicInteger();
        JnaWindowService service = new JnaWindowService(user32, extra);

        WindowService.FocusResult result = service.focus(new WindowService.WindowHandle(100, 10),
                () -> validations.getAndIncrement() == 0);

        assertEquals(WindowService.FocusResult.IDENTITY_CHANGED, result);
        verify(user32).ShowWindow(any(), anyInt());
        verify(user32, never()).SetForegroundWindow(any());
    }

    @Test
    void shouldNotPostWmCloseWhenBoundaryValidationFails() {
        User32 user32 = mock(User32.class);
        JnaWindowService.User32Extra extra = mock(JnaWindowService.User32Extra.class);
        prepareOwnedWindow(user32, 10);
        JnaWindowService service = new JnaWindowService(user32, extra);

        WindowService.CloseResult result = service.close(
                List.of(new WindowService.WindowHandle(100, 10)), () -> false);

        assertEquals(WindowService.CloseResult.IDENTITY_CHANGED, result);
        verify(user32, never()).PostMessage(any(), anyInt(), any(), any());
    }

    private void prepareOwnedWindow(User32 user32, int processId) {
        when(user32.IsWindow(any())).thenReturn(true);
        doAnswer(invocation -> {
            IntByReference reference = invocation.getArgument(1);
            reference.setValue(processId);
            return 1;
        }).when(user32).GetWindowThreadProcessId(any(WinDef.HWND.class), any(IntByReference.class));
    }
}
