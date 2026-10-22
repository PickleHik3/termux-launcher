package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Ink;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Measure;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.launcher.widget.builtin.WidgetTokens.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tasks widget's layout plan at every span (spec #101, ticket #119). A header that counts
 * what is open ("Today · 3 open"), checkbox rows that are 48 dp targets with the due tag in the
 * caution ink, and, with no tasks, an invitation that names the file it writes to. The view
 * only draws it and puts a transparent target in every TOUCH part. Android-free.
 *
 * <p>Part ids: {@code header}, {@code count}, {@code count.label}, {@code add} (the "+" glyph),
 * {@code add.touch}; per row {@code row.N.box}, {@code row.N.label}, {@code row.N.strike},
 * {@code row.N.due}, {@code row.N.touch}; the empty state's {@code empty.glyph},
 * {@code empty.title}, {@code empty.path}.</p>
 */
final class TasksLayoutPlanner {
    private TasksLayoutPlanner() { }

    /** The kind declares it ({@link BuiltinWidgetKind#maxSpan}); past it the widget only gains room. */
    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetKind.TASKS.maxSpan;

    static final String GLYPH_ADD = "";
    static final String GLYPH_OPEN = "";
    static final String GLYPH_DONE = "";

    /** One task as the card shows it, open ones first. */
    static final class Row {
        @NonNull final String text;
        @Nullable final String due;
        final boolean done;

        Row(@NonNull String text, @Nullable String due, boolean done) {
            this.text = text; this.due = due; this.done = done;
        }
    }

    /** The words the plan shows, from the app's strings. */
    interface Words {
        /** "Today · 3 open". */
        @NonNull String header(int open);
        /** "3 open", where the header's "Today" does not fit. */
        @NonNull String headerShort(int open);
        /** "open", under the count of a compact card. */
        @NonNull String openLabel(int open);
        /** "Add a task". */
        @NonNull String addTask();
        /** "Writes to ~/tasks.md". */
        @NonNull String writesTo(@NonNull String path);
    }

    private static final float BOX_DP = 20f;
    private static final float ADD_GLYPH_DP = 20f;
    private static final float EMPTY_GLYPH_DP = 28f;
    private static final float EMPTY_GLYPH_SMALL_DP = 24f;
    private static final float STRIKE_DP = 1f;
    /** A row's label is not cut below this to make room for the due tag. */
    private static final float LABEL_MIN_DP = 56f;
    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;
    private static final float ROW_DP = WidgetTokens.TOUCH_TARGET_DP;

