package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Pure item policy for the pane's empty-surface long-press menu. */
public final class WidgetPaneMenuPolicy {
    public enum Item { ADD_WIDGET, EDIT_WIDGETS, ADD_PAGE, REMOVE_PAGE }

    private WidgetPaneMenuPolicy() {}

    /**
     * Menu contents for the current pane state. An unsupported device gets no menu at all;
     * REMOVE_PAGE appears only for an empty, removable page.
     */
    @NonNull
    public static List<Item> itemsFor(boolean widgetsAvailable, int pageCount,
                                      boolean currentPageEmpty) {
        ArrayList<Item> items = new ArrayList<>();
        if (!widgetsAvailable) return items;
        items.add(Item.ADD_WIDGET);
        if (!currentPageEmpty) items.add(Item.EDIT_WIDGETS);
        items.add(Item.ADD_PAGE);
        if (currentPageEmpty && pageCount > 1) items.add(Item.REMOVE_PAGE);
        return items;
    }
}
