package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.WindowChips;
import com.termux.app.terminal.AgentStatus;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The Taskbar's run strip (spec "Taskbar"): one numbered entry per running window, then {@code +}
 * for a new window, built from the same chips the Workspace's window bar shows.
 *
 * <p>The chips come in the window chips' order ({@link WindowChips}): the row's windows, the
 * display window's among them at its place, then the Home floats. An entry
 * keeps its chip's index, so a tap goes wherever a tap on that chip goes. The entry in front is
 * lit, a minimised float is marked, and a window asking for the user (its bell, or an agent
 * blocked on it, as the chip column reads it) carries the attention mark. Nothing running is no
 * strip at all: no entries, not a lone {@code +}.</p>
 *
 * <p>A chip's label starts with its process glyph, a Nerd Font symbol no system face has: the
 * entry keeps it apart ({@link Entry#glyph}) for a view that draws it with the bundled symbols
 * face, and its label is plain text. Each entry also carries what tells it apart from the others
 * ({@link Entry#shortLabel}), for a strip too narrow for whole labels.</p>
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
        /**
         * The chip's label as plain text: the process glyph in front taken out ({@link #glyph}),
         * and any other Nerd Font symbol with it, since the strip's monospace face has none of
         * them. Empty for {@code +}.
         */
        @NonNull public final String label;
        /**
         * The process glyph from the front of the chip's label (a Nerd Font code point, to be
         * drawn only with the bundled symbols face), or empty when the label carries none.
         */
        @NonNull public final String glyph;
        /**
         * What tells this entry apart from the others ({@link #distinguishing}): the label less a
         * {@code user@host} prompt lead, a Home float's page mark and any lead every entry
         * shares. Empty for {@code +}.
         */
        @NonNull public final String shortLabel;
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
              @NonNull String glyph, @NonNull String shortLabel, @NonNull String spokenLabel,
              boolean current, boolean minimised, boolean attention, boolean busy) {
            this.kind = kind;
            this.chipIndex = chipIndex;
            this.number = number;
            this.label = label;
            this.glyph = glyph;
            this.shortLabel = shortLabel;
            this.spokenLabel = spokenLabel;
            this.current = current;
            this.minimised = minimised;
            this.attention = attention;
            this.busy = busy;
        }

        /** Whether this is a Home float: a terminal window of the same session, floating on Home. */
        public boolean isFloat() {
            return kind == Kind.HOME_FLOAT;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry that = (Entry) o;
            return kind == that.kind && chipIndex == that.chipIndex && current == that.current
                && minimised == that.minimised && attention == that.attention
                && busy == that.busy && number.equals(that.number) && label.equals(that.label)
                && glyph.equals(that.glyph) && shortLabel.equals(that.shortLabel)
                && spokenLabel.equals(that.spokenLabel);
        }

        @Override
        public int hashCode() {
            int h = kind.hashCode();
            h = 31 * h + chipIndex;
            h = 31 * h + label.hashCode();
            h = 31 * h + shortLabel.hashCode();
            h = 31 * h + (current ? 1 : 0) + (minimised ? 2 : 0) + (attention ? 4 : 0)
                + (busy ? 8 : 0);
            return h;
        }

        @NonNull @Override public String toString() {
            return "Entry{" + kind + " " + number + " '" + label + "' ('" + shortLabel + "')"
                + (current ? " current" : "") + (minimised ? " minimised" : "")
                + (attention ? " attention" : "") + "}";
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
        List<Kind> kinds = new ArrayList<>(chips.size());
        List<String> glyphs = new ArrayList<>(chips.size());
        List<String> labels = new ArrayList<>(chips.size());
        for (int i = 0; i < chips.size(); i++) {
            String label = chips.get(i).label;
            kinds.add(i >= row ? Kind.HOME_FLOAT : i == displayChip ? Kind.DISPLAY : Kind.WINDOW);
            glyphs.add(leadingGlyph(label));
            labels.add(plainText(label));
        }
        List<String> shorts = distinguishing(labels, kinds);
        List<Entry> entries = new ArrayList<>(chips.size() + 1);
        for (int i = 0; i < chips.size(); i++) {
            WindowItem chip = chips.get(i);
            Kind kind = kinds.get(i);
            int floatIndex = i - row;
            boolean minimised = kind == Kind.HOME_FLOAT && floatMinimised != null
                && floatIndex < floatMinimised.length && floatMinimised[floatIndex];
            entries.add(new Entry(kind, i, String.valueOf(i + 1), labels.get(i), glyphs.get(i),
                shorts.get(i), chip.spokenLabel, i == selectedChip, minimised,
                needsAttention(chip), isWorking(chip)));
        }
        entries.add(new Entry(Kind.NEW_WINDOW, -1, "+", "", "", "", newWindowSpoken, false, false,
            false, false));
        return entries;
    }

    /**
     * The strip for the window chips {@code chips} as {@code surface} (the run strip, or the Wall
     * strip's tiles) shows them, {@code items} each chip dressed for the bar, in the same order:
     * the entry in front is the one {@code surface} selects, a minimised float is marked.
     */
    @NonNull
    public static List<Entry> of(@NonNull WindowChips<?> chips,
                                 @NonNull WindowChips.Surface surface,
                                 @NonNull List<WindowItem> items,
                                 @NonNull String newWindowSpoken) {
        List<? extends WindowChips.Chip<?>> list = chips.chips(surface);
        boolean[] minimised = new boolean[Math.max(0, list.size() - chips.rowCount())];
        for (WindowChips.Chip<?> chip : list) {
            if (chip.kind == WindowChips.Kind.HOME_FLOAT && chip.index >= 0
                    && chip.index < minimised.length) {
                minimised[chip.index] = chip.minimised;
            }
        }
        return build(items, chips.selected(surface), chips.rowCount(), chips.displayChip(),
            minimised, newWindowSpoken);
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

    /**
     * The Wall strip's terminals: every window entry in chip order, the row's own and then the
     * Home floats (a float is a terminal window of the same session), {@code +} left out.
     */
    @NonNull
    public static List<Entry> wallEntries(@NonNull List<Entry> entries) {
        List<Entry> wall = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.kind != Kind.NEW_WINDOW) wall.add(entry);
        }
        return wall;
    }

    /** The row's own entries, Home floats and {@code +} left out. */
    @NonNull
    public static List<Entry> rowEntries(@NonNull List<Entry> entries) {
        List<Entry> row = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.kind == Kind.WINDOW || entry.kind == Kind.DISPLAY) row.add(entry);
        }
        return row;
    }

    /**
     * How much room each label gets when the labels share {@code room}: each wants its
     * {@code demands} (held to {@code max}); when they do not all fit, the short ones keep what
     * they want and the long ones split the rest equally, so a short label is never cut to make
     * room nobody uses. When that equal part falls below {@code min} no label says anything
     * useful, and every room is zero: the entries show their numbers alone. A demand of zero
     * gets zero.
     */
    @NonNull
    static float[] labelRooms(@NonNull float[] demands, float room, float max, float min) {
        int n = demands.length;
        float[] wants = new float[n];
        float total = 0f;
        int labelled = 0;
        for (int i = 0; i < n; i++) {
            wants[i] = Math.max(0f, Math.min(max, demands[i]));
            total += wants[i];
            if (wants[i] > 0f) labelled++;
        }
        if (labelled == 0) return new float[n];
        if (total <= room) return wants;
        if (room <= 0f) return new float[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(wants[a], wants[b]));
        float[] rooms = new float[n];
        float left = room;
        int remaining = labelled;
        float level = 0f;
        boolean levelled = false;
        for (int index : order) {
            if (wants[index] <= 0f) continue;
            float share = left / remaining;
            if (!levelled && wants[index] <= share) {
                rooms[index] = wants[index];
                left -= wants[index];
                remaining--;
            } else {
                if (!levelled) {
                    level = share;
                    levelled = true;
                }
                rooms[index] = level;
            }
        }
        if (levelled && level < min) return new float[n];
        return rooms;
    }

    // ---------------------------------------------------------------- labels

    /** A Home float's page mark at a label's end, as {@code HomeFloatPolicy.chipPageMark} adds it. */
    private static final Pattern FLOAT_PAGE_MARK = Pattern.compile("\\s+\u00B7\\s+\\d+$");
    /** A shell prompt's {@code user@host} lead, as window titles often carry one. */
    private static final Pattern USER_AT_HOST =
        Pattern.compile("^\\[?[^\\s@\\[\\]]+@[^\\s:\\]]+\\]?[:\\s]*");

    /**
     * Whether {@code codePoint} is in the Private Use Areas Nerd Fonts fill, as the bundled
     * symbols face reads them ({@code NerdFontSpans.isNerdSymbol}): no system face has these.
     */
    static boolean isNerdSymbol(int codePoint) {
        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
            || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD);
    }

    /**
     * The process glyph the chip factories put in front of a label ({@code TerminalWindowBar}:
     * one symbol, then a space or the label's end), or empty.
     */
    @NonNull
    static String leadingGlyph(@NonNull String label) {
        String trimmed = label.trim();
        if (trimmed.isEmpty()) return "";
        int codePoint = trimmed.codePointAt(0);
        if (!isNerdSymbol(codePoint)) return "";
        int end = Character.charCount(codePoint);
        if (end < trimmed.length() && trimmed.charAt(end) != ' ') return "";
        return trimmed.substring(0, end);
    }

    /** The label with every Nerd Font symbol taken out and its spaces closed up. */
    @NonNull
    static String plainText(@NonNull String label) {
        StringBuilder out = new StringBuilder(label.length());
        int index = 0;
        while (index < label.length()) {
            int codePoint = label.codePointAt(index);
            if (!isNerdSymbol(codePoint)) out.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * What tells each label apart from the others, alongside {@code labels}: a Home float's page
     * mark and a {@code user@host} prompt lead come off first (the badge and the float's tint say
     * those), then any lead every label shares, cut back to a word's start, as long as each label
     * keeps something. Labels that are all the same keep their whole text: nothing else would
     * tell them apart, and the badge's number does.
     */
    @NonNull
    static List<String> distinguishing(@NonNull List<String> labels, @NonNull List<Kind> kinds) {
        List<String> bases = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            String base = labels.get(i);
            if (i < kinds.size() && kinds.get(i) == Kind.HOME_FLOAT) {
                String unmarked = FLOAT_PAGE_MARK.matcher(base).replaceFirst("");
                if (!unmarked.isEmpty()) base = unmarked;
            }
            String unprompted = USER_AT_HOST.matcher(base).replaceFirst("").trim();
            if (!unprompted.isEmpty()) base = unprompted;
            bases.add(base);
        }
        int lead = sharedLead(bases);
        if (lead == 0) return bases;
        List<String> out = new ArrayList<>(bases.size());
        for (String base : bases) out.add(base.substring(lead).trim());
        return out;
    }

    /**
     * How much of the front every label shares and can lose: the common prefix cut back to just
     * after a separator; zero when the labels are fewer than two or all the same, or when any
     * label would be left with nothing.
     */
    static int sharedLead(@NonNull List<String> labels) {
        if (labels.size() < 2) return 0;
        String first = labels.get(0);
        int common = first.length();
        boolean differ = false;
        for (String label : labels) {
            if (!label.equals(first)) differ = true;
            int max = Math.min(common, label.length());
            int at = 0;
            while (at < max && label.charAt(at) == first.charAt(at)) at++;
            common = at;
        }
        if (!differ) return 0;
        // Back to just after a separator, so a word is never cut in half.
        while (common > 0 && !isSeparator(first.charAt(common - 1))) common--;
        if (common == 0) return 0;
        for (String label : labels) {
            if (label.substring(common).trim().isEmpty()) return 0;
        }
        return common;
    }

    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c) || c == ':' || c == '/' || c == '\u00B7' || c == '-'
            || c == '_' || c == '.' || c == '@' || c == '|';
    }
}
