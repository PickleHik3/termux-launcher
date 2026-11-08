package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The place settle a slide's landing leaves for just after its landed frame.
 *
 * <p>The frame a slide lands in draws the page at rest and the chrome at the end of its travel,
 * which is already what the place looks like there (the travel carried the dock, the keyboard
 * and the bar to the arriving place's state). Making that state the layout (the arrangement, the
 * bar, the keyboard's owner, the content's room) cost 25-36 ms of that very frame on pong, so the
 * page's last step of motion was the late one. The settle runs instead in the message just after
 * the landed frame, when nothing on screen moves.
 *
 * <p>One settle waits at most. Whatever reads the settled state before the post runs (a new drag,
 * a page change at rest, another settle, the activity going away) runs it first ({@link #take}),
 * so nothing ever reads a landing half done, and the post finds nothing left.
 */
public final class LandedFrameSettle {

    @Nullable private PaneWallPage mPending;

    /**
     * Whether a settle is left for after its frame: only one a slide's own last frame reports
     * ({@link PaneWallLayout#settleEndsSlide}), into a window that draws frames, outside a
     * test of the activity that has no looper to post to. A settle with no slide behind it (a
     * jump, reduced motion, a page taken away at rest) has no motion to keep smooth and runs at
     * once, as it always did. Neither does a landing on the display window: its landing
     * (PaneWallController#onWallCellSettled) hands the display over right after the settle and
     * reads the attachment the settle makes.
     */
    public static boolean defers(boolean endsSlide, boolean windowDraws,
                                 @NonNull PaneWallPage page) {
        return endsSlide && windowDraws && page != PaneWallPage.DISPLAY;
    }

    /**
     * Holds {@code page}'s settle for after the frame. A settle already waiting is handed back
     * for the caller to run first, so two landings never collapse into one.
     */
    @Nullable
    public PaneWallPage hold(@NonNull PaneWallPage page) {
        PaneWallPage earlier = mPending;
        mPending = page;
        return earlier;
    }

    /** The settle waiting, which the caller runs now; null when none waits. */
    @Nullable
    public PaneWallPage take() {
        PaneWallPage page = mPending;
        mPending = null;
        return page;
    }

    public boolean pending() {
        return mPending != null;
    }

    /** Forgets a waiting settle without running it: the activity is going away. */
    public void drop() {
        mPending = null;
    }
}
