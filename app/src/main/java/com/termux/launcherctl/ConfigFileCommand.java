package com.termux.launcherctl;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsFile;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@code launcherctl config export|apply}: the settings file over the API ({@link SettingsFile}).
 * Export answers the file as text in {@link ConfigCommand#TEXT}. Apply takes the file as
 * {@code "text"} and saves all of it or none ({@link SettingsFile#apply}, the same apply as
 * terl.conf): a line that cannot be read is a 400 with one {@code line N: ...} message per bad
 * line, a value refused while saving rolls everything back (a 409); otherwise every change is
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
        SettingsFile.Applied applied = SettingsFile.apply(request.optString("text"), registry);
        if (!applied.ok) {
            return new JSONObject().put("ok", false)
                .put("error", applied.beforeSaving ? "invalid_file" : "apply_failed")
                .put("message", String.join("\n", applied.errors))
                .put("errors", new JSONArray(applied.errors))
                .put("changed", 0) // all or nothing: a failed file leaves nothing changed
                .put("_statusCode", applied.beforeSaving ? 400 : 409);
        }
        int changed = 0;
        for (SettingsRegistry.Change change : applied.changes) {
            applier.apply(change);
            if (change.changed()) changed++;
        }
        return new JSONObject().put("ok", true).put("lines", applied.changes.size())
            .put("changed", changed);
    }
}
