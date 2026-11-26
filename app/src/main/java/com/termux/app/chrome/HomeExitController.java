package com.termux.app.chrome;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.HomeExitPolicy.Step;

import java.util.ArrayList;

/**
 * What a home press does, carried out (ADR 0034): {@link HomeExitPolicy} picks the step, this runs
 * it through the {@link Host}: ask first when a surface holds unsaved changes, then fade every
 * open surface out as one {@link HomeExit} group and settle the page underneath at its end.
 *
 * <p>A press that turns the wall to Home from another kind of page while the window is shown lands
 * behind {@link HomeLandingGate}: one frame with no pages, then the settle, then Home's first frame
 * whole ({@link HomeExitPolicy#holdsLanding}).
 *
 * <p>A press during the exit or the landing finishes it at once. Nothing here is per frame.
 */
public final class HomeExitController {

    /** The activity's side: the registry of open surfaces, motion preference and the page. */
    public interface Host {
        boolean anyOpen();

        boolean anyHoldsUnsavedChanges();

        /** The launcher was in front when the press came ({@code HomePress.goesHome()}). */
        boolean goesHome();

        boolean reducedMotion();

        /** The window is shown (started), as the wall's landing reads it. */
        boolean launcherVisible();

        /** The system navigates by gestures, so it animates the window under the press itself. */
        boolean gestureNavigation();

        /** The press lands on a Home page and the wall is on another kind of page now. */
        boolean turnsToHomePage();

        /** The window's root the landing hides the layers of, or null for no hold. */
        @Nullable
        ViewGroup landingRoot();

        void askBeforeLeaving(@NonNull Runnable leave, @NonNull Runnable stay);

        /** Starts the exit over the surfaces; adds the members' views to {@code out}. */
        int beginExit(@NonNull java.util.List<View> out);

        /** Each member closes. */
        void endExit();

        /** Every surface closes now. */
        void closeAllForHome();

        /**
         * The page underneath settles for {@code step}. Runs before the fade for a step with
         * nothing to fade, otherwise at its end.
         */
        void settle(@NonNull Step step);
    }

    @NonNull private final Host mHost;
    @NonNull private final HomeExit mExit;
    @NonNull private final HomeLandingGate mLanding;
    /** The settle a held landing runs, made once. */
    @NonNull private final Runnable mSettleHome;
    /** The views the exit runs over, reused. */
    @NonNull private final ArrayList<View> mViews = new ArrayList<>();

    public HomeExitController(@NonNull Host host, @NonNull HomeExit exit) {
        this(host, exit, new HomeLandingGate());
    }

    public HomeExitController(@NonNull Host host, @NonNull HomeExit exit,
                              @NonNull HomeLandingGate landing) {
        mHost = host;
        mExit = exit;
        mLanding = landing;
        mSettleHome = () -> mHost.settle(Step.GO_HOME);
    }

    public boolean isRunning() {
        return mExit.isRunning() || mLanding.isRunning();
    }

    /**
     * The exit's end and a held landing's, at once, whichever way it is being asked for (a stop,
     * a rotation, Back).
     */
    public void finishNow() {
        mLanding.finishNow();
        mExit.finishNow();
    }

    /** The launcher pauses: a held landing settles and shows now. */
    public void onPause() {
        mLanding.finishNow();
    }

    /** A home press: the one step it takes now. */
    public void onHomePress() {
        // A press during the exit or a held landing finishes it at once.
        if (mLanding.isRunning()) {
            mLanding.finishNow();
            return;
        }
        if (mExit.isRunning()) {
            mExit.finishNow();
            return;
        }
        Step step = HomeExitPolicy.decide(
            mHost.anyOpen(), mHost.anyHoldsUnsavedChanges(), mHost.goesHome());
        if (step == Step.ASK) {
            mHost.askBeforeLeaving(
                () -> carryOut(HomeExitPolicy.afterAnswer(true)),
                () -> carryOut(HomeExitPolicy.afterAnswer(false)));
            return;
        }
        carryOut(step);
    }

    private void carryOut(@NonNull Step step) {
        if (step == Step.NONE) return;
        // The members fade out as one group; every other overlay closes now. The page underneath
        // settles at the end, when each member has closed.
        if (step == Step.CLOSE_STAY && !mHost.reducedMotion()) {
            mViews.clear();
            if (mHost.beginExit(mViews) > 0) {
                mExit.start(mViews, false, () -> {
                    mHost.endExit();
                    mHost.settle(step);
                });
                mViews.clear();
                return;
            }
            mViews.clear();
            mHost.settle(step);
            return;
        }
        mHost.closeAllForHome();
        if (step == Step.GO_HOME && HomeExitPolicy.holdsLanding(step, mHost.turnsToHomePage(),
                mHost.launcherVisible(), mHost.gestureNavigation())
            && mLanding.begin(mHost.landingRoot(), mSettleHome)) {
            return;
        }
        mHost.settle(step);
    }
}
