package com.termux.app.chrome;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.chrome.HomeExitPolicy.Step;

import java.util.ArrayList;

/**
 * What a home press does, carried out (ADR 0034): {@link HomeExitPolicy} picks the step, this runs
 * it through the {@link Host}: ask first when a surface holds unsaved changes, then fade every
 * open surface out as one {@link HomeExit} group and settle the page underneath at its end.
 *
 * <p>A press during the exit finishes it at once. Nothing here is per frame.
 */
public final class HomeExitController {

    /** The activity's side: the registry of open surfaces, motion preference and the page. */
    public interface Host {
        boolean anyOpen();

        boolean anyHoldsUnsavedChanges();

        /** The launcher was in front when the press came ({@code HomePress.goesHome()}). */
        boolean goesHome();

        boolean reducedMotion();

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
    /** The views the exit runs over, reused. */
    @NonNull private final ArrayList<View> mViews = new ArrayList<>();

    public HomeExitController(@NonNull Host host, @NonNull HomeExit exit) {
        mHost = host;
        mExit = exit;
    }

    public boolean isRunning() {
        return mExit.isRunning();
    }

    /** The exit's end at once, whichever way it is being asked for (a stop, a rotation, Back). */
    public void finishNow() {
        mExit.finishNow();
    }

    /** A home press: the one step it takes now. */
    public void onHomePress() {
        // A press during the exit finishes it at once.
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
        mHost.settle(step);
    }
}
