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
 * The Calendar's layout plan at every span (spec #101, ticket #114). Events come first: the
 * weekday, the date and a dot per event of today at 1×1; the next event at 2×1; "Today" with
 * three events in time chips at 2×2, or the month grid when the widget is set to it; the next
 * event over the fortnight at 4×1; and the month beside "Next up" at 4×2. A header has its own
 * line, the month title carries the year, and with nothing left today the card says "Nothing else
 * today" and shows tomorrow's first event. Android-free.
 *
 * <p>The plan's text, glyphs and rules are drawn by {@link WidgetPlanView}. The parts the widget
 * draws itself are FACE parts, laid out under the text: the 1×1 dots ({@code dot.N}), the next
 * event's disc ({@code next.disc}), the time chips ({@code row.N.chip}, {@code tomorrow.chip}),
 * the month grid ({@code month}) and the fortnight strip ({@code strip}); the grids are drawn
 * from the {@link Grid} this planner works out for them. Taps land on the TOUCH parts:
 * {@code next.touch}, {@code row.N.touch}, {@code tomorrow.touch}, {@code allow.touch},
 * {@code step.prev.touch}, {@code step.next.touch} and, once the month was stepped off today's,
 * {@code month.title.touch}.</p>
 */
final class CalendarLayoutPlanner {
    private CalendarLayoutPlanner() { }

    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetSpan.FOUR_BY_TWO;

    static final String GLYPH_CALENDAR = "";
    static final String GLYPH_PREV = "";
    static final String GLYPH_NEXT = "";

    static final String WEEKDAY = "weekday";
    static final String DATE = "date";
    static final String DOT = "dot.";
    static final String NEXT_DISC = "next.disc";
    static final String NEXT_ICON = "next.icon";
    static final String NEXT_TITLE = "next.title";
    static final String NEXT_DETAIL = "next.detail";
    static final String NEXT_TOUCH = "next.touch";
    static final String HEADER = "header";
    static final String EMPTY = "empty";
    static final String TOMORROW_LABEL = "tomorrow.label";
    static final String TOMORROW = "tomorrow.";
    static final String ROW = "row.";
    static final String ALLOW = "allow";
    static final String ALLOW_TOUCH = "allow.touch";
    static final String MONTH = "month";
    static final String MONTH_TITLE = "month.title";
    static final String MONTH_TITLE_TOUCH = "month.title.touch";
    static final String STEP_PREV = "step.prev";
    static final String STEP_NEXT = "step.next";
    static final String STRIP = "strip";
    static final String DIVIDER = "divider";

    /** At least this much between two day cells of a grid, either way. */
    static final float CELL_GAP_DP = 2f;
    /** The event dot under a day's digit. */
    static final float DOT_DP = 4f;
    /** The 1×1's dots, one per event of today. */
    static final float ONE_DOT_DP = 6f;
    static final int ONE_DOTS_MAX = 3;
    /** The disc the next event's calendar glyph sits on. */
    static final float DISC_DP = 48f;
    static final float DISC_GLYPH_DP = 22f;
    static final float STEP_GLYPH_DP = 16f;
    /** The events the 2×2 and the 4×2 list at most. */
    static final int ROWS_MAX = 3;
    /** A title is not cut narrower than this to make room for a chip or a disc. */
    private static final float TEXT_MIN_DP = 56f;
    /** The 4×2's events column is not drawn narrower than this beside the month. */
    private static final float EVENTS_MIN_DP = 80f;
    /** The month takes this share of the 4×2's width. */
    private static final float MONTH_SHARE = 0.58f;
    /** The widest day number, for the grids' column test; numerals have tabular figures. */
    private static final String WIDEST_DAY = "28";

    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;
    private static final float TARGET = WidgetTokens.TOUCH_TARGET_DP;
    private static final float EPS = WidgetLayoutPlan.EPSILON;

    // ----- input ----------------------------------------------------------------------------

    /** One event as the card shows it. */
    static final class Event {
        /** Its start time today ("15:00"), or "All day". */
        @NonNull final String time;
        @NonNull final String title;
        /** How far off it is: "in 40 min", "now", "All day". */
        @NonNull final String when;

        Event(@NonNull String time, @NonNull String title, @NonNull String when) {
            this.time = time; this.title = title; this.when = when;
        }
    }

    /** The month the grid shows and its names. */
    static final class MonthShape {
        /** "October 2026", and "Oct 2026" for a narrow card: the year is always there. */
        @NonNull final String title;
        @NonNull final String shortTitle;
        /** Empty cells before the first day, the days, and the weeks the month spans. */
        final int lead, days, rows;
        /** Today's cell in the grid, or -1 when the month shown is not today's. */
        final int todayCell;
        /** Whether the month was stepped off today's: the title then leads back. */
        final boolean stepped;
        /** The column names, short ("Mon") and narrow ("M"), from the first day of the week. */
        @NonNull final String[] names;
        @NonNull final String[] letters;

