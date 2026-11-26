package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * A window's controls on its mark, pure: which marks open them, the hold that opens them, and
 * where they stand in the title's place. A touch on a window's mark (a Terminal window, a Linux
 * window, a Display) that keeps still for {@link #HOLD_MS} turns the title's place at the row's
 * end into Minimise, Maximise, Move (Linux windows only) and Close, Close last, with the window's
 * name beside them; the finger slides onto one and lifts. Page marks (Home, Overview, Assistant) never open them.
 *
 * <p>Positions run along the line in the rest layout's coordinates, as {@link MarksZoomPlan}'s
 * do; the view maps them to x (mirrored under right-to-left, so Close stays at the line's end) or
 * to y on a column.
 */
public final class MarkControls {

    /** What a control does to the window it was opened for. */
    public enum Control { MINIMISE, MAXIMISE, MOVE, CLOSE }

    /** How long a touch keeps still on a window's mark before its controls open, in ms. */
    public static final long HOLD_MS = 400L;
    /** The gap between two controls, and between the name and the first control, in dp. */
    public static final float GAP_DP = 4f;

    private static final Control[] WINDOW = {Control.MINIMISE, Control.MAXIMISE, Control.CLOSE};
    /** A Display page is the display itself: it has no Overview card to wait as, so no Minimise. */
    private static final Control[] DISPLAY = {Control.MAXIMISE, Control.CLOSE};
    private static final Control[] LINUX = {Control.MINIMISE, Control.MAXIMISE, Control.MOVE,
        Control.CLOSE};

    private MarkControls() {
    }

    /**
     * Whether holding {@code entry}'s mark opens its controls: a Terminal window (not the Terminal
     * page with no window, which starts a shell), a Linux window or a Display page. A folded stack
     * ({@code folded}) and Overview ({@code entry} null) open nothing.
     */
    public static boolean opens(@Nullable PageBarModel.Entry entry, boolean folded) {
        if (entry == null || folded) return false;
        switch (entry.kind) {
            case TERMINAL: return entry.target.index >= 0;
            case LINUX:
            case DISPLAY: return true;
            default: return false;
        }
    }

    /**
     * The controls a window of {@code kind} shows, in order along the line, Close last: Move for a
     * Linux window only, no Minimise for a Display page.
     */
    @NonNull
    public static Control[] controlsFor(@NonNull PageKind kind) {
        switch (kind) {
            case LINUX: return LINUX.clone();
            case DISPLAY: return DISPLAY.clone();
            default: return WINDOW.clone();
        }
    }

    /**
     * Where {@code count} round controls {@code sizePx} across stand along a line ending at
     * {@code lineEndPx}: packed against the end with {@link #GAP_DP} between them.
     */
    public static final class Layout {
        private final int mCount;
        private final float mSize;
        private final float mGap;
        private final float mReserve;
        private final float mStart;
        private final boolean mAtStart;

        /**
         * @param count how many controls
         * @param sizePx one control's diameter
         * @param lineStartPx where the row starts along the line
         * @param lineEndPx where the row ends along the line
         * @param namePx the held window's name capsule standing before the controls; 0 for none
         * @param fingerPx the finger along the line: when it is over the controls' and the name's
         *                 place at the row's end they stand at its start instead, as the title
         *                 goes round, so the lift that opened them never lands on one
         */
        public Layout(int count, float sizePx, float lineStartPx, float lineEndPx, float namePx,
                      float fingerPx, float density) {
            mCount = Math.max(0, count);
            mSize = Math.max(0f, sizePx);
            mGap = GAP_DP * density;
            mReserve = namePx > 0f ? namePx + mGap : 0f;
            float end = lineEndPx - lengthPx();
            mAtStart = fingerPx >= end - mReserve - mGap;
            mStart = mAtStart ? lineStartPx + mReserve : end;
        }

        public int count() {
            return mCount;
        }

        /** One control's diameter. */
        public float sizePx() {
            return mSize;
        }

        public float gapPx() {
            return mGap;
        }

        /** Whether the controls went round to the row's start, away from the finger. */
        public boolean atStart() {
            return mAtStart;
        }

        /** The controls' whole extent along the line. */
        public float lengthPx() {
            return mCount == 0 ? 0f : mCount * mSize + (mCount - 1) * mGap;
        }

        /** Where the first control starts along the line. */
        public float startPx() {
            return mStart;
        }

        /** Where the name's capsule ends along the line: a gap before the first control. */
        public float nameEndPx() {
            return mStart - mGap;
        }

        /** The centre of control {@code i} along the line. */
        public float centreAt(int i) {
            return mStart + i * (mSize + mGap) + mSize / 2f;
        }

        /**
         * The control under a finger at {@code pos} along the line, or -1: each control owns its
         * box and half the gaps beside it, nothing before the first or past the last.
         */
        public int controlAt(float pos) {
            if (mCount == 0 || pos < mStart - mGap / 2f || pos > mStart + lengthPx() + mGap / 2f) {
                return -1;
            }
            int i = (int) Math.floor((pos - mStart + mGap / 2f) / (mSize + mGap));
            return Math.max(0, Math.min(mCount - 1, i));
        }

        /** Whether a mark centred at {@code centrePx} lies under the controls or the name: they cover it. */
        public boolean covers(float centrePx) {
            return mCount > 0 && centrePx >= mStart - mReserve - mGap
                && centrePx <= mStart + lengthPx() + mGap;
        }

        /**
         * The left edge of control {@code i} on screen in a view {@code viewWidthPx} wide: along x
         * from the start, mirrored under right-to-left so the line's end (Close) is on the left.
         */
        public float leftPx(int i, float viewWidthPx, boolean rtl) {
            float start = centreAt(i) - mSize / 2f;
            return rtl ? viewWidthPx - start - mSize : start;
        }
    }

    /**
     * The hold: armed at the down on a mark that {@link #opens}, cancelled by a move past the
     * touch slop along or across the line, by the lift and by any end; {@link #fire} when the
     * timer runs out opens the controls once. The view runs the timer; this says what it means.
     */
    public static final class Hold {
        private boolean mArmed;
        private boolean mOpen;
        private float mDownAlong;
        private float mDownAcross;

        /** A touch went down at ({@code along}, {@code across}); arms when the mark opens controls. */
        public boolean arm(boolean opens, float along, float across) {
            mOpen = false;
            mArmed = opens;
            mDownAlong = along;
            mDownAcross = across;
            return mArmed;
        }

        /**
         * The finger moved: past {@code slopPx} from the down along or across the line before the
         * controls open, the hold is off (the slide goes on as it would). Returns whether it is
         * still armed.
         */
        public boolean moved(float along, float across, float slopPx) {
            if (mArmed && !mOpen && (Math.abs(along - mDownAlong) > slopPx
                || Math.abs(across - mDownAcross) > slopPx)) {
                mArmed = false;
            }
            return mArmed;
        }

        /** The timer ran out: whether the controls open now (armed, and not already open). */
        public boolean fire() {
            if (!mArmed || mOpen) return false;
            mOpen = true;
            return true;
        }

        public boolean armed() {
            return mArmed && !mOpen;
        }

        public boolean open() {
            return mOpen;
        }

        /** The touch ended, whichever way. */
        public void end() {
            mArmed = false;
            mOpen = false;
        }
    }
}
