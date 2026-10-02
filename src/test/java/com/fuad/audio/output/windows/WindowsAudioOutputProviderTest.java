package com.fuad.audio.output.windows;

import com.fuad.audio.output.AudioOutputSnapshot;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowsAudioOutputProviderTest {
    private final Ole32 ole32 = mock(Ole32.class);
    private final WindowsAudioOutputProvider provider = spy(new WindowsAudioOutputProvider(ole32));

    @ParameterizedTest
    @ValueSource(ints = {0, 1}) // S_OK and S_FALSE both acquire a COM initialization reference.
    void successfulReadShouldBalanceComInitializationOnce(int initializationResult) {
        when(ole32.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED))
                .thenReturn(new HRESULT(initializationResult));
        AudioOutputSnapshot snapshot = new AudioOutputSnapshot(true, "endpoint", "Speakers",
                OptionalDouble.of(0.5), true);
        doReturn(snapshot).when(provider).readSnapshot();

        assertSame(snapshot, provider.current());

        verifyBalancedLifecycle();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void readFailureShouldReturnUnavailableAndBalanceComInitialization(int initializationResult) {
        when(ole32.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED))
                .thenReturn(new HRESULT(initializationResult));
        doThrow(new IllegalStateException("endpoint unavailable")).when(provider).readSnapshot();

        assertEquals(AudioOutputSnapshot.unavailable(), provider.current());

        verifyBalancedLifecycle();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void linkageFailureShouldReturnUnavailableAndBalanceComInitialization(int initializationResult) {
        when(ole32.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED))
                .thenReturn(new HRESULT(initializationResult));
        doThrow(new UnsatisfiedLinkError("native dependency unavailable")).when(provider).readSnapshot();

        assertEquals(AudioOutputSnapshot.unavailable(), provider.current());

        verifyBalancedLifecycle();
    }

    @Test
    void failedInitializationShouldNotReadSnapshotOrUninitializeCom() {
        HRESULT failure = new HRESULT(0x80004005); // E_FAIL
        when(ole32.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED)).thenReturn(failure);

        COMException exception = assertThrows(COMException.class, provider::current);

        assertEquals(failure, exception.getHresult());
        verify(provider, never()).readSnapshot();
        verify(ole32).CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        verify(ole32, never()).CoUninitialize();
        verifyNoMoreInteractions(ole32);
    }

    @Test
    void eachPollShouldAcquireAndReleaseItsOwnComInitialization() {
        when(ole32.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED))
                .thenReturn(new HRESULT(0), new HRESULT(1));
        doReturn(AudioOutputSnapshot.unavailable()).when(provider).readSnapshot();

        provider.current();
        provider.current();

        var order = inOrder(ole32, provider);
        for (int i = 0; i < 2; i++) {
            order.verify(ole32).CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
            order.verify(provider).readSnapshot();
            order.verify(ole32).CoUninitialize();
        }
        verify(ole32, times(2)).CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        verify(ole32, times(2)).CoUninitialize();
        verifyNoMoreInteractions(ole32);
    }

    private void verifyBalancedLifecycle() {
        var order = inOrder(ole32, provider);
        order.verify(ole32).CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        order.verify(provider).readSnapshot();
        order.verify(ole32).CoUninitialize();
        verify(ole32).CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        verify(ole32).CoUninitialize();
        verifyNoMoreInteractions(ole32);
    }
}