    /**
     * The plan for Tasks at {@code span} in a {@code widthDp}×{@code heightDp} card; {@code rows}
     * are the tasks to show, {@code open} counts the open ones of the whole file, {@code loaded}
     * says the file has been read, and {@code path} is the file as the user wrote it.
     */
    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale, boolean loaded,
                                          @NonNull List<Row> rows, int open, @NonNull String path,
                                          @NonNull Words words, @NonNull Measure measure) {
        Box card = new Box(0f, 0f, widthDp, heightDp);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, WidgetTokens.insetDp(span));
        Builder b = new Builder(fontScale, words, measure);
        if (loaded) {
            boolean empty = rows.isEmpty();
            if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
                if (empty) b.emptyOne(inner, path); else b.countOne(inner, open);
            } else if (span.rows == 1) {
                if (empty) b.emptyRow(inner, path); else b.compact(inner, span, rows, open);
            } else {
                if (empty) b.emptyTall(inner, path); else b.tall(inner, rows, open);
            }
        }
        return new WidgetLayoutPlan(BuiltinWidgetKind.TASKS, span, MAX_SPAN, fontScale, card, inner,
            b.parts);
    }

    private static final class Builder {
        final float fontScale;
        final Words words;
        final Measure measure;
        final List<Part> parts = new ArrayList<>();

        Builder(float fontScale, Words words, Measure measure) {
            this.fontScale = fontScale; this.words = words; this.measure = measure;
        }

        float sp(Role role) { return WidgetTokens.sizeDp(role, fontScale); }

        float width(String text, Role role, boolean numeric) {
            return measure.widthDp(text, role, numeric, sp(role));
        }

        /** A line of {@code role} text in a box of its own height at {@code left, top}. */
        Box line(float left, float top, float maxWidth, Role role) {
            return new Box(left, top, maxWidth, WidgetTokens.lineDp(sp(role)));
        }

        // ----- one by one --------------------------------------------------------------------

        void countOne(Box inner, int open) {
            String count = String.valueOf(open);
            String label = words.openLabel(open);
            float labelH = WidgetTokens.lineDp(sp(Role.LABEL));
            float hero = Math.min(WidgetTokens.heroMaxDp(),
                (inner.height - labelH - GAP_S) / WidgetTokens.HERO_LINE_HEIGHT);
            float least = WidgetTokens.TEXT_FLOOR_SP * fontScale;
            while (hero > least && measure.widthDp(count, Role.HERO, true, hero) > inner.width) hero -= 1f;
            hero = Math.max(hero, least);
            float heroH = WidgetTokens.heroLineDp(hero);
            float top = inner.top + Math.max(0f, (inner.height - heroH - GAP_S - labelH) / 2f);
            float heroW = Math.min(inner.width, measure.widthDp(count, Role.HERO, true, hero));
            parts.add(Part.text("count", new Box(inner.centerX() - heroW / 2f, top, heroW, heroH),
                count, Role.HERO, true, hero, Ink.STRONG));
            float labelW = Math.min(inner.width, width(label, Role.LABEL, false));
            parts.add(Part.text("count.label", new Box(inner.centerX() - labelW / 2f,
                top + heroH + GAP_S, labelW, labelH), label, Role.LABEL, false, sp(Role.LABEL),
                Ink.SOFT));
        }

        void emptyOne(Box inner, String path) {
            float glyph = EMPTY_GLYPH_SMALL_DP;
            float titleH = WidgetTokens.lineDp(sp(Role.LABEL));
            float top = inner.top + Math.max(0f, (inner.height - glyph - GAP_M - titleH) / 2f);
            parts.add(Part.glyph("empty.glyph", new Box(inner.centerX() - glyph / 2f, top, glyph,
                glyph), GLYPH_ADD, Ink.MOON));
            String title = words.addTask();
            float titleW = Math.min(inner.width, width(title, Role.LABEL, false));
            parts.add(Part.text("empty.title", new Box(inner.centerX() - titleW / 2f,
                top + glyph + GAP_M, titleW, titleH), title, Role.LABEL, false, sp(Role.LABEL),
                Ink.STRONG));
            parts.add(Part.touch("add.touch", inner));
        }

        // ----- one row -----------------------------------------------------------------------

        void emptyRow(Box inner, String path) {
            float glyph = EMPTY_GLYPH_DP;
            parts.add(Part.glyph("empty.glyph", new Box(inner.left,
                inner.centerY() - glyph / 2f, glyph, glyph), GLYPH_ADD, Ink.MOON));
            float left = inner.left + glyph + GAP_L;
            float w = Math.max(0f, inner.right() - left);
            float titleH = WidgetTokens.lineDp(sp(Role.TITLE));
            float pathH = WidgetTokens.lineDp(sp(Role.LABEL));
            boolean withPath = titleH + GAP_S + pathH <= inner.height + WidgetLayoutPlan.EPSILON;
            float total = withPath ? titleH + GAP_S + pathH : titleH;
            float top = inner.top + Math.max(0f, (inner.height - total) / 2f);
            parts.add(Part.text("empty.title", new Box(left, top, w, titleH), words.addTask(),
                Role.TITLE, false, sp(Role.TITLE), Ink.STRONG));
            if (withPath) {
                parts.add(Part.text("empty.path", new Box(left, top + titleH + GAP_S, w, pathH),
                    words.writesTo(path), Role.LABEL, false, sp(Role.LABEL), Ink.SOFT));
            }
            parts.add(Part.touch("add.touch", inner));
        }

        /** The count at the left, the tasks in one or two columns to its right. */
        void compact(Box inner, BuiltinWidgetSpan span, List<Row> rows, int open) {
            String count = String.valueOf(open);
            String label = words.openLabel(open);
            float numeral = Math.max(sp(Role.HEADLINE), 1f);
            float labelH = WidgetTokens.lineDp(sp(Role.LABEL));
            float countW = Math.max(measure.widthDp(count, Role.HEADLINE, true, numeral),
                width(label, Role.LABEL, false));
            float countH = WidgetTokens.lineDp(numeral);
            boolean withLabel = countH + labelH <= inner.height + WidgetLayoutPlan.EPSILON;
            float blockH = withLabel ? countH + labelH : countH;
            float top = inner.top + Math.max(0f, (inner.height - blockH) / 2f);
            float centre = inner.left + countW / 2f;
            float numeralW = measure.widthDp(count, Role.HEADLINE, true, numeral);
            parts.add(Part.text("count", new Box(centre - numeralW / 2f, top, numeralW, countH),
                count, Role.HEADLINE, true, numeral, Ink.STRONG));
            if (withLabel) {
                float labelW = width(label, Role.LABEL, false);
                parts.add(Part.text("count.label", new Box(centre - labelW / 2f, top + countH,
                    labelW, labelH), label, Role.LABEL, false, sp(Role.LABEL), Ink.SOFT));
            }
            float areaLeft = inner.left + countW + GAP_L;
            int columns = span.columns == 4 ? 2 : 1;
            float columnW = (inner.right() - areaLeft - GAP_L * (columns - 1)) / columns;
            // A row is 48 dp; in a card with less than that inside its inset it takes what there is.
            float rowH = Math.min(ROW_DP, inner.height);
            int lines = Math.max(1, (int) Math.floor((inner.height + 0.01f) / rowH));
            float used = Math.min(lines, (rows.size() + columns - 1) / columns) * rowH;
            float rowsTop = inner.top + Math.max(0f, (inner.height - used) / 2f);
            for (int i = 0; i < rows.size() && i < lines * columns; i++) {
                float left = areaLeft + (i % columns) * (columnW + GAP_L);
                row(i, rows.get(i), left, rowsTop + (i / columns) * rowH, columnW, rowH);
            }
        }

        // ----- tall --------------------------------------------------------------------------

        void emptyTall(Box inner, String path) {
            float glyph = EMPTY_GLYPH_DP;
            float titleH = WidgetTokens.lineDp(sp(Role.TITLE));
            float pathH = WidgetTokens.lineDp(sp(Role.LABEL));
            float total = glyph + GAP_M + titleH + GAP_S + pathH;
            boolean withPath = total <= inner.height + WidgetLayoutPlan.EPSILON;
            if (!withPath) total = glyph + GAP_M + titleH;
            float top = inner.top + Math.max(0f, (inner.height - total) / 2f);
            parts.add(Part.glyph("empty.glyph", new Box(inner.centerX() - glyph / 2f, top, glyph,
                glyph), GLYPH_ADD, Ink.MOON));
            top += glyph + GAP_M;
            String title = words.addTask();
            float titleW = Math.min(inner.width, width(title, Role.TITLE, false));
            parts.add(Part.text("empty.title", new Box(inner.centerX() - titleW / 2f, top, titleW,
                titleH), title, Role.TITLE, false, sp(Role.TITLE), Ink.STRONG));
            if (withPath) {
                String note = words.writesTo(path);
                float noteW = Math.min(inner.width, width(note, Role.LABEL, false));
                parts.add(Part.text("empty.path", new Box(inner.centerX() - noteW / 2f,
                    top + titleH + GAP_S, noteW, pathH), note, Role.LABEL, false, sp(Role.LABEL),
                    Ink.SOFT));
            }
            parts.add(Part.touch("add.touch", inner));
        }

        void tall(Box inner, List<Row> rows, int open) {
            float titleH = WidgetTokens.lineDp(sp(Role.TITLE));
            float addW = Math.min(ROW_DP, inner.width);
            float headerW = inner.width - addW;
            String header = words.header(open);
            if (width(header, Role.TITLE, true) > headerW) header = words.headerShort(open);
            parts.add(Part.text("header", new Box(inner.left, inner.top, headerW, titleH), header,
                Role.TITLE, true, sp(Role.TITLE), Ink.STRONG));
            // A row's target is 48 dp but its label sits in the middle of it, so the first
            // row's target may reach up under the header and leave the design's gap visible.
            float bodyH = WidgetTokens.lineDp(sp(Role.BODY));
            float overlap = Math.max(0f, GAP_S + (ROW_DP - bodyH) / 2f - GAP_M);
            float rowsTop = inner.top + titleH + GAP_S - overlap;
            float addGlyph = Math.min(ADD_GLYPH_DP, titleH);
            parts.add(Part.glyph("add", new Box(inner.right() - addW / 2f - addGlyph / 2f,
                inner.top + titleH / 2f - addGlyph / 2f, addGlyph, addGlyph), GLYPH_ADD, Ink.MOON));
            int fit = Math.max(0, (int) Math.floor((inner.bottom() - rowsTop + 0.01f) / ROW_DP));
            for (int i = 0; i < rows.size() && i < fit; i++) {
                row(i, rows.get(i), inner.left, rowsTop + i * ROW_DP, inner.width, ROW_DP);
            }
            // The "+" target: 48 dp square, centred on the header, reaching up into the card's
            // inset; added after the rows so it is over the first row's top corner.
            float touchTop = Math.max(0f, inner.top + titleH / 2f - ROW_DP / 2f);
            parts.add(Part.touch("add.touch", new Box(inner.right() - addW, touchTop, addW, ROW_DP)));
        }

        // ----- a row -------------------------------------------------------------------------

        /** One 48 dp row at {@code left, top}, {@code width} wide. */
        void row(int index, Row row, float left, float top, float width, float rowH) {
            String id = "row." + index + ".";
            float box = Math.min(BOX_DP, width);
            parts.add(Part.glyph(id + "box", new Box(left, top + (rowH - box) / 2f, box, box),
                row.done ? GLYPH_DONE : GLYPH_OPEN, row.done ? Ink.MOON : Ink.SOFT));
            float labelLeft = left + box + GAP_M;
            float right = left + width;
            float bodySp = sp(Role.BODY);
            float bodyH = WidgetTokens.lineDp(bodySp);
            String due = row.due == null || row.done ? "" : row.due;
            if (!due.isEmpty()) {
                float dueW = width(due, Role.LABEL, true);
                if (right - dueW - GAP_S - labelLeft >= LABEL_MIN_DP) {
                    float dueH = WidgetTokens.lineDp(sp(Role.LABEL));
                    parts.add(Part.text(id + "due", new Box(right - dueW,
                        top + (rowH - dueH) / 2f, dueW, dueH), due, Role.LABEL, true,
                        sp(Role.LABEL), Ink.CAUTION));
                    right -= dueW + GAP_S;
                }
            }
            float labelW = Math.max(0f, right - labelLeft);
            parts.add(Part.text(id + "label", new Box(labelLeft, top + (rowH - bodyH) / 2f,
                labelW, bodyH), row.text, Role.BODY, false, bodySp,
                row.done ? Ink.SOFT : Ink.STRONG));
            if (row.done) {
                float drawn = Math.min(labelW, width(row.text, Role.BODY, false));
                parts.add(Part.rule(id + "strike", new Box(labelLeft, top + rowH / 2f, drawn,
                    STRIKE_DP)));
            }
            parts.add(Part.touch(id + "touch", new Box(left, top, width, rowH)));
        }
    }
}
