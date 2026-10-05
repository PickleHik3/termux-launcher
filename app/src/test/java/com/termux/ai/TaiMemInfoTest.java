package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The /proc/meminfo parser, the choice between it and {@code availMem}, and the swap rule. */
public class TaiMemInfoTest {
    private static final long KB = 1024L;
    private static final long GIB = 1024L * 1024L * KB;

    /** A trimmed /proc/meminfo as pong prints it: values in kB. */
    private static final String PONG = "MemTotal:       11530736 kB\n"
        + "MemFree:          611232 kB\n"
        + "MemAvailable:    4915200 kB\n"
        + "Buffers:            1832 kB\n"
        + "Cached:          4021244 kB\n"
        + "SwapCached:        98304 kB\n"
        + "SwapTotal:       8388604 kB\n"
        + "SwapFree:        5242880 kB\n";

    @Test
    public void parsesMemAvailableAndSwapInBytes() {
        TaiMemInfo.Reading reading = TaiMemInfo.parse(PONG);
        assertNotNull(reading);
        assertEquals(4_915_200L * KB, reading.availBytes);
        assertEquals(8_388_604L * KB, reading.swapTotalBytes);
        assertEquals(5_242_880L * KB, reading.swapFreeBytes);
        assertTrue(reading.fromProc);
        assertTrue(reading.swapKnown());
    }

    /** SwapCached shares a prefix with SwapTotal's neighbours; only the exact names count. */
    @Test
    public void onlyTheExactFieldNamesCount() {
        TaiMemInfo.Reading reading = TaiMemInfo.parse("MemAvailableX: 5 kB\nMemAvailable: 100 kB\nSwapCached: 7 kB\n"
            + "SwapTotal: 200 kB\nSwapFree: 50 kB\n");
        assertNotNull(reading);
        assertEquals(100L * KB, reading.availBytes);
        assertEquals(200L * KB, reading.swapTotalBytes);
        assertEquals(50L * KB, reading.swapFreeBytes);
    }

    @Test
    public void aFileWithoutMemAvailableIsUnusable() {
        assertNull(TaiMemInfo.parse(null));
        assertNull(TaiMemInfo.parse(""));
        assertNull(TaiMemInfo.parse("MemTotal: 100 kB\nMemFree: 50 kB\n"));
        assertNull(TaiMemInfo.parse("MemAvailable: garbage kB\n"));
        assertNull(TaiMemInfo.parse("MemAvailable: 0 kB\n"));
        assertNull(TaiMemInfo.parse("MemAvailable: -5 kB\n"));
    }

    @Test
    public void aMissingOrHalfSwapPairLeavesSwapUnknown() {
        TaiMemInfo.Reading noSwap = TaiMemInfo.parse("MemAvailable: 100 kB\n");
        assertNotNull(noSwap);
        assertFalse(noSwap.swapKnown());
        TaiMemInfo.Reading half = TaiMemInfo.parse("MemAvailable: 100 kB\nSwapTotal: 200 kB\n");
        assertNotNull(half);
        assertFalse(half.swapKnown());
        assertEquals(-1L, half.swapTotalBytes);
        // A Windows line ending does not matter.
        TaiMemInfo.Reading crlf = TaiMemInfo.parse("MemAvailable: 100 kB\r\nSwapTotal: 200 kB\r\nSwapFree: 50 kB\r\n");
        assertNotNull(crlf);
        assertEquals(100L * KB, crlf.availBytes);
        assertEquals(50L * KB, crlf.swapFreeBytes);
    }

    /** Below API 36 availMem is MemFree + Cached and reads high, so the file wins; from 36 availMem is MemAvailable. */
    @Test
    public void belowApi36TheFileReplacesAvailMem() {
        TaiMemInfo.Reading proc = TaiMemInfo.parse(PONG);
        long availMem = 7L * GIB;
        assertEquals(4_915_200L * KB, TaiMemInfo.choose(34, proc, availMem).availBytes);
        assertTrue(TaiMemInfo.choose(34, proc, availMem).fromProc);
        assertEquals(4_915_200L * KB, TaiMemInfo.choose(28, proc, availMem).availBytes);
        assertEquals(availMem, TaiMemInfo.choose(36, proc, availMem).availBytes);
        assertFalse(TaiMemInfo.choose(36, proc, availMem).fromProc);
        // Swap comes from the file on every release.
        assertEquals(5_242_880L * KB, TaiMemInfo.choose(36, proc, availMem).swapFreeBytes);
        // availMem unreadable on 36: the file is the only figure there is.
        assertEquals(4_915_200L * KB, TaiMemInfo.choose(36, proc, 0L).availBytes);
    }

    @Test
    public void aFailedReadFallsBackToAvailMemWithSwapUnknown() {
        TaiMemInfo.Reading reading = TaiMemInfo.choose(34, null, 3L * GIB);
        assertEquals(3L * GIB, reading.availBytes);
        assertFalse(reading.fromProc);
        assertFalse(reading.swapKnown());
        // Unknown swap below API 36 counts as low; on 36 it does not.
        assertTrue(reading.swapLow(34));
        assertFalse(reading.swapLow(36));
        assertEquals(0L, TaiMemInfo.choose(34, null, -1L).availBytes);
    }

    @Test
    public void swapIsLowBelowFifteenPercentFree() {
        long total = 10L * GIB;  // 15 % of it is a whole number of bytes
        assertFalse(TaiMemInfo.swapLow(total, total * 15L / 100L, false));
        assertTrue(TaiMemInfo.swapLow(total, total * 15L / 100L - 1L, false));
        assertTrue(TaiMemInfo.swapLow(total, 0L, false));
        assertFalse(TaiMemInfo.swapLow(total, total, true));
        // No swap at all has none to run out of; unknown swap is judged by the caller's rule.
        assertFalse(TaiMemInfo.swapLow(0L, 0L, true));
        assertTrue(TaiMemInfo.swapLow(-1L, -1L, true));
        assertFalse(TaiMemInfo.swapLow(-1L, -1L, false));
        // Pong's file: 5 GiB of 8 GiB free is not low.
        TaiMemInfo.Reading pong = TaiMemInfo.parse(PONG);
        assertNotNull(pong);
        assertFalse(pong.swapLow(34));
    }
}
