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
 * {@code Terminal=true} app, picking an already-open pane back out of a pane.list result, and the
 * focus-vs-rerun rule that reads {@link LinuxTerminalAppRunner.ForegroundState}.
 * {@link LinuxTerminalAppRunner#run} itself needs a live {@code TerminalActionDispatcher} host
 * and is exercised through the device gate instead (per the phase's SPEC), not here.
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

    // --- the idle-shell fix: focus only when confirmed still running, otherwise re-run ---------

    @Test public void aConfirmedRunningPaneIsFocusedNotRerun() {
        assertTrue("idle=false (something other than the shell owns the foreground) means focus",
            LinuxTerminalAppRunner.shouldFocusRatherThanRerun(Boolean.FALSE));
    }

    @Test public void aConfirmedIdlePaneIsReRunNotFocused() {
        // This is the bug the coordinator caught: the app already exited and the pane is sitting
        // at a bare prompt (idle=true) — focusing it silently "does nothing" from the user's side.
        assertFalse("idle=true (only the shell is in the foreground) means re-run",
            LinuxTerminalAppRunner.shouldFocusRatherThanRerun(Boolean.TRUE));
    }

    @Test public void anUnknownReadingIsTreatedAsReRunNeverAsConfirmedRunning() {
        // No privileged backend, or the terminal place has not been on screen recently: unknown
        // must never be upgraded to "running", or the same bug comes back for every install
        // without Shizuku/su configured.
        assertFalse("unknown is not confirmed running",
            LinuxTerminalAppRunner.shouldFocusRatherThanRerun(null));
    }
}
