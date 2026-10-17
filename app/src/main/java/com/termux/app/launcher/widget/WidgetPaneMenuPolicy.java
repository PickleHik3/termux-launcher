package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Pure item policy for the pane's empty-surface long-press menu. */
public final class WidgetPaneMenuPolicy {
    public enum Item { ADD_WIDGET, EDIT_WIDGETS, REMOVE_PAGE, TERMINAL }

    private WidgetPaneMenuPolicy() {}

    /**
     * Menu contents for the current pane state. An unsupported device gets no menu at all;
     * REMOVE_PAGE appears only for an empty, removable page. Nothing adds a page here: the + on
     * the page's border tab does that, and so does carrying a widget past the last page.
     */
    @NonNull
    public static List<Item> itemsFor(boolean widgetsAvailable, int pageCount,
                                      boolean currentPageEmpty) {
        return itemsFor(widgetsAvailable, pageCount, currentPageEmpty, false);
    }

    /**
     * The same, with Terminal last when the host can open a Home float on the page: it opens one
     * whether or not the device can host widgets.
     */
    @NonNull
    public static List<Item> itemsFor(boolean widgetsAvailable, int pageCount,
                                      boolean currentPageEmpty, boolean terminalOffered) {
        ArrayList<Item> items = new ArrayList<>();
        if (widgetsAvailable) {
            items.add(Item.ADD_WIDGET);
            if (!currentPageEmpty) items.add(Item.EDIT_WIDGETS);
            if (currentPageEmpty && pageCount > 1) items.add(Item.REMOVE_PAGE);
        }
        if (terminalOffered) items.add(Item.TERMINAL);
        return items;
    }
}
