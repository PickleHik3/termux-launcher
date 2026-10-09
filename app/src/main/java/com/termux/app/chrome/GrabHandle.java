package com.termux.app.chrome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;

/**
 * The grab handle a floating card is dragged by: a thin rounded pill in the middle of a short
 * strip, {@link R.drawable#floating_keyboard_grab_handle} in the on-surface-variant colour. The
 * floating keyboard's card carries it along its top, the dictation pill's card along its bottom;
 * both are moved the same way, by {@link Drag}.
 */
public final class GrabHandle {

    /** The strip the pill sits in, and the height a finger has to land in. */
    public static final float ROW_DP = 18f;

    /** The pill drawn in the middle of that strip. */
    public static final float PILL_WIDTH_DP = 52f;
    public static final float PILL_HEIGHT_DP = 3.2f;

    /**
     * A 3.2dp pill rounds away to a hairline on the lowest densities, so it never draws thinner
     * than this. {@link R.drawable#floating_keyboard_grab_handle} carries the matching radius.
     */
    public static final int PILL_MIN_HEIGHT_PX = 2;

    private GrabHandle() {}

    /** The pill's drawn thickness, never below {@link #PILL_MIN_HEIGHT_PX}. */
    public static int pillHeightPx(float density) {
        return Math.max(PILL_MIN_HEIGHT_PX, Math.round(PILL_HEIGHT_DP * density));
    }

    /** The pill itself, to centre in a {@link #ROW_DP} strip with {@link #pillParams}. */
    @NonNull
    public static View newPill(@NonNull Context context) {
        View pill = new View(context);
        pill.setBackgroundResource(R.drawable.floating_keyboard_grab_handle);
        return pill;
    }

    /** {@link #PILL_WIDTH_DP} by {@link #pillHeightPx}, centred in its strip. */
    @NonNull
    public static FrameLayout.LayoutParams pillParams(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return new FrameLayout.LayoutParams(Math.round(PILL_WIDTH_DP * density),
            pillHeightPx(density), Gravity.CENTER);
    }

    /**
     * One drag of a handle: where the card goes is where it was when the finger came down, moved by
     * however far the finger has gone since, in raw screen coordinates — the card moves under the
     * finger, so view-local deltas would feed back into themselves. Clamping is the caller's, since
     * only it knows the room the card may travel in.
     */
    public static final class Drag {
        private float mDownRawX;
        private float mDownRawY;
        private int mStartX;
        private int mStartY;
        private boolean mActive;

        public void begin(float rawX, float rawY, int startX, int startY) {
            mDownRawX = rawX;
            mDownRawY = rawY;
            mStartX = startX;
            mStartY = startY;
            mActive = true;
        }

        public boolean isActive() {
            return mActive;
        }

        public void end() {
            mActive = false;
        }

        /** Where the card's leading edge goes for the finger at {@code rawX}. */
        public int x(float rawX) {
            return mStartX + Math.round(rawX - mDownRawX);
        }

        /** Where the card's top edge goes for the finger at {@code rawY}. */
        public int y(float rawY) {
            return mStartY + Math.round(rawY - mDownRawY);
        }

        /** Whether the finger has gone further than {@code slopPx} from where it came down. */
        public boolean isPast(float rawX, float rawY, float slopPx) {
            return Math.hypot(rawX - mDownRawX, rawY - mDownRawY) > slopPx;
        }
    }
}
