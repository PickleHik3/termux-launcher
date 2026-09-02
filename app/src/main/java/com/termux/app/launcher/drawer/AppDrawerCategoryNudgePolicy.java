package com.termux.app.launcher.drawer;

/**
 * When the categories layout tells the user that installed apps are waiting to be sorted.
 *
 * <p>Once per count: the notice fires on the open that first sees enough pending apps, then holds
 * its tongue until the number changes — a run that sorts them resets it, a further install past the
 * threshold raises it again. Below the threshold nothing is ever said.
 */
final class AppDrawerCategoryNudgePolicy {

    /** "Above five": the smallest count worth interrupting an open for. */
    static final int MIN_PENDING = 6;

    private int mLastNudgedCount = -1;

    /** @return true when this open should carry the notice */
    boolean onDrawerOpened(int pendingCount) {
        if (pendingCount < MIN_PENDING) {
            mLastNudgedCount = -1;
            return false;
        }
        if (pendingCount == mLastNudgedCount) return false;
        mLastNudgedCount = pendingCount;
        return true;
    }

    void reset() {
        mLastNudgedCount = -1;
    }
}
