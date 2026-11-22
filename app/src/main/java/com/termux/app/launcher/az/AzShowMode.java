package com.termux.app.launcher.az;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How the A&#8211;Z strip shows (editor-redesign §4, "A–Z on Home, Niagara style"), the Dock
 * card's Bars › A–Z › Show row:
 *
 * <ul>
 *   <li>{@link #SHOWN}: the strip as it has always been.</li>
 *   <li>{@link #FAINT}: the same strip at {@link #FAINT_ALPHA}, still scrubbable; a finger on it
 *       brings the letters up to full for the scrub.</li>
 *   <li>{@link #PULL_TAB}: the strip claims no band, so the widgets and the content take its
 *       room, and the tab on the screen's edge ({@link AzTabPolicy}) brings the letters out over
 *       Home while it is held.</li>
 * </ul>
 *
 * <p>The scrub is the same in all three: same gesture, letter bubble and animation. The glass
 * behind the letters is a separate switch ({@link #KEY_GLASS}), off by default: without it the
 * letters stand on the wallpaper and take their ink from it.</p>
 *
 * <pre>
 *   AzShowMode mode = AzShowMode.of(prefs);
 *   row.setRestingAlpha(mode.restingAlpha());
 * </pre>
 *
 * <p>The keys are the Dock card's ({@code DockCard.KEY_AZ_SHOW}, {@code DockCard.AZ_SHOW_VALUES},
 * {@code DockCard.KEY_AZ_GLASS_BACKGROUND}); this is their one reader outside the editor.</p>
 */
public enum AzShowMode {
    SHOWN("shown"),
    FAINT("faint"),
    PULL_TAB("pull_tab");

    /** Where the mode is stored, as one of the {@link #storageValue}s. */
    public static final String KEY = "az_show";
    /** Whether the A–Z strip wears glass behind its letters. */
    public static final String KEY_GLASS = "az_glass_background";
    public static final boolean DEFAULT_GLASS = false;
    /** The letters' opacity at rest under {@link #FAINT}: about 30%, still read and found. */
    public static final float FAINT_ALPHA = 0.3f;

    @NonNull public final String storageValue;

    AzShowMode(@NonNull String storageValue) {
        this.storageValue = storageValue;
    }

    /** The mode a stored value names; {@link #SHOWN} for none or a value not known. */
    @NonNull
    public static AzShowMode parse(@Nullable String value) {
        for (AzShowMode mode : values()) {
            if (mode.storageValue.equals(value)) return mode;
        }
        return SHOWN;
    }

    /** The mode stored in {@code prefs}; {@link #SHOWN} with no store. */
    @NonNull
    public static AzShowMode of(@Nullable SharedPreferences prefs) {
        return prefs == null ? SHOWN : parse(prefs.getString(KEY, null));
    }

    /** Whether the strip wears glass, as stored in {@code prefs}. */
    public static boolean glassOf(@Nullable SharedPreferences prefs) {
        return prefs == null ? DEFAULT_GLASS : prefs.getBoolean(KEY_GLASS, DEFAULT_GLASS);
    }

    /** Whether the strip takes a band on its edge; the pull tab's letters stand over Home. */
    public boolean claimsBand() {
        return this != PULL_TAB;
    }

    /** The letters' opacity while no finger is on them. */
    public float restingAlpha() {
        return this == FAINT ? FAINT_ALPHA : 1f;
    }

    /** The letters' opacity, full while a scrub holds them whatever the mode. */
    public float lettersAlpha(boolean scrubbing) {
        return scrubbing ? 1f : restingAlpha();
    }

    /**
     * Whether the strip's own capsule draws its glass. Only the capsule the letters stand in alone
     * is theirs to take away: on the dock's row, on a shared plank or inside the Docked frame the
     * sheet is shared with other bars and stays.
     */
    public static boolean ownGlassShown(boolean glassBackground, boolean sharedSheet) {
        return glassBackground && !sharedSheet;
    }
}
