package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
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
 * {@code Terminal=true} app, and picking a still-running pane back out of a pane.list result.
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

    @Test public void findRunningPaneIdMatchesTheTaggedRunningPaneOnly() throws JSONException {
        JSONObject list = new JSONObject()
            .put("windows", new JSONArray()
                .put(new JSONObject().put("panes", new JSONArray()
                    // Not tagged: the user's own shell.
                    .put(new JSONObject().put("id", "p0").put("running", true))
                    // Tagged for a different app.
                    .put(new JSONObject().put("id", "p1").put("running", true)
                        .put("agent", new JSONObject().put("tag", "linux-terminal-app:other")))
                    // Tagged for this app, but its shell already exited.
                    .put(new JSONObject().put("id", "p2").put("running", false)
                        .put("agent", new JSONObject().put("tag", "linux-terminal-app:htop")))
                    // The one that should match.
                    .put(new JSONObject().put("id", "p3").put("running", true)
                        .put("agent", new JSONObject().put("tag", "linux-terminal-app:htop"))))));

        assertEquals("p3", LinuxTerminalAppRunner.findRunningPaneId(list, "linux-terminal-app:htop"));
        assertNull(LinuxTerminalAppRunner.findRunningPaneId(list, "linux-terminal-app:nothing-open"));
    }

    @Test public void findRunningPaneIdIsNullWithNoWindows() throws JSONException {
        assertNull(LinuxTerminalAppRunner.findRunningPaneId(new JSONObject(), "any-tag"));
    }
}
