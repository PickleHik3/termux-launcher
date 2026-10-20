package com.termux.app.surfaces;

import androidx.annotation.NonNull;

/**
 * The editor's Cursor trail menu: the styles plus Off. Off is not a style; it is the existing
 * on/off preference, so the stored style id never becomes "off".
 */
final class CursorTrailChoice {

    /** The menu id of the Off entry. */
    static final String OFF = "off";

    /** What a menu id means for the two preferences; {@code style} is null for Off. */
    static final class Resolved {
        final boolean enabled;
        final String style;

        Resolved(boolean enabled, String style) {
            this.enabled = enabled;
            this.style = style;
        }
    }

    private CursorTrailChoice() {}

    /** The menu id the button shows for the stored preferences. */
    @NonNull
    static String menuId(boolean enabled, @NonNull String styleId) {
        return enabled ? styleId : OFF;
    }

    /** The preferences a menu id writes: Off leaves the style alone, a style turns the trail on. */
    @NonNull
    static Resolved resolve(@NonNull String menuId) {
        return OFF.equals(menuId) ? new Resolved(false, null) : new Resolved(true, menuId);
    }
}
