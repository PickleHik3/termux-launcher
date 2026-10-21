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
 * The Weather widget's layout plan at every span (spec #101, "Weather spans"). Each span adds a
 * layer: the 1×1 is the sky, the temperature and today's range; 2×1 adds the condition, the
 * details and the next sunrise or sunset; 2×2 and 4×1 add the next hours; 4×2 adds the coming
 * days. Where the room is short, the least useful line goes first and the hero grows into what is
 * left, so a taller card shows more rather than the same thing larger. Android-free.
 */
final class WeatherLayoutPlanner {
    private WeatherLayoutPlanner() { }

    /** The kind declares it ({@link BuiltinWidgetKind#maxSpan}); past it the widget only gains room. */
    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetKind.WEATHER.maxSpan;

    /** The cloud the empty card shows. */
    static final String GLYPH_NO_WEATHER = "";

    /** The sky beside the hero is as tall as the hero's box; stacked above it, three quarters. */
    private static final float GLYPH_BESIDE_HERO = 1f;
    private static final float GLYPH_ABOVE_HERO = 0.75f;
    /** The sky glyph of an hour and of a day, in dp. */
    private static final float HOUR_GLYPH_DP = 20f;
    private static final float DAY_GLYPH_DP = 20f;
    /** The empty card's cloud, in dp: smaller at 1×1. */
    private static final float EMPTY_GLYPH_DP = 24f;
    private static final float EMPTY_GLYPH_SMALL_DP = 20f;
    /** A day's range bar: its thickness and the narrowest it is drawn at. */
    private static final float TRACK_DP = 6f;
    private static final float TRACK_MIN_DP = 24f;
    /** The narrowest a day's name is cut to before the days are left out. */
    private static final float DAY_NAME_MIN_DP = 24f;
    private static final float RULE_DP = 1f;
    /** The narrowest a column of text beside the hero is kept at. */
    private static final float TEXT_COLUMN_MIN_DP = 56f;
    /** How much of a one-row card the temperature's column may take: half a 2×1, a quarter of a 4×1. */
    private static final float NOW_SHARE_TWO = 0.45f;
    private static final float NOW_SHARE_FOUR = 0.25f;
    /** At 4×1, the lines' share of what the temperature leaves, and the hours kept beside them. */
    private static final float TEXT_SHARE_FOUR = 0.45f;
    private static final int HOURS_BESIDE_TEXT = 3;

    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;

    /** The words the plan shows, from the app's strings; a test passes English ones. */
    interface Words {
        /** "Current location", for a forecast with no place name. */
        @NonNull String placeUnknown();
        /** "24 / 15". */
        @NonNull String range(@NonNull String high, @NonNull String low);
        /** "H 24 · L 15". */
        @NonNull String highLow(@NonNull String high, @NonNull String low);
        /** "10% rain". */
        @NonNull String rain(int percent);
        /** "Sunrise 6:58". */
        @NonNull String sunrise(@NonNull String time);
        /** "Sunset 19:42". */
        @NonNull String sunset(@NonNull String time);
        /** "Next 5 days". */
        @NonNull String nextDays(int count);
        /** "Tap to refresh". */
        @NonNull String tapToRefresh();
        /** "Updated 40 min ago". */
        @NonNull String updatedAgo(int minutes);
        /** "Set a place": the card's offer when there is no location. */
        @NonNull String setAPlace();
    }

