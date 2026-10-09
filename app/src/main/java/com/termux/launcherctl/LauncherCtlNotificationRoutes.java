package com.termux.launcherctl;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The notification history routes {@code launcherctl notifications ...} sits on:
 * {@code GET /v1/notifications}, {@code GET /v1/notifications/apps},
 * {@code GET /v1/notifications/active} and {@code POST /v1/notifications/clear}.
 *
 * <p>The server's token check and per-route rate limit have already run by the time {@link #handle}
 * is called. Every answer carries a {@code hint} when there is nothing to read for a reason the
 * caller can fix: notification access not granted, or no app enabled for history. Reads answer
 * JSON, or with {@code format=text} one line per message, {@code time · app · title — text}, for
 * an agent or a person to read without a JSON parser.
 */
final class LauncherCtlNotificationRoutes {
    static final String NO_APP_HINT =
        "No app is enabled for notification history. Choose apps in the launcher's settings "
            + "(notification history); nothing is recorded until at least one is chosen.";

    /** A route answer: the JSON body, and the plain-text form when the caller asked for it. */
    static final class Result {
        final JSONObject json;
        @Nullable final String text;

        Result(@NonNull JSONObject json, @Nullable String text) {
            this.json = json;
            this.text = text;
        }
    }

    private LauncherCtlNotificationRoutes() {
    }

    static boolean handles(@NonNull String method, @NonNull String path) {
        switch (path) {
            case "/v1/notifications":
            case "/v1/notifications/apps":
            case "/v1/notifications/active":
                return "GET".equals(method);
            case "/v1/notifications/clear":
                return "POST".equals(method);
            default:
                return false;
        }
    }

    @NonNull
    static Result handle(@NonNull Context context, @NonNull String method, @NonNull String path,
                         @NonNull Map<String, String> params, @Nullable String body) throws JSONException {
        long now = System.currentTimeMillis();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        Set<String> enabled = preferences == null ? new HashSet<>() : preferences.getNotificationHistoryPackages();
        boolean maskCodes = preferences == null || preferences.isNotificationHistoryMaskCodesEnabled();
        boolean text = "text".equalsIgnoreCase(params.get("format"));
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();

        JSONObject out = new JSONObject();
        out.put("ok", true);
        String hint = hintFor(enabled);

        switch (path) {
            case "/v1/notifications": {
                LauncherCtlNotificationStore.Filter filter;
                try {
                    filter = parseFilter(params, now, ZoneId.systemDefault());
                } catch (IllegalArgumentException e) {
                    return new Result(badRequest(e.getMessage()), null);
                }
                List<LauncherCtlNotificationEvent> rows = store.query(filter);
                out.put("count", rows.size());
                out.put("notifications", rowsJson(rows));
                putHint(out, hint);
                return new Result(out, text ? rowsText(rows, hint) : null);
            }
            case "/v1/notifications/apps": {
                JSONArray apps = store.queryApps();
                out.put("count", apps.length());
                out.put("apps", apps);
                putHint(out, hint);
                return new Result(out, text ? appsText(apps, hint) : null);
            }
            case "/v1/notifications/active": {
                List<LauncherCtlNotificationEvent> rows =
                    LauncherCtlNotificationListener.captureActive(enabled, maskCodes);
                if (rows == null) {
                    rows = new java.util.ArrayList<>();
                    hint = join(hint, LauncherCtlNotificationListener.getListenerHint());
                }
                out.put("count", rows.size());
                out.put("notifications", rowsJson(rows));
                putHint(out, hint);
                return new Result(out, text ? rowsText(rows, hint) : null);
            }
            case "/v1/notifications/clear": {
                String app = params.get("app");
                if (body != null && !body.trim().isEmpty()) {
                    try {
                        JSONObject request = new JSONObject(body);
                        if (request.has("app") && !request.isNull("app")) app = request.getString("app");
                    } catch (JSONException e) {
                        return new Result(badRequest("Request body must be a JSON object"), null);
                    }
                }
                int removed = store.clear(app);
                out.put("removed", removed);
                if (app != null && !app.trim().isEmpty()) out.put("app", app.trim());
                return new Result(out, null);
            }
            default:
                return new Result(badRequest("Unknown endpoint"), null);
        }
    }

