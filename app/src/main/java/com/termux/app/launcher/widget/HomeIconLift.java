package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

/**
 * What a long press on a Home item does, and when the finger that is still down lifts it. Launcher3's
 * way for an app icon: the hold opens the App menu, and a finger that then travels past a short
 * distance closes the menu and carries the icon; one that lifts without travelling leaves the menu
 * open. A folder, or an app that is gone, has no menu, so the hold lifts it at once. Widgets keep
 * edit mode, which is also where several things are moved in a row. Pure, so it is testable
 * without a view.
 */
public final class HomeIconLift {

    /**
     * How many touch slops the finger travels before a held icon lifts: two, which at the platform's
     * 8dp slop is Launcher3's 16dp {@code deep_shortcuts_start_drag_threshold}, and scales with the
     * device's own slop the same way.
     */
    static final int LIFT_SLOP_FACTOR = 2;

    /** What the long press does. */
    public enum Hold {
        /** The App menu, with the finger watched for a lift. */
        MENU,
        /** The item lifts under the finger at once, with no edit mode left after the drop. */
        LIFT,
        /** Edit mode and its drag, as for a widget. */
        EDIT,
        /** Nothing moves: the layout is locked, and the page says so. */
        REFUSE
    }

    /** What the travel of the finger holding the menu does. */
    public enum Move {
        /** Not far enough yet, or already refused once: the menu stays. */
        STAY,
        /** The menu closes and the icon lifts under the finger. */
        LIFT,
        /** The layout is locked: the page says so, once, and the menu stays. */
        REFUSE
    }

    private HomeIconLift() {}

    /** The travel, in px, past which a held icon lifts, from the device's scaled touch slop. */
    public static int liftDistancePx(int touchSlopPx) {
        return Math.max(1, touchSlopPx * LIFT_SLOP_FACTOR);
    }

    /** Whether a finger that went down at (0, 0) and is now at ({@code dx}, {@code dy}) has gone past it. */
    public static boolean pastLiftDistance(float dx, float dy, int liftDistancePx) {
        return dx * dx + dy * dy > (float) liftDistancePx * liftDistancePx;
    }

    /**
     * What a long press on a Home item does.
     *
     * @param editing   an edit session is already open: the hold stays edit mode's own
     * @param hasMenu   an app icon whose app is there, so it has an App menu
     * @param iconLike  an app icon or a folder (a widget is neither)
     * @param locked    Settings › Home › Lock Home layout
     */
    @NonNull
    public static Hold onLongPress(boolean editing, boolean hasMenu, boolean iconLike,
                                   boolean locked) {
        if (editing) return locked ? Hold.REFUSE : Hold.EDIT;
        // The menu is not an edit: it opens on a locked layout too, and only the lift is refused.
        if (hasMenu) return Hold.MENU;
        if (locked) return Hold.REFUSE;
        return iconLike ? Hold.LIFT : Hold.EDIT;
    }

    /**
     * What the held finger's travel does.
     *
     * @param past     the finger has gone past the lift distance
     * @param locked   the layout is locked
     * @param refused  the lift has been refused once already in this hold
     */
    @NonNull
    public static Move onHoldMove(boolean past, boolean locked, boolean refused) {
        if (!past) return Move.STAY;
        if (!locked) return Move.LIFT;
        return refused ? Move.STAY : Move.REFUSE;
    }
}
