package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;

import java.util.Map;

/**
 * The upgrade nudge's bookkeeping (appearance-cards overhaul §6): whether the card offering to
 * reset the glass is due, decided once, on the first start of a build that knows about it. No
 * views and no {@code Context}: held by JVM tests.
 *
 * <p>The decision is taken at that first start, after the start-up migrations: a fresh install
 * has no earlier use and is settled at once; an install upgraded from an earlier pre or a stable
 * 1.x is due only when its glass differs from what ships. Decline and Accept both settle it.</p>
 */
public final class GlassResetNudge {

    private GlassResetNudge() {}

    /** The app preference holding {@link #UNDECIDED}, {@link #DUE} or {@link #SETTLED}. */
    public static final String KEY = "glass_reset_nudge";

    /** No start of this build has decided yet. */
    public static final int UNDECIDED = 0;
    /** The card is waiting for a Home page. */
    public static final int DUE = 1;
    /** Shown and answered, or never needed. */
    public static final int SETTLED = 2;

    /**
     * The state after this start: an undecided one becomes due for an earlier user whose glass
     * is not the shipped glass, else settled; a decided one stays as it is.
     */
    public static int evaluate(int state, boolean usedBefore, boolean glassCustom) {
        if (state == DUE || state == SETTLED) return state;
        return usedBefore && glassCustom ? DUE : SETTLED;
    }

    /**
     * Whether this install was used by an earlier build: the setup wizard was finished, the old
     * tour was finished, Use as or the old first-run card was answered, or the wall migration's
     * version was already stored before this start ran it (every pre since the wall rework).
     */
    public static boolean usedBefore(int setupWizardVersion, int tourCompletedVersion,
                                     boolean useAsStored, boolean firstRunCardAnswered,
                                     boolean wallMigrationStoredBefore) {
        return setupWizardVersion > 0 || tourCompletedVersion >= 1 || useAsStored
            || firstRunCardAnswered || wallMigrationStoredBefore;
    }

    /**
     * Whether the user's glass differs from what ships: any look-grid cell in {@code stored}
     * holding a value other than the one an empty grid ({@code shipped}) resolves it to, or the
     * Mode, Contrast or Dynamic colour away from its default (Accent is a grid cell).
     *
     * @param stored        every stored preference, grid cells among them
     * @param shipped       a grid over an empty store: what ships for every cell
     * @param nightMode     the stored Mode, null or "system" for the default
     * @param contrastLevel the stored Contrast level's value
     * @param dynamicColour whether Dynamic colour is on
     */
    public static boolean glassCustom(@NonNull Map<String, ?> stored, @NonNull LookGrid shipped,
                                      @Nullable String nightMode, @Nullable String contrastLevel,
                                      boolean dynamicColour) {
        if (dynamicColour) return true;
        if (nightMode != null && !nightMode.isEmpty() && !"system".equals(nightMode)) return true;
        if (contrastLevel != null && !contrastLevel.isEmpty() && !"default".equals(contrastLevel))
            return true;
        for (Map.Entry<String, ?> entry : stored.entrySet()) {
            LookGrid.Key cell = LookGrid.parse(entry.getKey());
            if (cell == null) continue;
            Object value = entry.getValue();
            if (!(value instanceof Integer)) return true;
            if ((Integer) value != shipped.resolve(cell).value) return true;
        }
        return false;
    }
}
