package com.fuad.presentation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsOverlayOwnerSupportTest {
    @Test
    void shouldPreserveExistingStylesAndAddNoActivate() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.windows = List.of(new WindowsOverlayOwnerSupport.NativeWindow(100, 25, "owner"));
        nativeApi.extendedStyle = 0x00080080;
        List<String> diagnostics = new ArrayList<>();
        WindowsOverlayOwnerSupport support = new WindowsOverlayOwnerSupport(nativeApi, diagnostics::add);

        support.configureAfterShow("owner", 25, 0);

        assertEquals(0x08080080, nativeApi.writtenStyle);
        assertEquals(WindowsOverlayOwnerSupport.GWL_EXSTYLE, nativeApi.writtenIndex);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void zeroReturnWithNoLastErrorShouldBeTreatedAsSuccess() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.windows = List.of(new WindowsOverlayOwnerSupport.NativeWindow(100, 25, "owner"));
        nativeApi.extendedStyle = 0;
        nativeApi.setWindowLongReturn = 0;
        List<String> diagnostics = new ArrayList<>();
        WindowsOverlayOwnerSupport support = new WindowsOverlayOwnerSupport(nativeApi, diagnostics::add);

        support.configureAfterShow("owner", 25, 0);

        assertEquals(WindowsOverlayOwnerSupport.WS_EX_NOACTIVATE, nativeApi.writtenStyle);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nativeStyleFailureShouldRemainNonFatalAndStillRestoreFocus() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.windows = List.of(new WindowsOverlayOwnerSupport.NativeWindow(100, 25, "owner"));
        nativeApi.foregroundWindow = 200;
        nativeApi.extendedStyle = 0;
        nativeApi.getWindowLongError = 5;
        List<String> diagnostics = new ArrayList<>();
        WindowsOverlayOwnerSupport support = new WindowsOverlayOwnerSupport(nativeApi, diagnostics::add);

        support.configureAfterShow("owner", 25, 150);

        assertEquals(150, nativeApi.restoredForegroundWindow);
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("error 5")));
    }

    @Test
    void setWindowLongFailureShouldRemainNonFatal() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.windows = List.of(new WindowsOverlayOwnerSupport.NativeWindow(100, 25, "owner"));
        nativeApi.extendedStyle = 0x80;
        nativeApi.setWindowLongReturn = 0;
        nativeApi.setWindowLongError = 5;
        List<String> diagnostics = new ArrayList<>();
        WindowsOverlayOwnerSupport support = new WindowsOverlayOwnerSupport(nativeApi, diagnostics::add);

        support.configureAfterShow("owner", 25, 0);

        assertTrue(diagnostics.stream().anyMatch(message ->
                message.contains("SetWindowLong") && message.contains("error 5")));
    }

    @Test
    void missingVerificationBitAndRejectedFocusRestoreShouldOnlyReportDiagnostics() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.windows = List.of(new WindowsOverlayOwnerSupport.NativeWindow(100, 25, "owner"));
        nativeApi.foregroundWindow = 200;
        nativeApi.extendedStyle = 0x80;
        nativeApi.keepWrittenStyle = false;
        nativeApi.foregroundRestoreResult = false;
        List<String> diagnostics = new ArrayList<>();
        WindowsOverlayOwnerSupport support = new WindowsOverlayOwnerSupport(nativeApi, diagnostics::add);

        support.configureAfterShow("owner", 25, 150);

        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("was not present")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("rejected")));
    }

    private static final class FakeNativeApi implements WindowsOverlayOwnerSupport.NativeApi {
        private List<WindowsOverlayOwnerSupport.NativeWindow> windows = List.of();
        private long foregroundWindow;
        private int extendedStyle;
        private int lastError;
        private int getWindowLongError;
        private int setWindowLongError;
        private int setWindowLongReturn = 1;
        private boolean keepWrittenStyle = true;
        private boolean foregroundRestoreResult = true;
        private int writtenIndex;
        private int writtenStyle;
        private long restoredForegroundWindow;

        @Override
        public long foregroundWindow() {
            return foregroundWindow;
        }

        @Override
        public List<WindowsOverlayOwnerSupport.NativeWindow> topLevelWindows() {
            return windows;
        }

        @Override
        public int getWindowLong(long handle, int index) {
            lastError = getWindowLongError;
            return extendedStyle;
        }

        @Override
        public int setWindowLong(long handle, int index, int value) {
            writtenIndex = index;
            writtenStyle = value;
            if (keepWrittenStyle) {
                extendedStyle = value;
            }
            lastError = setWindowLongError;
            return setWindowLongReturn;
        }

        @Override
        public boolean setForegroundWindow(long handle) {
            restoredForegroundWindow = handle;
            return foregroundRestoreResult;
        }

        @Override
        public void clearLastError() {
            lastError = 0;
        }

        @Override
        public int lastError() {
            return lastError;
        }
    }
}
