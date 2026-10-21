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
 * The Glance widget's layout plan (spec #101, "Glance"; glossary: Glance), modelled on Pixel's At
 * a Glance and drawn Bare. Two lines and no card:
 * <ol>
 *   <li>the date ("Wed, Oct 21") with the weather now beside it, a small sky glyph and the
 *       temperature; never the time, which is the Clock's;</li>
 *   <li>one item, the one that matters most now ({@link #pick}), with its glyph; hidden when there
 *       is nothing to say.</li>
 * </ol>
 * At 4×1 the item is one line; at 2×1 it wraps to two. Where the room is short, line one gives up
 * its separator and then the weather before the date drops under {@link #DATE_MIN_SP}, and the
 * item gives up its second line and then itself before the date does. Android-free.
 */
final class GlanceLayoutPlanner {
    private GlanceLayoutPlanner() { }

    /** Glance adds nothing past its 4×1. */
    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetSpan.FOUR_BY_ONE;

    /** The parts' ids. */
    static final String DATE = "date";
    static final String DOT = "dot";
    static final String WEATHER_GLYPH = "weather.glyph";
    static final String TEMP = "temp";
    static final String ITEM_GLYPH = "item.glyph";
    /** The item's lines: "item.line.0", "item.line.1". */
    static final String ITEM_LINE = "item.line.";
    static final String TOUCH = "touch";

    /**
     * The date's size in sp: the body's regular face at At a Glance's date size, between the
     * Title and the Headline roles; it follows the font scale.
     */
    static final float DATE_SP = 22f;
    /** The least the date shrinks to before line one gives up its weather or line two a line. */
    static final float DATE_MIN_SP = 16f;
    /** How finely the date is shrunk to fit its width, in dp. */
    private static final float SIZE_STEP_DP = 0.5f;

    /** An event starting within this long leads, ahead of everything. */
    static final long SOON_MS = 2L * 60L * 60L * 1000L;
    /** An alarm within this long is worth the line. */
    static final long ALARM_WITHIN_MS = 12L * 60L * 60L * 1000L;
    /** Under this, and not charging, the battery asks for the line. */
    static final int BATTERY_LOW_PERCENT = 20;

    /** The item glyphs, Nerd Font: a calendar, the Clock's bell, a battery a quarter full. */
    static final String GLYPH_EVENT = "";
    static final String GLYPH_ALARM = ClockLayoutPlanner.GLYPH_ALARM;
    static final String GLYPH_BATTERY_LOW = "";

    /** The separator between the date and the weather at 2×1. */
    static final String SEPARATOR = "·";

    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;

    /** What line two can be about, in the order it is chosen. */
    enum Kind { EVENT_SOON, BATTERY_LOW, ALARM, EVENT_TODAY }

    /** Line two: one item, its words, its glyph and its ink. */
    static final class Item {
        @NonNull final Kind kind;
        @NonNull final String text;
        @NonNull final String glyph;
        @NonNull final Ink ink;
        /** The event an event item is about, which a tap opens; null for the others. */
        @Nullable final CalendarEvent event;

        Item(@NonNull Kind kind, @NonNull String text, @NonNull String glyph, @NonNull Ink ink,
             @Nullable CalendarEvent event) {
            this.kind = kind; this.text = text; this.glyph = glyph; this.ink = ink;
            this.event = event;
        }
    }

    /** What the widget has to say. */
    static final class Content {
        /** "Wed, Oct 21". */
        @NonNull final String date;
        /** "21°", or empty with no forecast. */
        @NonNull final String temp;
        /** The sky's glyph, empty for none. */
        @NonNull final String weatherGlyph;
        @NonNull final Ink weatherInk;
        @Nullable final Item item;

        Content(@NonNull String date, @NonNull String temp, @NonNull String weatherGlyph,
                @NonNull Ink weatherInk, @Nullable Item item) {
            this.date = date; this.temp = temp; this.weatherGlyph = weatherGlyph;
            this.weatherInk = weatherInk; this.item = item;
        }

        boolean hasWeather() { return !temp.isEmpty(); }
    }

    /** The words line two is made of, from the app's strings; a test passes English ones. */
    interface Words {
        /** "15:00", in the user's habit. */
        @NonNull String time(long epochMs);
        /** An event with no title. */
        @NonNull String untitled();
        /** "Design review · 15:00 in 40 min". */
        @NonNull String eventSoon(@NonNull String title, @NonNull String time, long minutesAway);
        /** "Design review · 15:00". */
        @NonNull String eventToday(@NonNull String title, @NonNull String time);
        /** "Alarm 07:00". */
        @NonNull String alarm(@NonNull String time);
        /** "Battery 18% · Plug in soon". */
        @NonNull String batteryLow(int percent);
    }

    /**
     * Line two at {@code nowMs}: the one item that matters most, or null for none. In order:
     * <ol>
     *   <li>an event starting within {@link #SOON_MS};</li>
     *   <li>the battery under {@link #BATTERY_LOW_PERCENT} and not charging, in the caution ink;</li>
     *   <li>an alarm within {@link #ALARM_WITHIN_MS};</li>
     *   <li>the next event today.</li>
     * </ol>
     * An event is one that has not started yet; all-day events are the day itself, not an item.
     *
     * @param endOfTodayMs the start of tomorrow
     * @param batteryPercent the battery's level, or negative where it is not known
     * @param batteryCharging whether the battery is charging or plugged in
     * @param nextAlarmMs the platform's next alarm clock, or 0 for none
     */
    @Nullable static Item pick(long nowMs, long endOfTodayMs, @NonNull List<CalendarEvent> events,
                               int batteryPercent, boolean batteryCharging, long nextAlarmMs,
                               @NonNull Words words) {
        CalendarEvent next = null;
        for (CalendarEvent event : events) {
            if (event.allDay || event.startMs < nowMs) continue;
            if (next == null || CalendarEvent.ORDER.compare(event, next) < 0) next = event;
        }
        if (next != null && next.startMs - nowMs <= SOON_MS) {
            long minutes = (next.startMs - nowMs + 59_999L) / 60_000L;
            return new Item(Kind.EVENT_SOON, words.eventSoon(title(next, words),
                words.time(next.startMs), minutes), GLYPH_EVENT, Ink.STRONG, next);
        }
        if (batteryPercent >= 0 && batteryPercent < BATTERY_LOW_PERCENT && !batteryCharging) {
            return new Item(Kind.BATTERY_LOW, words.batteryLow(batteryPercent), GLYPH_BATTERY_LOW,
                Ink.CAUTION, null);
        }
        if (nextAlarmMs > nowMs && nextAlarmMs - nowMs <= ALARM_WITHIN_MS) {
            return new Item(Kind.ALARM, words.alarm(words.time(nextAlarmMs)), GLYPH_ALARM,
                Ink.STRONG, null);
        }
        if (next != null && next.startMs < endOfTodayMs) {
            return new Item(Kind.EVENT_TODAY, words.eventToday(title(next, words),
                words.time(next.startMs)), GLYPH_EVENT, Ink.STRONG, next);
        }
        return null;
    }

    @NonNull private static String title(@NonNull CalendarEvent event, @NonNull Words words) {
        return event.title.isEmpty() ? words.untitled() : event.title;
    }

    /** How line one parts the date from the weather. */
    private enum Separator { DOT, GAP, TIGHT }

    /**
     * The plan for Glance at {@code span} in a {@code widthDp}×{@code heightDp} card at
     * {@code fontScale}. The 1×1, 2×1 and 2×2 draw the narrow layout (line two wraps to two); the
     * 4×1 and 4×2 the wide one (one line).
     */
    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @NonNull Content content, @NonNull Measure measure) {
        Box card = new Box(0f, 0f, widthDp, heightDp);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, WidgetTokens.insetDp(span));
        List<Part> parts = new ArrayList<>();
        boolean narrow = !span.isWide();
        float bodySize = WidgetTokens.sizeDp(Role.BODY, fontScale);
        float bodyLine = WidgetTokens.lineDp(bodySize);
        float most = DATE_SP * fontScale;
        float least = DATE_MIN_SP * fontScale;
        float floor = WidgetTokens.TEXT_FLOOR_SP * fontScale;

        // Line one across: the separator, then the weather, go before the date is under its least.
        Separator separator = narrow ? Separator.DOT : Separator.GAP;
        boolean weather = content.hasWeather();
        if (weather && lineOneWidth(content, true, separator, least, measure) > inner.width
            && separator == Separator.DOT) {
            separator = Separator.TIGHT;
        }
        if (weather && lineOneWidth(content, true, separator, least, measure) > inner.width) {
            weather = false;
        }
        float size = most;
        while (size > floor
            && lineOneWidth(content, weather, separator, size, measure) > inner.width) {
            size = Math.max(floor, size - SIZE_STEP_DP);
        }

        // Down: line two gives up its second line, then itself, before the date is under its least.
        Item item = content.item;
        float glyphSide = bodySize;
        float textLeft = inner.left + glyphSide + GAP_L;
        float textWidth = inner.right() - textLeft;
        List<String> lines = Collections.emptyList();
        if (item != null && textWidth > 0f) {
            List<String> wrapped = WidgetLayoutPlan.wrap(item.text, textWidth, narrow ? 2 : 1,
                measure, Role.BODY, bodySize);
            for (int n = wrapped.size(); n >= 1; n--) {
                float fits = (inner.height - GAP_M - n * bodyLine) / WidgetTokens.LINE_HEIGHT;
                if (fits >= Math.min(size, least)) {
                    size = Math.min(size, fits);
                    lines = n == wrapped.size() ? wrapped
                        : WidgetLayoutPlan.wrap(item.text, textWidth, n, measure, Role.BODY,
                            bodySize);
                    break;
                }
            }
        }
        if (lines.isEmpty()) {
            size = Math.min(size, Math.max(floor, inner.height / WidgetTokens.LINE_HEIGHT));
        }

        // The two lines, centred down the card.
        float dateLine = WidgetTokens.lineDp(size);
        float block = dateLine + (lines.isEmpty() ? 0f : GAP_M + lines.size() * bodyLine);
        float y = inner.top + Math.max(0f, (inner.height - block) / 2f);
        float lineOne = Math.min(dateLine, inner.height);
        float weatherWidth = weather ? weatherWidth(content, separator, size, measure) : 0f;
        float dateWidth = Math.min(measure.widthDp(content.date, Role.BODY, false, size),
            inner.width - weatherWidth);
        float x = inner.left;
        parts.add(Part.text(DATE, new Box(x, y, dateWidth, lineOne), content.date, Role.BODY,
            false, size, Ink.STRONG));
        x += dateWidth;
        if (weather) {
            if (separator == Separator.DOT) {
                x += GAP_S;
                float dot = measure.widthDp(SEPARATOR, Role.BODY, false, size);
                parts.add(Part.text(DOT, new Box(x, y, dot, lineOne), SEPARATOR, Role.BODY, false,
                    size, Ink.SOFT));
                x += dot + GAP_S;
            } else {
                x += separator == Separator.GAP ? GAP_L : GAP_M;
            }
            if (!content.weatherGlyph.isEmpty()) {
                float side = Math.min(size, lineOne);
                parts.add(Part.glyph(WEATHER_GLYPH, new Box(x, y + (lineOne - side) / 2f, side,
                    side), content.weatherGlyph, content.weatherInk));
                x += side + GAP_S;
            }
            float temp = Math.min(measure.widthDp(content.temp, Role.BODY, true, size),
                inner.right() - x);
            parts.add(Part.text(TEMP, new Box(x, y, temp, lineOne), content.temp, Role.BODY, true,
                size, Ink.STRONG));
        }
        if (item != null && !lines.isEmpty()) {
            float top = y + dateLine + GAP_M;
            parts.add(Part.glyph(ITEM_GLYPH, new Box(inner.left, top + (bodyLine - glyphSide) / 2f,
                glyphSide, glyphSide), item.glyph, item.ink));
            for (int i = 0; i < lines.size(); i++) {
                parts.add(Part.text(ITEM_LINE + i, new Box(textLeft, top + i * bodyLine, textWidth,
                    bodyLine), lines.get(i), Role.BODY, false, bodySize, item.ink));
            }
        }
        // The whole card opens what line two is about.
        parts.add(Part.touch(TOUCH, card));
        return new WidgetLayoutPlan(BuiltinWidgetKind.GLANCE, span, MAX_SPAN, fontScale, card,
            inner, parts);
    }

    /** Line one's width with the date at {@code size}: the date, and the weather when kept. */
    private static float lineOneWidth(@NonNull Content content, boolean weather,
                                      @NonNull Separator separator, float size,
                                      @NonNull Measure measure) {
        float width = measure.widthDp(content.date, Role.BODY, false, size);
        return weather ? width + weatherWidth(content, separator, size, measure) : width;
    }

    /** What the weather adds to line one: the separator, the sky's glyph and the temperature. */
    private static float weatherWidth(@NonNull Content content, @NonNull Separator separator,
                                      float size, @NonNull Measure measure) {
        float width;
        switch (separator) {
            case DOT:
                width = GAP_S + measure.widthDp(SEPARATOR, Role.BODY, false, size) + GAP_S;
                break;
            case GAP: width = GAP_L; break;
            case TIGHT:
            default: width = GAP_M; break;
        }
        if (!content.weatherGlyph.isEmpty()) width += size + GAP_S;
        return width + measure.widthDp(content.temp, Role.BODY, true, size);
    }
}