        MonthShape(@NonNull String title, @NonNull String shortTitle, int lead, int days, int rows,
                   int todayCell, boolean stepped, @NonNull String[] names,
                   @NonNull String[] letters) {
            this.title = title; this.shortTitle = shortTitle;
            this.lead = lead; this.days = days; this.rows = rows; this.todayCell = todayCell;
            this.stepped = stepped; this.names = names; this.letters = letters;
        }
    }

    /** What the Calendar has to say now. */
    static final class Content {
        final boolean permitted;
        final boolean loaded;
        /** "Wed" and "21". */
        @NonNull final String weekday;
        @NonNull final String date;
        /** Today's events not yet over, in order, and the one to lead with. */
        @NonNull final List<Event> today;
        final int lead;
        /** The 1×1's dots: one per event of today, and which is the next (-1: none). */
        final int dots;
        final int leadDot;
        /** Tomorrow's first event, shown once today has nothing left. */
        @Nullable final Event tomorrow;
        /** Whether the 2×2 shows the month instead of today's events. */
        final boolean monthView;
        @NonNull final MonthShape month;
        /** The fortnight's narrow weekday letters, 14 from the first day of this week. */
        @NonNull final String[] stripLetters;

        Content(boolean permitted, boolean loaded, @NonNull String weekday, @NonNull String date,
                @NonNull List<Event> today, int lead, int dots, int leadDot,
                @Nullable Event tomorrow, boolean monthView, @NonNull MonthShape month,
                @NonNull String[] stripLetters) {
            this.permitted = permitted; this.loaded = loaded;
            this.weekday = weekday; this.date = date;
            this.today = Collections.unmodifiableList(new ArrayList<>(today));
            this.lead = today.isEmpty() ? -1 : Math.max(0, Math.min(lead, today.size() - 1));
            this.dots = dots; this.leadDot = leadDot; this.tomorrow = tomorrow;
            this.monthView = monthView; this.month = month; this.stripLetters = stripLetters;
        }

        @Nullable Event leadEvent() { return lead < 0 ? null : today.get(lead); }
    }

    /** The words the plan shows, from the app's strings. */
    interface Words {
        @NonNull String today();
        @NonNull String nextUp();
        @NonNull String nothingElseToday();
        @NonNull String tomorrow();
        @NonNull String allowAccess();
        @NonNull String allowShort();
        /** "first · second". */
        @NonNull String joined(@NonNull String first, @NonNull String second);
    }

    // ----- output ---------------------------------------------------------------------------

    /**
     * The geometry of a grid of days (the month, the fortnight), in dp from the grid's own top
     * left: the column names, and per day cell the digit's box and the event dot under it. Day
     * cells are at least {@link #CELL_GAP_DP} apart either way.
     */
    static final class Grid {
        final int columns;
        /** The weeks drawn, and the first of the month's drawn (0 unless they do not all fit). */
        final int rows;
        final int firstRow;
        final float width, height;
        /** The names' role and size, or null with no names row. */
        @Nullable final Role letterRole;
        final float letterSizeDp;
        /** Whether the names are the short ones ("Mon") rather than the narrow ("M"). */
        final boolean shortNames;
        @NonNull final Role digitRole;
        final float digitSizeDp;
        /** Per column, the name's box; empty with no names row. */
        @NonNull final List<Box> letters;
        /** Per drawn cell, row by row: the cell, the digit in it and the dot under the digit. */
        @NonNull final List<Box> cells;
        @NonNull final List<Box> digits;
        @NonNull final List<Box> dots;

        Grid(int columns, int rows, int firstRow, float width, float height,
             @Nullable Role letterRole, float letterSizeDp, boolean shortNames,
             @NonNull Role digitRole, float digitSizeDp, @NonNull List<Box> letters,
             @NonNull List<Box> cells, @NonNull List<Box> digits, @NonNull List<Box> dots) {
            this.columns = columns; this.rows = rows; this.firstRow = firstRow;
            this.width = width; this.height = height;
            this.letterRole = letterRole; this.letterSizeDp = letterSizeDp;
            this.shortNames = shortNames;
            this.digitRole = digitRole; this.digitSizeDp = digitSizeDp;
            this.letters = letters; this.cells = cells; this.digits = digits; this.dots = dots;
        }

        /** Column {@code col} from the top of its name to the bottom of its last cell. */
        @NonNull Box column(int col) {
            Box cell = cells.get(col);
            return new Box(cell.left, 0f, cell.width, height);
        }
    }

    /** The plan, and the geometry of the grids it holds (null where it holds none). */
    static final class Result {
        @NonNull final WidgetLayoutPlan plan;
        @Nullable final Grid month;
        @Nullable final Grid strip;

