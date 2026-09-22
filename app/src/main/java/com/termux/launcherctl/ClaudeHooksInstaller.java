package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * Writes the four Claude Code hooks that let an agent report its own status into the user's
 * {@code ~/.claude/settings.json}, on the explicit {@code launcherctl agent install-hooks} and never
 * on its own.
 *
 * <p>The merge is additive and idempotent: an existing settings file keeps every key and every hook
 * it already had, and running the command twice leaves the file exactly as the first run did.
 */
public final class ClaudeHooksInstaller {

    /** Claude Code's hook events, mapped to the state each one means. */
    private static final Map<String, String> EVENT_STATES = buildEventStates();

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
        JSONObject root = settings == null || settings.trim().isEmpty()
            ? new JSONObject() : new JSONObject(settings);
        JSONObject hooks = root.optJSONObject("hooks");
        if (hooks == null) {
            hooks = new JSONObject();
            root.put("hooks", hooks);
        }
        for (Map.Entry<String, String> event : EVENT_STATES.entrySet()) {
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

    /** How many of the four events {@code settings} already carries our hook for. */
    public static int installedCount(@Nullable String settings, @NonNull String launcherctlPath) {
        if (settings == null || settings.trim().isEmpty()) return 0;
        try {
            JSONObject hooks = new JSONObject(settings).optJSONObject("hooks");
            if (hooks == null) return 0;
            int found = 0;
            for (Map.Entry<String, String> event : EVENT_STATES.entrySet()) {
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

    /** The events this installs, for the docs and the API response. */
    @NonNull
    public static Map<String, String> events() {
        return EVENT_STATES;
    }

    /**
     * Merge the hooks into {@code settingsFile}, creating it and its directory when missing.
     * Returns true when the file changed.
     */
    public static boolean install(@NonNull File settingsFile, @NonNull String launcherctlPath)
            throws JSONException, IOException {
        String existing = settingsFile.isFile() ? read(settingsFile) : null;
        // Compared by what is in the file, not by its text: a settings.json the user formatted
        // themselves must not be rewritten just because we would have printed it differently.
        if (existing != null && installedCount(existing, launcherctlPath) == EVENT_STATES.size()) {
            return false;
        }
        String merged = merge(existing, launcherctlPath);
        File parent = settingsFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        // Written through a sibling temp file so a settings.json Claude is reading is never
        // half-replaced.
        File temp = new File(settingsFile.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(merged.getBytes(StandardCharsets.UTF_8));
        }
        if (!temp.renameTo(settingsFile)) {
            temp.delete();
            throw new IOException("Could not replace " + settingsFile.getAbsolutePath());
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
