package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarWidgetFormatsTest {
    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");
    private static final Locale US = Locale.US;
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 6);
    private static final CalendarWidgetFormats.Words WORDS = new CalendarWidgetFormats.Words(
        "now", "in %1$d min", "in %1$d h", "Tomorrow", "All day");

    private static long at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(KUWAIT).toInstant().toEpochMilli();
    }

    private static CalendarEvent timed(long id, LocalDate day, int hour, int minute, int minutes) {
        long begin = at(day, hour, minute);
        return CalendarEvent.of(id, begin, begin + minutes * 60_000L, false, "Event " + id, "",
            "Home", 0xFF3366CC, KUWAIT);
    }

    /** An all-day event as the provider stores it: UTC midnights. */
    private static CalendarEvent allDay(long id, LocalDate first, int days) {
        long begin = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long end = first.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        return CalendarEvent.of(id, begin, end, true, "Holiday", null, null, 0, KUWAIT);
    }

    private static String relative(CalendarEvent event, long now) {
        return CalendarWidgetFormats.relative(event, now, KUWAIT, true, US, WORDS);
    }

    private static String slot(CalendarEvent event, long now) {
        return CalendarWidgetFormats.slot(event, now, KUWAIT, true, US, WORDS);
    }

    // ----- events ---------------------------------------------------------------------------

    @Test public void allDayBoundsBecomeLocalMidnights() {
        CalendarEvent event = allDay(1, TUESDAY, 1);
        assertEquals(at(TUESDAY, 0, 0), event.startMs);
        assertEquals(at(TUESDAY.plusDays(1), 0, 0), event.endMs);
        assertTrue(CalendarWidgetFormats.overlaps(event, TUESDAY, KUWAIT));
        assertFalse(CalendarWidgetFormats.overlaps(event, TUESDAY.plusDays(1), KUWAIT));
        assertFalse(CalendarWidgetFormats.overlaps(event, TUESDAY.minusDays(1), KUWAIT));
    }

    @Test public void eventWithoutColourOrPlaceFallsBack() {
        CalendarEvent event = CalendarEvent.of(1, 0, 0, false, null, " ", "Work", 0x123456, KUWAIT);
        assertEquals("", event.title);
        assertEquals("Work", event.place());
        assertEquals(0xFF123456, event.color);
    }

    @Test public void allDayLeadsTimedEventsAtTheSameStart() {
        CalendarEvent midnight = timed(1, TUESDAY, 0, 0, 30);
        CalendarEvent holiday = allDay(2, TUESDAY, 1);
        List<CalendarEvent> events = new ArrayList<>(Arrays.asList(midnight, holiday));
        Collections.sort(events, CalendarEvent.ORDER);
        assertSame(holiday, events.get(0));
    }

    @Test public void nextPrefersTodaysTimedEventOverAnAllDayOne() {
        long now = at(TUESDAY, 13, 40);
        CalendarEvent holiday = allDay(1, TUESDAY, 1);
        CalendarEvent morning = timed(2, TUESDAY, 9, 0, 60);
        CalendarEvent standup = timed(3, TUESDAY, 14, 30, 15);
        List<CalendarEvent> events = Arrays.asList(holiday, morning, standup);
        assertSame(standup, CalendarWidgetFormats.next(events, now, KUWAIT));
        assertEquals(Arrays.asList(holiday, standup), CalendarWidgetFormats.upcoming(events, now));
        assertEquals(3, CalendarWidgetFormats.onDay(events, TUESDAY, KUWAIT).size());
    }

    @Test public void nextFallsBackToTheFirstEventNotOver() {
        long now = at(TUESDAY, 20, 0);
        CalendarEvent holiday = allDay(1, TUESDAY, 1);
        CalendarEvent tomorrow = timed(2, TUESDAY.plusDays(1), 10, 0, 60);
        assertSame(holiday, CalendarWidgetFormats.next(Arrays.asList(holiday, tomorrow), now, KUWAIT));
        assertSame(tomorrow, CalendarWidgetFormats.next(Collections.singletonList(tomorrow), now, KUWAIT));
        assertNull(CalendarWidgetFormats.next(Collections.emptyList(), now, KUWAIT));
    }

    @Test public void remainingTodayDropsEndedAndLaterDays() {
        long now = at(TUESDAY, 13, 40);
        CalendarEvent ended = timed(1, TUESDAY, 9, 0, 60);
        CalendarEvent later = timed(2, TUESDAY, 16, 0, 60);
        CalendarEvent tomorrow = timed(3, TUESDAY.plusDays(1), 10, 0, 60);
        assertEquals(Collections.singletonList(later), CalendarWidgetFormats.remainingToday(
            Arrays.asList(ended, later, tomorrow), now, KUWAIT));
    }

    @Test public void dayColorsMarkEveryDayAnEventTouchesWithTheFirstColour() {
        CalendarEvent trip = CalendarEvent.of(1, at(TUESDAY, 18, 0), at(TUESDAY.plusDays(2), 9, 0),
            false, "Trip", "", "", 0xFF00AA00, KUWAIT);
        CalendarEvent plain = CalendarEvent.of(2, at(TUESDAY, 20, 0), at(TUESDAY, 21, 0),
            false, "Dinner", "", "", 0, KUWAIT);
        CalendarEvent endsAtMidnight = CalendarEvent.of(3, at(TUESDAY.plusDays(5), 22, 0),
            at(TUESDAY.plusDays(6), 0, 0), false, "Late", "", "", 0, KUWAIT);
        Map<LocalDate, Integer> colors = CalendarWidgetFormats.dayColors(
            Arrays.asList(trip, plain, endsAtMidnight), TUESDAY, TUESDAY.plusDays(14), KUWAIT, 0xFF0000FF);
        assertEquals(Integer.valueOf(0xFF00AA00), colors.get(TUESDAY));
        assertEquals(Integer.valueOf(0xFF00AA00), colors.get(TUESDAY.plusDays(1)));
        assertEquals(Integer.valueOf(0xFF00AA00), colors.get(TUESDAY.plusDays(2)));
        assertEquals(Integer.valueOf(0xFF0000FF), colors.get(TUESDAY.plusDays(5)));
        assertFalse(colors.containsKey(TUESDAY.plusDays(6)));
        assertEquals(4, colors.size());
    }

    // ----- labels ---------------------------------------------------------------------------

    @Test public void relativeCountsMinutesInsideTheHour() {
        CalendarEvent standup = timed(1, TUESDAY, 14, 30, 15);
        assertEquals("in 50 min", relative(standup, at(TUESDAY, 13, 40)));
        // A tick lands a moment after the minute: still 50, not 49.
        assertEquals("in 50 min", relative(standup, at(TUESDAY, 13, 40) + 50));
        assertEquals("in 1 min", relative(standup, at(TUESDAY, 14, 29) + 30_000));
    }

    @Test public void relativeIsNowWhileTheEventIsOn() {
        CalendarEvent standup = timed(1, TUESDAY, 14, 30, 15);
        assertEquals("now", relative(standup, at(TUESDAY, 14, 30)));
        assertEquals("now", relative(standup, at(TUESDAY, 14, 44)));
    }

    @Test public void relativeRoundsHoursLaterToday() {
        CalendarEvent gym = timed(1, TUESDAY, 19, 0, 60);
        assertEquals("in 5 h", relative(gym, at(TUESDAY, 13, 40)));
        assertEquals("in 1 h", relative(gym, at(TUESDAY, 17, 50)));
    }

    @Test public void relativeNamesLaterDays() {
        long now = at(TUESDAY, 13, 40);
        assertEquals("Tomorrow 10:00", relative(timed(1, TUESDAY.plusDays(1), 10, 0, 60), now));
        assertEquals("Thu 10:00", relative(timed(2, TUESDAY.plusDays(2), 10, 0, 60), now));
        assertEquals("Tomorrow 00:30", relative(timed(3, TUESDAY.plusDays(1), 0, 30, 60),
            at(TUESDAY, 23, 0)));
    }

    @Test public void relativeForAllDayEvents() {
        long now = at(TUESDAY, 13, 40);
        assertEquals("All day", relative(allDay(1, TUESDAY, 1), now));
        assertEquals("All day", relative(allDay(2, TUESDAY.minusDays(1), 3), now));
        assertEquals("Tomorrow", relative(allDay(3, TUESDAY.plusDays(1), 1), now));
        assertEquals("Fri", relative(allDay(4, TUESDAY.plusDays(3), 1), now));
    }

    @Test public void slotIsTheTimeTodayAndTheWeekdayLater() {
        long now = at(TUESDAY, 13, 40);
        assertEquals("14:30", slot(timed(1, TUESDAY, 14, 30, 15), now));
        assertEquals("Wed", slot(timed(2, TUESDAY.plusDays(1), 10, 0, 60), now));
        assertEquals("All day", slot(allDay(3, TUESDAY, 1), now));
        assertEquals("Wed", slot(allDay(4, TUESDAY.plusDays(1), 1), now));
        assertEquals("now", slot(timed(5, TUESDAY.minusDays(1), 22, 0, 24 * 60), now));
    }

    @Test public void clockFollowsTheTwelveHourSetting() {
        long time = at(TUESDAY, 14, 30);
        assertEquals("14:30", CalendarWidgetFormats.clock(time, KUWAIT, true, US));
        assertEquals("2:30 PM", CalendarWidgetFormats.clock(time, KUWAIT, false, US));
        assertEquals("09:05", CalendarWidgetFormats.clock(at(TUESDAY, 9, 5), KUWAIT, true, US));
    }

    @Test public void namesComeFromTheLocale() {
        assertEquals("October", CalendarWidgetFormats.monthName(Month.OCTOBER, US));
        assertEquals("Oct", CalendarWidgetFormats.monthShort(Month.OCTOBER, US));
        assertEquals("OCT", CalendarWidgetFormats.monthShortUpper(Month.OCTOBER, US));
        assertEquals("TUE", CalendarWidgetFormats.weekdayShortUpper(DayOfWeek.TUESDAY, US));
        assertEquals("TUESDAY", CalendarWidgetFormats.weekdayFullUpper(DayOfWeek.TUESDAY, US));
        assertEquals("Tue", CalendarWidgetFormats.weekdayShort(DayOfWeek.TUESDAY, US));
        assertEquals("M", CalendarWidgetFormats.weekdayLetter(DayOfWeek.MONDAY, US));
    }

    // ----- weeks and months -----------------------------------------------------------------

    @Test public void firstDayOfWeekReadsCalendarConstants() {
        assertEquals(DayOfWeek.SUNDAY, CalendarWidgetFormats.firstDayOfWeek(java.util.Calendar.SUNDAY));
        assertEquals(DayOfWeek.MONDAY, CalendarWidgetFormats.firstDayOfWeek(java.util.Calendar.MONDAY));
        assertEquals(DayOfWeek.SATURDAY, CalendarWidgetFormats.firstDayOfWeek(java.util.Calendar.SATURDAY));
        assertEquals(DayOfWeek.MONDAY, CalendarWidgetFormats.firstDayOfWeek(0));
    }

    @Test public void weekStartAndIsoWeekMatchTheDesign() {
        LocalDate monday = CalendarWidgetFormats.weekStart(TUESDAY, DayOfWeek.MONDAY);
        assertEquals(LocalDate.of(2026, 10, 5), monday);
        assertEquals(41, CalendarWidgetFormats.isoWeek(monday));
        assertEquals(42, CalendarWidgetFormats.isoWeek(monday.plusDays(7)));
        LocalDate sunday = CalendarWidgetFormats.weekStart(TUESDAY, DayOfWeek.SUNDAY);
        assertEquals(LocalDate.of(2026, 10, 4), sunday);
        assertEquals(41, CalendarWidgetFormats.isoWeek(sunday));
        assertEquals(TUESDAY, CalendarWidgetFormats.weekStart(TUESDAY, DayOfWeek.TUESDAY));
    }

    @Test public void monthGridShape() {
        YearMonth october = YearMonth.of(2026, 10);
        assertEquals(3, CalendarWidgetFormats.leadingBlanks(october, DayOfWeek.MONDAY));
        assertEquals(4, CalendarWidgetFormats.leadingBlanks(october, DayOfWeek.SUNDAY));
        assertEquals(5, CalendarWidgetFormats.monthRows(october, DayOfWeek.MONDAY));
        // August 2026 starts on a Saturday: 5 blanks + 31 days need a sixth row.
        assertEquals(6, CalendarWidgetFormats.monthRows(YearMonth.of(2026, 8), DayOfWeek.MONDAY));
        // February 2021 fills exactly four weeks; the grid keeps five rows.
        assertEquals(0, CalendarWidgetFormats.leadingBlanks(YearMonth.of(2021, 2), DayOfWeek.MONDAY));
        assertEquals(5, CalendarWidgetFormats.monthRows(YearMonth.of(2021, 2), DayOfWeek.MONDAY));
    }

    @Test public void weekendsAreSaturdayAndSunday() {
        assertTrue(CalendarWidgetFormats.isWeekend(DayOfWeek.SATURDAY));
        assertTrue(CalendarWidgetFormats.isWeekend(DayOfWeek.SUNDAY));
        assertFalse(CalendarWidgetFormats.isWeekend(DayOfWeek.FRIDAY));
    }
}
