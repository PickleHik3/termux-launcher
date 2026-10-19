package com.termux.app.launcher.home;

import androidx.annotation.NonNull;

/**
 * Settings › Home › Lock Home layout: while it is on, nothing on a Home page can be moved,
 * resized, added or taken away by hand, so a stray hold can no longer drag an icon off its place.
 * Taps still launch and open folders, and a long press on the free area still opens Home's sheet,
 * whose first page row then unlocks. Off, the default, is Home as it was.
 */
public final class HomeLayoutLock {

    private HomeLayoutLock() {}

    /** What a hand can do to Home's layout; each is refused while the layout is locked. */
    public enum Edit {
        /** A long press on a widget, an icon or a folder: edit mode and its drag. */
        HOLD_TO_EDIT,
        /** The corner tab's pencil, and the sheet's Edit widgets. */
        EDIT_WIDGETS,
        /** A new page, from the corner tab's + or the sheet. */
        ADD_PAGE,
        /** A widget or an app put on the page from the sheet. */
        ADD_ITEM,
        /** A page taken away, or the pages arranged. */
        EDIT_PAGES
    }

    /** Whether {@code edit} may go ahead. */
    public static boolean allows(boolean locked, @NonNull Edit edit) {
        return !locked;
    }

    /** Whether Home's sheet offers {@code item} while the layout is {@code locked}. */
    public static boolean sheetOffers(boolean locked, @NonNull HomeActionsSheetPolicy.Item item) {
        switch (item) {
            case LOCK_LAYOUT: return !locked;
            case UNLOCK_LAYOUT: return locked;
            case ADD_WIDGET:
            case ADD_APP:
            case ADD_PAGE:
            case EDIT_WIDGETS:
            case EDIT_PAGES:
            case REMOVE_PAGE:
                return !locked;
            default:
                return true;
        }
    }
}
