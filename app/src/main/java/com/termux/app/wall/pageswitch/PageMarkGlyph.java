package com.termux.app.wall.pageswitch;

import androidx.annotation.Nullable;

import com.termux.app.terminal.ProcessGlyphs;

/** Whether a terminal window's mark is a program's glyph: only a full-screen program that has one. */
public final class PageMarkGlyph {

    private PageMarkGlyph() {}

    /**
     * The glyph for {@code process} while its pane is on the alternate screen; null off it, with
     * no process, or when the glyph table has no entry of its own (never the generic default).
     */
    @Nullable
    public static String forProgram(boolean alternateScreen, @Nullable String process) {
        if (!alternateScreen || !ProcessGlyphs.knows(process)) return null;
        return ProcessGlyphs.forProcess(process);
    }
}
