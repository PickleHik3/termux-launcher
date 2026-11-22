package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The {@code --estimate} contract with termux-x11-gpu-setup, and what the page does with a broken one. */
public class X11GpuBenchmarkEstimateTest {

    @Test
    public void readsTheFourKeys() {
        X11GpuBenchmarkEstimate e = X11GpuBenchmarkEstimate.parse(
            "download_bytes=125829120\ninstall_bytes=471859200\nremovable_bytes=314572800\nupgrade_count=12\n");
        assertEquals(125829120L, e.downloadBytes);
        assertEquals(471859200L, e.installBytes);
        assertEquals(314572800L, e.removableBytes);
        assertEquals(12L, e.upgradeCount);
        assertTrue(e.sizesKnown());
        assertFalse(e.noSizesKnown());
    }

    @Test
    public void ignoresChatterAroundTheKeysAndCarriageReturns() {
        X11GpuBenchmarkEstimate e = X11GpuBenchmarkEstimate.parse(
            "Hit:1 https://packages.termux.dev stable InRelease\r\nReading package lists... Done\r\n"
                + "download_bytes=10\r\n  install_bytes = 20 \nsomething_else=5\nremovable_bytes=0\n");
        assertEquals(10L, e.downloadBytes);
        assertEquals(20L, e.installBytes);
        assertEquals("zero is a real answer", 0L, e.removableBytes);
        assertEquals(X11GpuBenchmarkEstimate.UNKNOWN, e.upgradeCount);
        assertTrue(e.sizesKnown());
    }

    @Test
    public void aMissingOrBrokenValueIsUnknown() {
        X11GpuBenchmarkEstimate e = X11GpuBenchmarkEstimate.parse(
            "download_bytes=abc\ninstall_bytes=-5\nremovable_bytes=\n");
        assertEquals(X11GpuBenchmarkEstimate.UNKNOWN, e.downloadBytes);
        assertEquals(X11GpuBenchmarkEstimate.UNKNOWN, e.installBytes);
        assertEquals(X11GpuBenchmarkEstimate.UNKNOWN, e.removableBytes);
        assertTrue(e.noSizesKnown());

        X11GpuBenchmarkEstimate partial = X11GpuBenchmarkEstimate.parse("download_bytes=1\n");
        assertFalse(partial.sizesKnown());
        assertFalse(partial.noSizesKnown());
    }

    @Test
    public void nothingIsNothingKnown() {
        assertTrue(X11GpuBenchmarkEstimate.parse(null).noSizesKnown());
        assertTrue(X11GpuBenchmarkEstimate.parse("").noSizesKnown());
        assertTrue(X11GpuBenchmarkEstimate.parse("Unknown option: --estimate (try --help)\n").noSizesKnown());
        assertTrue("too big for a long",
            X11GpuBenchmarkEstimate.parse("download_bytes=99999999999999999999999\n").noSizesKnown());
    }
}
