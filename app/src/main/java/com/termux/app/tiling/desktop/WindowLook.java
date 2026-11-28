package com.termux.app.tiling.desktop;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneShape;

/**
 * How a window looks under the Terl desktop (#423): one card for every kind of window. The glass
 * is the Windows surface's (Opacity from the Terl look), the corners are Global's one radius
 * (a small tile clamps it, {@link PaneShape#radiusForBounds}), the gap is one number. The focused
 * window wears a rim; an unfocused one may be dimmed. Linux windows fade as a whole.
 *
 * <p>Pure arithmetic: no views, so every number is held by a test.
 */
public final class WindowLook {

    private WindowLook() { }

    /** A Linux window's opacity range, in percent: never so low the window is lost. */
    public static final int MIN_OPACITY = 20;
    public static final int MAX_OPACITY = 100;
    /** The most an unfocused window is dimmed, in percent. */
    public static final int MAX_DIM = 60;

    /** What the desktop reads, as the settings give it. */
    public interface Source {
        /** The gap between windows, in dp. */
        int gapDp();

        /** Global's corner radius, in dp. */
        int cornerDp();

        /** The Windows glass's opacity in percent (Opacity of the Terl look). */
        int glassOpacity();

        /** The Windows glass's Blur in dp (Blur of the Terl look); 0 is none. */
        default int glassBlurDp() { return 0; }

        /**
         * The glass a window's ground wears at {@code tintPercent} of tint over the shared blurred
         * wallpaper (the control bar's backdrop), or null when there is no blur or no backdrop.
         */
        @Nullable
        default android.graphics.drawable.Drawable glass(float radiusPx,
                                                         @NonNull android.view.View anchor,
                                                         int tintPercent) {
            return null;
        }

        boolean rimOn();

        /** The rim's colour name ({@code accent} or a named colour). */
        @NonNull String rimColour();

        /** How much an unfocused window is dimmed, in percent. */
        int unfocusedDimPercent();

        /** The Linux opacity for windows of {@code appId} in percent: the app's own, else the global one. */
        int linuxOpacity(@NonNull String appId);

        /** The app id of Linux window {@code linuxId}; empty while unknown. */
        @NonNull String appIdOf(long linuxId);
    }

    /** The rim colours by name; {@code accent} first. A test holds the list to the declaration's. */
    public static final java.util.List<String> RIM_COLOURS = java.util.Collections.unmodifiableList(
        java.util.Arrays.asList("accent", "white", "red", "orange", "green", "blue"));

    /** What the windows wear before any setting is read: 8dp gap, 14dp corners, a rim, no dim, whole Linux windows. */
    public static final Source DEFAULT = new Source() {
        @Override public int gapDp() { return 8; }
        @Override public int cornerDp() { return 14; }
        @Override public int glassOpacity() { return 46; }
        @Override public boolean rimOn() { return true; }
        @NonNull @Override public String rimColour() { return "accent"; }
        @Override public int unfocusedDimPercent() { return 0; }
        @Override public int linuxOpacity(@NonNull String appId) { return MAX_OPACITY; }
        @NonNull @Override public String appIdOf(long linuxId) { return ""; }
    };

    /** Every number the frames wear, as text: equal when nothing about the windows' look changed. */
    @NonNull
    public static String fingerprint(@NonNull Source look) {
        return look.gapDp() + "/" + look.cornerDp() + "/" + look.glassOpacity() + "/" + look.glassBlurDp() + "/" + look.rimOn() + "/"
            + look.rimColour() + "/" + look.unfocusedDimPercent() + "/" + look.linuxOpacity("");
    }

    public static int clampOpacity(int percent) {
        return Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, percent));
    }

    /** The corner radius a window of this size wears: Global's, clamped for a small tile. */
    public static float cornerPx(int cornerDp, float density, int widthPx, int heightPx) {
        return PaneShape.radiusForBounds(Math.max(0, cornerDp) * density, widthPx, heightPx);
    }

    /** The view alpha of a Linux window: its percent, 1 when whole. */
    public static float linuxAlpha(int percent) {
        return clampOpacity(percent) / 100f;
    }

    /** The alpha (0 to 255) of the black veil over an unfocused window; 0 for a focused one. */
    public static int dimAlpha(boolean focused, int dimPercent) {
        if (focused) return 0;
        int clamped = Math.max(0, Math.min(MAX_DIM, dimPercent));
        return Math.round(clamped * 255f / 100f);
    }

    /** Navigation mode's window brightness (the prototype's 0.82): what is left of a window's light. */
    static final float NAVIGATION_BRIGHTNESS = 0.82f;
    /** Navigation mode's rim on every window: a white hairline at 45 %, in dp (the prototype's). */
    public static final float NAVIGATION_RIM_DP = 1.5f;
    public static final int NAVIGATION_RIM_COLOUR = 0x73FFFFFF;

    /**
     * The black veil's alpha over a window: the unfocused dim ({@link #dimAlpha}) and, in
     * navigation mode, every window (the focused one too) darkened to {@link #NAVIGATION_BRIGHTNESS}
     * of the light the dim leaves (the prototype's {@code .nav .win{brightness(.82)}}, and
     * {@code brightness(udim * .82)} on the unfocused).
     */
    public static int veilAlpha(boolean focused, int dimPercent, boolean navigating) {
        int dim = dimAlpha(focused, dimPercent);
        if (!navigating) return dim;
        float light = (1f - dim / 255f) * NAVIGATION_BRIGHTNESS;
        return Math.round((1f - light) * 255f);
    }

    /** The rim's width in px: nothing when off or unfocused, else {@code rimDp} (at least 1px). */
    public static int rimWidthPx(boolean focused, boolean rimOn, float rimDp, float density) {
        if (!focused || !rimOn) return 0;
        return Math.max(1, Math.round(rimDp * density));
    }

    /** The rim's colour: {@code accent} (or an unknown name) is the accent passed in, else the named colour. */
    public static int rimColour(@Nullable String name, int accent) {
        if (name == null) return accent;
        switch (name) {
            case "white": return 0xFFFFFFFF;
            case "red": return 0xFFEF5350;
            case "orange": return 0xFFFFA726;
            case "green": return 0xFF66BB6A;
            case "blue": return 0xFF42A5F5;
            default: return accent;
        }
    }

    /** The window glass's alpha (0 to 255) from the Windows surface's Opacity in percent. */
    public static int glassAlpha(int opacityPercent) {
        return Math.round(Math.max(0, Math.min(100, opacityPercent)) * 255f / 100f);
    }
}
