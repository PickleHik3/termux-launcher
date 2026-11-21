package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * The theme the Appearance editor is showing but has not stored. Picking Light or Dark on the
 * Appearance card relaunches the window into it at once, so the person sees the choice; the
 * stored {@code night-mode} changes only on Done (or Save), and Undo or Discard puts the window
 * back. Process-wide on purpose: the activity the relaunch builds reads it while it themes
 * itself, before any editor exists ({@link #effective}).
 */
public final class ThemePreview {

    private ThemePreview() {}

    @Nullable private static String sPending;

    /** The theme shown but not stored, or null when the window wears the stored one. */
    @Nullable
    public static String pending() {
        return sPending;
    }

    /** Shows {@code mode} unstored; null (or the stored value itself) drops the preview. */
    static void set(@Nullable String mode, @Nullable String stored) {
        sPending = mode == null || mode.equals(orSystem(stored)) ? null : mode;
    }

    /** The theme the window should wear: the pending one, else {@code stored}. */
    @NonNull
    public static String effective(@Nullable String stored) {
        String pending = sPending;
        return pending != null ? pending : orSystem(stored);
    }

    @NonNull
    static String orSystem(@Nullable String stored) {
        return stored == null || stored.isEmpty()
            ? TermuxPropertyConstants.IVALUE_NIGHT_MODE_SYSTEM : stored;
    }
}
