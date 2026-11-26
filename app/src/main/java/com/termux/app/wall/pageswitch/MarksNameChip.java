package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

import java.util.List;

/**
 * The name chip, pure: the current window's mark at rest widens to hold its name beside its
 * number. Only a window mark (Terminal window, Linux window, Display) that is the current page
 * gets one; Home, Assistant and Overview never do, and neither does a window folded into a stack.
 */
public final class MarksNameChip {

    /** Names longer than this are cut with an ellipsis. */
    public static final int MAX_NAME_CHARS = 14;
    /** The room after the name, as a share of the mark's box. */
    public static final float TAIL_SHARE = 0.1875f;
    /** The chip's text size, as a share of the mark's box. */
    public static final float TEXT_SHARE = 0.62f;

    private MarksNameChip() {
    }

    /** Whether {@code e} wears a chip when it stands unfolded. */
    public static boolean wears(@NonNull PageBarModel.Entry e) {
        return e.current && (e.kind == PageKind.TERMINAL || e.kind == PageKind.DISPLAY
            || e.kind == PageKind.LINUX);
    }

    /** The index of the entry that wears the chip, or -1. */
    public static int entryIndex(@NonNull List<PageBarModel.Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (wears(entries.get(i))) return i;
        }
        return -1;
    }

    /** The chip's name: the entry's plain label, cut to {@link #MAX_NAME_CHARS} with an ellipsis. */
    @NonNull
    public static String text(@NonNull PageBarModel.Entry e) {
        String name = PageBarModel.plainLabel(e.label);
        if (name.codePointCount(0, name.length()) <= MAX_NAME_CHARS) return name;
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_CHARS - 1)) + "…";
    }
}
