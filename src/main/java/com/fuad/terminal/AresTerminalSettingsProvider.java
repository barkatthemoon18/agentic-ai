package com.fuad.terminal;

import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import javafx.scene.text.Font;
import org.jetbrains.annotations.NotNull;

public final class AresTerminalSettingsProvider extends DefaultSettingsProvider {
    @Override
    public @NotNull TerminalColor getDefaultForeground() {
        return TerminalColor.rgb(183, 214, 223);
    }

    @Override
    public @NotNull TerminalColor getDefaultBackground() {
        return TerminalColor.rgb(5, 21, 32);
    }

    @Override
    public Font getTerminalFont() {
        return Font.font("JetBrains Mono", getTerminalFontSize());
    }

    @Override
    public float getTerminalFontSize() {
        return 12.0f;
    }

    @Override
    public int getBufferMaxLinesCount() {
        return 5_000;
    }

    @Override
    public boolean audibleBell() {
        return false;
    }
}
