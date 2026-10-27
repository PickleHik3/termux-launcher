package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Ink;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Measure;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.launcher.widget.builtin.WidgetTokens.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Device widget's layout plan at every span (spec #101, ticket #117): battery first, then
 * storage, memory and temperature as it grows, and the processor only where there is privileged
 * access.
 *
 * <ul>
 *   <li>1×1: the battery ring, filling the card to the inset; the ring fits its own number
 *       ({@link OneByOneFit}).</li>
 *   <li>2×1: the level as the hero, the bolt while charging, and one line under it: "Full in
 *       1 h 10 m", the drain rate or the charger's state.</li>
 *   <li>4×1: the 2×1's block, then as many labelled bars as the row holds.</li>
 *   <li>2×2: the 2×1's block on top and the labelled bars (Storage, RAM, Temp) at the bottom.</li>
 *   <li>4×2: the 2×2 beside the last 24 hours' chart, with the CPU's recent history under it
 *       when there is access.</li>
 * </ul>
 *
 * <p>The hero is fit to the span and never follows the font scale, so a large font cannot blow
 * it up; every other line is a fixed role that does. Nothing is ever cut short: a line that does
 * not fit its room takes its short form or is left out, least useful first (a bar from the
 * bottom, then the line under the hero; on the chart side its hour axis, the CPU, the title).
 * Every bar is {@link #BAR_DP} high with a word label and its value on the line above.
 * Android-free.</p>
 *
 * <p>Part ids: views {@code ring}, {@code bolt}, {@code <reading>.bar}, {@code chart},
 * {@code cpu}, {@code cpu.value}; text {@code hero}, {@code detail}, {@code <reading>.label},
 * {@code <reading>.value}, {@code chart.title}, {@code axis.<i>}, {@code cpu.label}; glyphs
 * {@code <reading>.glyph}; the {@code rule} between the 4×1's and 4×2's halves.</p>
 */
final class DeviceLayoutPlanner {
    private DeviceLayoutPlanner() { }

    /** The kind declares it ({@link BuiltinWidgetKind#maxSpan}); past it the widget only gains room. */
    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetKind.DEVICE.maxSpan;

    /** Every bar's height: the spec's floor. */
    static final float BAR_DP = 6f;
    /** The shortest the 24 h chart and the CPU history are drawn. */
    static final float CHART_MIN_DP = 32f;
    static final float CPU_MIN_DP = 24f;
    /** The chart's share of the room it splits with the CPU history. */
    static final float CHART_SHARE = 0.6f;
    /** A bar's glyph is as tall as its row, up to this. */
    static final float GLYPH_MAX_DP = 32f;
    /** The widest level, which the hero is fit to so it keeps one size from 5 % to 100 %. */
    static final String HERO_WIDEST = "100%";
    /** The widest CPU reading, which its value's box is sized for. */
    static final String CPU_WIDEST = "100%";

    private static final float RULE_DP = 1f;
    private static final float PROBE = 100f;
    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;

    /** One labelled bar: "RAM" over its track, "3.1/7.7 GB" at the end of the line. */
    static final class Reading {
        /** "storage", "ram", "temp": the prefix of the reading's part ids. */
        @NonNull final String id;
        @NonNull final String glyph;
        @NonNull final String label;
        /** Empty while unknown: the label and an empty track still show, never a dash. */
        @NonNull final String value;

        Reading(@NonNull String id, @NonNull String glyph, @NonNull String label,
                @NonNull String value) {
            this.id = id; this.glyph = glyph; this.label = label; this.value = value;
        }
    }

    /** What the plan is built from. Strings are already the words to show. */
    static final class Content {
        /** "84%", or empty while the level is unknown. */
        @NonNull final String hero;
        /** The bolt beside the level: on the charger. */
        final boolean bolt;
        /** The line under the level and its short form for a narrow card. */
        @NonNull final String detail;
        @NonNull final String detailShort;
        /** The bars in their order; a span takes as many as it holds. */
        @NonNull final List<Reading> readings;
        /** "Battery · 24 h", and "24 h" for a narrow column. */
        @NonNull final String chartTitle;
        @NonNull final String chartTitleShort;
        /** The labels under the chart, evenly spaced from its oldest hour to now. */
        @NonNull final List<String> axis;
        /** "CPU", or null for no CPU history: no access, or no reading yet. Never a dash. */
        @Nullable final String cpuLabel;

        Content(@NonNull String hero, boolean bolt, @NonNull String detail,
                @NonNull String detailShort, @NonNull List<Reading> readings,
                @NonNull String chartTitle, @NonNull String chartTitleShort,
                @NonNull List<String> axis, @Nullable String cpuLabel) {
            this.hero = hero; this.bolt = bolt; this.detail = detail; this.detailShort = detailShort;
            this.readings = Collections.unmodifiableList(new ArrayList<>(readings));
            this.chartTitle = chartTitle; this.chartTitleShort = chartTitleShort;
            this.axis = Collections.unmodifiableList(new ArrayList<>(axis));
            this.cpuLabel = cpuLabel;
        }

        /** Everything the plan depends on, so the view plans again only when it changes. */
        @NonNull String signature() {
            StringBuilder out = new StringBuilder().append(hero).append('|').append(bolt)
                .append('|').append(detail).append('|').append(detailShort);
            for (Reading reading : readings) {
                out.append('|').append(reading.id).append('=').append(reading.label).append(':')
                    .append(reading.value);
            }
            out.append('|').append(chartTitle).append('|').append(chartTitleShort);
            for (String label : axis) out.append('|').append(label);
            return out.append('|').append(cpuLabel).toString();
        }
    }

    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @NonNull Content content, @NonNull Measure measure) {
        Box card = new Box(0f, 0f, widthDp, heightDp);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, WidgetTokens.insetDp(span));
        List<Part> parts = new ArrayList<>();
        switch (span) {
            case ONE_BY_ONE: {
                float side = Math.min(inner.width, inner.height);
                parts.add(Part.touch("ring", new Box(inner.centerX() - side / 2f,
                    inner.centerY() - side / 2f, side, side)));
                break;
            }
            case TWO_BY_ONE:
                battery(span, inner, fontScale, content, Collections.emptyList(), measure, parts);
                break;
            case TWO_BY_TWO:
                battery(span, inner, fontScale, content, content.readings, measure, parts);
                break;
            case FOUR_BY_ONE:
                fourByOne(span, inner, fontScale, content, measure, parts);
                break;
            case FOUR_BY_TWO:
            default:
                fourByTwo(span, inner, fontScale, content, measure, parts);
                break;
        }
        return new WidgetLayoutPlan(BuiltinWidgetKind.DEVICE, span, MAX_SPAN, fontScale, card,
            inner, parts);
    }

    // ----- the battery block ----------------------------------------------------------------

    /**
     * The level, its bolt and the line under it in {@code box}, with {@code wanted}'s bars at the
     * bottom on a tall span. On one row the block is centred and the bolt leads it; on a tall
     * span it sits at the top and the bolt follows the level. Returns how wide the block drew.
     */
    private static float battery(@NonNull BuiltinWidgetSpan span, @NonNull Box box, float fs,
                                 @NonNull Content content, @NonNull List<Reading> wanted,
                                 @NonNull Measure measure, @NonNull List<Part> parts) {
        boolean compact = span.rows == 1;
        float detailSize = WidgetTokens.sizeDp(Role.BODY, fs);
        float detailLine = WidgetTokens.lineDp(detailSize);
        boolean hasHero = !content.hero.isEmpty();
        boolean bolt = hasHero && content.bolt;
        float unit = 0f;
        if (hasHero) {
            unit = Math.max(measure.widthDp(HERO_WIDEST, Role.HERO, true, PROBE),
                measure.widthDp(content.hero, Role.HERO, true, PROBE)) / PROBE;
        }
        float boltShare = bolt ? WidgetTokens.HERO_LINE_HEIGHT : 0f;
        float widthFit = hasHero ? (box.width - (bolt ? GAP_M : 0f)) / (unit + boltShare) : 0f;
        float heroMin = WidgetTokens.heroMinDp(span, fs);
        Rows rows = rows(wanted, box.width, fs, measure);

        // The most content that fits: the line under the level before any bar, bars from the
        // bottom up. Without a level the line is what the card says, so it is always wanted.
        float hero = 0f;
        int keep = 0;
        String detail = null;
        boolean detailIndented = false;
        boolean found = false;
        for (int pass = 0; pass < 2 && !found; pass++) {
            boolean withDetail = pass == 0;
            for (int k = rows.size(); k >= 0 && !found; k--) {
                float fixed = (withDetail ? detailLine + (hasHero ? GAP_S : 0f) : 0f)
                    + (k > 0 ? rows.height(k) + ((hasHero || withDetail) ? GAP_L : 0f) : 0f);
                float size = 0f;
                if (hasHero) {
                    size = Math.min(WidgetTokens.heroMaxDp(), Math.min(widthFit,
                        (box.height - fixed) / WidgetTokens.HERO_LINE_HEIGHT));
                    if (size < heroMin) continue;
                } else if (fixed > box.height + WidgetLayoutPlan.EPSILON) {
                    continue;
                }
                String line = null;
                boolean underHero = false;
                if (withDetail) {
                    // Under the level, past the leading bolt, where it fits; else from the edge.
                    float indent = compact && bolt
                        ? size * WidgetTokens.HERO_LINE_HEIGHT + GAP_M : 0f;
                    if (indent > 0f && measure.widthDp(content.detail, Role.BODY, false,
                        detailSize) <= box.width - indent) {
                        line = content.detail;
                        underHero = true;
                    } else {
                        line = pick(content.detail, content.detailShort, box.width, Role.BODY,
                            detailSize, measure);
                    }
                    if (line == null) continue;
                }
                hero = size; keep = k; detail = line; detailIndented = underHero; found = true;
            }
        }
        if (!found && hasHero) {
            // Not even the level at its smallest beside nothing: the largest it can be, if legible.
            hero = Math.min(WidgetTokens.heroMaxDp(),
                Math.min(widthFit, box.height / WidgetTokens.HERO_LINE_HEIGHT));
            if (hero < WidgetTokens.TEXT_FLOOR_SP * fs) hero = 0f;
        }

        float heroLine = WidgetTokens.heroLineDp(hero);
        boolean showHero = hasHero && hero > 0f;
        float blockH = (showHero ? heroLine : 0f)
            + (detail != null ? detailLine + (showHero ? GAP_S : 0f) : 0f);
        float top = compact ? box.top + (box.height - blockH) / 2f : box.top;
        float textLeft = box.left;
        float right = box.left;
        if (showHero) {
            if (bolt && compact) {
                parts.add(Part.touch("bolt", new Box(box.left, top, heroLine, heroLine)));
                textLeft = box.left + heroLine + GAP_M;
            }
            float heroW = measure.widthDp(content.hero, Role.HERO, true, hero);
            parts.add(Part.text("hero", new Box(textLeft, top, heroW, heroLine), content.hero,
                Role.HERO, true, hero, Ink.STRONG));
            right = textLeft + heroW;
            if (bolt && !compact) {
                parts.add(Part.touch("bolt", new Box(right + GAP_M, top, heroLine, heroLine)));
                right += GAP_M + heroLine;
            }
            top += heroLine + GAP_S;
        }
        if (detail != null) {
            float w = measure.widthDp(detail, Role.BODY, false, detailSize);
            float x = detailIndented ? textLeft : box.left;
            parts.add(Part.text("detail", new Box(x, top, w, detailLine), detail,
                Role.BODY, false, detailSize, Ink.SOFT));
            right = Math.max(right, x + w);
        }
        if (keep > 0) {
            rows.place(keep, box.left, box.bottom() - rows.height(keep), box.right(), parts);
            right = box.right();
        }
        return right - box.left;
    }

    /** {@code full} where it fits {@code width}, else {@code shorter}, else null. */
    @Nullable private static String pick(@NonNull String full, @NonNull String shorter,
                                         float width, @NonNull Role role, float size,
                                         @NonNull Measure measure) {
        if (!full.isEmpty() && measure.widthDp(full, role, false, size) <= width) return full;
        if (!shorter.isEmpty() && measure.widthDp(shorter, role, false, size) <= width) {
            return shorter;
        }
        return null;
    }

    // ----- the bars -------------------------------------------------------------------------

    /** The bars that fit a column's width, and whether their glyphs do too. */
    private static final class Rows {
        final List<Reading> kept = new ArrayList<>();
        final List<float[]> widths = new ArrayList<>();
        final float labelSize, labelLine, rowH, glyph;
        boolean glyphs;
        @NonNull final Measure measure;

        Rows(float fs, @NonNull Measure measure) {
            this.measure = measure;
            labelSize = WidgetTokens.sizeDp(Role.LABEL, fs);
            labelLine = WidgetTokens.lineDp(labelSize);
            rowH = labelLine + GAP_S + BAR_DP;
            glyph = Math.min(rowH, GLYPH_MAX_DP);
        }

        int size() { return kept.size(); }

        /** The first {@code count} bars' height, gaps between them included. */
        float height(int count) {
            return count <= 0 ? 0f : count * rowH + (count - 1) * GAP_M;
        }

        void place(int count, float left, float top, float right, @NonNull List<Part> parts) {
            float y = top;
            for (int i = 0; i < count && i < kept.size(); i++) {
                Reading reading = kept.get(i);
                float labelW = widths.get(i)[0], valueW = widths.get(i)[1];
                float textLeft = left;
                if (glyphs) {
                    parts.add(Part.glyph(reading.id + ".glyph",
                        new Box(left, y + (rowH - glyph) / 2f, glyph, glyph), reading.glyph,
                        Ink.SOFT));
                    textLeft = left + glyph + GAP_M;
                }
                parts.add(Part.text(reading.id + ".label", new Box(textLeft, y, labelW, labelLine),
                    reading.label, Role.LABEL, false, labelSize, Ink.STRONG));
                if (valueW > 0f) {
                    parts.add(Part.text(reading.id + ".value",
                        new Box(right - valueW, y, valueW, labelLine), reading.value, Role.LABEL,
                        true, labelSize, Ink.SOFT));
                }
                parts.add(Part.touch(reading.id + ".bar",
                    new Box(textLeft, y + labelLine + GAP_S, right - textLeft, BAR_DP)));
                y += rowH + GAP_M;
            }
        }
    }

    /**
     * Of {@code wanted}, the bars whose label and value fit {@code width} on one line; the glyphs
     * lead them only when every one still fits beside its glyph, so the labels line up.
     */
    @NonNull private static Rows rows(@NonNull List<Reading> wanted, float width, float fs,
                                      @NonNull Measure measure) {
        Rows rows = new Rows(fs, measure);
        boolean allWithGlyph = true;
        for (Reading reading : wanted) {
            float labelW = measure.widthDp(reading.label, Role.LABEL, false, rows.labelSize);
            float valueW = reading.value.isEmpty() ? 0f
                : measure.widthDp(reading.value, Role.LABEL, true, rows.labelSize);
            float need = labelW + (valueW > 0f ? GAP_M + valueW : 0f);
            if (need > width) continue;
            if (need + rows.glyph + GAP_M > width) allWithGlyph = false;
            rows.kept.add(reading);
            rows.widths.add(new float[] {labelW, valueW});
        }
        rows.glyphs = allWithGlyph && !rows.kept.isEmpty();
        return rows;
    }

    // ----- the wide spans -------------------------------------------------------------------

    /** The 2×1's block in the left half at most, then the bars the rest of the row holds. */
    private static void fourByOne(@NonNull BuiltinWidgetSpan span, @NonNull Box inner, float fs,
                                  @NonNull Content content, @NonNull Measure measure,
                                  @NonNull List<Part> parts) {
        float used = battery(span, new Box(inner.left, inner.top, inner.width / 2f, inner.height),
            fs, content, Collections.emptyList(), measure, parts);
        float ruleX = inner.left + used + GAP_L;
        float left = used > 0f ? ruleX + RULE_DP + GAP_L : inner.left;
        Box rest = new Box(left, inner.top, inner.right() - left, inner.height);
        Rows rows = rows(content.readings, rest.width, fs, measure);
        int count = 0;
        while (count < rows.size() && rows.height(count + 1) <= rest.height + WidgetLayoutPlan.EPSILON) {
            count++;
        }
        if (count == 0) return;
        if (used > 0f) parts.add(Part.rule("rule", new Box(ruleX, inner.top, RULE_DP, inner.height)));
        rows.place(count, rest.left, rest.top + (rest.height - rows.height(count)) / 2f,
            rest.right(), parts);
    }

    /** The 2×2 on the left, a rule, and the 24 h chart (and the CPU's history) on the right. */
    private static void fourByTwo(@NonNull BuiltinWidgetSpan span, @NonNull Box inner, float fs,
                                  @NonNull Content content, @NonNull Measure measure,
                                  @NonNull List<Part> parts) {
        float column = (inner.width - 2f * GAP_L - RULE_DP) / 2f;
        battery(span, new Box(inner.left, inner.top, column, inner.height), fs, content,
            content.readings, measure, parts);
        float ruleX = inner.left + column + GAP_L;
        Box right = new Box(ruleX + RULE_DP + GAP_L, inner.top, column, inner.height);
        if (history(right, fs, content, measure, parts)) {
            parts.add(Part.rule("rule", new Box(ruleX, inner.top, RULE_DP, inner.height)));
        }
    }

    /** The chart side of the 4×2; false when not even the chart fits. */
    private static boolean history(@NonNull Box box, float fs, @NonNull Content content,
                                   @NonNull Measure measure, @NonNull List<Part> parts) {
        float labelSize = WidgetTokens.sizeDp(Role.LABEL, fs);
        float labelLine = WidgetTokens.lineDp(labelSize);
        String title = pick(content.chartTitle, content.chartTitleShort, box.width, Role.LABEL,
            labelSize, measure);
        List<Box> axis = axis(content.axis, box, labelSize, labelLine, measure);
        boolean axisOn = axis != null;
        String cpuLabel = content.cpuLabel;
        float cpuLabelW = 0f, cpuValueW = 0f;
        if (cpuLabel != null) {
            cpuLabelW = measure.widthDp(cpuLabel, Role.LABEL, false, labelSize);
            cpuValueW = measure.widthDp(CPU_WIDEST, Role.LABEL, true, labelSize);
            if (cpuLabelW + GAP_M + cpuValueW > box.width) cpuLabel = null;
        }

        float chartH, cpuH;
        while (true) {
            boolean cpu = cpuLabel != null;
            float fixed = (title != null ? labelLine + GAP_M : 0f)
                + (axisOn ? GAP_S + labelLine : 0f)
                + (cpu ? GAP_L + labelLine + GAP_S : 0f);
            float flex = box.height - fixed;
            chartH = cpu ? flex * CHART_SHARE : flex;
            cpuH = cpu ? flex - chartH : 0f;
            if (chartH >= CHART_MIN_DP && (!cpu || cpuH >= CPU_MIN_DP)) break;
            if (axisOn) { axisOn = false; continue; }
            if (cpu) { cpuLabel = null; continue; }
            if (title != null) { title = null; continue; }
            return false;
        }

        float y = box.top;
        if (title != null) {
            parts.add(Part.text("chart.title", new Box(box.left, y,
                measure.widthDp(title, Role.LABEL, false, labelSize), labelLine), title,
                Role.LABEL, false, labelSize, Ink.STRONG));
            y += labelLine + GAP_M;
        }
        parts.add(Part.touch("chart", new Box(box.left, y, box.width, chartH)));
        y += chartH;
        if (axisOn) {
            y += GAP_S;
            for (int i = 0; i < axis.size(); i++) {
                Box at = axis.get(i);
                if (at == null) continue;
                parts.add(Part.text("axis." + i, new Box(at.left, y, at.width, labelLine),
                    content.axis.get(i), Role.LABEL, true, labelSize, Ink.SOFT));
            }
            y += labelLine;
        }
        if (cpuLabel != null) {
            y += GAP_L;
            parts.add(Part.text("cpu.label", new Box(box.left, y, cpuLabelW, labelLine), cpuLabel,
                Role.LABEL, false, labelSize, Ink.STRONG));
            parts.add(Part.touch("cpu.value",
                new Box(box.right() - cpuValueW, y, cpuValueW, labelLine)));
            y += labelLine + GAP_S;
            parts.add(Part.touch("cpu", new Box(box.left, y, box.width, cpuH)));
        }
        return true;
    }

    /**
     * Where the hour labels go under a chart as wide as {@code box}: each centred on its place
     * along the chart and kept inside it, all of them if they clear each other, else every other
     * one, else the first and the last. Null entries are labels left out; null for no axis.
     */
    @Nullable private static List<Box> axis(@NonNull List<String> labels, @NonNull Box box,
                                            float size, float line, @NonNull Measure measure) {
        int count = labels.size();
        if (count == 0) return null;
        for (int step : new int[] {1, 2, Math.max(1, count - 1)}) {
            List<Box> out = new ArrayList<>();
            float lastRight = Float.NEGATIVE_INFINITY;
            boolean clear = true;
            for (int i = 0; i < count; i++) {
                // The newest label always shows.
                if (i % step != 0 && i != count - 1) { out.add(null); continue; }
                float w = measure.widthDp(labels.get(i), Role.LABEL, true, size);
                if (w > box.width) { clear = false; break; }
                float centre = box.left + (count == 1 ? 0.5f : i / (float) (count - 1)) * box.width;
                float left = Math.max(box.left, Math.min(box.right() - w, centre - w / 2f));
                if (left < lastRight + GAP_S - WidgetLayoutPlan.EPSILON) { clear = false; break; }
                out.add(new Box(left, 0f, w, line));
                lastRight = left + w;
            }
            if (clear) return out;
        }
        return null;
    }
}
