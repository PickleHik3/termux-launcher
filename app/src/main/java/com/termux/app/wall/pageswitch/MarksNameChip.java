package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

import java.util.List;

/**
 * The name chip, pure: the current window's mark at rest widens to hold its name beside its
 * number. Only a window mark with a name of its own (Terminal window, Linux window, Display) that is the current page
 * gets one; Home, Assistant and Overview never do, and neither does a window folded into a stack.
 */
public final class MarksNameChip {

    /** Names longer than this are cut with an ellipsis. */
    public static final int MAX_NAME_CHARS = 14;
    /** Between the leading zone (number or icon) and the name, as a share of the mark's box. */
    public static final float GAP_SHARE = 0.0625f;
    /** After the name, inside the round end, as a share of the mark's box. */
    public static final float END_PAD_SHARE = 0.3125f;
    /** The name's text size: the cut-out number's. */
    public static final float TEXT_SHARE = PageMarkShapes.TERMINAL_NUMBER_TEXT;

    /** The pill's height: the current mark's solid shape, so the chip is the same height. */
    public static float pillHeight(float size) {
        return PageMarkShapes.TERMINAL_H * size / PageMarkShapes.BOX;
    }

    /** The leading zone holding the number or icon: as wide as the pill is high (a round end). */
    public static float leadPx(float size) {
        return pillHeight(size);
    }

    /** From the mark's centre to where the name starts. */
    public static float textOffsetPx(float size) {
        return leadPx(size) / 2f + size * GAP_SHARE;
    }

    /** What the cell grows by: the gap, the name and the end padding. */
    public static float extraPx(float size, float textPx) {
        return size * GAP_SHARE + textPx + size * END_PAD_SHARE;
    }

    /** The pill's width: number zone, gap, name, end padding. */
    public static float pillWidthPx(float size, float textPx) {
        return leadPx(size) + extraPx(size, textPx);
    }

    private MarksNameChip() {
    }

    /** Whether {@code e} wears a chip when it stands unfolded. */
    public static boolean wears(@NonNull PageBarModel.Entry e) {
        return e.current && e.named && (e.kind == PageKind.TERMINAL || e.kind == PageKind.DISPLAY
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
