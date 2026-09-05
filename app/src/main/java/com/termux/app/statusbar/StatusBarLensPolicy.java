package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;

import java.util.List;

/**
 * Pure geometry for the status bar's place icons: where each place's icon sits, how present it
 * is, and how far the bar's glass has drifted towards that place's tint.
 *
 * <p>The bar is the pager. Every place is one bar-width from its neighbours, and a place's
 * distance from the one on screen, {@code t}, is measured in bar widths: 0 is the place on
 * screen, {@code -1} the one waiting past the left edge, {@code +1} the one past the right.
 * Three icons show at any time. The place on screen wears its icon at the home position beside
 * the clock; its two neighbours peek in from the edges, half past them. A drag moves all three
 * together along one line: the arriving icon travels from its edge to home while the one that was
 * home leaves through the other edge, and the third slips off and fades, to be found waiting at
 * the far edge once the wall lands.
 */
public final class StatusBarLensPolicy {

    /** Beyond one width away an icon is leaving; by this many widths it has gone. */
    private static final float FADE_OUT_WIDTHS = 0.5f;

    private StatusBarLensPolicy() {}

    /** Bar widths between {@code page} and the place on screen, with the wall's offset folded in. */
    public static float distance(@NonNull List<PaneWallPage> pages, @NonNull PaneWallPage current,
                                 @NonNull PaneWallPage page, float offsetPx, int widthPx) {
        int rel = page == current ? 0 : PaneWallPolicy.relativePosition(pages, current, page);
        float offset = widthPx <= 0 ? 0f : offsetPx / (float) widthPx;
        return rel + offset;
    }

    /** How far from home an icon is, 0 at home and 1 at either edge. */
    public static float presence(float t) {
        return Math.min(1f, Math.abs(t));
    }

    /** Icons within a width of home are whole; further out they dissolve as they leave. */
    public static float alpha(float t) {
        float beyond = Math.abs(t) - 1f;
        if (beyond <= 0f) return 1f;
        return Math.max(0f, 1f - beyond / FADE_OUT_WIDTHS);
    }

    /** The icon at home is full size; at the edges it is a little smaller. */
    public static float scale(float t) {
        return 1f - 0.14f * presence(t);
    }

    /**
     * The icon's left edge. {@code home} is where the place on screen rests, beside the clock;
     * {@code leftPeek} and {@code rightPeek} are the resting places of the two neighbours, half
     * past their edges. An icon further than a width away keeps travelling outward by its own
     * size per width, which is what carries it off while it fades.
     */
    public static float iconX(float t, float home, float leftPeek, float rightPeek, float size) {
        if (t >= -1f && t <= 0f) return leftPeek + (t + 1f) * (home - leftPeek);
        if (t > 0f && t <= 1f) return home + t * (rightPeek - home);
        if (t < -1f) return leftPeek + (t + 1f) * size * 1.5f;
        return rightPeek + (t - 1f) * size * 1.5f;
    }

    /** How much of a place's tint the glass wears: full on screen, gone one width away. */
    public static float tintWeight(float t) {
        return Math.max(0f, 1f - presence(t));
    }
}
