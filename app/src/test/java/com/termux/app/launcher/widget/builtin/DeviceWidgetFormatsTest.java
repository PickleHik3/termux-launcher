package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;

public class DeviceWidgetFormatsTest {
    private static final long KB_PER_GIB = 1024L * 1024L;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    @Test public void memoryIsBinaryGigabytesWithOneDecimal() {
        assertEquals("5.1/7.7G", DeviceWidgetFormats.memoryPair(
            Math.round(5.1 * KB_PER_GIB), Math.round(7.7 * KB_PER_GIB)));
        assertEquals("9.0/12G", DeviceWidgetFormats.memoryPair(9 * KB_PER_GIB, 12 * KB_PER_GIB));
    }

    @Test public void storageIsDecimalGigabytesAndWholeFromTenUp() {
        assertEquals("156/256G", DeviceWidgetFormats.storagePair(156_000_000_000L, 256_000_000_000L));
        assertEquals("10/128G", DeviceWidgetFormats.storagePair(9_960_000_000L, 128_000_000_000L));
        assertEquals("9.9/32G", DeviceWidgetFormats.storagePair(9_940_000_000L, 32_000_000_000L));
    }

    @Test public void anUnknownTotalIsADash() {
        assertEquals(DeviceWidgetFormats.UNKNOWN, DeviceWidgetFormats.memoryPair(100, 0));
        assertEquals(DeviceWidgetFormats.UNKNOWN, DeviceWidgetFormats.storagePair(-1, -1));
    }

    @Test public void percentIsClampedAndUnknownWithoutAWhole() {
        assertEquals(66, DeviceWidgetFormats.percent(66, 100));
        assertEquals(61, DeviceWidgetFormats.percent(156_000_000_000L, 256_000_000_000L));
        assertEquals(100, DeviceWidgetFormats.percent(200, 100));
        assertEquals(0, DeviceWidgetFormats.percent(-5, 100));
        assertEquals(-1, DeviceWidgetFormats.percent(5, 0));
        assertEquals("24%", DeviceWidgetFormats.percentText(24));
        assertEquals(DeviceWidgetFormats.UNKNOWN, DeviceWidgetFormats.percentText(-1));
    }

    @Test public void uptimeNamesTheTwoLargestUnits() {
        assertEquals("3d 4h", DeviceWidgetFormats.uptime((3 * 24 + 4) * HOUR + 59 * MINUTE));
        assertEquals("4h 12m", DeviceWidgetFormats.uptime(4 * HOUR + 12 * MINUTE));
        assertEquals("12m", DeviceWidgetFormats.uptime(12 * MINUTE + 30_000L));
        assertEquals("0m", DeviceWidgetFormats.uptime(0));
        assertEquals("1d 0h", DeviceWidgetFormats.uptime(24 * HOUR));
    }

    @Test public void durationRoundsUpToTheMinute() {
        assertEquals("42 min", DeviceWidgetFormats.duration(41 * MINUTE + 1));
        assertEquals("1 min", DeviceWidgetFormats.duration(0));
        assertEquals("1 h 5 min", DeviceWidgetFormats.duration(65 * MINUTE));
        assertEquals("2 h", DeviceWidgetFormats.duration(120 * MINUTE));
        assertEquals(42, DeviceWidgetFormats.minutesRoundedUp(41 * MINUTE + 1));
    }

    @Test public void localHoursFollowHalfHourZones() {
        TimeZone kolkata = TimeZone.getTimeZone("Asia/Kolkata");
        // Midnight UTC is 05:30 in Kolkata: the hour turns at :30 past the UTC hour.
        assertEquals(5, DeviceWidgetFormats.hourOfDay(DeviceWidgetFormats.localHour(29 * MINUTE, kolkata)));
        assertEquals(6, DeviceWidgetFormats.hourOfDay(DeviceWidgetFormats.localHour(30 * MINUTE, kolkata)));
        assertEquals(23, DeviceWidgetFormats.hourOfDay(-1));
    }

    @Test public void theDayAxisLabelsEverySixthHourFromTheOldestBar() {
        long now = 24L * 20_000 + 13;   // some day, 13:xx
        assertArrayEquals(new String[] {"14:00", "20:00", "02:00", "08:00"},
            DeviceWidgetFormats.hourAxis(now, 24));
        assertEquals("00:00", DeviceWidgetFormats.hourLabel(24));
    }

    @Test public void temperaturesFromTenths() {
        assertEquals("31.4°C", DeviceWidgetFormats.celsius(314));
        assertEquals("31.4°", DeviceWidgetFormats.degreesTenths(314));
        assertEquals("31°", DeviceWidgetFormats.degrees(314));
        assertEquals("32°", DeviceWidgetFormats.degrees(315));
        assertEquals(DeviceWidgetFormats.UNKNOWN, DeviceWidgetFormats.celsius(Integer.MIN_VALUE));
        assertEquals(DeviceWidgetFormats.UNKNOWN, DeviceWidgetFormats.degrees(Integer.MIN_VALUE));
    }

    @Test public void wattsKeepADecimalOnlyBelowTen() {
        assertEquals("18 W", DeviceWidgetFormats.watts(18.2));
        assertEquals("4.5 W", DeviceWidgetFormats.watts(4.46));
        assertEquals(DeviceWidgetFormats.UNKNOWN, DeviceWidgetFormats.watts(Double.NaN));
    }

    @Test public void powerUsesTheCurrentsMagnitudeAndRefusesImplausibleReadings() {
        assertEquals(18.0, DeviceWidgetFormats.powerWatts(-4_285_714, 4200), 0.01);
        assertEquals(18.0, DeviceWidgetFormats.powerWatts(4_285_714, 4200), 0.01);
        assertTrue(Double.isNaN(DeviceWidgetFormats.powerWatts(Integer.MIN_VALUE, 4200)));
        assertTrue(Double.isNaN(DeviceWidgetFormats.powerWatts(0, 4200)));
        assertTrue(Double.isNaN(DeviceWidgetFormats.powerWatts(1500, 4000)));
        assertTrue(Double.isNaN(DeviceWidgetFormats.powerWatts(1_000_000, -1)));
    }

    @Test public void voltageAcceptsMicrovolts() {
        assertEquals(4200, DeviceWidgetFormats.voltageMillivolts(4200));
        assertEquals(4200, DeviceWidgetFormats.voltageMillivolts(4_200_000));
        assertEquals(-1, DeviceWidgetFormats.voltageMillivolts(0));
    }

    @Test public void clockSpeedInGigahertz() {
        assertEquals("1.8 GHz", DeviceWidgetFormats.gigahertz(1_804_800));
        assertEquals("3.2 GHz", DeviceWidgetFormats.gigahertz(3_187_200));
        assertNull(DeviceWidgetFormats.gigahertz(0));
    }
}
