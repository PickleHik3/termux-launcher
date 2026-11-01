package com.termux.app.place.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one icon size of ADR 0020 §3 (amended): four steps, one value for both orientations, held
 * once in the {@code sizes} document as {@link #FIELD}. It sizes the Home grid's icons and every
 * dock design's alike; a design changes the frame around the icon, never the icon.
 *
 * <p>Steps (dp, the developer's, 2026-11-01): S 40 (today's default dock), M 48 (today's
 * default Home icon, the default), L 54, XL 60. The dock's height follows from the step, so
 * every step draws on every design.</p>
 */
public enum IconSize {
    S("s", 40),
    M("m", 48),
    L("l", 54),
    XL("xl", 60);

    /** The field in the {@code sizes} document's layout-wide section. */
    public static final String FIELD = "icon_size";

    /** What an install that never chose reads as: today's default Home icon. */
    @NonNull public static final IconSize DEFAULT = M;

    @NonNull public final String key;
    public final int dp;

    IconSize(@NonNull String key, int dp) {
        this.key = key;
        this.dp = dp;
    }

    /** The step named {@code key}, or null. */
    @Nullable
    public static IconSize forKey(@Nullable String key) {
        for (IconSize size : values()) {
            if (size.key.equals(key)) return size;
        }
        return null;
    }

    /** The step nearest {@code dp}; a tie goes to the smaller step. */
    @NonNull
    public static IconSize nearest(float dp) {
        IconSize best = S;
        for (IconSize size : values()) {
            if (Math.abs(size.dp - dp) < Math.abs(best.dp - dp)) best = size;
        }
        return best;
    }

    /**
     * The step an upgrade folds to (spec §8a): the Home icon size wins where the user chose one
     * (Home's icons are what the grid is laid out by), else the dock's icon as its height drew it.
     *
     * @param homeIconDp the Home icon size the user stored, or null where none was stored
     * @param dockIconDp the icon the stored dock height drew, or null where none was stored
     */
    @NonNull
    public static IconSize fold(@Nullable Integer homeIconDp, @Nullable Integer dockIconDp) {
        if (homeIconDp != null) return nearest(homeIconDp);
        if (dockIconDp != null) return nearest(dockIconDp);
        return DEFAULT;
    }

    /** The step the {@code sizes} document holds, or {@link #DEFAULT} where it holds none. */
    @NonNull
    public static IconSize of(@NonNull Sizes sizes) {
        Object stored = sizes.document().wide().get(FIELD);
        IconSize size = stored instanceof String ? forKey((String) stored) : null;
        return size != null ? size : DEFAULT;
    }

    /** {@code sizes} holding this step. */
    @NonNull
    public Sizes into(@NonNull Sizes sizes) {
        return new Sizes(sizes.document().withWide(FIELD, key));
    }
}
