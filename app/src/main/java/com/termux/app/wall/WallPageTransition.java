package com.termux.app.wall;

import androidx.annotation.NonNull;

import com.termux.app.launcher.home.HomePageTransition;

/**
 * Settings' Page transition on the wall: the one effect that turns Home's pages also turns the
 * wall's — across the seam between Home and the Workspace, and between the Workspace's windows,
 * the display window's among them. Each page is posed by where its slide puts it, a function of
 * that one number, so a drag, the spring back, a committed slide and a window's arrival all draw
 * the same curve as Home does ({@link HomePageTransition#transform}).
 *
 * <p>Only view properties: translation, scale, alpha, the turn about the vertical axis. Glass on a
 * page stays on the wallpaper because it aims past the page's whole transform
 * ({@code GlassAnchor#place}), as it already does under the sink and the plank.
 *
 * <p>What does not take the effect:
 * <ul>
 *   <li>A page whose picture is a live surface — the display window — slides: a surface takes no
 *       alpha, layer or turn ({@link SurfacePage}). The page beside it still takes its half.</li>
 *   <li>The chrome that travels with the seam — the dock's rows, the status bar, the keyboard —
 *       is not a page: it moves between the two places' states on its own path
 *       ({@code PlaceChromeTravel}) and never reads this.</li>
 *   <li>A pull against either end of the row, where no page waits, only slides.</li>
 * </ul>
 */
public final class WallPageTransition {

    private WallPageTransition() {}

    /** Which effect a page draws: the chosen one, or the slide for a live surface. */
    @NonNull
    public static HomePageTransition effectFor(@NonNull HomePageTransition chosen,
                                               boolean liveSurface) {
        return liveSurface ? HomePageTransition.SLIDE : chosen;
    }

    /**
     * Fills {@code out} for a page whose slide puts it {@code slidePx} from its rest on a wall
     * {@code widthPx} wide. {@code turning} is whether another page — or window — waits on the
     * side the wall is moving toward. A page a whole width or more away is parked where its slide
     * puts it, flat, as the slide always parked it; so is every page under the slide itself, to
     * the exact float the slide wrote.
     */
    public static void pose(@NonNull HomePageTransition effect, float slidePx, int widthPx,
                            boolean turning, @NonNull HomePageTransition.Transform out) {
        if (effect == HomePageTransition.SLIDE || !turning || widthPx <= 0
                || !(Math.abs(slidePx) < widthPx)) {
            flat(slidePx, out);
            return;
        }
        effect.transform(slidePx / widthPx, widthPx, true, out);
    }

    /** Whether {@code t} only moves the page: no scale, fade or turn, so it needs no layer. */
    public static boolean isFlat(@NonNull HomePageTransition.Transform t) {
        return t.scale == 1f && t.alpha == 1f && t.rotationY == 0f;
    }

    /**
     * Which of two pages sharing the screen draws over the other: +1 the one on the right, -1 the
     * one on the left, 0 either (they do not overlap, or meet only at an edge). Depth slides the
     * page coming from the right over the one sinking on the left; Stack lifts the page on the
     * left off the one rising beneath it on the right.
     */
    public static int topSide(@NonNull HomePageTransition effect) {
        switch (effect) {
            case DEPTH: return 1;
            case STACK: return -1;
            default: return 0;
        }
    }

    /**
     * Whether a window leaving as another arrives from {@code direction} (+1 from the right) is
     * drawn beneath the arriving one. The leaving window's picture is a card laid over the wall,
     * so the card goes under the wall where the effect puts the leaving page beneath.
     */
    public static boolean departureBeneath(@NonNull HomePageTransition effect, int direction) {
        int top = topSide(effect);
        int leavingSide = direction > 0 ? -1 : 1;
        return top != 0 && top != leavingSide;
    }

    /**
     * Where a leaving window is pictured, as a slide in px: its card is captured flat at this
     * slide and then posed from there, so the card begins exactly where the page stood. The slide
     * keeps today's capture (the page where the finger left it). An effect that only moves the
     * page is captured where it is drawn, so its glass is captured over the wallpaper it shows;
     * one that turns it is captured at rest, whole, since a turn can bring an edge the slide
     * pushed past the screen back into view.
     */
    public static float departureCaptureSlidePx(@NonNull HomePageTransition effect, float slidePx,
                                                int widthPx) {
        if (effect == HomePageTransition.SLIDE) return slidePx;
        HomePageTransition.Transform t = new HomePageTransition.Transform();
        pose(effect, slidePx, widthPx, true, t);
        return t.rotationY != 0f ? 0f : t.translationX;
    }

    private static void flat(float slidePx, @NonNull HomePageTransition.Transform out) {
        out.translationX = slidePx;
        out.scale = 1f;
        out.alpha = 1f;
        out.rotationY = 0f;
        out.pivotXFraction = 0.5f;
    }
}
