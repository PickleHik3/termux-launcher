package com.termux.launcherctl;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsFile;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code launcherctl config export|apply}: the settings file over the API ({@link SettingsFile}).
 * Export answers the file as text in {@link ConfigCommand#TEXT}. Apply takes the file as
 * {@code "text"}, checks every line first and saves nothing when any is wrong (a 400 with one
 * {@code line N: ...} message per bad line); otherwise each line is saved through the registry and
 * handed to the applier, so the screen refreshes as for {@code config set}.
 */
final class ConfigFileCommand {

    private ConfigFileCommand() {}

    @NonNull
    static JSONObject export(@NonNull SettingsRegistry registry) throws JSONException {
        return new JSONObject().put("ok", true).put(ConfigCommand.TEXT, SettingsFile.export(registry));
    }

    @NonNull
    static JSONObject apply(@NonNull SettingsRegistry registry, @NonNull SettingsApplier applier,
                            @NonNull JSONObject request) throws JSONException {
        if (!request.has("text") || request.isNull("text")) {
            return new JSONObject().put("ok", false).put("error", "bad_request")
                .put("message", "'text' is required").put("_statusCode", 400);
        }
        SettingsFile.Result result = SettingsFile.check(request.optString("text"), registry);
        if (!result.errors.isEmpty()) {
            return new JSONObject().put("ok", false).put("error", "invalid_file")
                .put("message", String.join("\n", result.errors))
                .put("errors", new JSONArray(result.errors)).put("_statusCode", 400);
        }
        int changed = 0;
        List<String> failed = new ArrayList<>();
        for (SettingsFile.Assignment each : result.assignments) {
            SettingsRegistry.Outcome outcome = registry.set(each.id, each.value);
            SettingsRegistry.Refusal refusal = outcome.refusal();
            if (refusal != null) {
                failed.add("line " + each.line + ": " + refusal.reason);
                continue;
            }
            SettingsRegistry.Change change = outcome.change();
            applier.apply(change);
            if (change.changed()) changed++;
        }
        if (!failed.isEmpty()) {
            return new JSONObject().put("ok", false).put("error", "apply_failed")
                .put("message", String.join("\n", failed)).put("errors", new JSONArray(failed))
                .put("changed", changed).put("_statusCode", 409);
        }
        return new JSONObject().put("ok", true).put("lines", result.assignments.size())
            .put("changed", changed);
    }
}