        Result(@NonNull WidgetLayoutPlan plan, @Nullable Grid month, @Nullable Grid strip) {
            this.plan = plan; this.month = month; this.strip = strip;
        }
    }

    // ----- planning -------------------------------------------------------------------------

    /** The Calendar at {@code span} in a {@code widthDp}×{@code heightDp} card. */
    @NonNull static Result plan(@NonNull BuiltinWidgetSpan span, float widthDp, float heightDp,
                                float fontScale, @NonNull Content content, @NonNull Words words,
                                @NonNull Measure measure) {
        Box card = new Box(0f, 0f, widthDp, heightDp);
        float insetDp = WidgetTokens.insetDp(span);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, insetDp);
        Builder b = new Builder(card, insetDp, fontScale, content, words, measure);
        switch (span) {
            case ONE_BY_ONE: b.oneByOne(inner); break;
            case TWO_BY_ONE: b.next(inner); break;
            case TWO_BY_TWO:
                if (!content.monthView || !b.month(inner, insetDp)) {
                    b.reset();
                    b.events(inner, words.today(), false);
                }
                break;
            case FOUR_BY_ONE: b.fourByOne(inner); break;
            case FOUR_BY_TWO:
            default: b.fourByTwo(inner); break;
        }
        WidgetLayoutPlan plan = new WidgetLayoutPlan(BuiltinWidgetKind.CALENDAR, span, MAX_SPAN,
            fontScale, card, inner, b.parts);
        return new Result(plan, b.monthGrid, b.stripGrid);
    }

    /**
     * The grid of {@code columns}×{@code rows} day cells in {@code width}×{@code height}, the
     * digits at {@code digitRole} and the names at {@code letterRole} (the short names when every
     * one fits its column, else the narrow; null: no names row); null when it does not fit.
     */
    @Nullable static Grid grid(float width, float height, int columns, int rows, int firstRow,
                               @Nullable String[] shortNames, @Nullable String[] narrowNames,
                               @Nullable Role letterRole, @NonNull Role digitRole,
                               float fontScale, @NonNull Measure measure) {
        if (columns <= 0 || rows <= 0) return null;
        float colW = (width - (columns - 1) * CELL_GAP_DP) / columns;
        if (colW <= 0f) return null;
        float digitSize = WidgetTokens.sizeDp(digitRole, fontScale);
        if (measure.widthDp(WIDEST_DAY, digitRole, true, digitSize) > colW + EPS) return null;
        float letterSize = 0f;
        float lettersBlock = 0f;
        boolean useShort = false;
        if (letterRole != null && (shortNames != null || narrowNames != null)) {
            letterSize = WidgetTokens.sizeDp(letterRole, fontScale);
            if (shortNames != null && allFit(shortNames, letterRole, letterSize, colW, measure)) {
                useShort = true;
            } else if (narrowNames == null
                || !allFit(narrowNames, letterRole, letterSize, colW, measure)) {
                return null;
            }
            lettersBlock = WidgetTokens.lineDp(letterSize) + GAP_S;
        } else {
            letterRole = null;
        }
        float digitH = WidgetTokens.lineDp(digitSize);
        float cellH = digitH + DOT_DP;
        float need = lettersBlock + rows * cellH + (rows - 1) * CELL_GAP_DP;
        if (need > height + EPS) return null;
        float rowGap = rows > 1
            ? Math.min(GAP_L, Math.max(CELL_GAP_DP, (height - lettersBlock - rows * cellH)
                / (rows - 1)))
            : 0f;
        float used = lettersBlock + rows * cellH + (rows - 1) * rowGap;
        List<Box> letters = new ArrayList<>();
        List<Box> cells = new ArrayList<>();
        List<Box> digits = new ArrayList<>();
        List<Box> dots = new ArrayList<>();
        for (int col = 0; col < columns && letterRole != null; col++) {
            letters.add(new Box(col * (colW + CELL_GAP_DP), 0f, colW,
                WidgetTokens.lineDp(letterSize)));
        }
        for (int row = 0; row < rows; row++) {
            float top = lettersBlock + row * (cellH + rowGap);
            for (int col = 0; col < columns; col++) {
                float left = col * (colW + CELL_GAP_DP);
                cells.add(new Box(left, top, colW, cellH));
                digits.add(new Box(left, top, colW, digitH));
                dots.add(new Box(left + colW / 2f - DOT_DP / 2f, top + digitH, DOT_DP, DOT_DP));
            }
        }
        return new Grid(columns, rows, firstRow, width, used, letterRole, letterSize, useShort,
            digitRole, digitSize, letters, cells, digits, dots);
    }

    private static boolean allFit(@NonNull String[] names, @NonNull Role role, float size,
                                  float width, @NonNull Measure measure) {
        for (String name : names) {
            if (measure.widthDp(name, role, false, size) > width + EPS) return false;
        }
        return true;
    }

    /** The first week shown when only {@code shown} of {@code total} fit: today's, if it is there. */
    static int firstWeek(int todayCell, int total, int shown) {
        if (shown >= total || todayCell < 0) return 0;
        return Math.max(0, Math.min(todayCell / 7, total - shown));
    }

    private static final class Builder {
        final Box card;
        final float insetDp;
        final float fontScale;
        final Content content;
        final Words words;
        final Measure measure;
        final List<Part> parts = new ArrayList<>();
        @Nullable Grid monthGrid;
        @Nullable Grid stripGrid;

        Builder(Box card, float insetDp, float fontScale, Content content, Words words,
                Measure measure) {
            this.card = card; this.insetDp = insetDp; this.fontScale = fontScale;
            this.content = content; this.words = words; this.measure = measure;
        }

        void reset() {
            parts.clear();
            monthGrid = null;
            stripGrid = null;
        }

        float sp(Role role) { return WidgetTokens.sizeDp(role, fontScale); }

        float lineH(Role role) { return WidgetTokens.lineDp(sp(role)); }

        float width(String text, Role role, boolean numeric) {
            return measure.widthDp(text, role, numeric, sp(role));
        }

        void text(String id, float left, float top, float maxWidth, String text, Role role,
                  boolean numeric, Ink ink) {
            float w = Math.max(0f, Math.min(maxWidth, width(text, role, numeric)));
            parts.add(Part.text(id, new Box(left, top, w, lineH(role)), text, role, numeric,
                sp(role), ink));
        }

        /** {@code text}, right-aligned so it ends at {@code right}. */
        void textEnd(String id, float right, float top, float maxWidth, String text, Role role,
                     boolean numeric, Ink ink) {
            float w = Math.max(0f, Math.min(maxWidth, width(text, role, numeric)));
            parts.add(Part.text(id, new Box(right - w, top, w, lineH(role)), text, role, numeric,
                sp(role), ink));
        }

        /** A 48 dp target centred on {@code box} where it is smaller, kept inside the card. */
        Box target(Box box) {
            float w = Math.min(card.width, Math.max(TARGET, box.width));
            float h = Math.min(card.height, Math.max(TARGET, box.height));
            float left = clamp(box.centerX() - w / 2f, 0f, card.width - w);
            float top = clamp(box.centerY() - h / 2f, 0f, card.height - h);
            return new Box(left, top, w, h);
        }

        // ----- 1×1 ---------------------------------------------------------------------------

        /**
         * The weekday over the date, and a dot per event of today. Short of room the weekday
         * drops to Label, then the dots go, then the weekday; the date is fit to what is left.
         */
        void oneByOne(Box inner) {
            int dots = content.permitted && content.loaded ? Math.min(ONE_DOTS_MAX, content.dots) : 0;
            Role[] weekdayRoles = { Role.TITLE, Role.LABEL, Role.LABEL, null };
            boolean[] withDots = { true, true, false, false };
            for (int i = 0; i < weekdayRoles.length; i++) {
                Role weekdayRole = weekdayRoles[i];
                boolean dotted = withDots[i] && dots > 0;
                float weekdayH = weekdayRole == null ? 0f : lineH(weekdayRole) + GAP_S;
                float dotsH = dotted ? GAP_M + ONE_DOT_DP : 0f;
                float room = inner.height - weekdayH - dotsH;
                float hero = Math.min(WidgetTokens.heroMaxDp(),
                    room / WidgetTokens.HERO_LINE_HEIGHT);
                while (hero > 1f
                    && measure.widthDp(content.date, Role.HERO, true, hero) > inner.width) {
                    hero -= 1f;
                }
                boolean last = i == weekdayRoles.length - 1;
                if (!last && hero < WidgetTokens.heroMinDp(BuiltinWidgetSpan.ONE_BY_ONE, fontScale)) {
                    continue;
                }
                hero = Math.max(1f, hero);
                float heroH = WidgetTokens.heroLineDp(hero);
                float total = weekdayH + heroH + dotsH;
                float top = inner.top + Math.max(0f, (inner.height - total) / 2f);
                if (weekdayRole != null) {
                    text(WEEKDAY, inner.left, top, inner.width, content.weekday, weekdayRole,
                        false, Ink.STRONG);
                    top += weekdayH;
                }
                float heroW = Math.min(inner.width,
                    measure.widthDp(content.date, Role.HERO, true, hero));
                parts.add(Part.text(DATE, new Box(inner.left, top, heroW, heroH), content.date,
                    Role.HERO, true, hero, Ink.STRONG));
                top += heroH;
                if (dotted) {
                    top += GAP_M;
                    int fit = (int) Math.floor((inner.width + GAP_S + EPS) / (ONE_DOT_DP + GAP_S));
                    for (int d = 0; d < dots && d < fit; d++) {
                        parts.add(Part.face(DOT + d, new Box(inner.left + d * (ONE_DOT_DP + GAP_S),
                            top, ONE_DOT_DP, ONE_DOT_DP)));
                    }
                }
                return;
            }
        }

        // ----- the next event (2×1, and the 4×1 without room for the strip) ----------------

        /** The next event beside its disc; with none left today, tomorrow's first. */
        void next(Box inner) {
            String primary;
            String secondary = "";
            Ink primaryInk = Ink.STRONG;
            boolean allow = !content.permitted;
            Event lead = content.leadEvent();
            if (allow) {
                primary = words.allowAccess();
                primaryInk = Ink.MOON;
            } else if (!content.loaded) {
                primary = "";
            } else if (lead != null) {
                primary = lead.title;
                secondary = detail(lead);
            } else {
                primary = words.nothingElseToday();
                primaryInk = Ink.SOFT;
                if (content.tomorrow != null) secondary = tomorrowLine(content.tomorrow);
            }
            float disc = Math.min(DISC_DP, inner.height);
            boolean withDisc = inner.width >= disc + GAP_L + TEXT_MIN_DP;
            float textLeft = inner.left;
            if (withDisc) {
                Box discBox = new Box(inner.left, inner.centerY() - disc / 2f, disc, disc);
                parts.add(Part.face(NEXT_DISC, discBox));
                float glyph = Math.min(DISC_GLYPH_DP, disc / 2f);
                parts.add(Part.glyph(NEXT_ICON, new Box(discBox.centerX() - glyph / 2f,
                    discBox.centerY() - glyph / 2f, glyph, glyph), GLYPH_CALENDAR, Ink.MOON));
                textLeft = discBox.right() + GAP_L;
            }
            float textW = Math.max(0f, inner.right() - textLeft);
            if (allow && width(primary, Role.TITLE, false) > textW) primary = words.allowShort();
            if (!primary.isEmpty()) {
                twoLines(NEXT_TITLE, NEXT_DETAIL, textLeft, inner, textW, primary, primaryInk,
                    secondary);
            }
            if (allow) {
                parts.add(Part.touch(ALLOW_TOUCH, target(inner)));
            } else if (content.loaded && (lead != null || content.tomorrow != null)) {
                parts.add(Part.touch(NEXT_TOUCH, target(inner)));
            }
        }

        /** "15:00 · in 40 min", or the one of them for an all-day event. */
        String detail(Event event) {
            return event.time.equals(event.when) || event.when.isEmpty() ? event.time
                : words.joined(event.time, event.when);
        }

        /** "Tomorrow 10:00 · Dentist". */
        String tomorrowLine(Event event) {
            return words.joined(words.tomorrow() + " " + event.time, event.title);
        }

        /**
         * A title over a detail line, centred in {@code inner}'s height: the title at Title, else
         * Body; the detail at Body, else Label, else left out, whichever first fits.
         */
        void twoLines(String titleId, String detailId, float left, Box inner, float width,
                      String title, Ink titleInk, String detail) {
            Role[][] ladder = {
                { Role.TITLE, Role.BODY }, { Role.TITLE, Role.LABEL }, { Role.BODY, Role.LABEL },
                { Role.TITLE, null }, { Role.BODY, null }, { Role.LABEL, null }
            };
            for (int i = 0; i < ladder.length; i++) {
                Role titleRole = ladder[i][0];
                Role detailRole = detail.isEmpty() ? null : ladder[i][1];
                if (detail.isEmpty() && ladder[i][1] != null) continue;
                float gap = detailRole == Role.BODY ? GAP_S : 0f;
                float total = lineH(titleRole) + (detailRole == null ? 0f : gap + lineH(detailRole));
                if (total > inner.height + EPS && i < ladder.length - 1) continue;
                float top = inner.top + Math.max(0f, (inner.height - total) / 2f);
                text(titleId, left, top, width, title, titleRole, false, titleInk);
                if (detailRole != null) {
                    text(detailId, left, top + lineH(titleRole) + gap, width, detail, detailRole,
                        true, Ink.SOFT);
                }
                return;
            }
        }

        // ----- events (2×2, and the 4×2's column) --------------------------------------------

        /**
         * A header on its own line, then up to three of today's events as 48 dp rows with their
         * time in a chip; with none left, "Nothing else today" and tomorrow's first event.
         * {@code withWhen} adds how far off each one is under its title.
         */
        void events(Box area, String header, boolean withWhen) {
            Role headerRole = Role.TITLE;
            if (lineH(headerRole) * 2f > area.height) headerRole = Role.BODY;
            float headerH = lineH(headerRole);
            text(HEADER, area.left, area.top, area.width, header, headerRole, false, Ink.STRONG);
            float top = area.top + headerH + GAP_S;
            if (!content.permitted) {
                String allow = words.allowAccess();
                if (width(allow, Role.BODY, false) > area.width) allow = words.allowShort();
                float lineTop = top + GAP_S;
                if (lineTop + lineH(Role.BODY) > area.bottom() + EPS) {
                    lineTop = area.bottom() - lineH(Role.BODY);
                }
                text(ALLOW, area.left, lineTop, area.width, allow, Role.BODY, false, Ink.MOON);
                Part line = parts.get(parts.size() - 1);
                parts.add(Part.touch(ALLOW_TOUCH, target(new Box(area.left, line.box.top,
                    area.width, line.box.height))));
                return;
            }
            if (!content.loaded) return;
            if (content.today.isEmpty()) {
                emptyToday(area, top, withWhen);
                return;
            }
            float chip = chipWidth(content.today, area.width);
            float rowsTop = top;
            Event first = content.today.get(0);
            float firstH = rowHeight(first, chip, withWhen);
            // A row is a 48 dp target with its text in the middle, so the first one may reach up
            // under the header and still leave the header a gap of 8 dp to its text.
            float slack = (firstH - rowContent(first, chip, withWhen)) / 2f;
            rowsTop -= Math.max(0f, Math.min(slack, GAP_S + slack - GAP_M));
            int shown = 0;
            for (int i = 0; i < content.today.size() && shown < ROWS_MAX; i++) {
                Event event = content.today.get(i);
                float h = rowHeight(event, chip, withWhen);
                if (!fits(area, rowsTop, event, chip, withWhen)) break;
                boolean more = i + 1 < content.today.size() && shown + 1 < ROWS_MAX
                    && fits(area, rowsTop + h, content.today.get(i + 1), chip, withWhen);
                row(ROW + i + ".", event, area.left, rowsTop, area.width, chip, withWhen, more);
                rowsTop += h;
                shown++;
            }
        }

        /**
         * Whether a row at {@code top} fits: what it shows inside {@code area}, its target inside
         * the card (the last row's may reach into the inset below, as the first one's reaches up
         * under the header).
         */
        boolean fits(Box area, float top, Event event, float chip, boolean withWhen) {
            float h = rowHeight(event, chip, withWhen);
            float shown = rowContent(event, chip, withWhen);
            return top + (h + shown) / 2f <= area.bottom() + EPS && top + h <= card.height + EPS;
        }

        /** "Nothing else today", "Tomorrow" and tomorrow's first event, as far as they fit. */
        void emptyToday(Box area, float top, boolean withWhen) {
            float y = top + GAP_S;
            float emptyH = lineH(Role.BODY);
            if (y + emptyH > area.bottom() + EPS) return;
            text(EMPTY, area.left, y, area.width, words.nothingElseToday(), Role.BODY, false,
                Ink.SOFT);
            y += emptyH;
            Event tomorrow = content.tomorrow;
            if (tomorrow == null) return;
            List<Event> one = Collections.singletonList(tomorrow);
            float chip = chipWidth(one, area.width);
            float rowH = rowHeight(tomorrow, chip, withWhen);
            float labelH = lineH(Role.LABEL);
            boolean withLabel = y + GAP_M + labelH + rowH <= area.bottom() + EPS;
            if (withLabel) {
                text(TOMORROW_LABEL, area.left, y + GAP_M, area.width, words.tomorrow(),
                    Role.LABEL, false, Ink.SOFT);
                y += GAP_M + labelH;
            } else {
                // Without the label the row's time says the day.
                tomorrow = new Event(words.tomorrow() + " " + tomorrow.time, tomorrow.title, "");
                one = Collections.singletonList(tomorrow);
                chip = chipWidth(one, area.width);
                rowH = rowHeight(tomorrow, chip, withWhen);
                if (y + rowH > area.bottom() + EPS) return;
            }
            row(TOMORROW, tomorrow, area.left, y, area.width, chip, withWhen, false);
        }

        /** The chip width the rows share: their widest time; zero when the titles would starve. */
        float chipWidth(List<Event> events, float width) {
            float widest = 0f;
            int count = 0;
            for (Event event : events) {
                if (count++ == ROWS_MAX) break;
                widest = Math.max(widest, width(event.time, Role.LABEL, true));
            }
            float chip = widest + 2f * GAP_M;
            return width - chip - GAP_M >= TEXT_MIN_DP ? chip : 0f;
        }

        float chipHeight() { return lineH(Role.LABEL) + 2f * GAP_S; }

        /** What a row's visible content is tall: its chip, or its text lines. */
        float rowContent(Event event, float chip, boolean withWhen) {
            float text = lineH(Role.BODY) + (hasSecondLine(event, chip, withWhen)
                ? lineH(Role.LABEL) : 0f);
            return Math.max(chip > 0f ? chipHeight() : 0f, text);
        }

        float rowHeight(Event event, float chip, boolean withWhen) {
            // A row keeps a little air at its foot for the rule under it.
            return Math.max(TARGET, rowContent(event, chip, withWhen) + GAP_S);
        }

        boolean hasSecondLine(Event event, float chip, boolean withWhen) {
            return !secondLine(event, chip, withWhen).isEmpty();
        }

        /** Under the title: how far off (4×2), and the time where there is no chip for it. */
        String secondLine(Event event, float chip, boolean withWhen) {
            String when = withWhen && !event.when.equals(event.time) ? event.when : "";
            if (chip > 0f) return when;
            return when.isEmpty() ? event.time : words.joined(event.time, when);
        }

        void row(String id, Event event, float left, float top, float width, float chip,
                 boolean withWhen, boolean ruleBelow) {
            float h = rowHeight(event, chip, withWhen);
            float textLeft = left;
            if (chip > 0f) {
                float chipH = chipHeight();
                Box chipBox = new Box(left, top + (h - chipH) / 2f, chip, chipH);
                parts.add(Part.face(id + "chip", chipBox));
                text(id + "time", chipBox.left + GAP_M, chipBox.top + GAP_S, chip - 2f * GAP_M,
                    event.time, Role.LABEL, true, Ink.MOON);
                textLeft = chipBox.right() + GAP_M;
            }
            float textW = Math.max(0f, left + width - textLeft);
            String second = secondLine(event, chip, withWhen);
            float titleH = lineH(Role.BODY);
            float block = titleH + (second.isEmpty() ? 0f : lineH(Role.LABEL));
            float y = top + (h - block) / 2f;
            text(id + "title", textLeft, y, textW, event.title, Role.BODY, false, Ink.STRONG);
            if (!second.isEmpty()) {
                text(id + "when", textLeft, y + titleH, textW, second, Role.LABEL, true, Ink.SOFT);
            }
            if (ruleBelow) parts.add(Part.rule(id + "rule", new Box(left, top + h - 1f, width, 1f)));
            parts.add(Part.touch(id + "touch", new Box(left, top, width, h)));
        }

        // ----- the month (2×2 option, the 4×2's left) -----------------------------------------

        /**
         * The month's title with the year and its steppers on one line, the grid under it; false,
         * drawing nothing, when even a week of the grid does not fit at the text floor. The
         * steppers' targets reach {@code reach} past the area's end, into the inset or the gap.
         */
        boolean month(Box area, float reach) {
            MonthShape m = content.month;
            float stepsW = 2f * TARGET - reach;
            float titleRoom = Math.max(0f, area.width - stepsW - GAP_S);
            String[] titles = { m.title, m.shortTitle, m.title, m.shortTitle, m.shortTitle };
            Role[] roles = { Role.TITLE, Role.TITLE, Role.BODY, Role.BODY, Role.LABEL };
            int pick = titles.length - 1;
            for (int i = 0; i < titles.length; i++) {
                if (width(titles[i], roles[i], false) <= titleRoom + EPS) {
                    pick = i;
                    break;
                }
            }
            // Room for the title's own target, which leads back to today's month.
            if (titleRoom < TARGET) return false;
            Role titleRole = roles[pick];
            float headerH = Math.max(lineH(titleRole), STEP_GLYPH_DP);
            float headerCy = area.top + headerH / 2f;
            float nextRight = Math.min(card.width, area.right() + reach);
            Box nextTarget = target(new Box(nextRight - TARGET, headerCy - TARGET / 2f, TARGET,
                TARGET));
            Box prevTarget = new Box(nextTarget.left - TARGET, nextTarget.top, TARGET,
                nextTarget.height);
            float headerBottom = area.top + headerH;

            Grid grid = null;
            float gridTop = 0f;
            for (int shown = m.rows; shown >= 1 && grid == null; shown--) {
                Role[] digitRoles = shown == m.rows
                    ? new Role[] { Role.BODY, Role.LABEL, Role.LABEL_FORCED }
                    : new Role[] { Role.LABEL_FORCED };
                for (Role digitRole : digitRoles) {
                    Role letterRole = digitRole == Role.BODY ? Role.LABEL : Role.LABEL_FORCED;
                    // The steppers' targets end before the first week's days.
                    float lettersBlock = lineH(letterRole) + GAP_S;
                    float top = Math.max(headerBottom + GAP_S, prevTarget.bottom() - lettersBlock);
                    grid = grid(area.width, area.bottom() - top, 7, shown,
                        firstWeek(m.todayCell, m.rows, shown), m.names, m.letters, letterRole,
                        digitRole, fontScale, measure);
                    if (grid != null) {
                        gridTop = top;
                        break;
                    }
                }
            }
            if (grid == null) return false;
            monthGrid = grid;

            String title = titles[pick];
            float titleTop = area.top + (headerH - lineH(titleRole)) / 2f;
            text(MONTH_TITLE, area.left, titleTop, titleRoom, title, titleRole, false, Ink.STRONG);
            if (m.stepped) {
                Part titlePart = parts.get(parts.size() - 1);
                parts.add(Part.touch(MONTH_TITLE_TOUCH, new Box(area.left, prevTarget.top,
                    Math.min(titleRoom, Math.max(titlePart.box.width, TARGET)), TARGET)));
            }
            stepper(STEP_PREV, GLYPH_PREV, prevTarget, headerCy);
            stepper(STEP_NEXT, GLYPH_NEXT, nextTarget, headerCy);
            parts.add(Part.face(MONTH, new Box(area.left, gridTop, area.width, grid.height)));
            return true;
        }

        void stepper(String id, String glyph, Box target, float cy) {
            parts.add(Part.glyph(id, new Box(target.centerX() - STEP_GLYPH_DP / 2f,
                cy - STEP_GLYPH_DP / 2f, STEP_GLYPH_DP, STEP_GLYPH_DP), glyph, Ink.SOFT));
            parts.add(Part.touch(id + ".touch", target));
        }

        // ----- 4×1 ---------------------------------------------------------------------------

        /** The next event on a line over the fortnight; just the event where both do not fit. */
        void fourByOne(Box inner) {
            Event lead = content.leadEvent();
            String title;
            String detail = "";
            Ink titleInk = Ink.STRONG;
            if (!content.permitted) {
                title = words.allowAccess();
                titleInk = Ink.MOON;
            } else if (!content.loaded) {
                title = "";
            } else if (lead != null) {
                title = lead.title;
                detail = detail(lead);
            } else {
                title = words.nothingElseToday();
                titleInk = Ink.SOFT;
                if (content.tomorrow != null) detail = tomorrowLine(content.tomorrow);
            }
            Role[][] lines = { { Role.TITLE, Role.BODY }, { Role.BODY, Role.LABEL } };
            Role[] digitRoles = { Role.LABEL, Role.LABEL_FORCED };
            for (Role[] line : lines) {
                float lineH = Math.max(lineH(line[0]), lineH(line[1]));
                for (Role digitRole : digitRoles) {
                    for (int letters = 1; letters >= 0; letters--) {
                        float stripRoom = inner.height - lineH - GAP_S;
                        Grid strip = grid(inner.width, stripRoom, 14, 1, 0, null,
                            letters == 1 ? content.stripLetters : null,
                            letters == 1 ? Role.LABEL_FORCED : null, digitRole, fontScale,
                            measure);
                        if (strip == null) continue;
                        float total = lineH + GAP_S + strip.height;
                        float top = inner.top + Math.max(0f, (inner.height - total) / 2f);
                        line(inner, top, lineH, line, title, titleInk, detail);
                        stripGrid = strip;
                        parts.add(Part.face(STRIP, new Box(inner.left, top + lineH + GAP_S,
                            inner.width, strip.height)));
                        if (!content.permitted) {
                            parts.add(Part.touch(ALLOW_TOUCH, target(new Box(inner.left, top,
                                inner.width, lineH))));
                        }
                        return;
                    }
                }
            }
            next(inner);
        }

        /** The title at the start of the line and the detail at its end, cut to share it. */
        void line(Box inner, float top, float lineH, Role[] roles, String title, Ink titleInk,
                  String detail) {
            float detailW = detail.isEmpty() ? 0f
                : Math.min(width(detail, roles[1], true), inner.width * 0.55f);
            float titleW = Math.max(0f, inner.width - (detailW > 0f ? detailW + GAP_M : 0f));
            if (!title.isEmpty()) {
                text(NEXT_TITLE, inner.left, top + (lineH - lineH(roles[0])) / 2f, titleW, title,
                    roles[0], false, titleInk);
            }
            if (detailW > 0f) {
                textEnd(NEXT_DETAIL, inner.right(), top + (lineH - lineH(roles[1])) / 2f, detailW,
                    detail, roles[1], true, Ink.SOFT);
            }
        }

        // ----- 4×2 ---------------------------------------------------------------------------

        /** The month on the left, a rule, and "Next up" with today's events on the right. */
        void fourByTwo(Box inner) {
            float divider = 1f;
            float side = GAP_L + divider + GAP_L;
            float monthW = Math.round(inner.width * MONTH_SHARE);
            if (inner.width - monthW - side < EVENTS_MIN_DP) {
                monthW = inner.width - side - EVENTS_MIN_DP;
            }
            if (monthW > 0f) {
                Box monthArea = new Box(inner.left, inner.top, monthW, inner.height);
                if (month(monthArea, GAP_L - divider)) {
                    float ruleLeft = monthArea.right() + GAP_L;
                    parts.add(Part.rule(DIVIDER, new Box(ruleLeft, inner.top, divider,
                        inner.height)));
                    float eventsLeft = ruleLeft + divider + GAP_L;
                    events(new Box(eventsLeft, inner.top, inner.right() - eventsLeft,
                        inner.height), words.nextUp(), true);
                    return;
                }
                reset();
            }
            events(inner, words.today(), true);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
