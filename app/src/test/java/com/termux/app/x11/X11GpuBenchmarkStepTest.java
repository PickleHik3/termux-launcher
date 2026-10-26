package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The step lines termux-x11-gpu-setup prints, as the benchmark panel and its notification read them. */
public class X11GpuBenchmarkStepTest {

    @Test
    public void theLastStepLineWins() {
        String output = "\n== Step 1 of 8: Looking at the phone's graphics chip ==\n"
            + "   Graphics chip: Mali-G52\n"
            + "\n== Step 2 of 8: Deciding what to try ==\n"
            + "   Will try, in this order:\n"
            + "\n== Step 3 of 8: Installing the test program and drivers ==\n"
            + "Get:1 https://packages.termux.dev glmark2 [1 MB]\n";
        X11GpuBenchmarkStep step = X11GpuBenchmarkStep.lastIn(output);
        assertNotNull(step);
        assertEquals(3, step.number);
        assertEquals(8, step.total);
        assertEquals("Installing the test program and drivers", step.text);
    }

    @Test
    public void noStepLineIsNull() {
        assertNull(X11GpuBenchmarkStep.lastIn(null));
        assertNull(X11GpuBenchmarkStep.lastIn(""));
        assertNull(X11GpuBenchmarkStep.lastIn("Reading package lists... Done\n"));
        assertNull("a line that only mentions a step",
            X11GpuBenchmarkStep.lastIn("   see Step 3 of 8 above\n"));
        assertNull("nonsense numbers", X11GpuBenchmarkStep.lastIn("== Step 9 of 8: Too far ==\n"));
    }

    @Test
    public void toleratesTrailingSpacesAndATerminalWithCarriageReturns() {
        X11GpuBenchmarkStep step = X11GpuBenchmarkStep.lastIn(
            "== Step 5 of 8: Testing each way ==   \r\nProgress 40%\r");
        assertNotNull(step);
        assertEquals(5, step.number);
        assertEquals("Testing each way", step.text);
    }

    @Test
    public void sameAsComparesTheWholeStep() {
        X11GpuBenchmarkStep a = X11GpuBenchmarkStep.lastIn("== Step 4 of 8: Starting a test display ==");
        X11GpuBenchmarkStep b = X11GpuBenchmarkStep.lastIn("== Step 4 of 8: Starting a test display ==");
        X11GpuBenchmarkStep c = X11GpuBenchmarkStep.lastIn("== Step 5 of 8: Testing each way ==");
        assertNotNull(a);
        assertTrue(a.sameAs(b));
        assertFalse(a.sameAs(c));
        assertFalse(a.sameAs(null));
    }
}
