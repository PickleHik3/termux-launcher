package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Which place takes which lifted item. Launcher3's way for what both launchers have: an icon moves
 * between Home and the dock (it is moved, never copied, so no Undo follows a move), Remove takes an
 * icon off Home or off the dock (with Undo), Uninstall is offered for an app that can go, and a
 * shortcut dragged out of the App menu lands only on Home. What neither model can hold is refused:
 * a Home folder is not a dock folder, a shortcut is not a dock pin. Pure, so it is testable
 * without a view.
 */
public final class LiftDropPolicy {

    /** What is in the air. */
    public enum Item {
        /** An app icon lifted off a Home page. */
        HOME_APP,
        /** A folder lifted off a Home page. */
        HOME_FOLDER,
        /** An app icon lifted out of the dock's pinned row. */
        DOCK_APP,
        /** An app carried out of the app drawer's top edge: Home's follower carries it on. */
        DRAWER_APP,
        /** An app shortcut dragged out of the App menu. */
        SHORTCUT
    }

    /** Where it is let go. */
    public enum Place {
        /** A cell of the Home page under the finger. */
        HOME,
        /** The dock's pinned row: a slot between its icons, or an icon or folder there. */
        DOCK,
        REMOVE,
        UNINSTALL
    }

    /** What letting go does, from where the finger is when it lets go. */
    public enum Release {
        /** Over a target (Remove, Uninstall): the target acts. */
        TARGET,
        /** Over the dock's row: the row takes it, or it goes back. */
        DOCK,
        /** Home followed the finger: Home puts it in the cell it offered, or it goes back. */
        HOME,
        /** Anywhere else, or the stream was cancelled: it goes back. */
        BACK
    }

    private LiftDropPolicy() {}

    /**
     * What a release does. A target under the finger wins, then the dock's row under it, then
     * Home when Home has been following the finger. Anywhere else (the screen's edge beside the
     * row or the grid, the status chrome without a target) it goes back, as does a cancel. Whatever
     * this answers, the place the icon left shows again once the release is done (an icon that
     * moved has already left it).
     *
     * @param overTarget   a drop target was under the finger
     * @param overDock     the dock's row was under the finger and takes this item
     * @param homeFollows  Home took the drag over and follows the finger
     */
    @NonNull
    public static Release release(boolean canceled, boolean overTarget, boolean overDock,
                                  boolean homeFollows) {
        if (canceled) return Release.BACK;
        if (overTarget) return Release.TARGET;
        if (overDock) return Release.DOCK;
        return homeFollows ? Release.HOME : Release.BACK;
    }

    /** Whether letting {@code item} go over {@code place} does anything; otherwise it goes back. */
    public static boolean accepts(@NonNull Item item, @NonNull Place place) {
        switch (place) {
            case HOME:
                return true;
            case DOCK:
                return item == Item.HOME_APP || item == Item.DOCK_APP || item == Item.DRAWER_APP;
            case REMOVE:
                // The drawer's own targets act before the hand-over.
                return item != Item.SHORTCUT && item != Item.DRAWER_APP;
            case UNINSTALL:
                return item == Item.HOME_APP || item == Item.DOCK_APP;
            default:
                return false;
        }
    }

    /**
     * The targets that come up while {@code item} is in the air, left to right: Remove, then
     * Uninstall for an app that can be uninstalled. A shortcut has none: anywhere but Home cancels.
     */
    @NonNull
    public static List<HomeDropTargets.Target> targetsFor(@NonNull Item item, boolean uninstallable) {
        if (!accepts(item, Place.REMOVE)) return Collections.emptyList();
        return HomeDropTargets.targetsFor(item == Item.HOME_FOLDER,
            uninstallable && accepts(item, Place.UNINSTALL));
    }
}