    /**
     * The plan for Weather at {@code span} in a {@code widthDp}×{@code heightDp} card at
     * {@code fontScale}; {@code model} null draws {@code emptyMessage} instead.
     */
    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @Nullable WeatherWidgetModel model,
                                          @NonNull String emptyMessage, @NonNull Words words,
                                          @NonNull Measure measure) {
        return plan(span, widthDp, heightDp, fontScale, model, emptyMessage, false, words, measure);
    }

    /** As above; {@code offerPlace} makes the empty card offer "Set a place" instead of a retry. */
    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @Nullable WeatherWidgetModel model,
                                          @NonNull String emptyMessage, boolean offerPlace,
                                          @NonNull Words words, @NonNull Measure measure) {
        Box card = new Box(0f, 0f, widthDp, heightDp);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, WidgetTokens.insetDp(span));
        Builder b = new Builder(span, fontScale, words, measure);
        if (model == null) {
            b.empty(inner, emptyMessage, offerPlace);
        } else {
            switch (span) {
                case ONE_BY_ONE: b.oneByOne(inner, model); break;
                case TWO_BY_TWO: b.tall(inner, model, true); break;
                case FOUR_BY_ONE: b.oneRow(inner, model, NOW_SHARE_FOUR, true); break;
                case FOUR_BY_TWO: b.fourByTwo(inner, model); break;
                case TWO_BY_ONE:
                default: b.oneRow(inner, model, NOW_SHARE_TWO, false); break;
            }
        }
        // The whole card refreshes on a tap.
        b.parts.add(Part.touch("touch", card));
        return new WidgetLayoutPlan(BuiltinWidgetKind.WEATHER, span, MAX_SPAN, fontScale, card,
            inner, b.parts);
    }

    // ----- fitting --------------------------------------------------------------------------

    /** A rank no shortage drops. */
    private static final int ESSENTIAL = Integer.MAX_VALUE;

    /**
     * One row of a vertical stack: {@code fixed} dp tall plus {@code perHero} dp per dp of hero
     * size, {@code gap} dp below the row above it. The lowest rank goes first when the stack is
     * short.
     */
    private static final class Row {
        final int rank;
        final float gap;
        final float fixed;
        final float perHero;
        boolean kept = true;
        float top;

        Row(int rank, float gap, float fixed, float perHero) {
            this.rank = rank; this.gap = gap; this.fixed = fixed; this.perHero = perHero;
        }

        float height(float hero) { return fixed + perHero * hero; }
    }

    /**
     * Keeps the rows of {@code rows} that fit {@code room} with the hero at least
     * {@code heroMin}, dropping the lowest ranks first, and answers the hero size that fills what
     * is left, at most {@code heroMax}. A stack with no hero answers 0.
     */
    private static float fit(@NonNull List<Row> rows, float room, float heroMin, float heroMax) {
        float least = Math.min(heroMin, heroMax);
        while (true) {
            float fixed = 0f, perHero = 0f;
            boolean first = true;
            for (Row row : rows) {
                if (!row.kept) continue;
                fixed += row.fixed + (first ? 0f : row.gap);
                perHero += row.perHero;
                first = false;
            }
            float hero = perHero > 0f ? (room - fixed) / perHero : 0f;
            boolean fits = perHero > 0f ? hero >= least - WidgetLayoutPlan.EPSILON
                : fixed <= room + WidgetLayoutPlan.EPSILON;
            Row weakest = null;
            for (Row row : rows) {
                if (row.kept && row.rank != ESSENTIAL && (weakest == null || row.rank < weakest.rank)) {
                    weakest = row;
                }
            }
            if (fits || weakest == null) {
                return perHero > 0f ? Math.max(least, Math.min(hero, heroMax)) : 0f;
            }
            weakest.kept = false;
        }
    }

    /** Places the kept rows top down from {@code top}; answers the bottom of the last. */
    private static float place(@NonNull List<Row> rows, float top, float hero) {
        float y = top;
        boolean first = true;
        for (Row row : rows) {
            if (!row.kept) continue;
            if (!first) y += row.gap;
            row.top = y;
            y += row.height(hero);
            first = false;
        }
        return y;
    }

    /** The kept rows' total height. */
    private static float stackHeight(@NonNull List<Row> rows, float hero) {
        return place(rows, 0f, hero);
    }

    // ----- building -------------------------------------------------------------------------

    private static final class Builder {
        @NonNull final BuiltinWidgetSpan span;
        final float fontScale;
        @NonNull final Words words;
        @NonNull final Measure measure;
        final List<Part> parts = new ArrayList<>();

        Builder(@NonNull BuiltinWidgetSpan span, float fontScale, @NonNull Words words,
                @NonNull Measure measure) {
            this.span = span; this.fontScale = fontScale; this.words = words; this.measure = measure;
        }

        float size(@NonNull Role role) { return WidgetTokens.sizeDp(role, fontScale); }

        float line(@NonNull Role role) { return WidgetTokens.lineDp(size(role)); }

        float width(@NonNull String text, @NonNull Role role, boolean numeric) {
            return measure.widthDp(text, role, numeric, size(role));
        }

        /** The hero's width per dp of its size, for {@code text}. */
        float heroRatio(@NonNull String text) {
            return measure.widthDp(text, Role.HERO, true, 100f) / 100f;
        }

        /** A line of text at {@code left}, {@code top}, no wider than {@code room}. */
        void text(@NonNull String id, @NonNull String text, @NonNull Role role, boolean numeric,
                  @NonNull Ink ink, float left, float top, float room) {
            float w = Math.min(width(text, role, numeric), Math.max(0f, room));
            parts.add(Part.text(id, new Box(left, top, w, line(role)), text, role, numeric,
                size(role), ink));
        }

        /** A line of text centred on {@code centerX}, no wider than {@code room}. */
        void textCentered(@NonNull String id, @NonNull String text, @NonNull Role role,
                          boolean numeric, @NonNull Ink ink, float centerX, float top, float room) {
            float w = Math.min(width(text, role, numeric), Math.max(0f, room));
            parts.add(Part.text(id, new Box(centerX - w / 2f, top, w, line(role)), text, role,
                numeric, size(role), ink));
        }

        void hero(@NonNull String text, float left, float top, float sizeDp, float room) {
            float w = Math.min(heroRatio(text) * sizeDp, room);
            parts.add(Part.text("temp", new Box(left, top, w, WidgetTokens.heroLineDp(sizeDp)),
                text, Role.HERO, true, sizeDp, Ink.STRONG));
        }

        float heroMin() { return WidgetTokens.heroMinDp(span, fontScale); }

        // ----- empty ----------------------------------------------------------------------

        void empty(@NonNull Box inner, @NonNull String message, boolean offerPlace) {
            boolean small = span == BuiltinWidgetSpan.ONE_BY_ONE;
            // A card too short for the message's role takes the forced label instead.
            Role role = small ? Role.LABEL : Role.BODY;
            if (line(role) > inner.height) role = Role.LABEL_FORCED;
            List<String> lines = WidgetLayoutPlan.wrap(message, inner.width, small ? 3 : 2,
                measure, role, size(role));
            float glyph = small ? EMPTY_GLYPH_SMALL_DP : EMPTY_GLYPH_DP;
            List<Row> rows = new ArrayList<>();
            // The cloud goes first, then the hint, then the message's later lines.
            Row glyphRow = new Row(1, 0f, glyph, 0f);
            rows.add(glyphRow);
            List<Row> lineRows = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                Row row = new Row(i == 0 ? ESSENTIAL : 2 + lines.size() - i,
                    i == 0 ? GAP_M : 0f, line(role), 0f);
                lineRows.add(row);
                rows.add(row);
            }
            // The offer to set a place is the way out of this card, so it outranks the hint and
            // shows at 1×1 too.
            Row hint = new Row(offerPlace ? 3 : 2, GAP_S, line(Role.LABEL), 0f);
            if (!small || offerPlace) rows.add(hint);
            fit(rows, inner.height, 0f, 0f);
            float top = inner.top + (inner.height - stackHeight(rows, 0f)) / 2f;
            place(rows, top, 0f);

            float cx = inner.centerX();
            if (glyphRow.kept) {
                parts.add(Part.glyph("glyph", new Box(cx - glyph / 2f, glyphRow.top, glyph, glyph),
                    GLYPH_NO_WEATHER, Ink.CLOUD));
            }
            // A line left out hands its words to the last line kept, which the view cuts short.
            int last = 0;
            for (int i = 0; i < lineRows.size(); i++) if (lineRows.get(i).kept) last = i;
            for (int i = 0; i <= last; i++) {
                String text = lines.get(i);
                if (i == last) {
                    StringBuilder rest = new StringBuilder(text);
                    for (int j = last + 1; j < lines.size(); j++) rest.append(' ').append(lines.get(j));
                    text = rest.toString();
                }
                textCentered("message." + i, text, role, false, Ink.STRONG, cx, lineRows.get(i).top,
                    inner.width);
            }
            if (offerPlace && hint.kept) {
                textCentered("setplace", words.setAPlace(), Role.LABEL, false, Ink.MOON, cx, hint.top,
                    inner.width);
            } else if (!small && hint.kept) {
                textCentered("hint", words.tapToRefresh(), Role.LABEL, false, Ink.SOFT, cx, hint.top,
                    inner.width);
            }
        }

        // ----- 1×1 ------------------------------------------------------------------------

        /** The sky over the temperature over today's range, centred. */
        void oneByOne(@NonNull Box inner, @NonNull WeatherWidgetModel m) {
            float heroMax = Math.min(WidgetTokens.heroMaxDp(), inner.width / heroRatio(m.temp));
            Row glyph = new Row(2, 0f, 0f, WidgetTokens.HERO_LINE_HEIGHT * GLYPH_ABOVE_HERO);
            Row hero = new Row(ESSENTIAL, GAP_S, 0f, WidgetTokens.HERO_LINE_HEIGHT);
            Row range = new Row(1, GAP_S, line(Role.LABEL), 0f);
            List<Row> rows = new ArrayList<>();
            rows.add(glyph);
            rows.add(hero);
            if (m.hasRange()) rows.add(range);
            float size = fit(rows, inner.height, heroMin(), heroMax);
            float top = inner.top + (inner.height - stackHeight(rows, size)) / 2f;
            place(rows, top, size);

            float cx = inner.centerX();
            if (glyph.kept) {
                float side = glyph.height(size);
                parts.add(Part.glyph("glyph", new Box(cx - side / 2f, glyph.top, side, side), m.glyph,
                    ink(m.tone)));
            }
            float heroWidth = Math.min(heroRatio(m.temp) * size, inner.width);
            hero(m.temp, cx - heroWidth / 2f, hero.top, size, inner.width);
            if (m.hasRange() && range.kept) {
                textCentered("range", words.range(m.high, m.low), Role.LABEL, true, Ink.SOFT, cx,
                    range.top, inner.width);
            }
        }

        // ----- one row: 2×1 and 4×1 ---------------------------------------------------------

        /**
         * The sky over the temperature in a column of its own, then the condition, the details
         * and the sun beside it; at 4×1 the next hours to the right.
         */
        void oneRow(@NonNull Box inner, @NonNull WeatherWidgetModel m, float nowShare,
                    boolean hours) {
            // The temperature keeps its share, or the whole card when the share would hold it under
            // its smallest.
            float nowRoom = inner.width * nowShare;
            if (nowRoom / heroRatio(m.temp) < heroMin()) nowRoom = inner.width;
            float heroMax = Math.min(WidgetTokens.heroMaxDp(), nowRoom / heroRatio(m.temp));
            Row glyph = new Row(1, 0f, 0f, WidgetTokens.HERO_LINE_HEIGHT * GLYPH_ABOVE_HERO);
            Row hero = new Row(ESSENTIAL, GAP_S, 0f, WidgetTokens.HERO_LINE_HEIGHT);
            List<Row> now = new ArrayList<>();
            now.add(glyph);
            now.add(hero);
            float size = fit(now, inner.height, heroMin(), heroMax);
            place(now, inner.top + (inner.height - stackHeight(now, size)) / 2f, size);
            float heroWidth = Math.min(heroRatio(m.temp) * size, nowRoom);
            float glyphSide = glyph.kept ? glyph.height(size) : 0f;
            float nowWidth = Math.max(heroWidth, glyphSide);
            float cx = inner.left + nowWidth / 2f;
            if (glyph.kept) {
                parts.add(Part.glyph("glyph", new Box(cx - glyphSide / 2f, glyph.top, glyphSide,
                    glyphSide), m.glyph, ink(m.tone)));
            }
            hero(m.temp, cx - heroWidth / 2f, hero.top, size, nowRoom);

            float left = inner.left + nowWidth + GAP_L;
            float room = inner.right() - left;
            if (hours && !m.hours.isEmpty() && line(Role.BODY) <= inner.height) {
                // The lines take what they need up to their share; the hours take the rest, and
                // the whole room when the share would leave them fewer than three.
                float divider = 2f * GAP_L + RULE_DP;
                float natural = naturalWidth(m);
                float textRoom = Math.min(natural,
                    Math.max(TEXT_COLUMN_MIN_DP, (room - divider) * TEXT_SHARE_FOUR));
                HourColumns strip = hourColumns(m.hours, room - textRoom - divider);
                int enough = Math.min(HOURS_BESIDE_TEXT, Math.min(m.hours.size(),
                    WeatherWidgetModel.HOURLY_MAX));
                if (natural <= 0f || strip.count < enough) {
                    hourStrip(hourColumns(m.hours, room), m.hours, left, inner.top, room,
                        inner.height, true);
                    return;
                }
                textColumn(m, left, inner, textRoom, false);
                float ruleX = left + textRoom + GAP_L;
                parts.add(Part.rule("rule", new Box(ruleX, inner.top, RULE_DP, inner.height)));
                float stripLeft = ruleX + RULE_DP + GAP_L;
                hourStrip(strip, m.hours, stripLeft, inner.top, inner.right() - stripLeft,
                    inner.height, true);
                return;
            }
            if (room >= TEXT_COLUMN_MIN_DP) textColumn(m, left, inner, room, true);
        }

        /** The widest of the condition, the details and the sun lines. */
        float naturalWidth(@NonNull WeatherWidgetModel m) {
            List<Line> lines = new ArrayList<>();
            addNowLines(lines, m);
            float widest = 0f;
            for (Line line : lines) widest = Math.max(widest, width(line.text, line.role, false));
            return widest;
        }

        /** The condition, the details and the sun, centred down {@code inner}. */
        void textColumn(@NonNull WeatherWidgetModel m, float left, @NonNull Box inner, float room,
                        boolean withPlace) {
            List<Line> lines = new ArrayList<>();
            if (withPlace) lines.add(new Line("place", placeName(m), Role.TITLE, Ink.STRONG, 1, 0f));
            addNowLines(lines, m);
            addStale(lines, m, 0);
            List<Row> rows = rowsOf(lines);
            fit(rows, inner.height, 0f, 0f);
            place(rows, inner.top + (inner.height - stackHeight(rows, 0f)) / 2f, 0f);
            emit(lines, rows, left, room);
        }

        // ----- 2×2 and the left of 4×2 --------------------------------------------------------

        /**
         * The place, the temperature with its sky beside it, the condition, the details and the
         * sun from the top; the next hours at the bottom under a rule.
         */
        void tall(@NonNull Box box, @NonNull WeatherWidgetModel m, boolean withHours) {
            float heroRatio = heroRatio(m.temp);
            // The sky beside the temperature goes when it would hold the temperature under its
            // smallest.
            float heroMax = Math.min(WidgetTokens.heroMaxDp(),
                (box.width - GAP_M) / (heroRatio + WidgetTokens.HERO_LINE_HEIGHT * GLYPH_BESIDE_HERO));
            boolean sky = heroMax >= heroMin();
            if (!sky) heroMax = Math.min(WidgetTokens.heroMaxDp(), box.width / heroRatio);
            List<Line> lines = new ArrayList<>();
            lines.add(new Line("place", placeName(m), Role.TITLE, Ink.STRONG, 1, 0f));
            Line heroLine = new Line("hero", m.temp, Role.HERO, Ink.STRONG, ESSENTIAL, GAP_S);
            lines.add(heroLine);
            addNowLines(lines, m);
            List<Row> rows = rowsOf(lines);
            HourColumns strip = withHours && !m.hours.isEmpty()
                ? hourColumns(m.hours, box.width) : null;
            Row stale = null;
            if (m.staleMinutes >= 0) {
                stale = new Row(3, GAP_M, line(Role.LABEL), 0f);
            }
            Row hours = null;
            if (strip != null && strip.count > 0) {
                hours = new Row(5, 2f * GAP_M + RULE_DP, hourBlockHeight(true), 0f);
                rows.add(hours);
            }
            if (stale != null) rows.add(stale);
            float size = fit(rows, box.height, heroMin(), heroMax);
            place(rows, box.top, size);

            Row hero = rows.get(lines.indexOf(heroLine));
            float heroWidth = Math.min(heroRatio * size, box.width);
            hero(m.temp, box.left, hero.top, size, box.width);
            float side = WidgetTokens.heroLineDp(size) * GLYPH_BESIDE_HERO;
            float glyphLeft = box.left + heroWidth + GAP_M;
            if (sky && glyphLeft + side <= box.right() + WidgetLayoutPlan.EPSILON) {
                parts.add(Part.glyph("glyph", new Box(glyphLeft, hero.top, side, side), m.glyph,
                    ink(m.tone)));
            }
            lines.remove(heroLine);
            rows.remove(hero);
            if (hours != null) rows.remove(hours);
            if (stale != null) rows.remove(stale);
            emit(lines, rows, box.left, box.width);

            float bottom = box.bottom();
            if (stale != null && stale.kept) {
                // The age sits on the bottom edge under the hours.
                bottom -= line(Role.LABEL);
                text("updated", words.updatedAgo(m.staleMinutes), Role.LABEL, false, Ink.SOFT,
                    box.left, bottom, box.width);
                bottom -= GAP_M;
            }
            if (hours != null && hours.kept) {
                // The strip sits above that; the slack falls between it and the lines.
                float blockTop = bottom - hourBlockHeight(true);
                parts.add(Part.rule("rule", new Box(box.left, blockTop - GAP_M - RULE_DP, box.width,
                    RULE_DP)));
                hourStrip(strip, m.hours, box.left, blockTop, box.width, hourBlockHeight(true),
                    false);
            }
        }

        // ----- 4×2 --------------------------------------------------------------------------

        /** The 2×2's column on the left, the coming days on the right, a rule between. */
        void fourByTwo(@NonNull Box inner, @NonNull WeatherWidgetModel m) {
            float column = (inner.width - 2f * GAP_L - RULE_DP) / 2f;
            if (!daysFit(m.days, column)) {
                tall(inner, m, true);
                return;
            }
            tall(new Box(inner.left, inner.top, column, inner.height), m, true);
            float ruleX = inner.left + column + GAP_L;
            parts.add(Part.rule("rule.days", new Box(ruleX, inner.top, RULE_DP, inner.height)));
            float left = ruleX + RULE_DP + GAP_L;
            days(m.days, new Box(left, inner.top, inner.right() - left, inner.height));
        }

        /** Whether a day's name, low and high fit {@code width}: else the days are left out. */
        boolean daysFit(@NonNull List<WeatherWidgetModel.Day> days, float width) {
            if (days.isEmpty()) return false;
            float lowWidth = 0f, highWidth = 0f;
            for (WeatherWidgetModel.Day day : days) {
                lowWidth = Math.max(lowWidth, width(day.low, Role.BODY, true));
                highWidth = Math.max(highWidth, width(day.high, Role.BODY, true));
            }
            return DAY_NAME_MIN_DP + GAP_M + lowWidth + GAP_M + highWidth <= width;
        }

        /** "Next 5 days" over one row a day, spread down the column. */
        void days(@NonNull List<WeatherWidgetModel.Day> days, @NonNull Box box) {
            float rowHeight = Math.max(line(Role.BODY), DAY_GLYPH_DP);
            float titleHeight = line(Role.TITLE) + GAP_M;
            int all = days.size();
            int withTitle = Math.min(all, rowsThatFit(box.height - titleHeight, rowHeight));
            int without = Math.min(all, rowsThatFit(box.height, rowHeight));
            boolean title = withTitle >= without;
            int count = title ? withTitle : without;
            if (count <= 0) return;
            float top = box.top;
            if (title) {
                text("days.title", words.nextDays(count), Role.TITLE, false, Ink.STRONG, box.left,
                    top, box.width);
                top += titleHeight;
            }
            float spacing = count > 1 ? (box.bottom() - top - count * rowHeight) / (count - 1) : 0f;

            float nameWidth = 0f, lowWidth = 0f, highWidth = 0f;
            for (int i = 0; i < count; i++) {
                WeatherWidgetModel.Day day = days.get(i);
                nameWidth = Math.max(nameWidth, width(day.name, Role.BODY, false));
                lowWidth = Math.max(lowWidth, width(day.low, Role.BODY, true));
                highWidth = Math.max(highWidth, width(day.high, Role.BODY, true));
            }
            // The name and the low and high always show; the bar goes first, then the sky.
            float essential = nameWidth + GAP_M + lowWidth + GAP_M + highWidth;
            float spare = box.width - essential;
            boolean glyph = spare >= DAY_GLYPH_DP + GAP_M;
            boolean bar = spare - (glyph ? DAY_GLYPH_DP + GAP_M : 0f) >= TRACK_MIN_DP + GAP_M;
            if (spare < 0f) nameWidth = Math.max(0f, nameWidth + spare);

            for (int i = 0; i < count; i++) {
                WeatherWidgetModel.Day day = days.get(i);
                float y = top + i * (rowHeight + spacing);
                float textTop = y + (rowHeight - line(Role.BODY)) / 2f;
                String id = "day." + i + ".";
                text(id + "name", day.name, Role.BODY, false, Ink.STRONG, box.left, textTop,
                    nameWidth);
                float x = box.left + nameWidth + GAP_M;
                if (glyph) {
                    parts.add(Part.glyph(id + "glyph", new Box(x, y + (rowHeight - DAY_GLYPH_DP) / 2f,
                        DAY_GLYPH_DP, DAY_GLYPH_DP), day.glyph, ink(day.tone)));
                    x += DAY_GLYPH_DP + GAP_M;
                }
                float lowRight = x + lowWidth;
                float low = Math.min(width(day.low, Role.BODY, true), lowWidth);
                text(id + "low", day.low, Role.BODY, true, Ink.SOFT, lowRight - low, textTop, low);
                float highLeft = box.right() - highWidth;
                if (bar) {
                    float barLeft = lowRight + GAP_M;
                    parts.add(Part.track(id + "bar", new Box(barLeft, y + (rowHeight - TRACK_DP) / 2f,
                        highLeft - GAP_M - barLeft, TRACK_DP), day.start, day.end));
                }
                text(id + "high", day.high, Role.BODY, true, Ink.STRONG, highLeft, textTop,
                    highWidth);
            }
        }

        // ----- the next hours ---------------------------------------------------------------

        /** How many hour columns fit {@code room}, and whether they show "14:00" or "14". */
        final class HourColumns {
            final int count;
            final boolean longLabels;
            final float columnWidth;

            HourColumns(int count, boolean longLabels, float columnWidth) {
                this.count = count; this.longLabels = longLabels; this.columnWidth = columnWidth;
            }

            float width() { return count <= 0 ? 0f : count * columnWidth + (count - 1) * GAP_S; }
        }

        @NonNull HourColumns hourColumns(@NonNull List<WeatherWidgetModel.Hour> hours, float room) {
            HourColumns full = hourColumns(hours, room, true);
            HourColumns brief = hourColumns(hours, room, false);
            return brief.count > full.count ? brief : full;
        }

        @NonNull HourColumns hourColumns(@NonNull List<WeatherWidgetModel.Hour> hours, float room,
                                         boolean longLabels) {
            int most = Math.min(hours.size(), WeatherWidgetModel.HOURLY_MAX);
            float need = HOUR_GLYPH_DP;
            for (int i = 0; i < most; i++) {
                WeatherWidgetModel.Hour hour = hours.get(i);
                String label = longLabels && !hour.longLabel.isEmpty() ? hour.longLabel : hour.label;
                need = Math.max(need, width(label, Role.LABEL, true));
                need = Math.max(need, width(hour.temp, Role.BODY, true));
            }
            int count = Math.max(0, Math.min(most, (int) Math.floor((room + GAP_S) / (need + GAP_S))));
            return new HourColumns(count, longLabels, need);
        }

        float hourBlockHeight(boolean withGlyph) {
            return line(Role.LABEL) + GAP_S + (withGlyph ? HOUR_GLYPH_DP + GAP_S : 0f)
                + line(Role.BODY);
        }

        /**
         * The hours as equal columns across {@code width} — hour, sky, temperature — centred down
         * {@code height}. Where the strip is short of height, the sky goes first, then the hour.
         */
        void hourStrip(@NonNull HourColumns strip, @NonNull List<WeatherWidgetModel.Hour> hours,
                       float left, float top, float width, float height, boolean center) {
            if (strip.count <= 0) return;
            Row label = new Row(2, 0f, line(Role.LABEL), 0f);
            Row glyph = new Row(1, GAP_S, HOUR_GLYPH_DP, 0f);
            Row temp = new Row(ESSENTIAL, GAP_S, line(Role.BODY), 0f);
            List<Row> rows = new ArrayList<>();
            rows.add(label);
            rows.add(glyph);
            rows.add(temp);
            fit(rows, height, 0f, 0f);
            place(rows, center ? top + (height - stackHeight(rows, 0f)) / 2f : top, 0f);
            float column = (width - (strip.count - 1) * GAP_S) / strip.count;
            for (int i = 0; i < strip.count; i++) {
                WeatherWidgetModel.Hour hour = hours.get(i);
                float cx = left + i * (column + GAP_S) + column / 2f;
                String id = "hour." + i + ".";
                if (label.kept) {
                    String text = strip.longLabels && !hour.longLabel.isEmpty()
                        ? hour.longLabel : hour.label;
                    textCentered(id + "label", text, Role.LABEL, true, Ink.SOFT, cx, label.top,
                        column);
                }
                if (glyph.kept) {
                    parts.add(Part.glyph(id + "glyph", new Box(cx - HOUR_GLYPH_DP / 2f, glyph.top,
                        HOUR_GLYPH_DP, HOUR_GLYPH_DP), hour.glyph, ink(hour.tone)));
                }
                textCentered(id + "temp", hour.temp, Role.BODY, true, Ink.STRONG, cx, temp.top,
                    column);
            }
        }

        // ----- lines ------------------------------------------------------------------------

        /** The condition, today's details and the next sun time, where the model has them. */
        void addNowLines(@NonNull List<Line> lines, @NonNull WeatherWidgetModel m) {
            if (!m.condition.isEmpty()) {
                lines.add(new Line("condition", m.condition, Role.TITLE, Ink.STRONG, 4, GAP_M));
            }
            String details = details(m);
            if (!details.isEmpty()) {
                lines.add(new Line("details", details, Role.BODY, Ink.SOFT, 3, GAP_S));
            }
            String sun = m.sun == WeatherWidgetModel.Sun.RISE ? words.sunrise(m.sunTime)
                : m.sun == WeatherWidgetModel.Sun.SET ? words.sunset(m.sunTime) : "";
            if (!sun.isEmpty()) lines.add(new Line("sun", sun, Role.BODY, Ink.SOFT, 2, GAP_S));
        }

        /** "Updated 40 min ago" as the card's last line, when the data is past its cache life. */
        void addStale(@NonNull List<Line> lines, @NonNull WeatherWidgetModel m, int rank) {
            if (m.staleMinutes < 0) return;
            lines.add(new Line("updated", words.updatedAgo(m.staleMinutes), Role.LABEL, Ink.SOFT,
                rank, GAP_M));
        }

        /** "H 24 · L 15 · 10% rain"; a dry hour says nothing about rain. */
        @NonNull String details(@NonNull WeatherWidgetModel m) {
            StringBuilder text = new StringBuilder();
            if (m.hasRange()) text.append(words.highLow(m.high, m.low));
            if (m.rainPct > 0) {
                if (text.length() > 0) text.append(" · ");
                text.append(words.rain(m.rainPct));
            }
            return text.toString();
        }

        @NonNull String placeName(@NonNull WeatherWidgetModel m) {
            return m.place.isEmpty() ? words.placeUnknown() : m.place;
        }

        @NonNull List<Row> rowsOf(@NonNull List<Line> lines) {
            List<Row> rows = new ArrayList<>();
            for (Line line : lines) {
                rows.add(line.role == Role.HERO
                    ? new Row(line.rank, line.gap, 0f, WidgetTokens.HERO_LINE_HEIGHT)
                    : new Row(line.rank, line.gap, line(line.role), 0f));
            }
            return rows;
        }

        void emit(@NonNull List<Line> lines, @NonNull List<Row> rows, float left, float room) {
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                Row row = rows.get(i);
                if (row.kept) text(line.id, line.text, line.role, false, line.ink, left, row.top, room);
            }
        }

        @NonNull Ink ink(@NonNull WeatherWidgetModel.Tone tone) {
            switch (tone) {
                case SUN: return Ink.SUN;
                case MOON: return Ink.MOON;
                case CLOUD:
                default: return Ink.CLOUD;
            }
        }
    }

    /** A line of a stack before it is fit. */
    private static final class Line {
        @NonNull final String id;
        @NonNull final String text;
        @NonNull final Role role;
        @NonNull final Ink ink;
        final int rank;
        final float gap;

        Line(@NonNull String id, @NonNull String text, @NonNull Role role, @NonNull Ink ink,
             int rank, float gap) {
            this.id = id; this.text = text; this.role = role; this.ink = ink; this.rank = rank;
            this.gap = gap;
        }
    }

    private static int rowsThatFit(float room, float rowHeight) {
        if (room < rowHeight) return 0;
        return (int) Math.floor((room + GAP_S) / (rowHeight + GAP_S));
    }
}
