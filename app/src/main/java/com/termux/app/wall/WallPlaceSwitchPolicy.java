package com.termux.app.wall;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Where the place switch's thumb sits for a given wall position. The thumb is a live map of the
 * wall, not an animation of its own: it is driven by the wall's offset, so a drag moves it with
 * the finger and a slide moves it with the spring, and it always lands on the place that came to
 * rest.
 */
public final class WallPlaceSwitchPolicy {

    private WallPlaceSwitchPolicy() {}

    /**
     * The thumb's position in segment units — {@code 1.0} is centred on the second segment,
     * {@code 0.5} halfway between the first and the second.
     *
     * @param offsetPx the wall's signed offset from the current page's rest, positive when the
     *                 wall is displaced to the right (the page on the left coming in)
     * @param widthPx  the wall's width, one page
     */
    public static float thumbPosition(@NonNull List<PaneWallPage> pages, @NonNull PaneWallPage current,
                                      float offsetPx, int widthPx) {
        int from = pages.indexOf(current);
        if (from < 0) return 0f;
        if (widthPx <= 0 || offsetPx == 0f) return from;
        // Displaced right means the left neighbour is sliding in, and vice versa. At a line's end
        // the neighbour is the page itself, so a rubber band leaves the thumb where it is.
        PaneWallPage neighbour = PaneWallPolicy.neighbour(pages, current, offsetPx > 0f ? -1 : 1);
        int to = pages.indexOf(neighbour);
        float progress = Math.min(1f, Math.abs(offsetPx) / widthPx);
        return from + (to - from) * progress;
    }
}
