package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.app.chrome.CornerHold;
import com.termux.app.chrome.CornerHoldArbiter;
import com.termux.app.chrome.CornerZones;

/**
 * Who owns a finger that lands on the app drawer's pane: its corner, which drops the drawer's
 * settings tab after a still hold, or the content, which keeps every event (scroll, tap, the
 * section swipe, the close pull) and whose icons long-press. The decision is the shared
 * {@link CornerHoldArbiter}'s, made once at the down; this adds the drawer's one question, which
 * points the chrome owns outright.
 *
 * <p>An icon's long press is a platform timer at the full system timeout while the hold is
 * three quarters of it, so a still finger in a corner square is claimed (and the content cancelled)
 * before the icon's menu can open; a finger that moves, lifts or adds a second finger first leaves
 * that timer running, and the icon's menu opens as it always did.
 *
 * <p>Pure: fed a touch stream, read back; nothing here knows what a view is.
 */
final class AppDrawerCornerTabPolicy {

    private final CornerHoldArbiter mArbiter = new CornerHoldArbiter();

    /**
     * Whether a point lies in any of the chrome's rectangles ({@code left, top, right, bottom}
     * each, four numbers per rectangle). The search bar, the section switch and the A-Z column
     * answer their own taps, so a corner square never takes a point they hold.
     */
    static boolean chromeOwnsPoint(float x, float y, @NonNull float[] rects, int count) {
        for (int i = 0; i + 3 < count * 4 && i + 3 < rects.length; i += 4) {
            if (x >= rects[i] && x < rects[i + 2] && y >= rects[i + 1] && y < rects[i + 3]) {
                return true;
            }
        }
        return false;
    }

    /** A finger landed; the corner that armed, or {@link CornerZones#NONE} for the content's press. */
    int down(float x, float y, float width, float height, float density, boolean chromeOwns,
             float slopPx) {
        return mArbiter.down(x, y, width, height, density, chromeOwns, slopPx);
    }

    /** The hold time passed; true when the corner just claimed the gesture and the content is owed a cancel. */
    boolean holdElapsed() {
        return mArbiter.holdElapsed();
    }

    /** @return true when the wait was just abandoned, so the hold timer can be dropped. */
    boolean move(float x, float y) {
        return mArbiter.move(x, y) == CornerHold.Move.ABANDONED;
    }

    boolean secondFinger() {
        return mArbiter.secondFinger();
    }

    /** @return true when the lift is the end of a hold: the tab opens at {@link #corner()}. */
    boolean lift() {
        return mArbiter.lift() == CornerHold.Lift.OPEN_TAB;
    }

    void reset() {
        mArbiter.reset();
    }

    boolean isTracking() {
        return mArbiter.isTracking();
    }

    /** Whether the corner owns the gesture: the content has been cancelled and sees nothing more. */
    boolean isClaimed() {
        return mArbiter.isClaimed();
    }

    boolean contentMayLongPress() {
        return mArbiter.contentMayLongPress();
    }

    int corner() {
        return mArbiter.corner();
    }
}
