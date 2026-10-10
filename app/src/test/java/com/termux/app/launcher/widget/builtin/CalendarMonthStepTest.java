package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.ArrayList;

/** The calendar's stepped month: its bounds, the anchor, the kept reads and the heading's fit. */
public class CalendarMonthStepTest {
    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    /** Paraguay sprang forward at midnight on Sunday 1 October 2017: that day began at 01:00. */
    private static final ZoneId ASUNCION = ZoneId.of("America/Asuncion");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    // ----- bounds -----------------------------------------------------------------------------

    @Test public void monthRunsFromItsFirstMidnightToTheNextMonths() {
        assertEquals(Instant.parse("2026-09-30T21:00:00Z").toEpochMilli(),
            CalendarWidgetFormats.monthStart(OCTOBER, KUWAIT));
        assertEquals(Instant.parse("2026-10-31T21:00:00Z").toEpochMilli(),
            CalendarWidgetFormats.monthStart(OCTOBER.plusMonths(1), KUWAIT));
    }

    @Test public void monthBoundsFollowTheZone() {
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(),
            CalendarWidgetFormats.monthStart(OCTOBER, ZoneId.of("UTC")));
        assertEquals(Duration.ofHours(3).toMillis(),
            CalendarWidgetFormats.monthStart(OCTOBER, ZoneId.of("UTC"))
                - CalendarWidgetFormats.monthStart(OCTOBER, KUWAIT));
    }

    @Test public void aMonthWithADaylightSavingChangeIsAnHourShort() {
        YearMonth march = YearMonth.of(2026, 3);
        long length = CalendarWidgetFormats.monthStart(march.plusMonths(1), BERLIN)
            - CalendarWidgetFormats.monthStart(march, BERLIN);
        assertEquals(Duration.ofDays(31).minusHours(1).toMillis(), length);
    }

    @Test public void aMonthWhoseMidnightIsSkippedStartsAtTheFirstMomentItHas() {
        YearMonth october = YearMonth.of(2017, 10);
        assertEquals(Instant.parse("2017-10-01T04:00:00Z").toEpochMilli(),
            CalendarWidgetFormats.monthStart(october, ASUNCION));
        // September ends where October begins, with no gap and no overlap.
        long september = CalendarWidgetFormats.monthStart(october, ASUNCION)
            - CalendarWidgetFormats.monthStart(october.minusMonths(1), ASUNCION);
        assertEquals(Duration.ofDays(30).toMillis(), september);
    }

    // ----- the anchor ---------------------------------------------------------------------------

    @Test public void noAnchorFollowsToday() {
        assertEquals(OCTOBER, CalendarWidgetFormats.shownMonth(null, TODAY));
        assertEquals(YearMonth.of(2026, 11),
            CalendarWidgetFormats.shownMonth(null, LocalDate.of(2026, 11, 1)));
    }

    @Test public void anAnchorHoldsItsMonthWhenTheMonthTurns() {
        YearMonth august = YearMonth.of(2026, 8);
        assertEquals(august, CalendarWidgetFormats.shownMonth(august, LocalDate.of(2026, 10, 31)));
        assertEquals(august, CalendarWidgetFormats.shownMonth(august, LocalDate.of(2026, 11, 1)));
    }

    @Test public void steppingWalksFromTheShownMonth() {
        YearMonth back = CalendarWidgetFormats.stepMonth(null, TODAY, -1);
        assertEquals(YearMonth.of(2026, 9), back);
        assertEquals(YearMonth.of(2026, 8), CalendarWidgetFormats.stepMonth(back, TODAY, -1));
        assertEquals(YearMonth.of(2027, 1),
            CalendarWidgetFormats.stepMonth(YearMonth.of(2026, 12), TODAY, 1));
        assertEquals(YearMonth.of(2025, 12),
            CalendarWidgetFormats.stepMonth(YearMonth.of(2026, 1), TODAY, -1));
    }

    @Test public void steppingOntoTodaysMonthFollowsTodayAgain() {
        assertNull(CalendarWidgetFormats.stepMonth(YearMonth.of(2026, 9), TODAY, 1));
        assertNull(CalendarWidgetFormats.stepMonth(YearMonth.of(2026, 11), TODAY, -1));
    }

    // ----- the kept months ----------------------------------------------------------------------

    @Test public void theCacheKeepsOnlyTheMostRecentlyUsedMonths() {
        CalendarEventsSource.MonthCache<String> cache =
            new CalendarEventsSource.MonthCache<>(CalendarEventsSource.MONTHS_KEPT);
        cache.put(YearMonth.of(2026, 8), "aug");
        cache.put(YearMonth.of(2026, 9), "sep");
        cache.put(OCTOBER, "oct");
        // Reading August makes September the least recently used.
        assertEquals("aug", cache.get(YearMonth.of(2026, 8)));
        cache.put(YearMonth.of(2026, 11), "nov");
        assertEquals(CalendarEventsSource.MONTHS_KEPT, cache.size());
        assertFalse(cache.containsKey(YearMonth.of(2026, 9)));
        assertEquals(Arrays.asList(OCTOBER, YearMonth.of(2026, 8), YearMonth.of(2026, 11)),
            new ArrayList<>(cache.keySet()));
        for (int i = 0; i < 24; i++) cache.put(OCTOBER.plusMonths(i), "m" + i);
        assertEquals(CalendarEventsSource.MONTHS_KEPT, cache.size());
    }

    // ----- the heading --------------------------------------------------------------------------

    // In dp at density 1: "September" at 12sp bold ≈ 66, "2026" at 11sp mono ≈ 27, 8dp gaps,
    // 22dp discs, the discs 8dp apart and never closer than 4.

    @Test public void roomForEverythingKeepsTheYearAndTheGap() {
        CalendarWidgetFormats.HeadingFit fit = CalendarWidgetFormats.fitHeading(170, 66, 27, 8, 8,
            22, 8, 4);
        assertTrue(fit.showYear);
        assertEquals(8, fit.stepperGap);
        assertEquals(66, fit.nameWidth);
    }

    @Test public void theYearGoesBeforeTheGapCloses() {
        CalendarWidgetFormats.HeadingFit fit = CalendarWidgetFormats.fitHeading(126, 66, 27, 8, 8,
            22, 8, 4);
        assertFalse(fit.showYear);
        assertEquals(8, fit.stepperGap);
        assertEquals(66, fit.nameWidth);
    }

    @Test public void thenTheGapClosesWithTheNameWhole() {
        CalendarWidgetFormats.HeadingFit fit = CalendarWidgetFormats.fitHeading(124, 66, 27, 8, 8,
            22, 8, 4);
        assertFalse(fit.showYear);
        assertEquals(6, fit.stepperGap);
        assertEquals(66, fit.nameWidth);
    }

    @Test public void theNameIsCutOnlyPastTheTightestGap() {
        int need = CalendarWidgetFormats.headingNeed(66, 8, 22, 4);
        assertEquals(122, need);
        CalendarWidgetFormats.HeadingFit atNeed = CalendarWidgetFormats.fitHeading(need, 66, 27, 8,
            8, 22, 8, 4);
        assertEquals(4, atNeed.stepperGap);
        assertEquals(66, atNeed.nameWidth);
        // The 4×2's old 112dp month less its 4dp sides: 104dp, where "September" cannot stay whole.
        CalendarWidgetFormats.HeadingFit narrow = CalendarWidgetFormats.fitHeading(104, 66, 27, 8,
            8, 22, 8, 4);
        assertEquals(4, narrow.stepperGap);
        assertEquals(48, narrow.nameWidth);
    }

    // ----- touch areas --------------------------------------------------------------------------

    @Test public void touchAreasMeetHalfwayAndKeepOffTheText() {
        // Discs at 100 and 130 (an 8 gap), grown by 7: they meet at 126.
        assertArrayEquals(new int[] {93, 126, 126, 159},
            CalendarWidgetFormats.stepperSpans(100, 130, 22, 7, 80, true));
        // Text ending 3 short of the first disc keeps its own touches.
        assertArrayEquals(new int[] {97, 126, 126, 159},
            CalendarWidgetFormats.stepperSpans(100, 130, 22, 7, 97, true));
        // Right to left the text follows the second disc.
        assertArrayEquals(new int[] {93, 126, 126, 155},
            CalendarWidgetFormats.stepperSpans(100, 130, 22, 7, 155, false));
    }

    @Test public void touchAreasNeverShrinkBelowTheDisc() {
        // Text running into where the first disc's area would start: the disc still answers.
        assertArrayEquals(new int[] {100, 124, 124, 155},
            CalendarWidgetFormats.stepperSpans(100, 126, 22, 7, 110, true));
    }
}
