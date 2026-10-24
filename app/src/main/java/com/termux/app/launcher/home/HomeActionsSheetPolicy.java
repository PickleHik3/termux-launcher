package com.termux.app.launcher.home;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What Home's long-press sheet offers, in order: first the Home page's own actions, then the
 * launcher's. Pure, so the list is the same wherever it is asked for and testable without a view.
 */
public final class HomeActionsSheetPolicy {

    /** The sheet's two inner groups, in the order they stand. */
    public enum Group {
        /** What acts on the Home page under the finger. */
        PAGE,
        /** What acts on the launcher as a whole. */
        LAUNCHER
    }

    /** One row of the sheet. */
    public enum Item {
        /** A new Home float on this page: the first and primary row. */
        TERMINAL(Group.PAGE),
        ADD_WIDGET(Group.PAGE),
        /** An app icon on this page, from a list of the drawer's apps. */
        ADD_APP(Group.PAGE),
        ADD_PAGE(Group.PAGE),
        /** Edit widgets: the corner tab's pencil. */
        EDIT_WIDGETS(Group.PAGE),
        /** Edit pages: reorder, remove, add pages and choose the default one. */
        EDIT_PAGES(Group.PAGE),
        /** Takes the page on screen away; only an empty page, and never the last one. */
        REMOVE_PAGE(Group.PAGE),
        /** Lock Home layout: nothing on the pages moves by hand until it is unlocked. */
        LOCK_LAYOUT(Group.PAGE),
        /** The way back from a locked layout, offered right under Terminal while it is locked. */
        UNLOCK_LAYOUT(Group.PAGE),
        WALLPAPER(Group.LAUNCHER),
        /** The Appearance editor, on Home's card. */
        APPEARANCE(Group.LAUNCHER),
        LAUNCHER_SETTINGS(Group.LAUNCHER),
        /** The phone's home-app chooser; offered only while another app is the home screen. */
        SET_DEFAULT_HOME(Group.LAUNCHER),
        HELP(Group.LAUNCHER);

        @NonNull public final Group group;

        Item(@NonNull Group group) {
            this.group = group;
        }
    }

    private HomeActionsSheetPolicy() {}

    /**
     * The sheet's rows for this state. Terminal is always first: a Home float opens on the page
     * whether or not the Workspace has a session.
     *
     * @param isDefaultHome the launcher is the phone's home app, so Set as default home is not
     *                      offered
     * @param hasPages      the Home pages are there to act on (a terminal-only install has none),
     *                      so the page's widget, page and editor rows are offered
     * @param pageEmpty     the Home page on screen holds no widget
     * @param pageCount     how many Home pages there are; Remove page needs more than one
     * @param locked        Lock Home layout is on: the rows that change the pages give way to
     *                      Unlock ({@link HomeLayoutLock#sheetOffers})
     */
    @NonNull
    public static List<Item> itemsFor(boolean isDefaultHome, boolean hasPages,
                                      boolean pageEmpty, int pageCount, boolean locked) {
        ArrayList<Item> items = new ArrayList<>();
        items.add(Item.TERMINAL);
        if (hasPages) {
            ArrayList<Item> page = new ArrayList<>();
            page.add(Item.UNLOCK_LAYOUT);
            page.add(Item.ADD_WIDGET);
            page.add(Item.ADD_APP);
            page.add(Item.ADD_PAGE);
            page.add(Item.EDIT_WIDGETS);
            page.add(Item.EDIT_PAGES);
            if (pageEmpty && pageCount > 1) page.add(Item.REMOVE_PAGE);
            page.add(Item.LOCK_LAYOUT);
            for (Item item : page) if (HomeLayoutLock.sheetOffers(locked, item)) items.add(item);
        }
        items.add(Item.WALLPAPER);
        items.add(Item.APPEARANCE);
        items.add(Item.LAUNCHER_SETTINGS);
        if (!isDefaultHome) items.add(Item.SET_DEFAULT_HOME);
        items.add(Item.HELP);
        return Collections.unmodifiableList(items);
    }

    /** The rows of {@code items} that stand in {@code group}, in their order. */
    @NonNull
    public static List<Item> inGroup(@NonNull List<Item> items, @NonNull Group group) {
        ArrayList<Item> out = new ArrayList<>();
        for (Item item : items) if (item.group == group) out.add(item);
        return out;
    }

    /**
     * How many two-column lines {@code count} rows take: the last line holds one row when the
     * count is odd.
     */
    public static int lineCount(int count) {
        return count <= 0 ? 0 : (count + 1) / 2;
    }
}
