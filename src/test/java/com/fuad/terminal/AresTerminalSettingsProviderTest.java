package com.fuad.terminal;

import com.fuad.presentation.JavaFxRuntime;
import com.techsenger.jeditermfx.core.TerminalColor;
import javafx.scene.text.Font;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AresTerminalSettingsProviderTest {

    @BeforeAll
    static void initToolkit() {
        new JavaFxRuntime();
    }

    @Test
    void shouldProvideCustomTerminalThemeAndFontSettings() {
        AresTerminalSettingsProvider provider = new AresTerminalSettingsProvider();

        assertEquals(TerminalColor.rgb(183, 214, 223), provider.getDefaultForeground());
        assertEquals(TerminalColor.rgb(5, 21, 32), provider.getDefaultBackground());
        assertEquals(12.0f, provider.getTerminalFontSize());
        assertEquals(5_000, provider.getBufferMaxLinesCount());
        assertFalse(provider.audibleBell());

        Font font = provider.getTerminalFont();
        assertNotNull(font);
        assertEquals(12.0, font.getSize(), 0.01);
    }
}
