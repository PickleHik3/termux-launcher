package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.AgentStatus;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Taskbar's run strip (spec "Taskbar"): one numbered entry per running window, then {@code +}
 * for a new window, built from the same chips the Workspace's window bar shows.
 *
 * <p>The chips come in the bar's order ({@link com.termux.app.statusbar.WindowChipTarget}): the
 * row's windows, the display window's among them at its place, then the Home floats. An entry
 * keeps its chip's index, so a tap goes wherever a tap on that chip goes. The entry in front is
 * lit, a minimised float is marked, and a window asking for the user (its bell, or an agent
 * blocked on it, as the chip column reads it) carries the attention mark. Nothing running is no
 * strip at all: no entries, not a lone {@code +}.</p>
 *
 * <p>Pure: the activity hands it the chips and the counts, and the view draws the entries.</p>
 */
public final class RunStripModel {

    /** What an entry stands for. */
    public enum Kind { WINDOW, DISPLAY, HOME_FLOAT, NEW_WINDOW }

    /** One entry of the strip. */
    public static final class Entry {
        @NonNull public final Kind kind;
        /** The chip this entry stands for, as the window bar counts chips; -1 for {@code +}. */
        public final int chipIndex;
        /** What the entry's badge says: the window's number, or {@code +}. */
        @NonNull public final String number;
        /** The chip's label; empty for {@code +}. */
        @NonNull public final String label;
        /** What a screen reader says for it. */
        @NonNull public final String spokenLabel;
        /** Whether this is the window in front. */
        public final boolean current;
        /** Whether this is a Home float folded into its chip. */
        public final boolean minimised;
        /** Whether the window is asking for the user. */
        public final boolean attention;
        /** Whether the window is working. */
        public final boolean busy;

        Entry(@NonNull Kind kind, int chipIndex, @NonNull String number, @NonNull String label,
              @NonNull String spokenLabel, boolean current, boolean minimised, boolean attention,
              boolean busy) {
            this.kind = kind;
            this.chipIndex = chipIndex;
            this.number = number;
            this.label = label;
            this.spokenLabel = spokenLabel;
            this.current = current;
            this.minimised = minimised;
            this.attention = attention;
            this.busy = busy;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry that = (Entry) o;
            return kind == that.kind && chipIndex == that.chipIndex && current == that.current
                && minimised == that.minimised && attention == that.attention
                && busy == that.busy && number.equals(that.number) && label.equals(that.label)
                && spokenLabel.equals(that.spokenLabel);
        }

        @Override
        public int hashCode() {
            int h = kind.hashCode();
            h = 31 * h + chipIndex;
            h = 31 * h + label.hashCode();
            h = 31 * h + (current ? 1 : 0) + (minimised ? 2 : 0) + (attention ? 4 : 0)
                + (busy ? 8 : 0);
            return h;
        }

        @NonNull @Override public String toString() {
            return "Entry{" + kind + " " + number + " '" + label + "'" + (current ? " current" : "")
                + (minimised ? " minimised" : "") + (attention ? " attention" : "") + "}";
        }
    }

    private RunStripModel() {}

    /**
     * The strip for {@code chips}.
     *
     * @param selectedChip the chip in front, as the bar selects it; -1 for none
     * @param rowCount the row's windows among the chips, the display window's included; the
     *                 chips after them are the Home floats
     * @param displayChip the display window's chip, or -1 when it is not on the row
     * @param floatMinimised for each Home float in chip order, whether it is minimised; missing
     *                       entries read as not minimised
     * @param newWindowSpoken what a screen reader says for {@code +}
     */
    @NonNull
    public static List<Entry> build(@Nullable List<WindowItem> chips, int selectedChip,
                                    int rowCount, int displayChip,
                                    @Nullable boolean[] floatMinimised,
                                    @NonNull String newWindowSpoken) {
        if (chips == null || chips.isEmpty()) return Collections.emptyList();
        int row = Math.max(0, Math.min(rowCount, chips.size()));
        List<Entry> entries = new ArrayList<>(chips.size() + 1);
        for (int i = 0; i < chips.size(); i++) {
            WindowItem chip = chips.get(i);
            Kind kind = i >= row ? Kind.HOME_FLOAT
                : i == displayChip ? Kind.DISPLAY : Kind.WINDOW;
            int floatIndex = i - row;
            boolean minimised = kind == Kind.HOME_FLOAT && floatMinimised != null
                && floatIndex < floatMinimised.length && floatMinimised[floatIndex];
            entries.add(new Entry(kind, i, String.valueOf(i + 1), chip.label.trim(),
                chip.spokenLabel, i == selectedChip, minimised, needsAttention(chip),
                isWorking(chip)));
        }
        entries.add(new Entry(Kind.NEW_WINDOW, -1, "+", "", newWindowSpoken, false, false, false,
            false));
        return entries;
    }

    /** Asking for the user, the way the window chip column reads it: a bell, or a blocked agent. */
    static boolean needsAttention(@NonNull WindowItem chip) {
        return chip.attention || chip.agentState == AgentStatus.State.BLOCKED;
    }

    /** Working, as the column reads it: a running command, or an agent at work. */
    static boolean isWorking(@NonNull WindowItem chip) {
        return chip.busy || chip.agentState == AgentStatus.State.WORKING;
    }

    /** Whether the strip shows: only while something runs. */
    public static boolean isShown(@NonNull List<Entry> entries) {
        return !entries.isEmpty();
    }

    /** The row's own entries, Home floats and {@code +} left out: the Wall strip's terminals. */
    @NonNull
    public static List<Entry> rowEntries(@NonNull List<Entry> entries) {
        List<Entry> row = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.kind == Kind.WINDOW || entry.kind == Kind.DISPLAY) row.add(entry);
        }
        return row;
    }
}
