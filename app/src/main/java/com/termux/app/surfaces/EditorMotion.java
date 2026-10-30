package com.termux.app.surfaces;

import android.animation.TimeInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Appearance editor's motion tokens (editor-cards spec §6): four M3 Expressive springs of
 * mass 1, and every use in the editor named against one. With {@code ω = √k}, a spring settles
 * to within 2% at {@code t = ln(1/0.02) / (ζω)} when underdamped (its envelope), and at
 * {@code t = 5.83 / ω} when critically damped ({@code ζ = 1}, the standard 2% settle of an
 * approach with no overshoot). A use's duration is its spring's settle
 * time (to within 2% of the distance) and its curve is the spring's own step response, so a
 * property animator plays the spring exactly. The page swipe keeps {@code SettleSpring}.
 * Reduced motion: a spatial use jumps (or, where it also fades, runs its fade spring); fades run.
 */
public final class EditorMotion {

    private EditorMotion() {}

    /** The four springs. */
    public enum Token {
        SPATIAL_FAST(0.6, 800, true),
        SPATIAL_DEFAULT(0.8, 380, true),
        EFFECTS_FAST(1.0, 3800, false),
        EFFECTS_DEFAULT(1.0, 1600, false);

        public final double dampingRatio;
        public final double stiffness;
        /** Whether the spring moves something (true) or fades it (false). */
        public final boolean spatial;
        private final long mSettleMs;

        Token(double dampingRatio, double stiffness, boolean spatial) {
            this.dampingRatio = dampingRatio;
            this.stiffness = stiffness;
            this.spatial = spatial;
            this.mSettleMs = settle(dampingRatio, Math.sqrt(stiffness));
        }

        /** The time the spring takes to stay within 2% of the distance. */
        public long settleMs() {
            return mSettleMs;
        }

        /** The spring's progress from 0 to 1 at {@code tMs}; it may pass 1 (overshoot). */
        public float progressAt(double tMs) {
            return progressAt(tMs, 0.0);
        }

        /**
         * The progress at {@code tMs} of a spring that starts moving toward its end at
         * {@code velocity} distances per second (a release velocity over the distance left).
         */
        public float progressAt(double tMs, double velocity) {
            return (float) (1.0 - displacement(dampingRatio, Math.sqrt(stiffness), tMs / 1000.0,
                velocity));
        }
    }

    /** Every motion of the editor, by the token it plays. */
    public enum Use {
        /** The sheet moving between detents. */
        SHEET_DETENT(Token.SPATIAL_DEFAULT, null),
        /** The sheet falling away as the editor closes. */
        SHEET_FALL(Token.SPATIAL_DEFAULT, null),
        /** The frame's pose (its scale and place) as the sheet moves. */
        FRAME_POSE(Token.SPATIAL_DEFAULT, null),
        /** The editor coming in: the launcher scales into the frame, the chrome fades in. */
        EDITOR_ENTER(Token.SPATIAL_DEFAULT, Token.EFFECTS_DEFAULT),
        /** The editor leaving: the frame scales back out, the chrome fades away. */
        EDITOR_EXIT(Token.SPATIAL_DEFAULT, Token.EFFECTS_FAST),
        /** A card's or the pill's reveal fading in. */
        REVEAL_IN(Token.EFFECTS_DEFAULT, Token.EFFECTS_DEFAULT),
        /** A card's or the pill's reveal fading out. */
        REVEAL_OUT(Token.EFFECTS_FAST, Token.EFFECTS_FAST),
        /** The outline overlay fading in. */
        OVERLAY_IN(Token.EFFECTS_DEFAULT, Token.EFFECTS_DEFAULT),
        /** The outline overlay fading out. */
        OVERLAY_OUT(Token.EFFECTS_FAST, Token.EFFECTS_FAST),
        /** The ring moving to the new card's surface, and the body settling after a swap. */
        RING_MOVE(Token.SPATIAL_FAST, null),
        BODY_SETTLE(Token.SPATIAL_FAST, Token.EFFECTS_DEFAULT),
        /** A value springing to its new cell (Reset this row, a chip change). */
        VALUE_SPRING(Token.SPATIAL_FAST, null),
        /** The header's title, glyph and chip labels fading out before a card swap. */
        LABEL_OUT(Token.EFFECTS_FAST, Token.EFFECTS_FAST),
        /** The header's labels fading back in, relabelled. */
        LABEL_IN(Token.EFFECTS_DEFAULT, Token.EFFECTS_DEFAULT),
        /** The card's body fading out before a swap; {@link #BODY_SETTLE} brings the new one in. */
        BODY_OUT(Token.EFFECTS_FAST, Token.EFFECTS_FAST),
        /** The ring fading out (a page swipe, the Wallpaper page rising). */
        RING_FADE_OUT(Token.EFFECTS_FAST, Token.EFFECTS_FAST),
        /** The ring fading in on its new target. */
        RING_FADE_IN(Token.EFFECTS_DEFAULT, Token.EFFECTS_DEFAULT),
        /** The Wallpaper page rising over the sheet, or falling away. */
        WALLPAPER_PAGE(Token.SPATIAL_DEFAULT, null),
        /** The editor bar dropping 8dp into place as the editor comes in. */
        BAR_DROP(Token.SPATIAL_FAST, null),
        /** A row's dot fading out on Reset this row. */
        DOT_OUT(Token.EFFECTS_FAST, Token.EFFECTS_FAST),
        /** A row's dot fading back in, in the new cell's colour. */
        DOT_IN(Token.EFFECTS_DEFAULT, Token.EFFECTS_DEFAULT);

