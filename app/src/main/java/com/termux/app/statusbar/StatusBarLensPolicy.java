package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;

import java.util.List;

/**
 * Pure geometry for the status bar's lens edges: where each place's glyph sits, how present it
 * is, and how far the bar's glass has drifted towards that place's tint.
 *
 * <p>The bar is the pager. Every place is one bar-width from its neighbours, and a place's
 * distance from the one on screen, {@code t}, is measured in bar widths: 0 is the place on
 * screen, {@code -1} the one waiting past the left edge, {@code +1} the one past the right. The
 * glyph of the place on screen is hidden; a neighbour's glyph rests half past its edge, and as a
 * drag brings that place in the glyph travels to the bar's home position and dissolves — the
 * place you are arriving at needs no mark once it is here.
 */
public final class StatusBarLensPolicy {

    private StatusBarLensPolicy() {}

    /** Bar widths between {@code page} and the place on screen, with the wall's offset folded in. */
    public static float distance(@NonNull List<PaneWallPage> pages, @NonNull PaneWallPage current,
                                 @NonNull PaneWallPage page, float offsetPx, int widthPx) {
        int rel = page == current ? 0 : PaneWallPolicy.relativePosition(pages, current, page);
        float offset = widthPx <= 0 ? 0f : offsetPx / (float) widthPx;
        return rel + offset;
    }

    /** How present a glyph is: 0 for the place on screen, 1 for a neighbour at rest. */
    public static float presence(float t) {
        return Math.min(1f, Math.abs(t));
    }

    /** The glyph's opacity, eased so it lingers as it dissolves. */
    public static float alpha(float t) {
        return (float) Math.pow(presence(t), 0.7);
    }

    /** The glyph shrinks a fifth on its way in. */
    public static float scale(float t) {
        return 1f - 0.2f * presence(t);
    }

    /**
     * The glyph's left edge. Rest for the place on screen is {@code home}; a neighbour on the left
     * sits {@code inLeg} short of it, one on the right {@code outLeg} past it, and anything
     * further out keeps travelling off the bar.
     */
    public static float lensX(float t, float home, float inLeg, float outLeg) {
        if (t >= -1f && t <= 0f) return home + t * inLeg;
        if (t > 0f && t <= 1f) return home + t * outLeg;
        if (t < -1f) return home - inLeg + (t + 1f) * inLeg * 3f;
        return home + outLeg + (t - 1f) * inLeg * 3f;
    }

    /** How much of a place's tint the glass wears: full on screen, gone one width away. */
    public static float tintWeight(float t) {
        return Math.max(0f, 1f - presence(t));
    }
}
