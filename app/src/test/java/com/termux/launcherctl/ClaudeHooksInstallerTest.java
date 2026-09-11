package com.termux.launcherctl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** org.json is stubbed on the JVM, so these run under Robolectric for the real implementation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P)
public class ClaudeHooksInstallerTest {

    private static final String CTL = "/data/data/com.termux/files/usr/bin/launcherctl";

    @Test
    public void mergeIntoNothingInstallsTheFourEvents() throws Exception {
        String merged = ClaudeHooksInstaller.merge(null, CTL);
        JSONObject hooks = new JSONObject(merged).getJSONObject("hooks");
        assertEquals(4, ClaudeHooksInstaller.events().size());
        assertEquals(CTL + " agent working", command(hooks, "UserPromptSubmit"));
        assertEquals(CTL + " agent blocked", command(hooks, "Notification"));
        assertEquals(CTL + " agent idle", command(hooks, "Stop"));
        assertEquals(CTL + " agent clear", command(hooks, "SessionEnd"));
        assertEquals(4, ClaudeHooksInstaller.installedCount(merged, CTL));
    }

    @Test
    public void mergeIsIdempotentAndPreservesEverythingAlreadyThere() throws Exception {
        String existing = "{\"model\":\"opus\","
            + "\"permissions\":{\"allow\":[\"Bash(ls:*)\"]},"
            + "\"hooks\":{\"Stop\":[{\"hooks\":[{\"type\":\"command\",\"command\":\"say done\"}]}],"
            + "\"PreToolUse\":[{\"matcher\":\"Bash\",\"hooks\":[{\"type\":\"command\",\"command\":\"audit\"}]}]}}";

        String once = ClaudeHooksInstaller.merge(existing, CTL);
        String twice = ClaudeHooksInstaller.merge(once, CTL);
        assertEquals(once, twice);

        JSONObject root = new JSONObject(twice);
        assertEquals("opus", root.getString("model"));
        assertEquals("Bash(ls:*)",
            root.getJSONObject("permissions").getJSONArray("allow").getString(0));
        JSONObject hooks = root.getJSONObject("hooks");
        // The user's own PreToolUse hook is untouched, and their Stop hook still runs beside ours.
        assertEquals("audit", command(hooks, "PreToolUse"));
        JSONArray stop = hooks.getJSONArray("Stop");
        assertEquals(2, stop.length());
        assertEquals("say done", stop.getJSONObject(0).getJSONArray("hooks")
            .getJSONObject(0).getString("command"));
        assertEquals(CTL + " agent idle", stop.getJSONObject(1).getJSONArray("hooks")
            .getJSONObject(0).getString("command"));
        assertEquals(4, ClaudeHooksInstaller.installedCount(twice, CTL));
    }

    @Test
    public void installCreatesTheFileOnceAndThenReportsNoChange() throws Exception {
        File directory = Files.createTempDirectory("claude-hooks").toFile();
        File settings = new File(new File(directory, ".claude"), "settings.json");

        assertTrue(ClaudeHooksInstaller.install(settings, CTL));
        assertTrue(settings.isFile());
        String written = new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8);
        assertEquals(4, ClaudeHooksInstaller.installedCount(written, CTL));

        // Running it again leaves the file exactly as it was.
        assertFalse(ClaudeHooksInstaller.install(settings, CTL));
        assertEquals(written,
            new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void installedCountIgnoresAFileWithNoHooksOrNoJson() {
        assertEquals(0, ClaudeHooksInstaller.installedCount(null, CTL));
        assertEquals(0, ClaudeHooksInstaller.installedCount("{\"model\":\"opus\"}", CTL));
        assertEquals(0, ClaudeHooksInstaller.installedCount("not json at all", CTL));
    }

    private static String command(JSONObject hooks, String event) throws Exception {
        return hooks.getJSONArray(event).getJSONObject(hooks.getJSONArray(event).length() - 1)
            .getJSONArray("hooks").getJSONObject(0).getString("command");
    }
}
