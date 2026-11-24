package com.termux.launcherctl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** org.json is stubbed on the JVM, so these run under Robolectric for the real implementation. */
public class ClaudeHooksInstallerTest {

    private static final String CTL = "/data/data/com.termux/files/usr/bin/launcherctl";

    @Test
    public void mergeIntoNothingInstallsEveryEvent() throws Exception {
        String merged = ClaudeHooksInstaller.merge(null, CTL);
        JSONObject hooks = new JSONObject(merged).getJSONObject("hooks");
        assertEquals(5, ClaudeHooksInstaller.events().size());
        assertEquals(CTL + " agent session --agent claude", command(hooks, "SessionStart"));
        assertEquals(CTL + " agent working", command(hooks, "UserPromptSubmit"));
        assertEquals(CTL + " agent blocked", command(hooks, "Notification"));
        assertEquals(CTL + " agent idle", command(hooks, "Stop"));
        assertEquals(CTL + " agent clear", command(hooks, "SessionEnd"));
        assertEquals(5, ClaudeHooksInstaller.installedCount(merged, CTL));
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
        assertEquals(5, ClaudeHooksInstaller.installedCount(twice, CTL));
    }

    @Test
    public void installCreatesTheFileOnceAndThenReportsNoChange() throws Exception {
        File directory = Files.createTempDirectory("claude-hooks").toFile();
        File settings = new File(new File(directory, ".claude"), "settings.json");

        assertTrue(ClaudeHooksInstaller.install(settings, CTL));
        assertTrue(settings.isFile());
        String written = new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8);
        assertEquals(5, ClaudeHooksInstaller.installedCount(written, CTL));

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

    @Test
    public void codexHooksAreAdditiveAndInstallingTwiceChangesNothing() throws Exception {
        File home = Files.createTempDirectory("codex-hooks").toFile();
        File hooks = new File(new File(home, ".codex"), "hooks.json");
        hooks.getParentFile().mkdirs();
        Files.write(hooks.toPath(), ("{\"note\":1,\"hooks\":{\"SessionStart\":[{\"hooks\":["
            + "{\"type\":\"command\",\"command\":\"mine\"}]}]}}").getBytes(StandardCharsets.UTF_8));

        assertFalse(ClaudeHooksInstaller.isInstalled("codex", home, CTL));
        assertTrue(ClaudeHooksInstaller.installForAgent("codex", home, CTL));
        String once = new String(Files.readAllBytes(hooks.toPath()), StandardCharsets.UTF_8);
        assertFalse(ClaudeHooksInstaller.installForAgent("codex", home, CTL));
        assertEquals(once, new String(Files.readAllBytes(hooks.toPath()), StandardCharsets.UTF_8));
        assertTrue(ClaudeHooksInstaller.isInstalled("codex", home, CTL));

        JSONObject root = new JSONObject(once);
        assertEquals(1, root.getInt("note"));
        JSONArray start = root.getJSONObject("hooks").getJSONArray("SessionStart");
        assertEquals(2, start.length());
        assertEquals("mine", start.getJSONObject(0).getJSONArray("hooks")
            .getJSONObject(0).getString("command"));
        assertEquals(CTL + " agent session --agent codex", start.getJSONObject(1)
            .getJSONArray("hooks").getJSONObject(0).getString("command"));
    }

