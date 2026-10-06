package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

public class SignalsWidgetFormatsTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kuwait");

    private static long at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    @Test public void todayIsAClockTime() {
        long now = at(6, 13, 40);
        assertEquals("13:32", SignalsWidgetFormats.clockOrDay(at(6, 13, 32), now, ZONE, true, Locale.US));
        assertEquals("00:05", SignalsWidgetFormats.clockOrDay(at(6, 0, 5), now, ZONE, true, Locale.US));
        assertEquals("1:32 PM", SignalsWidgetFormats.clockOrDay(at(6, 13, 32), now, ZONE, false, Locale.US));
    }

    @Test public void anEarlierDayIsItsWeekday() {
        // 2026-10-05 is a Monday.
        long now = at(6, 0, 30);
        assertEquals("Mon", SignalsWidgetFormats.clockOrDay(at(5, 23, 59), now, ZONE, true, Locale.US));
    }

    @Test public void agoSteps() {
        long now = 1_000_000_000L;
        assertEquals("just now", SignalsWidgetFormats.ago(now - 2_000L, now));
        assertEquals("just now", SignalsWidgetFormats.ago(now + 60_000L, now));
        assertEquals("30s ago", SignalsWidgetFormats.ago(now - 30_000L, now));
        assertEquals("12m ago", SignalsWidgetFormats.ago(now - 12 * 60_000L - 5_000L, now));
        assertEquals("2h ago", SignalsWidgetFormats.ago(now - 2 * 3_600_000L - 59 * 60_000L, now));
        assertEquals("3d ago", SignalsWidgetFormats.ago(now - 3 * 86_400_000L, now));
    }

    @Test public void everyPrintsTheLargestWholeUnit() {
        assertEquals("every 15m", SignalsWidgetFormats.every(15));
        assertEquals("every 1h", SignalsWidgetFormats.every(60));
        assertEquals("every 1h 30m", SignalsWidgetFormats.every(90));
        assertEquals("every 1d", SignalsWidgetFormats.every(1440));
        assertEquals("every 1m", SignalsWidgetFormats.every(0));
    }

    @Test public void durationKeepsTwoFiguresUnderTenSeconds() {
        assertEquals("0.18 s", SignalsWidgetFormats.duration(180));
        assertEquals("4.20 s", SignalsWidgetFormats.duration(4_200));
        assertEquals("12.4 s", SignalsWidgetFormats.duration(12_400));
        assertEquals("1m 05s", SignalsWidgetFormats.duration(65_000));
    }

    @Test public void overflowAndBadge() {
        assertEquals("+6", SignalsWidgetFormats.overflow(6));
        assertEquals("", SignalsWidgetFormats.overflow(0));
        assertEquals("7", SignalsWidgetFormats.badge(7));
        assertEquals("99+", SignalsWidgetFormats.badge(120));
    }
}
