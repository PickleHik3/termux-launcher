package com.termux.app.launcher.az;

import androidx.annotation.NonNull;

/**
 * The minimised A&#8211;Z index's reveal, as a state machine: the letters tucked behind the tab,
 * out while a finger holds it, and on their way back once it lets go.
 *
 * <p>The finger that lands on the tab owns the gesture from its first touch — there is no slop and
 * no hold, so an immediate drag scrubs letters before anything else under the finger (the page's
 * border drag, the corner tab, the content) could think it was theirs. The letters are asked out
 * on that touch and stay out for as long as the finger is down; release asks them back, after the
 * scrub has had the release and launched whatever it was going to launch. A finger that lands
 * while they are still on their way back takes them out again from wherever they are.
 *
 * <p>The spring that moves them is the view's; this only says where it is heading
 * ({@link #target}) and whether the letters have anything to draw ({@link #lettersVisible}).
 * Reduced motion is the same machine with a spring that arrives at once.
 *
 * <p>Pure: no views, no clock.
 */
public final class AzTabReveal {

    public enum Phase {
        /** Behind the tab: only the tab is drawn. */
        TUCKED,
        /** A finger is on the tab, and the letters are out or on their way. */
        OUT,
        /** Let go: the letters are going back behind the tab. */
        RETURNING
    }

    /** How close to tucked the slide has to come before the letters stop being drawn. */
    public static final float SETTLED_EPSILON = 0.01f;

    @NonNull private Phase mPhase = Phase.TUCKED;
    private boolean mEnabled = true;

    @NonNull
    public Phase phase() {
        return mPhase;
    }

    /**
     * A finger landed on the tab.
     *
     * @return whether the gesture is the tab's: always, unless the tab is switched off
     */
    public boolean press() {
        if (!mEnabled) return false;
        mPhase = Phase.OUT;
        return true;
    }

    /** The finger let go, or its stream was cancelled. */
    public void release() {
        if (mPhase == Phase.OUT) mPhase = Phase.RETURNING;
    }

    /**
     * The slide came to rest at {@code progress}, 0 tucked and 1 out. Only a slide that has come
     * home ends the return; one that settled out is simply out.
     */
    public void settled(float progress) {
        if (mPhase == Phase.RETURNING && progress <= SETTLED_EPSILON) mPhase = Phase.TUCKED;
    }

    /** Back behind the tab at once, with no slide: the tab went away or moved under the finger. */
    public void tuck() {
        mPhase = Phase.TUCKED;
    }

    /** Whether the tab takes a touch at all. Switching it off tucks the letters away. */
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) tuck();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /** Where the slide is heading: 1 while a finger holds the tab, 0 otherwise. */
    public float target() {
        return mPhase == Phase.OUT ? 1f : 0f;
    }

    /** Whether the letters have anything to draw, which is whenever they are not tucked. */
    public boolean lettersVisible() {
        return mPhase != Phase.TUCKED;
    }

    /** Whether a finger is holding the tab. */
    public boolean isHeld() {
        return mPhase == Phase.OUT;
    }

    /** The tab fades as the letters arrive over it, and comes back as they leave. */
    public static float tabAlpha(float progress) {
        return 1f - clamp01(progress);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
