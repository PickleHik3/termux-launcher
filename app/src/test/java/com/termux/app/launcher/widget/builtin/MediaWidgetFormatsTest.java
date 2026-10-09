package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaWidgetFormatsTest {
    @Test public void clockReadsMinutesAndSeconds() {
        assertEquals("0:00", MediaWidgetFormats.clock(0L));
        assertEquals("0:09", MediaWidgetFormats.clock(9_999L));
        assertEquals("1:42", MediaWidgetFormats.clock(102_000L));
        assertEquals("3:58", MediaWidgetFormats.clock(238_000L));
        assertEquals("59:59", MediaWidgetFormats.clock(3_599_000L));
    }

    @Test public void clockAddsHoursPastTheHour() {
        assertEquals("1:00:00", MediaWidgetFormats.clock(3_600_000L));
        assertEquals("1:02:05", MediaWidgetFormats.clock(3_725_000L));
    }

    @Test public void clockTreatsANegativePositionAsZero() {
        assertEquals("0:00", MediaWidgetFormats.clock(-5_000L));
    }

    @Test public void elapsedOfTotalJoinsBothOrShowsThePositionAlone() {
        assertEquals("1:42 / 3:58", MediaWidgetFormats.elapsedOfTotal(102_000L, 238_000L));
        assertEquals("1:42", MediaWidgetFormats.elapsedOfTotal(102_000L, 0L));
    }

    @Test public void positionAdvancesOnlyWhilePlaying() {
        assertEquals(105_000L, MediaWidgetFormats.positionAt(102_000L, 1_000L, 4_000L, true, 238_000L));
        assertEquals(102_000L, MediaWidgetFormats.positionAt(102_000L, 1_000L, 4_000L, false, 238_000L));
    }

    @Test public void positionStopsAtTheEndOfAKnownTrack() {
        assertEquals(238_000L, MediaWidgetFormats.positionAt(237_000L, 0L, 10_000L, true, 238_000L));
        // A stream with no length keeps counting.
        assertEquals(247_000L, MediaWidgetFormats.positionAt(237_000L, 0L, 10_000L, true, 0L));
    }

    @Test public void positionNeverRunsBackwardsOnAClockThatDid() {
        assertEquals(102_000L, MediaWidgetFormats.positionAt(102_000L, 5_000L, 4_000L, true, 238_000L));
    }

    @Test public void aRepeatedReportForTheSameTrackCarriesThePositionOn() {
        assertTrue(MediaWidgetFormats.carriesPosition("app", "Low Tide", 102_000L,
            "app", "Low Tide", 102_000L));
    }

    @Test public void aSeekANewTrackOrAnotherAppStartsAFreshPosition() {
        assertFalse(MediaWidgetFormats.carriesPosition("app", "Low Tide", 102_000L,
            "app", "Low Tide", 30_000L));
        assertFalse(MediaWidgetFormats.carriesPosition("app", "Low Tide", 102_000L,
            "app", "High Tide", 102_000L));
        assertFalse(MediaWidgetFormats.carriesPosition("app", "Low Tide", 102_000L,
            "other", "Low Tide", 102_000L));
    }

    @Test public void fractionIsClampedAndZeroWithoutALength() {
        assertEquals(0.5f, MediaWidgetFormats.fraction(60_000L, 120_000L), 0.0001f);
        assertEquals(1f, MediaWidgetFormats.fraction(200_000L, 120_000L), 0.0001f);
        assertEquals(0f, MediaWidgetFormats.fraction(60_000L, 0L), 0.0001f);
    }
}
