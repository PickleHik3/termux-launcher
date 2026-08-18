package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import static com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.clamp01;
import static com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.ramp;

/**
 * Pure choreography for the one band above the drawer plane — the app-owned top status bar
 * ({@code terminal_window_bar_host}) — the mirror of {@link AppDrawerAccessoryChoreography}'s
 * two bands below it.
 *
 * <p>The plane's open rectangle starts at the top of the drawer host, so the bar is swallowed
 * rather than left standing over the drawer under the backdrop tint. It leaves the way the bottom
 * bands do: transforms only, on a band captured once at drag begin. Nothing here may relayout the
 * pane — its height is the terminal's height, so animating it would run the two-state collapse
 * animator and a SIGWINCH per frame, which is exactly what the transition freezes.
 *
 * <p>Three channels, in the order the eye reads them:
 *
 * <ul>
 *   <li><b>Collapse</b> — an EXPANDED pane visually becomes a COMPACT one. The pane's rows are
 *       bottom-aligned inside it, so clipping {@code height - compactHeight} off the top is the
 *       collapse: the widget area above the window row goes first, exactly as the real animator's
 *       shrink shows it. A pane that is already compact has nothing to clip and skips this by
 *       construction.
 *   <li><b>Slide</b> — the compact remnant travels up by the band's full height.
 *   <li><b>Fade</b> — held back until the slide is under way, so the bar reads as leaving rather
 *       than dissolving in place.
 * </ul>
 *
 * <p>The clip is the maximum of the collapse clip and the slide distance, never their sum: the
 * slide's share of it is a <em>ceiling</em> at the band's original top edge, not more content
 * removal. Without it the bar would draw above its own layout bounds — every ancestor here sets
 * {@code clipChildren="false"} — and appear in the system status-bar inset strip, which the plane
 * cannot cover.
 */
public final class AppDrawerStatusBandChoreography {

    /** The expanded pane's extras go early; by a quarter of the transition it reads as compact. */
    private static final float COLLAPSE_START = 0.02f;
    private static final float COLLAPSE_END = 0.28f;
    /** The remnant's exit, finishing before the plane's top edge would have overtaken it. */
    private static final float SLIDE_START = 0.10f;
    private static final float SLIDE_END = 0.60f;
    private static final float FADE_START = 0.32f;
    private static final float FADE_END = 0.62f;

    private AppDrawerStatusBandChoreography() {}

    /** Immutable per-frame output for the top band. */
    public static final class Result {

        /** Never positive: the band only ever travels up. */
        public final float translationY;
        /** Pixels clipped off the top of the band; 0 means the band draws whole. */
        public final float clipTopPx;
        public final float alpha;

        Result(float translationY, float clipTopPx, float alpha) {
            this.translationY = translationY;
            this.clipTopPx = clipTopPx;
            this.alpha = alpha;
        }
    }

    /**
     * @param progress       0 = dock, 1 = full drawer
     * @param bandHeightPx   the measured pane height, captured at drag begin
     * @param compactHeightPx the height the same pane has in COMPACT, clamped to the band
     */
    @NonNull
    public static Result resolve(float progress, float bandHeightPx, float compactHeightPx) {
        float p = clamp01(progress);
        float height = Math.max(0f, bandHeightPx);
        float compact = Math.max(0f, Math.min(compactHeightPx, height));
        float collapse = (height - compact) * ramp(p, COLLAPSE_START, COLLAPSE_END);
        float slide = height * ramp(p, SLIDE_START, SLIDE_END);
        float clip = Math.min(height, Math.max(collapse, slide));
        return new Result(-slide, clip, 1f - ramp(p, FADE_START, FADE_END));
    }
}
