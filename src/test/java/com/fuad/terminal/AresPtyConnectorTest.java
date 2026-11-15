package com.fuad.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.techsenger.jeditermfx.core.util.TermSize;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AresPtyConnectorTest {

    @Test
    void shouldRejectNullProcess() {
        assertThrows(NullPointerException.class, () -> new AresPtyConnector(null));
    }

    @Test
    void shouldReturnConfiguredName() {
        PtyProcess process = mock(PtyProcess.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());

        AresPtyConnector connector = new AresPtyConnector(process);

        assertEquals("ARES // POWERSHELL", connector.getName());
    }

    @Test
    void shouldPropagateResizeWhenProcessIsAlive() {
        PtyProcess process = mock(PtyProcess.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.isAlive()).thenReturn(true);

        AresPtyConnector connector = new AresPtyConnector(process);
        connector.resize(new TermSize(120, 40));

        ArgumentCaptor<WinSize> captor = ArgumentCaptor.forClass(WinSize.class);
        verify(process).setWinSize(captor.capture());
        WinSize size = captor.getValue();
        assertEquals(120, size.getColumns());
        assertEquals(40, size.getRows());
    }

    @Test
    void shouldIgnoreResizeWhenProcessIsNotAlive() {
        PtyProcess process = mock(PtyProcess.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.isAlive()).thenReturn(false);

        AresPtyConnector connector = new AresPtyConnector(process);
        connector.resize(new TermSize(80, 24));

        verify(process, never()).setWinSize(any());
    }
}
