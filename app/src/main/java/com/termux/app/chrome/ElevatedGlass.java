package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The glass a surface raised over other glass wears: the sheets' glass ({@link SheetGlass}, which
 * is Global's with its opacity floor) made denser so it reads as a pane standing above the glass it
 * covers. More blur, more grain and a stronger rim, with the lift shadow the anchored menus draw
 * (Material's menu container elevation) and the Fancier Glass edge the cards wear; never less of
 * any of them than the sheet's, and never fully opaque unless the look already is. The glass cues
 * carry the elevation, not the body: the body gains only a step, so what is behind still shows
 * through the blur (a denser body read as a flat grey slab over the terminal). The page bar
 * the extra-keys key pops up wears it first; the next raised surface asks {@link #of} the same.
 *
 * <p>Pure: reads an {@link ElementGlass} and answers another, at dress time, never per frame.</p>
 */
public final class ElevatedGlass {

    /** The share of the remaining transparency the raised glass fills: o' = o + (100 - o) x gain. */
    public static final float BODY_GAIN = 0.12f;
    /** The most opacity the body gain reaches; a look already above it keeps its own. */
    public static final int OPACITY_CEILING_PERCENT = 80;
    /** Blur grows by this factor, and by at least {@link #BLUR_STEP_DP}, up to the chrome's blur ceiling. */
    public static final float BLUR_GAIN = 1.5f;
    public static final int BLUR_STEP_DP = 8;
    /** Grain grows by this factor, and by at least {@link #GRAIN_STEP_PERCENT}, up to {@link #GRAIN_CEILING_PERCENT}. */
    public static final float GRAIN_GAIN = 1.6f;
    public static final int GRAIN_STEP_PERCENT = 4;
    public static final int GRAIN_CEILING_PERCENT = 50;
    /** The rim's alpha against the sheet's: more edge, where no Fancier Glass edge is drawn. */
    public static final float RIM_GAIN = 1.5f;

    private ElevatedGlass() {}

    /** The raised glass over the sheets' glass in {@code grid}. */
    @NonNull
    public static ElementGlass of(@NonNull LookGrid grid) {
        return of(SheetGlass.of(grid));
    }

    /** {@link #of(LookGrid)} over the live store; the shipped look when there is none. */
    @NonNull
    public static ElementGlass of(@Nullable TermuxAppSharedPreferences preferences) {
        return of(SheetGlass.of(preferences));
    }

    /** {@code sheet} raised: denser in blur, grain and body, by {@link #blurDp}, {@link #grainPercent} and {@link #opacityPercent}. */
    @NonNull
    public static ElementGlass of(@NonNull ElementGlass sheet) {
        return sheet.withBody(blurDp(sheet.blurDp), opacityPercent(sheet.opacityPercent),
            grainPercent(sheet.grainPercent));
    }

    /**
     * The raised blur: {@code max(b x 1.5, b + 8)} capped at the chrome's blur ceiling, never less
     * than {@code b}. Blur off stays off: grain and body carry the raised look.
     */
    public static int blurDp(int baseDp) {
        if (baseDp <= 0) return 0;
        int raised = Math.max(Math.round(baseDp * BLUR_GAIN), baseDp + BLUR_STEP_DP);
        return Math.max(baseDp, Math.min(ElementGlass.MAX_BLUR_DP, raised));
    }

    /** The raised opacity: {@link #BODY_GAIN} of what is left, capped at {@link #OPACITY_CEILING_PERCENT}, never less than {@code base}. */
    public static int opacityPercent(int basePercent) {
        int base = Math.max(0, Math.min(100, basePercent));
        int raised = Math.round(base + (100 - base) * BODY_GAIN);
        return Math.max(base, Math.min(OPACITY_CEILING_PERCENT, raised));
    }

    /** The raised grain: {@code max(g x 1.6, g + 4)} capped at {@link #GRAIN_CEILING_PERCENT}, never less than {@code base}. */
    public static int grainPercent(int basePercent) {
        int base = Math.max(0, Math.min(100, basePercent));
        int raised = Math.max(Math.round(base * GRAIN_GAIN), base + GRAIN_STEP_PERCENT);
        return Math.max(base, Math.min(GRAIN_CEILING_PERCENT, raised));
    }

    /**
     * {@code raised} as a {@link LookGlassSpec}, for a surface dressed through a pane style
     * ({@link LookGlassStyle}); {@code wallpaper} says whether there is a wallpaper to stand on.
     */
    @NonNull
    public static LookGlassSpec lookSpec(boolean wallpaper, @NonNull ElementGlass raised) {
        return LookGlassSpec.of(wallpaper, raised.blurDp, raised.opacityPercent,
            raised.grainPercent, raised.tintPercent, raised.cornersDp);
    }

    /** A rim alpha (0..255) as the raised glass draws it. */
    public static int rimAlpha(int baseAlpha) {
        return Math.max(0, Math.min(255, Math.round(baseAlpha * RIM_GAIN)));
    }
}
