package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the hooks that let an agent report to the launcher, on the explicit {@code launcherctl
 * agent install-hooks} (or the Save dialog's Set up resume) and never on its own: Claude Code's
 * five in {@code ~/.claude/settings.json} (status, and the session id), Codex's session hook in
 * {@code ~/.codex/hooks.json}, and an OpenCode plugin file of its own.
 *
 * <p>The merge is additive and idempotent: an existing file keeps every key and every hook it
 * already had, and running the command twice leaves the file exactly as the first run did.
 */
public final class ClaudeHooksInstaller {

    public static final String AGENT_CLAUDE = "claude";
    public static final String AGENT_CODEX = "codex";
    public static final String AGENT_OPENCODE = "opencode";

    /** The plugin's own file, so nothing the user wrote in the plugins folder is ever edited. */
    static final String OPENCODE_PLUGIN_FILE = "termux-launcher-session.js";

    /** Claude Code's hook events, mapped to the {@code launcherctl agent} words each one runs. */
    private static final Map<String, String> EVENT_STATES = buildEventStates();

    /** Codex's hook events; it has no status hooks here, only the session id. */
    private static final Map<String, String> CODEX_EVENTS = buildCodexEvents();

    private static Map<String, String> buildEventStates() {
        Map<String, String> events = new LinkedHashMap<>();
        // A submitted prompt starts a turn.
        events.put("UserPromptSubmit", "working");
        // Claude sends Notification for a permission prompt and for a prompt left unanswered, which
        // is exactly the case the user has to come back for.
        events.put("Notification", "blocked");
        // Stop ends the turn: the agent is back at its prompt with nothing to do.
        events.put("Stop", "idle");
        // The session is over, so the pane stops speaking for an agent at all.
        events.put("SessionEnd", "clear");
        // Start, resume, /clear and compaction each name the conversation the pane is in.
        events.put("SessionStart", "session --agent " + AGENT_CLAUDE);
        return events;
    }

    private static Map<String, String> buildCodexEvents() {
        Map<String, String> events = new LinkedHashMap<>();
        events.put("SessionStart", "session --agent " + AGENT_CODEX);
        return events;
    }

    private ClaudeHooksInstaller() {}

    /**
     * {@code settings} with the hooks merged in, pretty-printed. {@code settings} may be null, empty
     * or a file with hooks of its own; anything it already holds survives.
     *
     * @param launcherctlPath absolute path to the launcherctl wrapper, so a hook does not depend on
     *                        PATH being what the shell had.
     * @throws JSONException when the existing file is not a JSON object.
     */
    @NonNull
    public static String merge(@Nullable String settings, @NonNull String launcherctlPath)
            throws JSONException {
        return merge(settings, launcherctlPath, EVENT_STATES);
    }

    @NonNull
    private static String merge(@Nullable String settings, @NonNull String launcherctlPath,
                                @NonNull Map<String, String> events) throws JSONException {
        JSONObject root = settings == null || settings.trim().isEmpty()
            ? new JSONObject() : new JSONObject(settings);
        JSONObject hooks = root.optJSONObject("hooks");
        if (hooks == null) {
            hooks = new JSONObject();
            root.put("hooks", hooks);
        }
        for (Map.Entry<String, String> event : events.entrySet()) {
            String command = launcherctlPath + " agent " + event.getValue();
            JSONArray groups = hooks.optJSONArray(event.getKey());
            if (groups == null) {
                groups = new JSONArray();
                hooks.put(event.getKey(), groups);
            }
            if (hasCommand(groups, command)) continue;
            JSONArray entries = new JSONArray();
            entries.put(new JSONObject().put("type", "command").put("command", command));
            groups.put(new JSONObject().put("hooks", entries));
        }
        return root.toString(2);
    }

    /** How many of Claude Code's events {@code settings} already carries our hook for. */
    public static int installedCount(@Nullable String settings, @NonNull String launcherctlPath) {
        return installedCount(settings, launcherctlPath, EVENT_STATES);
    }

    private static int installedCount(@Nullable String settings, @NonNull String launcherctlPath,
                                      @NonNull Map<String, String> events) {
        if (settings == null || settings.trim().isEmpty()) return 0;
        try {
            JSONObject hooks = new JSONObject(settings).optJSONObject("hooks");
            if (hooks == null) return 0;
            int found = 0;
            for (Map.Entry<String, String> event : events.entrySet()) {
                JSONArray groups = hooks.optJSONArray(event.getKey());
                if (groups != null && hasCommand(groups, launcherctlPath + " agent " + event.getValue())) {
                    found++;
                }
            }
            return found;
        } catch (JSONException e) {
            return 0;
        }
    }

    /** The Claude Code events this installs, for the docs and the API response. */
    @NonNull
    public static Map<String, String> events() {
        return EVENT_STATES;
    }

    /** The agents this can set up, in the order the API reports them. */
    @NonNull
    public static String[] agents() {
        return new String[] {AGENT_CLAUDE, AGENT_CODEX, AGENT_OPENCODE};
    }

    /** Where {@code agent}'s hook or plugin lives under {@code home}, or null for another agent. */
    @Nullable
    public static File fileFor(@NonNull String agent, @NonNull File home) {
        switch (agent) {
            case AGENT_CLAUDE: return new File(home, ".claude/settings.json");
            case AGENT_CODEX: return new File(home, ".codex/hooks.json");
            case AGENT_OPENCODE: return new File(home, ".config/opencode/plugins/" + OPENCODE_PLUGIN_FILE);
            default: return null;
        }
    }

    /** Whether {@code agent}'s hook or plugin is already in place, by what the file holds. */
    public static boolean isInstalled(@NonNull String agent, @NonNull File home,
                                      @NonNull String launcherctlPath) {
        File file = fileFor(agent, home);
        if (file == null || !file.isFile()) return false;
        try {
            String text = read(file);
            switch (agent) {
                case AGENT_CLAUDE:
                    return installedCount(text, launcherctlPath, EVENT_STATES) == EVENT_STATES.size();
                case AGENT_CODEX:
                    return installedCount(text, launcherctlPath, CODEX_EVENTS) == CODEX_EVENTS.size();
                default:
                    return text.equals(openCodePlugin(launcherctlPath));
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Put {@code agent}'s hook or plugin in place under {@code home}. Returns true when a file
     * changed.
     *
     * @throws JSONException when the agent's existing JSON file is not a JSON object.
     */
    public static boolean installForAgent(@NonNull String agent, @NonNull File home,
                                          @NonNull String launcherctlPath)
            throws JSONException, IOException {
        File file = fileFor(agent, home);
        if (file == null) throw new IOException("Unknown agent " + agent);
        switch (agent) {
            case AGENT_CLAUDE: return installJson(file, launcherctlPath, EVENT_STATES);
            case AGENT_CODEX: return installJson(file, launcherctlPath, CODEX_EVENTS);
            default: return writeIfChanged(file, openCodePlugin(launcherctlPath));
        }
    }

    /**
     * Set up {@code only} (or every agent when null), one failure not stopping the rest. The
     * answer lists each agent's file and whether it changed; {@code ok} is false when any file
     * was left alone, and {@code notice} carries what the user still has to do.
     */
    @NonNull
    public static JSONObject installAll(@NonNull File home, @NonNull String launcherctlPath,
                                        @Nullable String only) throws JSONException {
        JSONArray results = new JSONArray();
        JSONArray notices = new JSONArray();
        boolean ok = true;
        for (String agent : agents()) {
            if (only != null && !only.equals(agent)) continue;
            File file = fileFor(agent, home);
            JSONObject result = new JSONObject().put("agent", agent).put("path", file.getAbsolutePath());
            try {
                boolean changed = installForAgent(agent, home, launcherctlPath);
                result.put("changed", changed);
                if (changed && AGENT_CODEX.equals(agent)) notices.put(CODEX_TRUST_NOTICE);
            } catch (JSONException e) {
                ok = false;
                result.put("error", "Not valid JSON, so it was left alone.");
            } catch (IOException e) {
                ok = false;
                result.put("error", "Could not write it: " + e.getMessage());
            }
            results.put(result);
        }
        return new JSONObject().put("ok", ok).put("agents", results).put("notice", notices);
    }

    /** Codex skips a hook it has not been told to trust; the user does that once, in Codex. */
    public static final String CODEX_TRUST_NOTICE =
        "Codex asks you to trust the new hook once: open Codex and run /hooks.";

    /**
     * Merge Claude Code's hooks into {@code settingsFile}, creating it and its directory when
     * missing. Returns true when the file changed.
     */
    public static boolean install(@NonNull File settingsFile, @NonNull String launcherctlPath)
            throws JSONException, IOException {
        return installJson(settingsFile, launcherctlPath, EVENT_STATES);
    }

    private static boolean installJson(@NonNull File settingsFile, @NonNull String launcherctlPath,
                                       @NonNull Map<String, String> events)
            throws JSONException, IOException {
        String existing = settingsFile.isFile() ? read(settingsFile) : null;
        // Compared by what is in the file, not by its text: a settings.json the user formatted
        // themselves must not be rewritten just because we would have printed it differently.
        if (existing != null && installedCount(existing, launcherctlPath, events) == events.size()) {
            return false;
        }
        return writeIfChanged(settingsFile, merge(existing, launcherctlPath, events));
    }

    /**
     * The OpenCode plugin: on {@code session.created} and {@code session.updated} it reports the
     * session id for the pane OpenCode runs in. Picking an older session inside OpenCode's own
     * screen raises neither event, so that switch is not followed.
     */
    @NonNull
    static String openCodePlugin(@NonNull String launcherctlPath) {
        return "// Written by `launcherctl agent install-hooks`: tells the Termux Launcher which\n"
            + "// OpenCode conversation this pane is in, so a saved session can resume it.\n"
            + "// Safe to delete.\n"
            + "import { execFile } from \"node:child_process\"\n"
            + "\n"
            + "const LAUNCHERCTL = " + JSONObject.quote(launcherctlPath) + "\n"
            + "\n"
            + "export const TermuxLauncherSession = async () => {\n"
            + "  const pane = process.env." + TermuxShellEnvironment.ENV_LAUNCHER_PANE + "\n"
            + "  let last = \"\"\n"
            + "  return {\n"
            + "    event: async ({ event }) => {\n"
            + "      if (!pane) return\n"
            + "      if (event.type !== \"session.created\" && event.type !== \"session.updated\") return\n"
            + "      const props = event.properties || {}\n"
            + "      const id = props.sessionID || (props.info && props.info.id)\n"
            + "      if (!id || id === last) return\n"
            + "      last = id\n"
            + "      execFile(LAUNCHERCTL, [\"agent\", \"session\", \"--agent\", \"opencode\", \"--id\", id], () => {})\n"
            + "    },\n"
            + "  }\n"
            + "}\n";
    }

    /** Writes {@code text} when the file differs, through a sibling temp file. */
    private static boolean writeIfChanged(@NonNull File file, @NonNull String text)
            throws IOException {
        if (file.isFile() && read(file).equals(text)) return false;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        // Written through a sibling temp file so a settings.json Claude is reading is never
        // half-replaced.
        File temp = new File(file.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!temp.renameTo(file)) {
            temp.delete();
            throw new IOException("Could not replace " + file.getAbsolutePath());
        }
        return true;
    }

    private static boolean hasCommand(@NonNull JSONArray groups, @NonNull String command) {
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            JSONArray entries = group.optJSONArray("hooks");
            if (entries == null) continue;
            for (int j = 0; j < entries.length(); j++) {
                JSONObject entry = entries.optJSONObject(j);
                if (entry != null && command.equals(entry.optString("command"))) return true;
            }
        }
        return false;
    }

    @NonNull
    private static String read(@NonNull File file) throws IOException {
        byte[] bytes = new byte[(int) Math.min(file.length(), 1 << 20)];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int read = 0;
            while (read < bytes.length) {
                int step = in.read(bytes, read, bytes.length - read);
                if (step < 0) break;
                read += step;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }
}