    /**
     * Reads {@code app}, {@code since}, {@code until}, {@code query} and {@code limit}. Throws
     * {@link IllegalArgumentException} with a message fit for the caller on a value it cannot read.
     */
    @NonNull
    static LauncherCtlNotificationStore.Filter parseFilter(@NonNull Map<String, String> params,
                                                           long nowMs, @NonNull ZoneId zone) {
        LauncherCtlNotificationStore.Filter filter = new LauncherCtlNotificationStore.Filter();
        filter.app = blankToNull(params.get("app"));
        filter.query = blankToNull(params.get("query"));
        String since = blankToNull(params.get("since"));
        String until = blankToNull(params.get("until"));
        if (since != null) filter.sinceMs = parseTime(since, nowMs, zone, false);
        if (until != null) filter.untilMs = parseTime(until, nowMs, zone, true);
        if (filter.sinceMs > 0 && filter.untilMs > 0 && filter.untilMs < filter.sinceMs) {
            throw new IllegalArgumentException("--until is before --since");
        }
        String limit = blankToNull(params.get("limit"));
        if (limit != null) {
            try {
                int value = Integer.parseInt(limit.trim());
                if (value < 1) throw new NumberFormatException();
                filter.limit = Math.min(value, LauncherCtlNotificationStore.MAX_LIMIT);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("limit must be a whole number of 1 or more, got: " + limit);
            }
        }
        return filter;
    }

    /**
     * A moment as a relative age ({@code 90m}, {@code 12h}, {@code 7d}, {@code 2w}, counted back
     * from now), epoch milliseconds, or ISO-8601 (a date, a local date-time, or one with an
     * offset). A bare date means its start, or for an upper bound its last instant, so
     * {@code --until 2026-09-28} includes that whole day.
     */
    static long parseTime(@NonNull String value, long nowMs, @NonNull ZoneId zone, boolean endOfDay) {
        String trimmed = value.trim();
        if (trimmed.matches("\\d+[mhdw]")) {
            long amount = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
            TimeUnit unit;
            switch (trimmed.charAt(trimmed.length() - 1)) {
                case 'm': unit = TimeUnit.MINUTES; break;
                case 'h': unit = TimeUnit.HOURS; break;
                case 'd': unit = TimeUnit.DAYS; break;
                default: unit = TimeUnit.DAYS; amount *= 7; break;
            }
            return Math.max(1, nowMs - unit.toMillis(amount));
        }
        if (trimmed.matches("\\d{11,}")) return Long.parseLong(trimmed);
        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            return endOfDay
                ? date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                : date.atStartOfDay(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException(
            "Cannot read time \"" + value + "\"; use 7d, 12h, 30m, 2w or an ISO date such as 2026-09-28");
    }

    @Nullable
    private static String hintFor(@NonNull Set<String> enabled) {
        String hint = null;
        if (!LauncherCtlNotificationListener.isListenerConnected()) {
            hint = LauncherCtlNotificationListener.getListenerHint();
        }
        if (enabled.isEmpty()) hint = join(hint, NO_APP_HINT);
        return hint;
    }

    @Nullable
    private static String join(@Nullable String first, @Nullable String second) {
        if (first == null || first.isEmpty()) return second;
        if (second == null || second.isEmpty() || first.contains(second)) return first;
        return first + " " + second;
    }

    private static void putHint(@NonNull JSONObject out, @Nullable String hint) throws JSONException {
        if (hint != null && !hint.isEmpty()) out.put("hint", hint);
    }

    @NonNull
    private static JSONArray rowsJson(@NonNull List<LauncherCtlNotificationEvent> rows) throws JSONException {
        JSONArray array = new JSONArray();
        for (LauncherCtlNotificationEvent row : rows) array.put(row.toJson());
        return array;
    }

    @NonNull
    static String rowsText(@NonNull List<LauncherCtlNotificationEvent> rows, @Nullable String hint) {
        StringBuilder text = new StringBuilder();
        if (hint != null && !hint.isEmpty()) text.append("# ").append(hint).append('\n');
        if (rows.isEmpty()) text.append("(no notifications)\n");
        for (LauncherCtlNotificationEvent row : rows) text.append(row.toLine()).append('\n');
        return text.toString();
    }

    @NonNull
    static String appsText(@NonNull JSONArray apps, @Nullable String hint) {
        StringBuilder text = new StringBuilder();
        if (hint != null && !hint.isEmpty()) text.append("# ").append(hint).append('\n');
        if (apps.length() == 0) text.append("(no apps recorded)\n");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            String seen = format.format(Instant.ofEpochMilli(app.optLong("lastSeen")).atZone(ZoneId.systemDefault()));
            text.append(app.optString("app")).append(" · ").append(app.optString("package"))
                .append(" · ").append(app.optLong("count")).append(" messages")
                .append(" · last ").append(seen).append('\n');
        }
        return text.toString();
    }

    @NonNull
    private static JSONObject badRequest(@Nullable String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", "bad_request");
            error.put("message", message == null ? "" : message);
            error.put("_statusCode", 400);
        } catch (JSONException ignored) {
        }
        return error;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
