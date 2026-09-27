package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;

import java.util.List;

/**
 * Where the dock, the keyboard and the status bar's content stand while the wall is between two
 * places. The layout and the look are the same on every place (ADR 0003), so the only chrome a
 * place change moves is state — whether the keyboard is up, whether the place is in minimal mode —
 * and each of those is drawn as a function of the wall's live offset rather than switched when the
 * slide lands. A slow drag, a fling, a reversal and a drag that springs back all read the same
 * function, so they all track the finger exactly and none of them needs a case of its own.
 *
 * <p>Everything here is a transform over what is already laid out: the reveal fractions say how
 * much of each piece is showing, and {@link #stackTranslationPx} turns them into the one
 * {@code translationY} the accessory stack is drawn at. The real layout change — the stack's
 * height, the pane's rows — happens once, at settle, and only if the wall landed somewhere new.
 *
 * <p>Pure: no views, so the interpolation is read and tested as arithmetic.
 */
public final class PlaceChromeTravel {

    private PlaceChromeTravel() {}

    /** What a place's chrome looks like while the wall rests on it. */
    public static final class Rest {
        /** Whether the in-app keyboard is up. */
        public final boolean keyboardUp;
        /** Whether the place is in minimal mode, which puts the dock and the keyboard away. */
        public final boolean minimal;

        public Rest(boolean keyboardUp, boolean minimal) {
            this.keyboardUp = keyboardUp;
            this.minimal = minimal;
        }

        /** How much of the keyboard shows: all of it or none, at rest. */
        public float keyboardReveal() {
            return keyboardUp && !minimal ? 1f : 0f;
        }

        /** How much of the dock's rows show: none in minimal mode. */
        public float dockReveal() {
            return minimal ? 0f : 1f;
        }

