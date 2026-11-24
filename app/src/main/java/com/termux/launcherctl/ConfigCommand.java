package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@code launcherctl config get|set|list}: the Settings registry over the API. Pure over a
 * registry and its applier, so the grammar is tested without a device. A {@code set} saves through
 * the registry and hands the Change to the applier, so the screen refreshes the way a Settings row
 * does; a bad value is a 400 naming the allowed range, the same as {@code layout}.
 *
 * <p>Request: {@code {"op":"get|set|list","id":"terminal.contrast","value":"harder"}};
 * {@code list} with {@code "format":"text"} answers {@code id = value} lines in {@link #TEXT}.</p>
 */
final class ConfigCommand {

    /** The key of a text answer the server sends as text/plain instead of JSON. */
    static final String TEXT = "_text";

    private ConfigCommand() {}

    @NonNull
    static JSONObject handle(@NonNull SettingsRegistry registry, @NonNull SettingsApplier applier,
                             @NonNull JSONObject request) throws JSONException {
        String op = request.optString("op", "");
        String id = request.optString("id", "").trim();
        switch (op) {
            case "get": {
                String value = registry.get(id);
                if (value == null) return unknown(id);
                return ok().put("id", id).put("value", value);
            }
            case "set": {
                if (registry.declaration(id) == null) return unknown(id);
                if (!request.has("value") || request.isNull("value"))
                    return error("bad_request", "'value' is required", null);
                SettingsRegistry.Outcome outcome = registry.set(id, request.optString("value"));
                SettingsRegistry.Refusal refusal = outcome.refusal();
                if (refusal != null) return error("invalid_value", refusal.reason, refusal.allowed);
                SettingsRegistry.Change change = outcome.change();
                applier.apply(change);
                return ok().put("id", id).put("old", change.oldValue).put("value", change.newValue);
            }
            case "list":
                return "text".equals(request.optString("format", "")) ? listText(registry)
                    : list(registry);
            default:
                return error("bad_request", "'op' must be get, set or list", null);
        }
    }

    @NonNull
    private static JSONObject list(@NonNull SettingsRegistry registry) throws JSONException {
        JSONArray settings = new JSONArray();
        for (SettingsRegistry.Entry entry : registry.list()) {
            SettingDeclaration declaration = entry.declaration;
            JSONObject setting = new JSONObject().put("id", declaration.id)
                .put("value", entry.value).put("default", declaration.defaultValue());
            if (declaration.kind == SettingDeclaration.Kind.INTEGER) {
                setting.put("kind", "integer").put("min", declaration.min)
                    .put("max", declaration.max).put("step", declaration.step);
            } else if (declaration.kind == SettingDeclaration.Kind.TEXT) {
                setting.put("kind", "text");
            } else {
                setting.put("kind", "choice").put("choices", new JSONArray(declaration.choices))
                    .put("ordered", declaration.ordered);
            }
            settings.put(setting);
        }
        return ok().put("settings", settings);
    }

    @NonNull
    private static JSONObject listText(@NonNull SettingsRegistry registry) throws JSONException {
        StringBuilder text = new StringBuilder();
        for (SettingsRegistry.Entry entry : registry.list()) {
            if (text.length() > 0) text.append('\n');
            text.append(entry.declaration.id).append(" = ").append(entry.value);
        }
        return ok().put(TEXT, text.toString());
    }

    @NonNull
    private static JSONObject unknown(@NonNull String id) throws JSONException {
        return error("unknown_setting", "no setting is called \"" + id
            + "\"; launcherctl config list shows them all", null);
    }

    @NonNull
    private static JSONObject ok() throws JSONException {
        return new JSONObject().put("ok", true);
    }

    @NonNull
    private static JSONObject error(@NonNull String code, @NonNull String message,
                                    @Nullable String allowed) throws JSONException {
        JSONObject error = new JSONObject().put("ok", false).put("error", code)
            .put("message", message).put("_statusCode", 400);
        if (allowed != null) error.put("allowed", allowed);
        return error;
    }
}
