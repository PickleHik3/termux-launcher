package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the wall reads from the activity and never changes anything with: settings and session
 * facts, each asked fresh when it matters. {@link #NONE} answers what a host with nothing to say
 * would. Main thread only.
 */
public interface WallFacts {

    /** Honour the system's reduce-motion setting for the page slide. */
    default boolean reducedMotion() { return false; }

    /**
     * Whether the current session's row has a terminal window, so the Terminal is on the
     * row (a Display page is a page of its own, not one of the row's windows). Home is on it unless {@link #useAs} is
     * Terminal only.
     */
    default boolean hasWindow() { return true; }

    /** Whether a terminal window is open, known for sure (the service is bound). */
    default boolean terminalWindowOpen() { return false; }

    /**
     * Use as, read fresh on every ask: under Terminal only the row is the Terminal alone,
     * and the home press, the last window closing and a cold start all land there.
     */
    @NonNull default UseAs useAs() { return UseAs.HOME; }

    /**
     * Whether the wall holds Home's pages alone ({@link TerlWall}): under Terl a terminal and the
     * Display are windows of the Workspace stack, never pages of the wall; the Assistant is the AI
     * page ({@link #aiPage}).
     */
    default boolean homePagesOnly() { return false; }

    /**
     * Under Terl ({@link #homePagesOnly}), whether the AI page is on: the wall then lays out its
     * Assistant page left of Home 1 too (ADR 0043).
     */
    default boolean aiPage() { return false; }

    /**
     * The wall's pages (ADR 0015), read fresh on every ask, or null for the kind-keyed row
     * alone ({@link UseAs#rowPages}). The wall lays out in their order.
     */
    @Nullable default WallPages wallPages() { return null; }

    /**
     * What builds the Assistant page's view (ADR 0022), asked when the wall first holds an
     * Assistant page. The wall owns the view; the host only says how it is made.
     */
    @NonNull default com.termux.app.assistant.AssistantPageHost assistantPageHost() {
        return com.termux.app.assistant.AssistantPageHost.DEFAULT;
    }

    /**
     * Whether the pages a border drag pulls tip like planks ({@link PlankTilt}): Fancier
     * Glass on, with the phone animating. Asked as the drag claims the finger.
     */
    default boolean isPlankTiltEnabled() { return false; }

    /**
     * Whether a sideways swipe that starts at this screen point, on a terminal window's text,
     * pages the row ({@link PaneWallLayout.Listener#isBodySwipePaging}): Settings lets it,
     * and the pane under the finger gives the swipe away. Asked as a finger lands.
     */
    default boolean bodySwipePagesWall(float rawX, float rawY) { return false; }

    /**
     * Whether a clearly sideways swipe that starts at this screen point, in a terminal
     * window's last row on the seam band, pages the row ({@link
     * PaneWallLayout.Listener#isSeamSwipePaging}): the pane under the finger gives the swipe
     * away. The body swipe's setting is not asked; the band is the bottom border's.
     */
    default boolean seamSwipePagesWall(float rawX, float rawY) {
        return bodySwipePagesWall(rawX, rawY);
    }

    /** Facts of a host with none. */
    WallFacts NONE = new WallFacts() { };
}