        /** How much of the status bar's content shows: none on a minimal place's strip. */
        public float statusReveal() {
            return minimal ? 0f : 1f;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Rest)) return false;
            Rest that = (Rest) other;
            return keyboardUp == that.keyboardUp && minimal == that.minimal;
        }

        @Override public int hashCode() {
            return (keyboardUp ? 1 : 0) * 2 + (minimal ? 1 : 0);
        }

        @NonNull @Override public String toString() {
            return "Rest{keyboard=" + keyboardUp + ", minimal=" + minimal + "}";
        }
    }

    /** Answers what each place looks like at rest. */
    public interface States {
        @NonNull Rest restOf(@NonNull PaneWallPage place);
    }

    /**
     * The stretch of the way, at either end, over which the chrome already rests: the reveals
     * move over the middle {@code 1 - 2 * LANDING} of a step and sit at the far place's state
     * for the last {@code LANDING} of it. The slide's curve ({@code Motion.settle}) spends its
     * last ~300 ms on the last few percent of the way, and a keyboard still creeping up over the
     * text while the page's edge creeps at the screen's edge read as the keyboard landing late
     * (pong, 2026-09-27). The same margin at both ends, so a reversal and a drag that springs
     * back still read one function of position.
     */
    public static final float LANDING = 0.08f;

    /** The chrome for one frame of the wall's motion. */
    public static final class Frame {
        /** The place on the near side of the frame: the one whose state the fraction starts from. */
        @NonNull public final PaneWallPage from;
        /** The place on the far side; the same as {@link #from} at rest and past an outer edge. */
        @NonNull public final PaneWallPage toward;
        /** How far across from {@link #from} to {@link #toward} the screen is, 0 to 1. */
        public final float fraction;
        /** How far across the chrome is: {@link #fraction} with {@link #LANDING} taken off each end. */
        public final float chromeFraction;
        public final float keyboardReveal;
        public final float dockReveal;
        public final float statusReveal;
        /** Whether either side of the frame shows the keyboard, or the dock: what a pre-roll is for. */
        public final boolean keyboardInPlay;
        public final boolean dockInPlay;

        Frame(@NonNull PaneWallPage from, @NonNull PaneWallPage toward, float fraction,
              float keyboardReveal, float dockReveal, float statusReveal,
              boolean keyboardInPlay, boolean dockInPlay) {
            this.from = from;
            this.toward = toward;
            this.fraction = fraction;
            this.chromeFraction = chromeFraction(fraction);
            this.keyboardReveal = keyboardReveal;
            this.dockReveal = dockReveal;
            this.statusReveal = statusReveal;
            this.keyboardInPlay = keyboardInPlay;
            this.dockInPlay = dockInPlay;
        }

        @NonNull @Override public String toString() {
            return "Frame{" + from + "->" + toward + " @" + fraction + ", keyboard=" + keyboardReveal
                + ", dock=" + dockReveal + ", status=" + statusReveal + "}";
        }
    }

    /** {@code fraction} with {@link #LANDING} taken off each end, clamped: where the chrome is. */
    public static float chromeFraction(float fraction) {
        return clamp01((fraction - LANDING) / (1f - 2f * LANDING));
    }

    /**
     * The chrome for the wall standing {@code offsetPx} from {@code current}'s rest. The offset is
     * the wall's own ({@code PaneWallLayout#offsetPx}): positive while the pages sit to the right
     * of where they land, so the place to the left of {@code current} is the one coming into view.
     *
     * <p>Read off the pixels, not off which page the wall has committed to: the commit at release
     * moves {@code current} and the offset together by one page, and the screen shows the same
     * blend of the same two places on either side of it, so the chrome does not jump there either.
     * A drag past an outer page has no neighbour to blend toward, and resists at the page's own
     * state.
     */
    @NonNull
    public static Frame at(@NonNull List<PaneWallPage> pages, @NonNull PaneWallPage current,
                           float offsetPx, int widthPx, @NonNull States states) {
        if (widthPx <= 0 || offsetPx == 0f || Float.isNaN(offsetPx)) {
            return rest(current, states.restOf(current));
        }
        // The screen shows position p, in places relative to current: a page r places away is at
        // r * width + offset, so it is centred on screen when r = -offset / width.
        float position = -offsetPx / widthPx;
        int near = (int) Math.floor(position);
        float fraction = position - near;
        // On a line, a step past the outer page clamps back onto it (neighbour stops there), so a
        // drag into the edge's resistance blends the page with itself and nothing moves.
        PaneWallPage from = PaneWallPolicy.neighbour(pages, current, near);
        PaneWallPage toward = PaneWallPolicy.neighbour(pages, current, near + 1);
        Rest a = states.restOf(from);
        Rest b = toward == from ? a : states.restOf(toward);
        boolean keyboardInPlay = a.keyboardReveal() > 0f || b.keyboardReveal() > 0f;
        boolean dockInPlay = a.dockReveal() > 0f || b.dockReveal() > 0f;
        if (fraction <= 0f || a.equals(b)) {
            // Snap the degenerate cases, so a frame between two places that look the same is
            // exactly their state rather than a rounding of it.
            float f = fraction <= 0f ? 0f : fraction;
            return new Frame(from, toward, f, a.keyboardReveal(), a.dockReveal(),
                a.statusReveal(), keyboardInPlay, dockInPlay);
        }
        // The chrome lands before the wall does (LANDING): the reveals read the shortened way.
        float t = chromeFraction(fraction);
        return new Frame(from, toward, fraction,
            lerp(a.keyboardReveal(), b.keyboardReveal(), t),
            lerp(a.dockReveal(), b.dockReveal(), t),
            lerp(a.statusReveal(), b.statusReveal(), t), keyboardInPlay, dockInPlay);
    }

    /** The chrome of a place at rest. */
    @NonNull
    public static Frame rest(@NonNull PaneWallPage place, @NonNull Rest state) {
        return new Frame(place, place, 0f, state.keyboardReveal(), state.dockReveal(),
            state.statusReveal(), state.keyboardReveal() > 0f, state.dockReveal() > 0f);
    }

    /**
     * How far down the accessory stack is drawn from where it is laid out, for one frame.
     *
     * <p>The stack is laid out for the most it will show during the slide — the keyboard is
     * pre-rolled in before the first frame that needs it, and a minimal place's dock rows likewise
     * — so every frame only ever takes chrome away, by sliding it down past the screen's bottom
     * edge. The keyboard sits under the dock: the part of it that is not revealed is slid out
     * first, and the dock follows it down once the dock itself is going away.
     *
     * @param keyboardLaidOutPx the keyboard's height as the stack lays it out, 0 while it is not in
     *                          the stack (down, or floating in a frame of its own)
     * @param dockLaidOutPx     everything else the stack lays out above the keyboard, plus the
     *                          margin under it, so a dock going away clears the screen entirely
     */
    public static float stackTranslationPx(@NonNull Frame frame, int keyboardLaidOutPx,
                                           int dockLaidOutPx) {
        float keyboard = Math.max(0, keyboardLaidOutPx) * (1f - clamp01(frame.keyboardReveal));
        float dock = Math.max(0, dockLaidOutPx) * (1f - clamp01(frame.dockReveal));
        return keyboard + dock;
    }

    /**
     * Whether a frame is going to show more keyboard than the stack lays out, which is the cue to
     * pre-roll it: lay it out at its full height below the screen, so the slide can bring it up.
     * Read off the places in play rather than the reveal, which rests over the first
     * {@link #LANDING} of the way: the pre-roll belongs to the slide's first frame.
     */
    public static boolean needsKeyboardPreRoll(@NonNull Frame frame, boolean keyboardLaidOut) {
        return !keyboardLaidOut && frame.keyboardInPlay;
    }

    /** As {@link #needsKeyboardPreRoll}, for the dock rows a minimal place has put away. */
    public static boolean needsDockPreRoll(@NonNull Frame frame, boolean dockLaidOut) {
        return !dockLaidOut && frame.dockInPlay;
    }

    /**
     * How far the content reaches past the top of the stack while the wall travels with chrome
     * pre-rolled: exactly what keeps the room the chrome takes from the content where it was
     * committed. The stack can grow for the slide; the content does not hear of it until settle.
     *
     * @param stackHeightPx       the stack's height as this pass lays it out
     * @param stackBottomMarginPx the margin under it
     * @param heldReservationPx   the room the content was committed with before the slide
     */
    public static int heldOverlapPx(int stackHeightPx, int stackBottomMarginPx,
                                    int heldReservationPx) {
        return Math.max(0, Math.max(0, stackHeightPx) + Math.max(0, stackBottomMarginPx)
            - Math.max(0, heldReservationPx));
    }

    /**
     * The place a frame is travelling toward, seen from the place the chrome is committed to:
     * whichever side of the frame is not {@code leaving}. Null while the frame blends the leaving
     * place with itself — at rest, or pressed into an outer edge's resistance.
     */
    @Nullable
    public static PaneWallPage arriving(@NonNull Frame frame, @NonNull PaneWallPage leaving) {
        if (frame.toward != leaving) return frame.toward == frame.from ? null : frame.toward;
        return frame.from == leaving ? null : frame.from;
    }

    /**
     * How far, 0 to 1, the chrome has travelled toward {@code place}: the frame's
     * {@link Frame#chromeFraction} when the place is the frame's far side, the rest of it when the
     * place is the near side, and 0 for a place the frame does not touch. On the chrome's way
     * rather than the wall's, so what follows it — the terminal's rows — lands with the keyboard.
     * A drag that springs back reads back down to 0 through the same number.
     */
    public static float progressToward(@NonNull Frame frame, @NonNull PaneWallPage place) {
        if (frame.toward == frame.from) return 0f;
        if (place == frame.toward) return frame.chromeFraction;
        if (place == frame.from) return 1f - frame.chromeFraction;
        return 0f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
