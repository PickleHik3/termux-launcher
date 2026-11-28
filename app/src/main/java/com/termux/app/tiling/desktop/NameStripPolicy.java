package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;

import com.termux.app.tiling.DwindleTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which tiled windows show a name strip along their top edge (#422): those on screen that no tab
 * strip names. Full screen shows none; the caller passes no windows then.
 */
final class NameStripPolicy {
    private NameStripPolicy() {}

    @NonNull
    static List<Long> named(@NonNull Collection<Long> live, @NonNull List<DwindleTree.TabStrip> strips) {
        List<Long> out = new ArrayList<>();
        for (long id : live) {
            boolean onStrip = false;
            for (DwindleTree.TabStrip strip : strips) {
                for (long w : strip.windows) if (w == id) onStrip = true;
            }
            if (!onStrip) out.add(id);
        }
        return out;
    }

    /** The top inset a terminal gives its text for a name strip {@code stripDp} thin: none without one. */
    static int insetPx(float stripDp, float density, boolean hasStrip) {
        return hasStrip ? Math.round(stripDp * density) : 0;
    }

    /**
     * Where the strip's icon starts, in px from the window's left: {@code padPx}, or further in
     * where the window's corner of radius {@code cornerPx} would clip an icon whose top is
     * {@code iconTopPx} below the window's top.
     */
    static float leadPx(float cornerPx, float iconTopPx, float padPx) {
        if (cornerPx <= 0f || iconTopPx >= cornerPx) return padPx;
        float rise = cornerPx - Math.max(0f, iconTopPx);
        float edge = cornerPx - (float) Math.sqrt(Math.max(0f, cornerPx * cornerPx - rise * rise));
        return Math.max(padPx, (float) Math.ceil(edge));
    }

    /**
     * Where a Float's title starts, in px from its left (round 5: it sat on the rim's curve): clear
     * of the corner of radius {@code cornerPx} from the strip's top down ({@link #leadPx} with the
     * text's box starting under the rim), then clear of the rim, {@code rimPx}, as a tile's name
     * stands clear of its window's edge.
     */
    static float floatTitleLeadPx(float cornerPx, float rimPx, float padPx) {
        return leadPx(cornerPx, Math.max(0f, rimPx), padPx) + Math.max(0f, rimPx);
    }
}
