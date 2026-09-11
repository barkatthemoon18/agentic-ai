package com.fuad.assistant.skills.os;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class WindowsApplicationControllerTest {
    @Test
    void shouldDistinguishBackgroundAndVisibleApplicationStates() {
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        WindowsApplicationController controller = new WindowsApplicationController(windows, () -> List.of(process));

        assertEquals(ApplicationRuntimeState.RUNNING_BACKGROUND,
                controller.runtimeState(application()).runtimeState());

        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        assertEquals(ApplicationRuntimeState.RUNNING_WITH_WINDOW,
                controller.runtimeState(application()).runtimeState());
    }

    @Test
    void shouldCloseWindowGracefullyBeforeDestroyingProcess() {
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        windows.onClose = () -> process.alive = false;
        WindowsApplicationController controller = new WindowsApplicationController(windows, () -> List.of(process));

        assertEquals(ApplicationActionResult.Status.SUCCESS, controller.closeDetailed(application()).status());
        assertTrue(windows.closeCalled);
        assertFalse(process.destroyCalled);
        assertFalse(process.forceCalled);
    }

    @Test
    void shouldUseNormalProcessTerminationWhenNoWindowCanClose() {
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        WindowsApplicationController controller = new WindowsApplicationController(
                new FakeWindowService(), () -> List.of(process));

        assertEquals(ApplicationActionResult.Status.SUCCESS, controller.closeDetailed(application()).status());
        assertTrue(process.destroyCalled);
        assertFalse(process.forceCalled);
    }

    @Test
    void shouldReturnVerifiedFocusResultFromWindowService() {
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        windows.focusResult = WindowService.FocusResult.REJECTED;
        WindowsApplicationController controller = new WindowsApplicationController(windows, () -> List.of(process));

        assertEquals(ApplicationActionResult.Status.FOCUS_REJECTED,
                controller.focus(application()).status());
    }

    private ApplicationDefinition application() {
        return new ApplicationDefinition("spotify", "Spotify", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Apps\\Spotify.exe"), Set.of(), Set.of()));
    }

    private static final class FakeWindowService implements WindowService {
        private List<WindowHandle> windows = List.of();
        private FocusResult focusResult = FocusResult.FOCUSED;
        private boolean closeCalled;
        private Runnable onClose = () -> { };

        @Override public List<WindowHandle> visibleWindows(Set<Long> processIds) { return windows; }
        @Override public boolean close(WindowHandle window) {
            closeCalled = true;
            onClose.run();
            return true;
        }
        @Override public FocusResult focus(WindowHandle window) { return focusResult; }
    }

    private static final class FakeProcess implements ProcessHandle {
        private final long pid;
        private final String command;
        private boolean alive = true;
        private boolean destroyCalled;
        private boolean forceCalled;

        private FakeProcess(long pid, String command) {
            this.pid = pid;
            this.command = command;
        }

        @Override public long pid() { return pid; }
        @Override public Optional<ProcessHandle> parent() { return Optional.empty(); }
        @Override public Stream<ProcessHandle> children() { return Stream.empty(); }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public Info info() { return new FakeInfo(command); }
        @Override public CompletableFuture<ProcessHandle> onExit() { return new CompletableFuture<>(); }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public boolean destroy() { destroyCalled = true; alive = false; return true; }
        @Override public boolean destroyForcibly() { forceCalled = true; alive = false; return true; }
        @Override public boolean isAlive() { return alive; }
        @Override public int compareTo(ProcessHandle other) { return Long.compare(pid, other.pid()); }
    }

    private record FakeInfo(String executable) implements ProcessHandle.Info {
        @Override public Optional<String> command() { return Optional.of(executable); }
        @Override public Optional<String> commandLine() { return Optional.of(executable); }
        @Override public Optional<String[]> arguments() { return Optional.of(new String[0]); }
        @Override public Optional<Instant> startInstant() { return Optional.empty(); }
        @Override public Optional<Duration> totalCpuDuration() { return Optional.empty(); }
        @Override public Optional<String> user() { return Optional.empty(); }
    }
}
