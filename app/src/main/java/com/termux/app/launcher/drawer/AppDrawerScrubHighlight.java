package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * What a scrub does to one grid cell: dim it, or lift it.
 *
 * <p>Two write paths have to agree on this — the per-frame walk over the grid's attached children and
 * the rule applied at the end of {@code onBindViewHolder} for cells the auto-scroll binds mid-scrub —
 * so the answer lives here once rather than being written twice.
 *
 * <p>Only {@code setAlpha} and {@code setScaleX/Y} come out of this class. Nothing here can change an
 * icon's size, so a scrub cannot put a second rendered bitmap into the shared byte-budgeted icon
 * cache no matter how far it drags.
 *
 * <p><b>The strength-0 identity is load-bearing.</b> With no scrub in progress every cell must come
 * back exactly 1 and 1 — not 0.999 — because that is what makes the drawer byte-identical to B-2 when
 * nobody is scrubbing, and because a holder returned to the pool at 0.28 alpha would otherwise be
 * reused as a permanently dim cell.
 */
public final class AppDrawerScrubHighlight {

    /** Alpha a non-matching cell reaches at full strength. */
    public static final float DIM_ALPHA = 0.28f;
    /** Scale a matching cell reaches at full strength. */
    public static final float MATCH_SCALE = 1.06f;

    /** The no-scrub answer, shared so the common case allocates nothing. */
    public static final Result NEUTRAL = new Result(1f, 1f);

    private AppDrawerScrubHighlight() {}

    /** An immutable pair of view properties. */
    public static final class Result {

        public final float alpha;
        public final float scale;

        Result(float alpha, float scale) {
            this.alpha = alpha;
            this.scale = scale;
        }

        @NonNull
        @Override
        public String toString() {
            return "Result(alpha=" + alpha + ", scale=" + scale + ")";
        }
    }

    /**
     * @param entryLetter  the cell's normalised letter
     * @param activeLetter the letter under the finger, or {@code 0} when there is no scrub
     * @param strength     0 = no scrub, 1 = fully dimmed; the release spring runs it back to 0
     */
    @NonNull
    public static Result resolve(char entryLetter, char activeLetter, float strength) {
        float s = AppDrawerTransitionGeometry.clamp01(strength);
        if (s <= 0f || activeLetter == '\0') return NEUTRAL;
        return new Result(alphaFor(entryLetter, activeLetter, s),
            scaleFor(entryLetter, activeLetter, s));
    }

    /**
     * The alpha alone. The per-frame walk uses the scalar pair rather than {@link #resolve} so that a
     * scrub long enough to run the controller's loop for seconds does not allocate a result per
     * attached child per frame.
     */
    public static float alphaFor(char entryLetter, char activeLetter, float strength) {
        float s = AppDrawerTransitionGeometry.clamp01(strength);
        if (s <= 0f || activeLetter == '\0') return 1f;
        return matches(entryLetter, activeLetter)
            ? 1f : AppDrawerTransitionGeometry.lerp(1f, DIM_ALPHA, s);
    }

    /** The scale alone; see {@link #alphaFor}. */
    public static float scaleFor(char entryLetter, char activeLetter, float strength) {
        float s = AppDrawerTransitionGeometry.clamp01(strength);
        if (s <= 0f || activeLetter == '\0') return 1f;
        return matches(entryLetter, activeLetter)
            ? AppDrawerTransitionGeometry.lerp(1f, MATCH_SCALE, s) : 1f;
    }

    private static boolean matches(char entryLetter, char activeLetter) {
        return Character.toUpperCase(entryLetter) == Character.toUpperCase(activeLetter);
    }
}
