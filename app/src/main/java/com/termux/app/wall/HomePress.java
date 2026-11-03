package com.termux.app.wall;

/**
 * What a home press means to the wall. Made while the launcher is in front it goes to Home (the
 * page Start in names, {@link PaneWallController#goHome}), from a window or from another Home
 * page; made from another app it brings the launcher back to the page that was left.
 *
 * <p>The launcher is in front from its resume until its stop: a pause alone (the shade, a dialog
 * over it) keeps it in front. An activity starts as not in front, because the system can hand a
 * home press to an instance that has never resumed: a launcher waiting behind another app is
 * relaunched on its way back when the configuration moved meanwhile (a new wallpaper's colours
 * restyle every app), and the press that brings it back reaches the new instance before its first
 * resume. That press is a return, never a press in front.
 *
 * <p>Pure: the activity reports resume and stop and asks on each home intent.
 */
public final class HomePress {

    private boolean mInFront;

    public void onResume() {
        mInFront = true;
    }

    public void onStop() {
        mInFront = false;
    }

    /** Whether a home press now goes to Home, rather than back to the page left. */
    public boolean goesHome() {
        return mInFront;
    }
}
