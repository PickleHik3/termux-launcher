package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * The pure pieces of D5's terminal-pane routing: the tag and pane.open arguments built for a
 * {@code Terminal=true} app, picking an already-open pane back out of a pane.list result, the
 * three-way focus/re-run/open-fresh rule ({@link LinuxTerminalAppRunner#actionFor}), and the
 * unprivileged {@code /proc/<pid>/stat} read that feeds it ({@link LinuxTerminalAppRunner#readIsIdle}).
 * {@link LinuxTerminalAppRunner#run(LinuxAppCatalog.LinuxApp)} itself needs a live
 * {@code TerminalActionDispatcher} host and a real pane, and is covered by
 * {@code com.termux.app.terminal.LinuxTerminalAppRunnerPaneTest} instead of here.
 */
public class LinuxTerminalAppRunnerTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File write(File dir, String name, String content) throws IOException {
        File file = new File(dir, name);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private LinuxAppCatalog.LinuxApp prefixTerminalApp() throws IOException {
        File dir = temp.newFolder("applications");
        write(dir, "htop.desktop", "[Desktop Entry]\nType=Application\nName=htop\nExec=htop\nTerminal=true\n");
        List<LinuxAppCatalog.LinuxApp> apps =
            LinuxAppCatalog.scan(LinuxAppCatalog.prefixRoots(Arrays.asList(dir)));
        return apps.get(0);
    }

    @Test public void theTagIsStableAndCarriesTheAppsId() throws IOException {
        LinuxAppCatalog.LinuxApp app = prefixTerminalApp();
        assertEquals(LinuxTerminalAppRunner.tagFor(app), LinuxTerminalAppRunner.tagFor(app));
        assertTrue(LinuxTerminalAppRunner.tagFor(app).endsWith(app.id));
    }

    @Test public void openArgumentsRunTheAppsOwnCommandUnderItsOwnName() throws IOException, JSONException {
        LinuxAppCatalog.LinuxApp app = prefixTerminalApp();
        JSONObject arguments = LinuxTerminalAppRunner.openArguments(app);
        assertEquals(app.command(), arguments.getString("command"));
        assertEquals("htop", arguments.getString("title"));
        assertEquals(LinuxTerminalAppRunner.tagFor(app), arguments.getString("tag"));
        assertTrue(arguments.getBoolean("focus"));
    }

    @Test public void findExistingPaneMatchesTheTaggedLivePaneOnlyAndCarriesItsPid() throws JSONException {
        JSONObject list = new JSONObject()
            .put("windows", new JSONArray()
                .put(new JSONObject().put("panes", new JSONArray()
                    // Not tagged: the user's own shell.
                    .put(new JSONObject().put("id", "p0").put("pid", 100).put("running", true))
                    // Tagged for a different app.
                    .put(new JSONObject().put("id", "p1").put("pid", 101).put("running", true)
                        .put("agent", new JSONObject().put("tag", "linux-terminal-app:other")))
                    // Tagged for this app, but its shell already exited.
                    .put(new JSONObject().put("id", "p2").put("pid", 102).put("running", false)
                        .put("agent", new JSONObject().put("tag", "linux-terminal-app:htop")))
                    // The one that should match.
                    .put(new JSONObject().put("id", "p3").put("pid", 103).put("running", true)
                        .put("agent", new JSONObject().put("tag", "linux-terminal-app:htop"))))));

        LinuxTerminalAppRunner.ExistingPane found =
            LinuxTerminalAppRunner.findExistingPane(list, "linux-terminal-app:htop");
        assertEquals("p3", found.id);
        assertEquals(103, found.pid);
        assertNull(LinuxTerminalAppRunner.findExistingPane(list, "linux-terminal-app:nothing-open"));
    }

    @Test public void findExistingPaneIsNullWithNoWindows() throws JSONException {
        assertNull(LinuxTerminalAppRunner.findExistingPane(new JSONObject(), "any-tag"));
    }

    // --- the three-way reuse rule: focus / re-run / open fresh, never a wrong guess ------------

    @Test public void aConfirmedRunningPaneIsFocusedNotTouched() {
        assertEquals("idle=false (something other than the shell owns the foreground) means focus",
            LinuxTerminalAppRunner.Action.FOCUS, LinuxTerminalAppRunner.actionFor(Boolean.FALSE));
    }

    @Test public void aConfirmedIdlePaneIsReRunInPlace() {
        // The first bug the coordinator caught: the app already exited and the pane is sitting at
        // a bare prompt (idle=true) — focusing it silently "does nothing" from the user's side.
        assertEquals("idle=true (only the shell is in the foreground) means re-run in place",
            LinuxTerminalAppRunner.Action.RERUN_IN_PLACE, LinuxTerminalAppRunner.actionFor(Boolean.TRUE));
    }

    @Test public void anUnknownReadingOpensAFreshPaneRatherThanGuessing() {
        // The second, worse bug: with idle unknown (no privileged backend and no /proc reading —
        // the common case for most installs before the procfs read was added), re-running in
        // place would type the command's own keystrokes into whatever the pane turns out to be
        // running, which is destructive when that is a live program rather than an idle shell.
        // Unknown must open a fresh pane instead of ever choosing FOCUS or RERUN_IN_PLACE.
        assertEquals("unknown means open fresh, never focus or re-run",
            LinuxTerminalAppRunner.Action.OPEN_FRESH, LinuxTerminalAppRunner.actionFor(null));
    }

    // --- the procfs read: idle iff the shell's own pid is (or ties) its pane's tpgid -----------

    private File statFile(String pid, String afterComm) throws IOException {
        return write(temp.newFolder("proc-" + pid), "stat", pid + " (sh) " + afterComm + "\n");
    }

    @Test public void aShellThatIsItsOwnForegroundGroupReadsAsIdle() throws IOException {
        // pid (comm) state ppid pgrp session tty_nr tpgid ...  -- tpgid (6th field after ")") ==
        // pid itself: nothing else has taken over the pane's foreground.
        File stat = statFile("500", "S 1 500 500 34816 500 0 0");
        assertEquals(Boolean.TRUE, LinuxTerminalAppRunner.readIsIdle(stat, 500));
    }

    @Test public void aShellWhoseForegroundGroupIsSomethingElseReadsAsNotIdle() throws IOException {
        // htop (or proot-distro, for a container app) took the foreground: tpgid (600) differs
        // from the shell's own pid (500). No process name is read anywhere in this decision.
        File stat = statFile("500", "S 1 500 500 34816 600 0 0");
        assertEquals(Boolean.FALSE, LinuxTerminalAppRunner.readIsIdle(stat, 500));
    }

    @Test public void aDetachedTpgidReadsAsIdle() throws IOException {
        // tpgid <= 0: no controlling terminal owns the foreground right now either way.
        File stat = statFile("500", "S 1 500 500 34816 0 0 0");
        assertEquals(Boolean.TRUE, LinuxTerminalAppRunner.readIsIdle(stat, 500));
    }

    @Test public void aCommContainingSpacesAndParenthesesDoesNotShiftTheFields() throws IOException {
        File stat = write(temp.newFolder("proc-odd"), "stat",
            "500 (my (odd) prog) S 1 500 500 34816 500 0 0\n");
        assertEquals(Boolean.TRUE, LinuxTerminalAppRunner.readIsIdle(stat, 500));
    }

    @Test public void aMissingOrUnparsableStatFileIsUnknownNotAGuess() throws IOException {
        assertNull(LinuxTerminalAppRunner.readIsIdle(new File(temp.getRoot(), "no-such-file"), 500));
        assertNull(LinuxTerminalAppRunner.readIsIdle(statFile("501", "garbage"), 501));
        assertNull(LinuxTerminalAppRunner.readIsIdle(statFile("502", "S 1 502"), 502));
    }
}
