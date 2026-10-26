package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What a grid shrink did to the wall: every widget that moved, changed page or lost span. */
public final class WidgetReflowReport {
    /** One widget's before and after. */
    public static final class Entry {
        public final int appWidgetId;
        public final int fromPage;
        @NonNull public final WidgetCellRect fromCell;
        public final int toPage;
        @NonNull public final WidgetCellRect toCell;

        Entry(int appWidgetId, int fromPage, @NonNull WidgetCellRect fromCell, int toPage,
              @NonNull WidgetCellRect toCell) {
            this.appWidgetId = appWidgetId;
            this.fromPage = fromPage;
            this.fromCell = fromCell;
            this.toPage = toPage;
            this.toCell = toCell;
        }

        public boolean changedPage() { return fromPage != toPage; }

        /** True when the widget came out smaller than it went in. */
        public boolean shrank() {
            return toCell.columnSpan() < fromCell.columnSpan()
                || toCell.rowSpan() < fromCell.rowSpan();
        }
    }

    static final WidgetReflowReport EMPTY = new WidgetReflowReport(new ArrayList<Entry>());

    @NonNull private final List<Entry> entries;

    WidgetReflowReport(@NonNull List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    @NonNull public List<Entry> entries() { return entries; }
    public boolean isEmpty() { return entries.isEmpty(); }
    public int movedCount() { return entries.size(); }

    public int shrunkCount() {
        int count = 0;
        for (Entry entry : entries) if (entry.shrank()) count++;
        return count;
    }

    public int pageChangeCount() {
        int count = 0;
        for (Entry entry : entries) if (entry.changedPage()) count++;
        return count;
    }
}
