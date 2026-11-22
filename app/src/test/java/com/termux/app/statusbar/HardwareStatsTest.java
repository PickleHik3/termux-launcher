package com.termux.app.statusbar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HardwareStatsTest {

    @Test
    public void busyAcceptsThePercentFormsAndDevfreqLoad() {
        assertEquals(34, HardwareStats.parseBusyPercent("34 %"));
        assertEquals(34, HardwareStats.parseBusyPercent("34"));
        assertEquals(34, HardwareStats.parseBusyPercent("34@585000000Hz\n"));
        assertEquals(100, HardwareStats.parseBusyPercent("250"));
        assertEquals(HardwareStats.UNKNOWN, HardwareStats.parseBusyPercent("n/a"));
        assertEquals(HardwareStats.UNKNOWN, HardwareStats.parseBusyPercent(""));
        assertEquals(HardwareStats.UNKNOWN, HardwareStats.parseBusyPercent(null));
    }

    @Test
    public void frequenciesConvertFromHzKhzAndMhz() {
        assertEquals(585, HardwareStats.parseMhz("585000000", HardwareStats.Unit.HZ));
        assertEquals(1800, HardwareStats.parseMhz("1800000\n", HardwareStats.Unit.KHZ));
        assertEquals(900, HardwareStats.parseMhz("900", HardwareStats.Unit.MHZ));
        assertEquals(HardwareStats.UNKNOWN, HardwareStats.parseMhz("0", HardwareStats.Unit.KHZ));
        assertEquals(HardwareStats.UNKNOWN, HardwareStats.parseMhz("x", HardwareStats.Unit.KHZ));
        assertEquals(HardwareStats.UNKNOWN, HardwareStats.parseMhz(null, HardwareStats.Unit.HZ));
    }

    @Test
    public void temperaturesAreMillidegreesOrDegreesAndFaultsAreDropped() {
        assertEquals(48.5f, HardwareStats.parseTempC("48500"), 0.001f);
        assertEquals(36f, HardwareStats.parseTempC("36"), 0.001f);
        assertTrue(Float.isNaN(HardwareStats.parseTempC("-5000")));
        assertTrue(Float.isNaN(HardwareStats.parseTempC("200000")));
        assertTrue(Float.isNaN(HardwareStats.parseTempC("151")));
        assertTrue(Float.isNaN(HardwareStats.parseTempC("junk")));
        assertTrue(Float.isNaN(HardwareStats.parseTempC(null)));
    }

    @Test
    public void relatedCpusGiveTheFirstAndLast() {
        assertArrayEquals(new int[] {0, 3}, HardwareStats.parseRelatedCpus("0 1 2 3\n"));
        assertArrayEquals(new int[] {4, 6}, HardwareStats.parseRelatedCpus("4 5 6"));
        assertArrayEquals(new int[] {7, 7}, HardwareStats.parseRelatedCpus("7"));
        assertArrayEquals(new int[] {0, 3}, HardwareStats.parseRelatedCpus("0-3"));
        assertNull(HardwareStats.parseRelatedCpus(""));
        assertNull(HardwareStats.parseRelatedCpus("a b"));
        assertNull(HardwareStats.parseRelatedCpus(null));
    }
}
