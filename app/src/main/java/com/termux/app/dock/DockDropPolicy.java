package com.termux.app.dock;

import androidx.annotation.NonNull;

import com.termux.app.launcher.drawer.DrawerHandOverPolicy;
import com.termux.app.launcher.widget.WidgetPaneController;

/**
 * An app icon carried onto the dock's pinned row from somewhere else (a Home icon lifted off its
 * page) or lifted out of the row itself: where it lands in the pin list, what a page that was
 * already full gives up to the next one, what a rest at either end of the row does, and what a
 * release over an icon makes. Launcher3's hotseat, on a dock that pages and has no limit: the
 * icon takes the slot under the finger on the page in view, and the last icon of a full page
 * moves on to the start of the next page (and so on down the pages, since the pins are one list).
 * Pure, so all of it is testable without a view.
 */
public final class DockDropPolicy {

    /**
     * How long a carried icon rests in the band at either end of the row before the row turns a
     * page: Home's own edge hold.
     */
    public static final long EDGE_HOLD_MS = WidgetPaneController.EDGE_FLIP_DELAY_MS;
    /** That band's width, measured in from each end of the row: Home's edge band. */
    public static final int EDGE_BAND_DP = WidgetPaneController.EDGE_BAND_DP;

    /**
     * The middle share of an icon's slot, as a fraction of its width, where a release makes a
     * folder (or joins the folder there) rather than going beside it.
     */
    static final float FOLDER_BAND_START = 0.28f;
    static final float FOLDER_BAND_END = 0.72f;

    /** What a release of an app carried in from outside the row does. */
    public enum Outcome {
        /** It goes into the row at the slot under the finger. */
        INSERT,
        /** Over the middle of an app icon: the two become a folder in that slot. */
        MAKE_FOLDER,
        /** Over a folder: the app joins it. */
        ADD_TO_FOLDER,
        /** The app is already in the row (or already in that folder): nothing changes. */
        ALREADY_THERE,
        /** The folder under it has no room left: nothing changes. */
        FOLDER_FULL
    }

    private DockDropPolicy() {}

    /** Whether a release at {@code dropXRatio} across an icon's slot lands on the icon itself. */
    public static boolean onIcon(float dropXRatio) {
        return dropXRatio >= FOLDER_BAND_START && dropXRatio <= FOLDER_BAND_END;
    }

    /**
     * What a release does with an app carried in from outside the row, over a slot holding an app
     * ({@code overApp}), a folder ({@code overFolder}) or nothing. The dock's own drag already
     * does the same with its pins: a folder takes whatever is dropped on it, an icon makes a folder
     * only from the middle of its slot.
     *
     * @param alreadyDocked  the app is a pin of the row's own already (not inside a folder)
     * @param folderHasIt    the folder under the finger holds the app already
     * @param folderHasRoom  the folder under the finger can take one more app
     */
    @NonNull
    public static Outcome outcome(boolean alreadyDocked, boolean overApp, boolean overFolder,
                                  boolean folderHasIt, boolean folderHasRoom, float dropXRatio) {
        if (overFolder) {
            if (folderHasIt) return Outcome.ALREADY_THERE;
            return folderHasRoom ? Outcome.ADD_TO_FOLDER : Outcome.FOLDER_FULL;
        }
        if (alreadyDocked) return Outcome.ALREADY_THERE;
        return overApp && onIcon(dropXRatio) ? Outcome.MAKE_FOLDER : Outcome.INSERT;
    }

    /**
     * Where in the pin list an icon released while page {@code page} is in view goes: {@code wanted}
     * (the slot under the finger, or the one after it), kept on that page. A full page cannot grow,
     * so a release past its last icon takes the last slot and that icon moves on; a page with room
     * (only ever the last) takes it at the end. A page past the pins (the most-used page) appends.
     *
     * @param perPage how many pins one page holds
     * @param count   how many pins there are before the insert
     */
    public static int insertIndex(int page, int perPage, int count, int wanted) {
        int size = Math.max(1, perPage);
        int total = Math.max(0, count);
        int first = Math.max(0, page) * size;
        if (first >= total) return total;
        int last = Math.min(total, first + size - 1);
        return Math.max(first, Math.min(last, wanted));
    }

    /**
     * The pin, by its index once the insert is made, that the insert at {@code insertIndex} pushed
     * from the end of its page onto the start of the next: the page held {@code perPage} pins
     * already. -1 when the page had room and nothing moved on.
     */
    public static int pushedToNextPage(int insertIndex, int perPage, int countBefore) {
        int size = Math.max(1, perPage);
        int page = Math.max(0, insertIndex) / size;
        int nextStart = (page + 1) * size;
        return countBefore >= nextStart ? nextStart : -1;
    }

    /** The page an icon inserted at {@code insertIndex} is on. */
    public static int pageOf(int insertIndex, int perPage) {
        return Math.max(0, insertIndex) / Math.max(1, perPage);
    }

    /**
     * Which way a rest at {@code x} along a row {@code width} wide turns its page: -1 to the page
     * before in the leading band, +1 to the page after in the trailing band, 0 elsewhere, at either
     * end of the run (a carried icon does not wrap round), or on a row too narrow for two bands.
     */
    public static int edgeDirection(float x, float width, float bandPx, int page, int pageCount) {
        if (width <= 2f * bandPx || pageCount <= 1) return 0;
        if (x <= bandPx) return page > 0 ? -1 : 0;
        if (x >= width - bandPx) return page < pageCount - 1 ? 1 : 0;
        return 0;
    }

    /**
     * Whether an icon lifted out of the row, now at ({@code x}, {@code y}) on screen, has left the
     * row ({@code left}, {@code top}, {@code right}, {@code bottom}) far enough to be Home's: as an
     * icon leaves the drawer, a finger {@link DrawerHandOverPolicy#CROSS_DP} past its edge. A
     * finger that only wanders off the row while reordering it does not turn the wall to Home.
     */
    public static boolean handsOverToHome(float x, float y, float left, float top, float right,
                                          float bottom, float density) {
        float outside = Math.max(Math.max(left - x, x - right), Math.max(top - y, y - bottom));
        return outside >= DrawerHandOverPolicy.CROSS_DP * density;
    }

    /**
     * Where an unpinned icon goes back to on Undo: the index it had, or the end of the row when
     * the row has since got shorter than that.
     */
    public static int restoreIndex(int index, int count) {
        return Math.max(0, Math.min(Math.max(0, count), index));
    }
}
