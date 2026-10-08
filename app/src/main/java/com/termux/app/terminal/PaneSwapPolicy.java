package com.termux.app.terminal;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The rules behind swapping a split pane with its neighbour by a two-finger flick: which pane sits
 * across a given edge, and when two fingers on a pane are a flick rather than the terminal's own
 * pinch or scroll. Pure, so the controller only applies the answer.
 */
public final class PaneSwapPolicy {

    /** The edge of the pane a flick heads for, in screen terms (UP is towards the top). */
    public enum Direction { LEFT, RIGHT, UP, DOWN }

    private PaneSwapPolicy() {}

    /**
     * The candidate that shares {@code source}'s edge in {@code dir}: its near edge lies within
     * {@code tolerancePx} of that edge (the divider gap between two panes) and it overlaps the
     * source along the other axis. With several, the one sharing the most of the edge wins, then
     * the one whose centre is nearest. A full-width pane above two columns is therefore the
     * neighbour of either column going up.
     *
     * @return the candidate's index, or -1 when nothing borders that edge
     */
    public static int neighbourAcross(@NonNull RectF source, @NonNull List<RectF> candidates,
                                      @NonNull Direction dir, float tolerancePx) {
        int best = -1;
        float bestOverlap = 0f;
        float bestDistance = Float.MAX_VALUE;
        float sourceCx = (source.left + source.right) / 2f;
        float sourceCy = (source.top + source.bottom) / 2f;
        for (int i = 0; i < candidates.size(); i++) {
            RectF c = candidates.get(i);
            if (c == null || c == source) continue;
            float gap;
            float overlap;
            switch (dir) {
                case LEFT:  gap = source.left - c.right;  overlap = overlapY(source, c); break;
                case RIGHT: gap = c.left - source.right;  overlap = overlapY(source, c); break;
                case UP:    gap = source.top - c.bottom;  overlap = overlapX(source, c); break;
                default:    gap = c.top - source.bottom;  overlap = overlapX(source, c); break;
            }
            if (Math.abs(gap) > tolerancePx || overlap <= 0f) continue;
            float dx = (c.left + c.right) / 2f - sourceCx;
            float dy = (c.top + c.bottom) / 2f - sourceCy;
            float distance = dx * dx + dy * dy;
            if (overlap > bestOverlap || (overlap == bestOverlap && distance < bestDistance)) {
                best = i;
                bestOverlap = overlap;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static float overlapX(@NonNull RectF a, @NonNull RectF b) {
        return Math.min(a.right, b.right) - Math.max(a.left, b.left);
    }

    private static float overlapY(@NonNull RectF a, @NonNull RectF b) {
        return Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
    }

    /**
     * Tells a brisk two-finger flick on a pane from the terminal's own two-finger gestures. It
     * watches one touch stream from its first finger; everything it has not claimed as a flick
     * stays the terminal's, so a pinch still zooms and a slow two-finger drag still scrolls.
     *
     * <p>Feed it {@link #update} on every event with the fingers still down after that event, and
     * {@link #reset} on the stream's first finger.
     */
    public static final class TwoFingerSwipe {

        /** How far the fingers' midpoint must travel to count as a flick. */
        static final float SWIPE_DISTANCE_DP = 48f;
        /** How soon after the second finger lands that travel must be covered. */
        static final long SWIPE_TIME_MS = 300L;
        /** How much the travel's main axis must exceed the other one; a diagonal is no direction. */
        static final float DOMINANT_AXIS_RATIO = 1.5f;

        public enum State {
            /** Fewer than two fingers have landed yet. */
            IDLE,
            /** Two fingers are down and have not yet said what they are doing. */
            TRACKING,
            /** The fingers are pinching: the terminal's zoom, for the rest of the stream. */
            PINCH,
            /** Anything else the terminal owns, for the rest of the stream. */
            PASS_THROUGH,
            /** A flick; {@link #direction()} says where. Final for the stream. */
            SWIPED
        }

        private final float mSlopPx;
        private final float mSwipeDistancePx;
        private State mState = State.IDLE;
        @Nullable private Direction mDirection;
        private float mStartX, mStartY, mStartSpread;
        private long mStartTime;

        /**
         * @param density     display density, turning {@link #SWIPE_DISTANCE_DP} into pixels
         * @param touchSlopPx the platform's touch slop, the spread change that makes a pinch
         */
        public TwoFingerSwipe(float density, float touchSlopPx) {
            mSlopPx = touchSlopPx;
            mSwipeDistancePx = SWIPE_DISTANCE_DP * density;
        }

        /** Forget the last stream; call on its first finger landing. */
        public void reset() {
            mState = State.IDLE;
            mDirection = null;
        }

        @NonNull public State state() {
            return mState;
        }

        /** Where the flick went, once {@link #state()} is {@link State#SWIPED}; null before. */
        @Nullable public Direction direction() {
            return mDirection;
        }

        /**
         * One event of the stream.
         *
         * @param pointersDown fingers still down after this event (a lifting finger not counted)
         * @param centroidX    the midpoint of the fingers down
         * @param centroidY    the midpoint of the fingers down
         * @param spread       the distance between the two fingers (any value with fewer)
         * @param timeMs       the event time
         */
        @NonNull
        public State update(int pointersDown, float centroidX, float centroidY, float spread,
                            long timeMs) {
            switch (mState) {
                case IDLE:
                    if (pointersDown > 2) {
                        mState = State.PASS_THROUGH;
                    } else if (pointersDown == 2) {
                        mState = State.TRACKING;
                        mStartX = centroidX;
                        mStartY = centroidY;
                        mStartSpread = spread;
                        mStartTime = timeMs;
                    }
                    return mState;
                case TRACKING:
                    if (pointersDown != 2) {
                        mState = State.PASS_THROUGH;
                        return mState;
                    }
                    return decide(centroidX - mStartX, centroidY - mStartY,
                        spread - mStartSpread, timeMs - mStartTime);
                default:
                    return mState;
            }
        }

        @NonNull
        private State decide(float dx, float dy, float spreadChange, long elapsedMs) {
            float travel = (float) Math.hypot(dx, dy);
            float spread = Math.abs(spreadChange);
            if (spread > mSlopPx && spread > travel) {
                mState = State.PINCH;
            } else if (travel >= mSwipeDistancePx) {
                Direction direction = dominantDirection(dx, dy);
                if (elapsedMs <= SWIPE_TIME_MS && direction != null) {
                    mState = State.SWIPED;
                    mDirection = direction;
                } else {
                    mState = State.PASS_THROUGH;
                }
            } else if (elapsedMs > SWIPE_TIME_MS) {
                // Too slow to still become a flick: a drag, which is the terminal's scroll.
                mState = State.PASS_THROUGH;
            }
            return mState;
        }

        @Nullable
        private static Direction dominantDirection(float dx, float dy) {
            float ax = Math.abs(dx), ay = Math.abs(dy);
            if (ax >= ay * DOMINANT_AXIS_RATIO) return dx < 0 ? Direction.LEFT : Direction.RIGHT;
            if (ay >= ax * DOMINANT_AXIS_RATIO) return dy < 0 ? Direction.UP : Direction.DOWN;
            return null;
        }
    }
}