        @NonNull public final Token token;
        /** What runs under reduced motion: a fade spring, or null for a jump. */
        @Nullable public final Token reduced;

        Use(@NonNull Token token, @Nullable Token reduced) {
            this.token = token;
            this.reduced = reduced;
        }

        /** The spring this use plays now: under reduced motion, its fade or none (a jump). */
        @Nullable
        public Token tokenFor(boolean reducedMotion) {
            return reducedMotion && token.spatial ? reduced : token;
        }

        /** How long the use runs: its spring's settle time, 0 for a jump. */
        public long durationMs(boolean reducedMotion) {
            Token played = tokenFor(reducedMotion);
            return played == null ? 0L : played.settleMs();
        }

        /** How long the use runs with motion on. */
        public long settleMs() {
            return token.settleMs();
        }

        /** The use's curve for a property animator over {@link #durationMs}. */
        @NonNull
        public TimeInterpolator curve(boolean reducedMotion) {
            return curve(reducedMotion, 0.0);
        }

        /**
         * The curve of a move that starts at {@code velocity} distances per second toward its
         * end (rule e: a detent takes the release velocity as the spring's initial velocity).
         */
        @NonNull
        public TimeInterpolator curve(boolean reducedMotion, double velocity) {
            Token played = tokenFor(reducedMotion);
            Token curve = played == null ? token : played;
            long settle = curve.settleMs();
            return fraction -> fraction >= 1f ? 1f : curve.progressAt(fraction * settle, velocity);
        }
    }

    /** The transitions of the editor-cards spec §7, each by the uses it plays. */
    public enum Rule {
        /** a. Card to card: labels and body out, then in relabelled; the ring moves. */
        CARD_SWAP(Use.LABEL_OUT, Use.LABEL_IN, Use.BODY_OUT, Use.BODY_SETTLE, Use.RING_MOVE),
        /** b. Surface to Appearance and back: as a, the ring growing to the frame. */
        APPEARANCE_SWAP(Use.LABEL_OUT, Use.LABEL_IN, Use.BODY_OUT, Use.BODY_SETTLE,
            Use.RING_MOVE),
        /** c. The page swipe: the ring fades out, the card swaps by a, the ring fades in. */
        PAGE_SWIPE(Use.RING_FADE_OUT, Use.RING_FADE_IN, Use.LABEL_OUT, Use.LABEL_IN,
            Use.BODY_OUT, Use.BODY_SETTLE),
        /** d. The Wallpaper page rises over the sheet and falls back; the ring fades. */
        WALLPAPER_PAGE(Use.WALLPAPER_PAGE, Use.RING_FADE_OUT, Use.RING_FADE_IN),
        /** e. A detent move, with the release velocity. */
        DETENT(Use.SHEET_DETENT, Use.FRAME_POSE),
        /** f. The editor coming in and going out. */
        EDITOR(Use.EDITOR_ENTER, Use.EDITOR_EXIT, Use.BAR_DROP, Use.SHEET_FALL),
        /** g. Reset this row: the value springs, the dot fades out and in. */
        RESET_ROW(Use.VALUE_SPRING, Use.DOT_OUT, Use.DOT_IN);

        @NonNull private final Use[] mUses;

        Rule(@NonNull Use... uses) {
            mUses = uses;
        }

        @NonNull
        public Use[] uses() {
            return mUses.clone();
        }
    }

    /** x(t) of a unit step on a spring of damping ratio {@code zeta}, natural frequency {@code w}. */
    static double displacement(double zeta, double w, double t) {
        return displacement(zeta, w, t, 0.0);
    }

    /**
     * {@link #displacement(double, double, double)} for a spring already moving toward its end at
     * {@code u} distances per second (x(0) = 1, x'(0) = -u).
     */
    static double displacement(double zeta, double w, double t, double u) {
        if (zeta < 1.0) {
            double wd = w * Math.sqrt(1 - zeta * zeta);
            return Math.exp(-zeta * w * t)
                * (Math.cos(wd * t) + (zeta * w - u) / wd * Math.sin(wd * t));
        }
        return (1 + (w - u) * t) * Math.exp(-w * t);
    }

    /** The 2% settle of a critically damped spring, in units of 1/ω. */
    static final double CRITICAL_SETTLE = 5.83;

    /** The 2% settle time in ms: the envelope's when underdamped, 5.83/ω when critical. */
    static long settle(double zeta, double w) {
        double seconds = zeta < 1.0 ? Math.log(1 / 0.02) / (zeta * w) : CRITICAL_SETTLE / w;
        return Math.round(seconds * 1000.0);
    }
}
