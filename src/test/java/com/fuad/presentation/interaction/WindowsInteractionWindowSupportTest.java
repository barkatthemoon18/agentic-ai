package com.fuad.presentation.interaction;

import com.fuad.interaction.FocusRequirement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsInteractionWindowSupportTest {
    private static final String TITLE = "Ares Interaction Surface";
    private static final long PROCESS_ID = 42;
    private static final long PREVIOUS = 100;
    private static final long INTERACTION = 200;
    private static final int WS_EX_NOACTIVATE = 0x08000000;

    @Test
    void passiveShouldNotRestoreWhenNoActivateWorksWithoutActivation() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.foreground = PREVIOUS;
        WindowsInteractionWindowSupport support = support(nativeApi);

        WindowsInteractionWindowSupport.WindowConfiguration configuration =
                support.configureAfterShow(TITLE, PROCESS_ID,
                        FocusRequirement.PASSIVE, PREVIOUS);

        assertEquals(WS_EX_NOACTIVATE, nativeApi.style);
        assertEquals(List.of(), nativeApi.foregroundRequests);
        assertFalse(configuration.restoreOnDismiss());
    }

    @Test
    void passiveShouldRestoreImmediatelyAfterAccidentalActivation() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.foreground = INTERACTION;
        WindowsInteractionWindowSupport support = support(nativeApi);

        WindowsInteractionWindowSupport.WindowConfiguration configuration =
                support.configureAfterShow(TITLE, PROCESS_ID,
                        FocusRequirement.PASSIVE, PREVIOUS);

        assertEquals(List.of(PREVIOUS), nativeApi.foregroundRequests);
        assertFalse(configuration.restoreOnDismiss());
    }

    @Test
    void passiveFailureShouldRequestConditionalRestoration() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.foreground = INTERACTION;
        nativeApi.writeError = 5;
        WindowsInteractionWindowSupport support = support(nativeApi);

        WindowsInteractionWindowSupport.WindowConfiguration configuration =
                support.configureAfterShow(TITLE, PROCESS_ID,
                        FocusRequirement.PASSIVE, PREVIOUS);

        assertTrue(configuration.restoreOnDismiss());
        assertEquals(List.of(PREVIOUS), nativeApi.foregroundRequests);
    }

    @Test
    void passiveReadFailureShouldRequestConditionalRestoration() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.foreground = INTERACTION;
        nativeApi.readError = 5;
        WindowsInteractionWindowSupport support = support(nativeApi);

        WindowsInteractionWindowSupport.WindowConfiguration configuration =
                support.configureAfterShow(TITLE, PROCESS_ID,
                        FocusRequirement.PASSIVE, PREVIOUS);

        assertTrue(configuration.restoreOnDismiss());
        assertEquals(List.of(PREVIOUS), nativeApi.foregroundRequests);
    }

    @Test
    void requiredShouldRemoveNoActivateBeforeFocusIsRequestedByPresenter() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.foreground = PREVIOUS;
        nativeApi.style = WS_EX_NOACTIVATE;
        WindowsInteractionWindowSupport support = support(nativeApi);

        WindowsInteractionWindowSupport.WindowConfiguration configuration =
                support.configureAfterShow(TITLE, PROCESS_ID,
                        FocusRequirement.REQUIRED, PREVIOUS);

        assertEquals(0, nativeApi.style);
        assertTrue(configuration.restoreOnDismiss());
    }

    @Test
    void dismissalShouldNotOverrideAWindowChosenByTheUser() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.foreground = 300;
        WindowsInteractionWindowSupport support = support(nativeApi);

        support.restoreForegroundWindow(PREVIOUS, INTERACTION);

        assertEquals(List.of(), nativeApi.foregroundRequests);
    }

    @Test
    void dismissalShouldRestoreOnlyWhileInteractionRemainsForeground() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.foreground = INTERACTION;
        WindowsInteractionWindowSupport support = support(nativeApi);

        support.restoreForegroundWindow(PREVIOUS, INTERACTION);

        assertEquals(List.of(PREVIOUS), nativeApi.foregroundRequests);
    }

    private static WindowsInteractionWindowSupport support(FakeNativeApi nativeApi) {
        return new WindowsInteractionWindowSupport(nativeApi, ignored -> { });
    }

    private static final class FakeNativeApi
            implements WindowsInteractionWindowSupport.NativeApi {
        private long foreground;
        private int style;
        private int readError;
        private int writeError;
        private int currentError;
        private final List<Long> foregroundRequests = new ArrayList<>();

        @Override
        public long foregroundWindow() {
            return foreground;
        }

        @Override
        public List<WindowsInteractionWindowSupport.NativeWindow> topLevelWindows() {
            return List.of(new WindowsInteractionWindowSupport.NativeWindow(
                    INTERACTION, PROCESS_ID, TITLE));
        }

        @Override
        public int getWindowLong(long handle, int index) {
            currentError = readError;
            return style;
        }

        @Override
        public int setWindowLong(long handle, int index, int value) {
            currentError = writeError;
            if (writeError != 0) {
                return 0;
            }
            int previous = style;
            style = value;
            return previous;
        }

        @Override
        public boolean setForegroundWindow(long handle) {
            foregroundRequests.add(handle);
            foreground = handle;
            return true;
        }

        @Override
        public void clearLastError() {
            currentError = 0;
        }

        @Override
        public int lastError() {
            return currentError;
        }
    }
}
