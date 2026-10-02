package com.termux.app.chrome.wallpaper;

/**
 * The session rules around the self-check and the screen, pure so they can be tested without an
 * activity (animated-wallpaper SPEC §5, §9.4).
 *
 * <p>Kills: a kill (the lowest tier still failed a window, or a render threw) lasts until the next
 * visible session (screen on, unlock or onStart), when the renderer is tried again from the top
 * tier. After {@link #MAX_KILLS} kills in one process the still stays and no retry is offered.
 * A different background choice starts over.</p>
 *
 * <p>Screen: the lock rest pose must be left whichever signal arrives first after a screen-off,
 * {@code USER_PRESENT} or the activity starting or resuming; some ROMs and face or smart unlock
 * never send {@code USER_PRESENT}. Each screen-off yields exactly one unlock.</p>
 */
final class WallpaperSession {

    /** Kills in one process life after which the renderer stays dead. */
    static final int MAX_KILLS = 3;

    private int mKills;
    private boolean mKilled;
    private boolean mWasScreenOff;

    /** The renderer was killed. */
    void onKill() {
        mKills++;
        mKilled = true;
    }

    /** Kills so far this process. */
    int kills() {
        return mKills;
    }

    /** Whether the renderer is currently killed (a retry may still be pending). */
    boolean killed() {
        return mKilled;
    }

    /**
     * A visible session begins (screen on, unlock, onStart). True when the kill is lifted and the
     * renderer should be rebuilt from the top tier; false when nothing was killed or the cap is hit.
     */
    boolean retryOnVisibleSession() {
        if (!mKilled || mKills >= MAX_KILLS) return false;
        mKilled = false;
        return true;
    }

    /** A different background was chosen: forget the kills. */
    void reset() {
        mKills = 0;
        mKilled = false;
    }

    /** The screen went off. */
    void onScreenOff() {
        mWasScreenOff = true;
    }

    /** USER_PRESENT: true once per screen-off, the unlock to give the Director. */
    boolean unlockOnUserPresent() {
        if (!mWasScreenOff) return false;
        mWasScreenOff = false;
        return true;
    }

    /** onStart or onResume: true when the screen was off and is on now and no unlock was given yet. */
    boolean unlockOnStart(boolean screenOn) {
        if (!mWasScreenOff || !screenOn) return false;
        mWasScreenOff = false;
        return true;
    }
}
