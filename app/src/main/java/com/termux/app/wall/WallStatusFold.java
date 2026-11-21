package com.termux.app.wall;

/**
 * The status bar's fold as the top border's swipe drives it: the bar takes the drag across itself
 * and runs the landing its release asks for. {@link #NONE} is a bar that does not fold. Main
 * thread only.
 */
public interface WallStatusFold {

    /**
     * Whether a vertical swipe off the page's top border drives the status bar's fold: where
     * the bar stands along the top, can unfold, and is not put away. Asked on every frame the
     * wall draws its grabbers; see {@link PaneWallLayout.Listener#isBorderStatusSwipeEnabled}.
     */
    default boolean isEnabled() { return false; }

    /**
     * A swipe off the top border asked for the bar unfolded ({@code expand}) or folded, for a
     * swipe the fold did not follow ({@link #onFoldBegin} answered 0, or reduced motion).
     */
    default void onSwipe(boolean expand) { }

    /**
     * The status bar swipe was claimed, going down ({@code expanding}) or up: the fold's whole
     * way in px for the finger to drive, or 0 where it cannot follow. See
     * {@link PaneWallLayout.Listener#onStatusFoldBegin}.
     */
    default int onFoldBegin(boolean expanding) { return 0; }

    /** The finger has the bar {@code towardOpenPx} from the form it had, positive opening. */
    default void onFoldProgress(float towardOpenPx) { }

    /** The fold's finger let go or lost it: land the bar unfolded ({@code expanded}) or folded. */
    default void onFoldEnd(boolean expanded, float towardOpenVelocityPxPerSec) { }

    /** A fold that does nothing, for a wall whose host has none. */
    WallStatusFold NONE = new WallStatusFold() { };
}
