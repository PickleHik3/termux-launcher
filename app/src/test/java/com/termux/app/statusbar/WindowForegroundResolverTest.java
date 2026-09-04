package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WindowForegroundResolverTest {

    @Test
    public void theCommandAsksForEveryPidAndFiltersGroupRowsByForegroundGroup() {
        String command = WindowForegroundResolver.buildCommand(Arrays.asList(10, 20, -1));
        assertTrue(command, command.startsWith("groups=' '; for p in 10 20; do"));
        // Skips the non-positive pid rather than emitting "/proc/-1/stat".
        assertFalse(command, command.contains("-1"));
        // The group pass has to be a single read over all of /proc: this kernel has no
        // /proc/<pid>/task/<pid>/children, so a per-process tree walk is not available.
        assertTrue(command, command.contains("cat /proc/[0-9]*/stat"));
        assertFalse(command, command.contains("children"));
        // utime+stime of each group member, not the leader's cutime/cstime.
        assertTrue(command, command.contains("${12} + ${13}"));
    }

    /** The regression: a wrapper script leads the group while its child does all the work. */
    @Test
    public void groupRowsAreSummedSoAWrapperScriptsChildCounts() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|sh\tbuild.sh\ng|500|0\ng|500|40\n",
            Collections.singletonList(10), 1000L);
        // First sighting has no delta to measure against.
        assertEquals(-1d, resolver.get(10).cpuFraction, 0.0001d);

        // 40 -> 240 ticks over one second: the leader still burns nothing, the child burns two cores.
        resolver.applyOutput("10|fg|500|sh\tbuild.sh\ng|500|0\ng|500|240\n",
            Collections.singletonList(10), 2000L);
        WindowForegroundResolver.ForegroundInfo info = resolver.get(10);
        assertEquals("sh", info.processName);
        assertEquals(500, info.foregroundPid);
        assertEquals(2d, info.cpuFraction, 0.05d);
        assertTrue(info.working);
    }

    /**
     * A CPU delta is only true of the moment it was measured. A reading nobody is refreshing — the
     * privileged backend went away mid-poll — must stop claiming the pane is working, or the window
     * pill turns its ring over a command that finished long ago.
     */
    @Test
    public void aWorkingReadingStopsCountingOnceItIsStale() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|sh\tbuild.sh\ng|500|0\n",
            Collections.singletonList(10), 1000L);
        resolver.applyOutput("10|fg|500|sh\tbuild.sh\ng|500|200\n",
            Collections.singletonList(10), 2000L);
        WindowForegroundResolver.ForegroundInfo info = resolver.get(10);

        assertTrue(info.working);
        assertTrue(info.isWorkingAsOf(2000L + WindowForegroundResolver.WORKING_TTL_MS));
        assertFalse("a reading nobody refreshed cannot keep asserting work",
            info.isWorkingAsOf(2000L + WindowForegroundResolver.WORKING_TTL_MS + 1L));
    }

    @Test
    public void aGroupWithNoRowsReportsUnknownRatherThanZero() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|nvim\n", Collections.singletonList(10), 1000L);
        resolver.applyOutput("10|fg|500|nvim\n", Collections.singletonList(10), 2000L);
        assertEquals(-1d, resolver.get(10).cpuFraction, 0.0001d);
        assertFalse(resolver.get(10).working);
    }

    @Test
    public void anotherGroupsTicksAreNotBorrowed() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        String first = "10|fg|500|make\n20|fg|600|nvim\ng|500|0\ng|600|0\n";
        String second = "10|fg|500|make\n20|fg|600|nvim\ng|500|300\ng|600|1\n";
        resolver.applyOutput(first, Arrays.asList(10, 20), 1000L);
        resolver.applyOutput(second, Arrays.asList(10, 20), 2000L);
        assertTrue(resolver.get(10).working);
        assertFalse(resolver.get(20).working);
    }

    @Test
    public void anIdleShellClearsItsForegroundAndItsSample() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|make\ng|500|100\n", Collections.singletonList(10), 1000L);
        resolver.applyOutput("10|idle|\n", Collections.singletonList(10), 2000L);
        assertTrue(resolver.get(10).idle);
        assertEquals(-1, resolver.get(10).foregroundPid);

        // Returning to the same foreground pid must not take a delta across the idle gap.
        resolver.applyOutput("10|fg|500|make\ng|500|9000\n", Collections.singletonList(10), 3000L);
        assertEquals(-1d, resolver.get(10).cpuFraction, 0.0001d);
    }

    /** A pid asked about but no longer readable must not leave a stale entry for a reused pid. */
    @Test
    public void anUnreadablePidIsEvictedButUnaskedPidsSurvive() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|make\n20|fg|600|nvim\ng|500|10\ng|600|10\n",
            Arrays.asList(10, 20), 1000L);
        assertNotNull(resolver.get(10));
        assertNotNull(resolver.get(20));

        // Only pid 10 is asked about this round, and it is gone.
        resolver.applyOutput("10|x|\n", Collections.singletonList(10), 2000L);
        assertNull(resolver.get(10));
        assertNotNull("pid 20 was not covered this round, so its entry stands", resolver.get(20));
    }

    @Test
    public void aFallingGroupSumReadsAsUnknownRatherThanNegativeWork() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|make\ng|500|400\n", Collections.singletonList(10), 1000L);
        // A reaped child drops out of the sum, so the counter can go backwards.
        resolver.applyOutput("10|fg|500|make\ng|500|50\n", Collections.singletonList(10), 2000L);
        assertEquals(-1d, resolver.get(10).cpuFraction, 0.0001d);
        assertFalse(resolver.get(10).working);
    }

    @Test
    public void editorsStillReportTheirOpenFile() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|nvim\t-p\t/home/amal/config.toml\ng|500|0\n",
            Collections.singletonList(10), 1000L);
        assertEquals("nvim", resolver.get(10).processName);
        assertEquals("config.toml", resolver.get(10).openFile);
    }
}
