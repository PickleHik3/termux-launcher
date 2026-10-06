package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ClockWidgetFormatsTest {
    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");
    /** The design's sample moment: 13:40:12 on Tue 6 Oct 2026 in Kuwait. */
    private static final ZonedDateTime SAMPLE = ZonedDateTime.of(2026, 10, 6, 13, 40, 12, 0, KUWAIT);
    private static final List<String> IDS = Arrays.asList("Europe/London", "Asia/Kolkata",
        "Asia/Calcutta", "America/New_York", "America/Argentina/Buenos_Aires", "Asia/Kuwait");

    private Locale saved;

    @Before public void pinLocale() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After public void restoreLocale() {
        Locale.setDefault(saved);
    }

    @Test public void cityIsTheZonesLastSegmentWithSpaces() {
        assertEquals("London", ClockWidgetFormats.cityName("Europe/London"));
        assertEquals("Buenos Aires", ClockWidgetFormats.cityName("America/Argentina/Buenos_Aires"));
        assertEquals("UTC", ClockWidgetFormats.cityName("UTC"));
        assertEquals("", ClockWidgetFormats.cityName(null));
        assertEquals("new-york", ClockWidgetFormats.paneCity("America/New_York"));
    }

    @Test public void aZoneResolvesFromAnIdAnOffsetOrJustTheCity() {
        assertEquals(ZoneId.of("Europe/London"), ClockWidgetFormats.resolveZone("Europe/London", IDS));
        assertEquals(ZoneId.of("Europe/London"), ClockWidgetFormats.resolveZone("  london ", IDS));
        assertEquals(ZoneId.of("America/New_York"), ClockWidgetFormats.resolveZone("New York", IDS));
        assertEquals(ZoneId.of("Asia/Kolkata"), ClockWidgetFormats.resolveZone("kolkata", IDS));
        assertEquals(ZoneId.of("America/New_York"), ClockWidgetFormats.resolveZone("america/new_york", IDS));
        assertEquals(ZoneOffset.ofHours(3),
            ClockWidgetFormats.resolveZone("UTC+3", IDS).getRules().getOffset(SAMPLE.toInstant()));
    }

    @Test public void anUnknownOrEmptyZoneResolvesToNothing() {
        assertNull(ClockWidgetFormats.resolveZone("Atlantis", IDS));
        assertNull(ClockWidgetFormats.resolveZone("", IDS));
        assertNull(ClockWidgetFormats.resolveZone("   ", IDS));
        assertNull(ClockWidgetFormats.resolveZone(null, IDS));
    }

    @Test public void offsetsAreMeasuredFromThePhonesZoneOnTheDay() {
        // London is on summer time in early October: two hours behind Kuwait, not three.
        assertEquals(-120, ClockWidgetFormats.offsetMinutes(SAMPLE, ZoneId.of("Europe/London")));
        assertEquals(150, ClockWidgetFormats.offsetMinutes(SAMPLE, ZoneId.of("Asia/Kolkata")));
        assertEquals(0, ClockWidgetFormats.offsetMinutes(SAMPLE, KUWAIT));
    }

    @Test public void dayDeltaSaysWhetherTheZoneIsAlreadyTomorrowOrStillYesterday() {
        assertEquals(0, ClockWidgetFormats.dayDelta(SAMPLE, ZoneId.of("Europe/London")));
        ZonedDateTime lateEvening = ZonedDateTime.of(2026, 10, 6, 23, 30, 0, 0, KUWAIT);
        assertEquals(1, ClockWidgetFormats.dayDelta(lateEvening, ZoneId.of("Asia/Tokyo")));
        ZonedDateTime earlyMorning = ZonedDateTime.of(2026, 10, 6, 1, 0, 0, 0, KUWAIT);
        assertEquals(-1, ClockWidgetFormats.dayDelta(earlyMorning, ZoneId.of("America/Los_Angeles")));
    }

    @Test public void offsetTextMatchesTheDesign() {
        assertEquals("−2 h", ClockWidgetFormats.offsetText(-120));
        assertEquals("+2:30", ClockWidgetFormats.offsetText(150));
        assertEquals("+5:45", ClockWidgetFormats.offsetText(345));
        assertEquals("−9:30", ClockWidgetFormats.offsetText(-570));
        assertEquals("+1:05", ClockWidgetFormats.offsetText(65));
        assertEquals("", ClockWidgetFormats.offsetText(0));
    }

    @Test public void offsetLabelAddsTheDayOnlyWhenTheDateDiffers() {
        String today = "Today", tomorrow = "%1$s tomorrow", yesterday = "%1$s yesterday";
        assertEquals("Today", ClockWidgetFormats.offsetLabel(0, 0, today, tomorrow, yesterday));
        assertEquals("−2 h", ClockWidgetFormats.offsetLabel(-120, 0, today, tomorrow, yesterday));
        assertEquals("+6 h tomorrow", ClockWidgetFormats.offsetLabel(360, 1, today, tomorrow, yesterday));
        assertEquals("−10 h yesterday",
            ClockWidgetFormats.offsetLabel(-600, -1, today, tomorrow, yesterday));
    }

    @Test public void zoneLabelReadsLikeGmtPlusHours() {
        assertEquals("GMT+3", ClockWidgetFormats.zoneLabel(3 * 3600));
        assertEquals("GMT+5:30", ClockWidgetFormats.zoneLabel(5 * 3600 + 30 * 60));
        assertEquals("GMT−4", ClockWidgetFormats.zoneLabel(-4 * 3600));
        assertEquals("GMT", ClockWidgetFormats.zoneLabel(0));
    }

    @Test public void dayAndYearProgressMatchTheDesignSample() {
        assertEquals(57, ClockWidgetFormats.percent(ClockWidgetFormats.dayFraction(SAMPLE)));
        assertEquals(76, ClockWidgetFormats.percent(ClockWidgetFormats.yearFraction(SAMPLE)));
        ZonedDateTime midnight = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, KUWAIT);
        assertEquals(0f, ClockWidgetFormats.dayFraction(midnight), 1e-6f);
        assertEquals(0f, ClockWidgetFormats.yearFraction(midnight), 1e-6f);
    }

    @Test public void aShortDaylightSavingDayIsCountedAsItIs() {
        // 29 March 2026 in London has 23 hours: noon is 11 real hours in.
        ZonedDateTime noon = ZonedDateTime.of(2026, 3, 29, 12, 0, 0, 0, ZoneId.of("Europe/London"));
        assertEquals(11f / 23f, ClockWidgetFormats.dayFraction(noon), 1e-4f);
    }

    @Test public void percentOnlyReadsAHundredWhenItIsOver() {
        assertEquals(99, ClockWidgetFormats.percent(0.999f));
        assertEquals(100, ClockWidgetFormats.percent(1f));
        assertEquals(0, ClockWidgetFormats.percent(0.004f));
        assertEquals(0, ClockWidgetFormats.percent(-1f));
    }

    @Test public void timeFollowsTheTwelveOrTwentyFourHourHabit() {
        LocalTime afternoon = LocalTime.of(13, 40, 12);
        assertEquals("13:40", ClockWidgetFormats.hoursMinutes(afternoon, true));
        assertEquals("1:40", ClockWidgetFormats.hoursMinutes(afternoon, false));
        assertEquals("12:05", ClockWidgetFormats.hoursMinutes(LocalTime.of(0, 5), false));
        assertEquals("09", ClockWidgetFormats.hours(LocalTime.of(9, 0), true));
        assertEquals("9", ClockWidgetFormats.hours(LocalTime.of(21, 0), false));
        assertEquals("40", ClockWidgetFormats.minutes(afternoon));
        assertEquals("12", ClockWidgetFormats.seconds(afternoon));
        assertEquals("PM", ClockWidgetFormats.amPm(afternoon, false));
        assertEquals("", ClockWidgetFormats.amPm(afternoon, true));
    }

    @Test public void sunTimesFromTheWeatherFollowTheHabitAndHideWhenUnreadable() {
        assertEquals("05:38", ClockWidgetFormats.clockText("05:38", true));
        assertEquals("5:22", ClockWidgetFormats.clockText("17:22", false));
        assertEquals("", ClockWidgetFormats.clockText("", true));
        assertEquals("", ClockWidgetFormats.clockText(null, true));
        assertEquals("", ClockWidgetFormats.clockText("soon", true));
    }

    @Test public void handsPointWhereTheDesignDrawsThem() {
        assertEquals(50f, ClockWidgetFormats.hourAngle(13, 40, 0), 1e-4f);
        assertEquals(240f, ClockWidgetFormats.minuteAngle(40, 0), 1e-4f);
        assertEquals(72f, ClockWidgetFormats.secondAngle(12), 1e-4f);
        assertEquals(0f, ClockWidgetFormats.hourAngle(12, 0, 0), 1e-4f);
        assertEquals(243f, ClockWidgetFormats.minuteAngle(40, 30), 1e-4f);
    }
}
