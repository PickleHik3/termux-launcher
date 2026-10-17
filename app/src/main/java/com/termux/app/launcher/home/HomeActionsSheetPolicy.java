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
        ADD_PAGE(Group.PAGE),
        /** The Home page editor: the corner tab's pencil. */
        EDIT_PAGES(Group.PAGE),
        /** The Layout editor on Home. */
        LAYOUT(Group.PAGE),
        WALLPAPER(Group.LAUNCHER),
        /** The Appearance editor's Look page. */
        LOOK(Group.LAUNCHER),
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
     * The sheet's rows for this state.
     *
     * @param isDefaultHome   the launcher is the phone's home app, so Set as default home is not
     *                        offered
     * @param hasPages        the Home pages are there to act on (a terminal-only install has none),
     *                        so the page's widget, page, editor and Layout rows are offered
     * @param terminalOffered a Home float can open on the page (the host has the panes for it)
     */
    @NonNull
    public static List<Item> itemsFor(boolean isDefaultHome, boolean hasPages,
                                      boolean terminalOffered) {
        ArrayList<Item> items = new ArrayList<>();
        if (terminalOffered) items.add(Item.TERMINAL);
        if (hasPages) {
            items.add(Item.ADD_WIDGET);
            items.add(Item.ADD_PAGE);
            items.add(Item.EDIT_PAGES);
            items.add(Item.LAYOUT);
        }
        items.add(Item.WALLPAPER);
        items.add(Item.LOOK);
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
