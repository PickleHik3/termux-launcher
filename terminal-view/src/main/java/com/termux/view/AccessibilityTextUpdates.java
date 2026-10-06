package com.termux.view;

/**
 * When the terminal tells a screen reader its text changed.
 *
 * <p>The event makes the view build its whole screen as one string, so it is only sent while a
 * screen reader that explores by touch (TalkBack) is running — not merely while any accessibility
 * service is, which includes password managers and automation tools that never read it — and at
 * most once per {@link #THROTTLE_MS}: a burst of output becomes one event carrying the text as it
 * stands when the event is sent.
 */
final class AccessibilityTextUpdates {

    /** The trailing window a burst of screen updates is folded into. */
    static final long THROTTLE_MS = 250;

    /** What the view does on this policy's behalf. */
    interface Host {
        void postDelayed(Runnable action, long delayMs);

        void removeCallbacks(Runnable action);

        void sendTextChanged();
    }

    private final Host mHost;
    private boolean mActive;
    private boolean mPending;

    private final Runnable mSend = () -> {
        mPending = false;
        if (mActive) mHost.sendTextChanged();
    };

    AccessibilityTextUpdates(Host host) {
        mHost = host;
    }

    /** Whether a service in this state reads the terminal's text. */
    static boolean readsText(boolean accessibilityEnabled, boolean touchExplorationEnabled) {
        return accessibilityEnabled && touchExplorationEnabled;
    }

    /** The accessibility services changed; called at start and from the manager's listeners. */
    void setServiceState(boolean accessibilityEnabled, boolean touchExplorationEnabled) {
        mActive = readsText(accessibilityEnabled, touchExplorationEnabled);
        if (!mActive) cancel();
    }

    boolean isActive() {
        return mActive;
    }

    /** The screen changed: send one event once the burst this belongs to has had its window. */
    void onScreenUpdated() {
        if (!mActive || mPending) return;
        mPending = true;
        mHost.postDelayed(mSend, THROTTLE_MS);
    }

    /** Drop an event still waiting, as when the view leaves its window. */
    void cancel() {
        if (!mPending) return;
        mPending = false;
        mHost.removeCallbacks(mSend);
    }
}
