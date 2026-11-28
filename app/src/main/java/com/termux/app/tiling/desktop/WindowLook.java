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

    /** The look as v1's desktop wore it before this: 8dp gap, 14dp corners, a rim, no dim, opaque. */
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
