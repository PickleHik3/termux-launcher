package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;

/**
 * How a tab strip shows its tabs in the width it has (#420): with names while each tab can hold
 * an icon and a short name, icons only while each can hold its icon, else icons at their own width
 * on a strip that scrolls sideways. Pure.
 */
final class TabStripFit {

    enum Mode {
        /** Icon and short name, the tabs sharing the width. */
        NAMED,
        /** Icons only, the tabs sharing the width. */
        ICONS,
        /** Icons only, each its own width, the strip scrolling sideways. */
        SCROLLING
    }

    /** The narrowest tab that still shows an icon and a few letters of its name, in dp. */
    static final float NAMED_TAB_DP = 72f;
    /** The narrowest tab that shows its icon whole, in dp. */
    static final float ICON_TAB_DP = 32f;

    private TabStripFit() {}

    /** The mode for {@code count} tabs in {@code widthPx} at {@code density}. */
    @NonNull
    static Mode of(float widthPx, int count, float density) {
        if (count <= 0) return Mode.NAMED;
        float each = Math.max(0f, widthPx) / count;
        if (each >= NAMED_TAB_DP * density) return Mode.NAMED;
        if (each >= ICON_TAB_DP * density) return Mode.ICONS;
        return Mode.SCROLLING;
    }
}
