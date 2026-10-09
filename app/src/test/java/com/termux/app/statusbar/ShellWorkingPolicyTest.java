package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ShellWorkingPolicyTest {

    private static final long GRACE_MS = 700L;
    private static final long NOW = 10_000L;

    /** Two polls one second apart; {@code ticks} of CPU between them (100 ticks is one core). */
    private static WindowForegroundResolver.ForegroundInfo reading(String process, int ticks) {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|" + process + "\ng|500|0\n", Collections.singletonList(10), NOW - 1000L);
        resolver.applyOutput("10|fg|500|" + process + "\ng|500|" + ticks + "\n", Collections.singletonList(10), NOW);
        return resolver.get(10);
    }

    /** The bug: a multiplexer repainting its status line every second kept the ring turning forever. */
    @Test
    public void anIdleFullScreenProgramRepaintingItsStatusLineIsNotWorking() {
        WindowForegroundResolver.ForegroundInfo idleTui = reading("herdr", 1);
        assertFalse(ShellWorkingPolicy.isWorking(NOW, 0L, GRACE_MS, idleTui, true, true));
    }

    @Test
    public void aFullScreenProgramBurningCpuIsWorking() {
        WindowForegroundResolver.ForegroundInfo busyTui = reading("htop", 60);
        assertTrue(ShellWorkingPolicy.isWorking(NOW, 0L, GRACE_MS, busyTui, true, false));
    }

    @Test
    public void aFullScreenProgramWithoutAReadingIsNotWorking() {
        assertFalse(ShellWorkingPolicy.isWorking(NOW, 0L, GRACE_MS, null, true, true));
    }

    /** A download printing progress burns no CPU; on the main screen its output still counts. */
    @Test
    public void aMainScreenCommandPrintingSteadilyIsStillWorking() {
        WindowForegroundResolver.ForegroundInfo download = reading("curl", 1);
        assertTrue(ShellWorkingPolicy.isWorking(NOW, 0L, GRACE_MS, download, false, true));
        assertTrue(ShellWorkingPolicy.isWorking(NOW, 0L, GRACE_MS, null, false, true));
    }

    @Test
    public void aMainScreenCommandWithNeitherSignalIsNotWorking() {
        assertFalse(ShellWorkingPolicy.isWorking(NOW, 0L, GRACE_MS, reading("sleep", 0), false, false));
    }

    @Test
    public void typingSilencesTheIndicationWhateverTheSignals() {
        WindowForegroundResolver.ForegroundInfo busy = reading("make", 150);
        assertFalse(ShellWorkingPolicy.isWorking(NOW, NOW - 100L, GRACE_MS, busy, false, true));
    }

    @Test
    public void theShellHoldingTheTerminalIsNeverWorking() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|idle|\n", Collections.singletonList(10), NOW);
        assertFalse(ShellWorkingPolicy.isWorking(NOW, 0L, GRACE_MS, resolver.get(10), false, true));
    }
}
