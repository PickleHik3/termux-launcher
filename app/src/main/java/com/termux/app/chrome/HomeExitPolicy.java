package com.termux.app.chrome;

/**
 * What a home press does, from what is open (ADR 0034). Nothing open: as it always was, the home
 * page when the launcher is in front, the page left when it returns from another app. Something
 * open: a surface holding unsaved changes asks first, then everything open closes and the press
 * stays on the page underneath; the wall does not page.
 *
 * <p>Pure: the activity reads the registry and the front flag, and carries out the step.
 */
public final class HomeExitPolicy {

    /** The one step a home press takes now. */
    public enum Step {
        /** A surface holds unsaved changes: it asks, and nothing else moves meanwhile. */
        ASK,
        /** Close everything open; the wall stays on the page underneath. */
        CLOSE_STAY,
        /** Nothing open, launcher in front: the home page. */
        GO_HOME,
        /** Nothing open, coming back from another app: the page that was left. */
        RETURN,
        /** The answer was to keep editing: leave everything as it was. */
        NONE
    }

    private HomeExitPolicy() { }

    /**
     * @param anyOpen a surface is open over the page
     * @param anyDraft an open surface holds unsaved changes
     * @param inFront the launcher was in front when the press came ({@code HomePress.goesHome()})
     */
    public static Step decide(boolean anyOpen, boolean anyDraft, boolean inFront) {
        if (!anyOpen) return inFront ? Step.GO_HOME : Step.RETURN;
        return anyDraft ? Step.ASK : Step.CLOSE_STAY;
    }

    /**
     * Whether the wall springs to the home page. With gesture navigation the system has already
     * animated the window under the swipe, so the launcher lands at once: one motion for one
     * press. With buttons the system plays nothing and the spring stays.
     */
    public static boolean animatesWall(boolean launcherVisible, boolean gestureNavigation) {
        return launcherVisible && !gestureNavigation;
    }

    /**
     * Whether the press's landing is held behind a frame with no pages ({@link HomeLandingGate}):
     * it turns the wall to a Home page from a page of another kind while the window is shown, and
     * the wall lands at once. The system fades the window in under the gesture with the last frame
     * it drew, so without the hold the page left showed under the fade and Home cut in after it:
     * two motions for one press. A wall that springs to Home (buttons) plays its own slide and is
     * not held.
     *
     * @param turnsToHomePage the press lands on a Home page and the wall is on another kind of page
     */
    public static boolean holdsLanding(Step step, boolean turnsToHomePage, boolean launcherVisible,
                                       boolean gestureNavigation) {
        return step == Step.GO_HOME && turnsToHomePage && launcherVisible
            && !animatesWall(launcherVisible, gestureNavigation);
    }

    /** The step after the question: continue leaves, stay changes nothing. */
    public static Step afterAnswer(boolean leave) {
        return leave ? Step.CLOSE_STAY : Step.NONE;
    }
}
