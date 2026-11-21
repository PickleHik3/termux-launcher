package com.termux.app.launcher.home;

import androidx.annotation.NonNull;

/**
 * When a swipe down on Home pulls the system's notification shade live, and where the drag it
 * hands the shade lies ({@link ShadePullInjector} applies the answer).
 *
 * <p>The shade follows the finger by a drag injected at the status bar, as the shell user, through
 * the privileged lane. The input system gives injected events a virtual device of their own. Up
 * to Android 14 its dispatcher lets one device touch at a time: the injected press takes over and
 * the launcher's own finger is dropped from then on, so the launcher could not feed the drag.
 * From Android 15 each device keeps its own stream in its own window, so the finger stays the
 * launcher's and every move is passed on. Below that, and whenever the lane is not bound, the
 * swipe opens the shade when it is let go, as before.
 */
public final class ShadePullPolicy {

    /** Android 15: the first release whose input dispatcher keeps two devices' streams apart. */
    public static final int MIN_SDK = 35;

    private ShadePullPolicy() {}

    /**
     * Whether a swipe down on Home, claimed after the slop (the pane only asks for a downward
     * claim), pulls the shade live.
     *
     * @param sdk the phone's SDK level
     * @param action what swipe down is set to
     * @param keyboardSwipe the swipe puts a palette or a keyboard away instead
     * @param laneBound the privileged lane's service is bound now
     * @param statusBarPx the status bar's height on screen; 0 when it is hidden, where the press
     *     would land on the launcher itself
     */
    public static boolean pullsLive(int sdk, @NonNull HomeGestures.Action action,
                                    boolean keyboardSwipe, boolean laneBound, int statusBarPx) {
        return sdk >= MIN_SDK && action == HomeGestures.Action.NOTIFICATIONS && !keyboardSwipe
            && laneBound && statusBarPx > 0;
    }

    /** Where the injected press goes down: the middle of the status bar, never above the screen. */
    public static float pressY(int statusBarPx) {
        return Math.max(1f, statusBarPx / 2f);
    }

    /**
     * The injected drag's y for the finger at {@code rawY}: the press plus the finger's travel
     * since the claim, never above the press, so the shade opens as far as the finger has come.
     */
    public static float dragY(float pressY, float startRawY, float rawY) {
        return pressY + Math.max(0f, rawY - startRawY);
    }

    /** The injected drag's x: the finger's own (some phones open other panels by side), on screen. */
    public static float dragX(float rawX) {
        return Math.max(0f, rawX);
    }

    /**
     * Whether the let-go swipe opens the shade the old way: the live drag failed somewhere
     * ({@code failed}) and the release reads as a swipe down ({@code releaseWay} > 0).
     */
    public static boolean fallsBackOnRelease(boolean failed, int releaseWay) {
        return failed && releaseWay > 0;
    }
}
