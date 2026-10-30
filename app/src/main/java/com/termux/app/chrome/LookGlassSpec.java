package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The Look's own glass: the Base values the Looks menu and the Appearance editor's shared layer
 * set, which every surface inherits until it is detached. What Home's widgets wear, so a
 * detached terminal, keyboard, status bar or dock never reaches them.
 *
 * <p>Pure: read from the stored Base numbers, clamped as the surfaces clamp them, with the
 * corner radius's "follow the style" sentinel resolved in the one place it is resolved.</p>
 */
public final class LookGlassSpec {

    /** The blur ceiling every slab shares (the terminal's). */
    public static final int MAX_BLUR_DP = 48;

    /** Whether the launcher stands on the wallpaper, which is where glass can be worn at all. */
    public final boolean wallpaper;
    public final int blurDp;
    public final int opacityPercent;
    public final int grainPercent;
    public final int tintPercent;
    /** Corners, the sentinel resolved. */
    public final int cornerRadiusDp;

    private LookGlassSpec(boolean wallpaper, int blurDp, int opacityPercent, int grainPercent,
                          int tintPercent, int cornerRadiusDp) {
        this.wallpaper = wallpaper;
        this.blurDp = blurDp;
        this.opacityPercent = opacityPercent;
        this.grainPercent = grainPercent;
        this.tintPercent = tintPercent;
        this.cornerRadiusDp = cornerRadiusDp;
    }

    /** The spec for stored Base values; {@code storedCornersDp} may be the -1 sentinel. */
    public static LookGlassSpec of(boolean wallpaper, int blurDp, int opacityPercent,
                                   int grainPercent, int tintPercent, int storedCornersDp) {
        return new LookGlassSpec(wallpaper, clamp(blurDp, 0, MAX_BLUR_DP),
            clamp(opacityPercent, 0, 100), clamp(grainPercent, 0, 100),
            clamp(tintPercent, 0, 100), cornerRadiusDp(storedCornersDp));
    }

    /** The Workspace's Look as stored now; the shipped Base when there is no store to read. */
    public static LookGlassSpec read(@Nullable TermuxAppSharedPreferences preferences) {
        return read(preferences, TermuxAppSharedPreferences.terminalPageId());
    }

    /** One half's Look as stored now (ADR 0013); the shipped Base when there is no store. */
    public static LookGlassSpec read(@Nullable TermuxAppSharedPreferences preferences,
                                     int pageId) {
        if (preferences == null) {
            return of(false, TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
                TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY, TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN,
                TERMUX_APP.DEFAULT_SURFACE_BASE_TINT,
                TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS);
        }
        return of(preferences.isUseSystemWallpaperEnabled(),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, pageId),
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, pageId),
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, pageId),
            preferences.getSurfaceBaseValue(SurfaceProperty.TINT, pageId),
            preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
    }

    /**
     * Base's Corners in dp. The -1 sentinel follows the rounded-surface token, as the chrome's
     * own Corners do: a widget is a card, never the Docked straight edge.
     */
    public static int cornerRadiusDp(int storedDp) {
        return storedDp < 0
            ? TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(null, true)
            : storedDp;
    }

    /** Base's Corners in dp, as stored now. */
    public static int cornerRadiusDp(@Nullable TermuxAppSharedPreferences preferences) {
        return cornerRadiusDp(preferences == null
            ? TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS
            : preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
    }

    /** Whether a slab is worn: on the wallpaper, whatever the blur (tint and grain alone at 0). */
    public boolean slabShown() {
        return wallpaper;
    }

    /** Whether the slab draws the wallpaper's blur, which costs a shared frame at {@link #blurDp}. */
    public boolean wantsBlurFrame() {
        return wallpaper && blurDp > 0;
    }

    /** The Look's surface colour: {@code baseColor}'s hue at the Look's opacity. */
    public int surfaceColor(int baseColor) {
        int alpha = Math.round(opacityPercent / 100f * 255f);
        return (alpha << 24) | (baseColor & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof LookGlassSpec)) return false;
        LookGlassSpec that = (LookGlassSpec) other;
        return wallpaper == that.wallpaper && blurDp == that.blurDp
            && opacityPercent == that.opacityPercent && grainPercent == that.grainPercent
            && tintPercent == that.tintPercent && cornerRadiusDp == that.cornerRadiusDp;
    }

    @Override
    public int hashCode() {
        int hash = wallpaper ? 1 : 0;
        hash = 31 * hash + blurDp;
        hash = 31 * hash + opacityPercent;
        hash = 31 * hash + grainPercent;
        hash = 31 * hash + tintPercent;
        return 31 * hash + cornerRadiusDp;
    }
}