    @Test
    public void claudeSettingsInstallByAgentKeepsUserHooksAndIsIdempotent() throws Exception {
        File home = Files.createTempDirectory("claude-home").toFile();
        File settings = new File(new File(home, ".claude"), "settings.json");
        settings.getParentFile().mkdirs();
        Files.write(settings.toPath(), ("{\"hooks\":{\"SessionStart\":[{\"hooks\":["
            + "{\"type\":\"command\",\"command\":\"mine\"}]}]}}").getBytes(StandardCharsets.UTF_8));

        assertFalse(ClaudeHooksInstaller.isInstalled("claude", home, CTL));
        assertTrue(ClaudeHooksInstaller.installForAgent("claude", home, CTL));
        String once = new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8);
        assertFalse(ClaudeHooksInstaller.installForAgent("claude", home, CTL));
        assertEquals(once, new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8));
        assertTrue(ClaudeHooksInstaller.isInstalled("claude", home, CTL));
        JSONArray start = new JSONObject(once).getJSONObject("hooks").getJSONArray("SessionStart");
        assertEquals("mine", start.getJSONObject(0).getJSONArray("hooks")
            .getJSONObject(0).getString("command"));
        assertEquals(2, start.length());
    }

    @Test
    public void theOpenCodePluginIsItsOwnFileWrittenOnceAndLeavesOthersAlone() throws Exception {
        File home = Files.createTempDirectory("opencode-home").toFile();
        File plugins = new File(home, ".config/opencode/plugins");
        plugins.mkdirs();
        File other = new File(plugins, "mine.js");
        Files.write(other.toPath(), "export const Mine = async () => ({})".getBytes(StandardCharsets.UTF_8));

        assertFalse(ClaudeHooksInstaller.isInstalled("opencode", home, CTL));
        assertTrue(ClaudeHooksInstaller.installForAgent("opencode", home, CTL));
        File plugin = ClaudeHooksInstaller.fileFor("opencode", home);
        assertEquals(plugins, plugin.getParentFile());
        String once = new String(Files.readAllBytes(plugin.toPath()), StandardCharsets.UTF_8);
        assertFalse(ClaudeHooksInstaller.installForAgent("opencode", home, CTL));
        assertEquals(once, new String(Files.readAllBytes(plugin.toPath()), StandardCharsets.UTF_8));
        assertTrue(ClaudeHooksInstaller.isInstalled("opencode", home, CTL));
        assertTrue(once.contains(CTL));
        assertTrue(once.contains("session.created"));
        assertTrue(once.contains("session.updated"));
        assertTrue(once.contains("sessionID"));
        assertEquals("export const Mine = async () => ({})",
            new String(Files.readAllBytes(other.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void installAllTwiceLeavesEveryFileAsTheFirstRunLeftItAndKeepsUserHooks() throws Exception {
        File home = Files.createTempDirectory("all-agents").toFile();
        String userHook = "{\"hooks\":{\"SessionStart\":[{\"hooks\":["
            + "{\"type\":\"command\",\"command\":\"mine\"}]}]}}";
        File claude = ClaudeHooksInstaller.fileFor("claude", home);
        File codex = ClaudeHooksInstaller.fileFor("codex", home);
        File opencode = ClaudeHooksInstaller.fileFor("opencode", home);
        File otherPlugin = new File(opencode.getParentFile(), "mine.js");
        for (File file : new File[] {claude, codex, otherPlugin}) {
            file.getParentFile().mkdirs();
        }
        Files.write(claude.toPath(), userHook.getBytes(StandardCharsets.UTF_8));
        Files.write(codex.toPath(), userHook.getBytes(StandardCharsets.UTF_8));
        Files.write(otherPlugin.toPath(), "export const Mine = async () => ({})"
            .getBytes(StandardCharsets.UTF_8));

        assertTrue(ClaudeHooksInstaller.installAll(home, CTL, null).getBoolean("ok"));
        byte[] claudeOnce = Files.readAllBytes(claude.toPath());
        byte[] codexOnce = Files.readAllBytes(codex.toPath());
        byte[] opencodeOnce = Files.readAllBytes(opencode.toPath());

        assertTrue(ClaudeHooksInstaller.installAll(home, CTL, null).getBoolean("ok"));
        assertTrue(java.util.Arrays.equals(claudeOnce, Files.readAllBytes(claude.toPath())));
        assertTrue(java.util.Arrays.equals(codexOnce, Files.readAllBytes(codex.toPath())));
        assertTrue(java.util.Arrays.equals(opencodeOnce, Files.readAllBytes(opencode.toPath())));

        for (File file : new File[] {claude, codex}) {
            JSONArray start = new JSONObject(new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8)).getJSONObject("hooks").getJSONArray("SessionStart");
            assertEquals(file.getName(), "mine", start.getJSONObject(0).getJSONArray("hooks")
                .getJSONObject(0).getString("command"));
        }
        assertEquals("export const Mine = async () => ({})",
            new String(Files.readAllBytes(otherPlugin.toPath()), StandardCharsets.UTF_8));
    }

    private static String command(JSONObject hooks, String event) throws Exception {
        return hooks.getJSONArray(event).getJSONObject(hooks.getJSONArray(event).length() - 1)
            .getJSONArray("hooks").getJSONObject(0).getString("command");
    }
}
