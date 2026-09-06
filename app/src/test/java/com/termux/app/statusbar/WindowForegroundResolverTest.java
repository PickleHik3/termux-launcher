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

    /** The regression: a shebang-launched npm CLI reported as its interpreter, never itself. */
    @Test
    public void aNodeShebangScriptResolvesToItsOwnNameNotNode() {
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput(
            "10|fg|500|node\t/data/data/com.termux/files/usr/lib/node_modules/@openai/codex/bin/codex.js\t--foo\n"
                + "g|500|0\n",
            Collections.singletonList(10), 1000L);
        assertEquals("codex", resolver.get(10).processName);
    }

    /**
     * The reported device's actual install: Codex CLI Termux ships as a scoped npm package whose
     * {@code #!/usr/bin/env node} shebang starts a JS wrapper that itself spawns a native
     * {@code codex.bin} child. The child never becomes the foreground group leader — the resolver
     * has no way to walk to it (see the class doc), so the wrapper's own argv is all it ever reads,
     * for the pane's whole lifetime, not just at startup.
     */
    @Test
    public void theRealCodexTermuxPackagePathResolvesToCodex() {
        assertEquals("codex", WindowForegroundResolver.unwrapInterpreter("node", new String[]{"node",
            "/data/data/com.termux/files/usr/lib/node_modules/@mmmbuto/codex-cli-termux/bin/codex.js"}));
    }

    /** A generic entrypoint filename climbs past itself and past the generic wrapper directory. */
    @Test
    public void aGenericEntrypointClimbsToThePackageDirectory() {
        assertEquals("some-tool", WindowForegroundResolver.unwrapInterpreter("node",
            new String[]{"node", "/usr/lib/node_modules/some-tool/bin/cli.js"}));
        assertEquals("some-tool", WindowForegroundResolver.unwrapInterpreter("node",
            new String[]{"node", "/usr/lib/node_modules/some-tool/bin/index.js"}));
    }

    /**
     * Python's {@code -m} is the one flag confidently known to carry the identity itself: the
     * module name, not a file. {@code 8000} is {@code http.server}'s own argument, not a further
     * script to resolve, and must not be picked up as one.
     */
    @Test
    public void pythonModuleFlagResolvesToTheModuleNameNotItsTrailingArgument() {
        assertEquals("http.server", WindowForegroundResolver.unwrapInterpreter("python3",
            new String[]{"python3", "-m", "http.server", "8000"}));
    }

    /**
     * Regression: {@code -c}'s value is a code string, not a script path. It must not be parsed at
     * all — sanitizing the code into something path-shaped ("print1") is a confident-looking wrong
     * answer, which is worse than falling back to the plain interpreter name.
     */
    @Test
    public void pythonEvalFlagValueIsNeverReadAsAScriptName() {
        assertNull(WindowForegroundResolver.unwrapInterpreter("python3",
            new String[]{"python3", "-c", "print(1)"}));
    }

    /**
     * Regression: once an eval flag is seen, nothing after it is a script either — it is the
     * evaluated code's own argv, e.g. {@code userarg} below is {@code process.argv[2]} inside
     * {@code -e}'s script, not a program name.
     */
    @Test
    public void nodeEvalFlagsTrailingArgumentIsNeverReadAsAScriptName() {
        assertNull(WindowForegroundResolver.unwrapInterpreter("node",
            new String[]{"node", "-e", "code", "userarg"}));
    }

    /**
     * An unrecognized flag is not assumed to take no value: guessing wrong (treating its value as
     * the script) is worse than bailing out to the plain interpreter name.
     */
    @Test
    public void anUnrecognizedFlagBailsOutRatherThanGuessing() {
        assertNull(WindowForegroundResolver.unwrapInterpreter("node",
            new String[]{"node", "-r", "dotenv/config", "/opt/mytool/bin/mytool.js"}));
    }

    /** Nothing but flags after the interpreter (an eval one-liner): fall back to the interpreter. */
    @Test
    public void anInterpreterWithNoScriptPathIsLeftAlone() {
        assertNull(WindowForegroundResolver.unwrapInterpreter("node",
            new String[]{"node", "-e", "console.log(1)"}));
    }

    /** Shells are not treated as interpreters: their own glyph already names them. */
    @Test
    public void shellsAreNotUnwrapped() {
        assertNull(WindowForegroundResolver.unwrapInterpreter("bash",
            new String[]{"bash", "/home/amal/build.sh"}));
    }
}
