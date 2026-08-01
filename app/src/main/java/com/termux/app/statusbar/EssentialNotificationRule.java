package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * One rule that pins a matching notification into the widget slot. A rule matches on package and/or
 * a case-insensitive substring of the title or body; an empty field means "any". A rule with both
 * fields empty would pin everything and is rejected by {@link #isUsable()}.
 */
public final class EssentialNotificationRule {

    public final String id;
    public final String packageName;
    public final String match;
    /** Whether dismissing the pin should also cancel the source notification. */
    public final boolean clearOnDismiss;

    public EssentialNotificationRule(@NonNull String id, @Nullable String packageName,
                                     @Nullable String match, boolean clearOnDismiss) {
        this.id = id;
        this.packageName = packageName == null ? "" : packageName.trim();
        this.match = match == null ? "" : match.trim();
        this.clearOnDismiss = clearOnDismiss;
    }

    public boolean isUsable() {
        return !packageName.isEmpty() || !match.isEmpty();
    }

    public boolean matches(@Nullable String pkg, @Nullable String title, @Nullable String body) {
        if (!isUsable()) return false;
        if (!packageName.isEmpty() && !packageName.equalsIgnoreCase(pkg == null ? "" : pkg.trim())) {
            return false;
        }
        if (match.isEmpty()) return true;
        String needle = match.toLowerCase(Locale.ROOT);
        return contains(title, needle) || contains(body, needle);
    }

    private static boolean contains(@Nullable String haystack, @NonNull String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("package", packageName);
            json.put("match", match);
            json.put("clear", clearOnDismiss);
        } catch (JSONException ignored) {
        }
        return json;
    }

    @Nullable
    public static EssentialNotificationRule fromJson(@Nullable JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "");
        if (id.isEmpty()) return null;
        EssentialNotificationRule rule = new EssentialNotificationRule(id,
            json.optString("package", ""), json.optString("match", ""),
            json.optBoolean("clear", false));
        return rule.isUsable() ? rule : null;
    }
}
