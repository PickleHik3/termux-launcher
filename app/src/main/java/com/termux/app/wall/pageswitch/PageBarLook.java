package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Status bar card › Page bar: how the pages are marked in the status bar, as glyphs (the window
 * chips and the page bar's glyphs, as today) or as Marks (a plain line of marks, one per page).
 * One global value. Existing installs read Glyphs; a new install is written Marks.
 */
public enum PageBarLook {
    GLYPHS("glyphs"),
    MARKS("marks");

    @NonNull public final String value;

    PageBarLook(@NonNull String value) {
        this.value = value;
    }

    public static final PageBarLook DEFAULT = GLYPHS;

    @NonNull
    public static PageBarLook fromValue(@Nullable String value) {
        for (PageBarLook look : values()) if (look.value.equals(value)) return look;
        return DEFAULT;
    }

    /** Whether the status bar shows the Marks line in place of the window chips. */
    public boolean marksShown() {
        return this == MARKS;
    }
}
