package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * {@code POST /v1/panes/{id}/wait}: hold the request until a pane's recent text has a line holding
 * a literal string ({@code match}) or matching a regular expression ({@code regex}), so an agent
 * that typed a command into a pane of its own can wait for the output it expects instead of
 * sleeping and reading in a loop.
 *
 * <p>Waiting is polling: every {@link #POLL_MS} the pane's last lines are read through
 * {@code pane.read}, so the same ownership rule applies and the UI thread only ever does one
 * short read per poll, never the wait itself. The first read happens at once, so output already
 * on screen matches immediately. The server gives each wait a thread of its own, at most
 * {@link #MAX_CONCURRENT} at a time; see {@code LauncherCtlApiServer#startPaneWait}.
 *
 * <p>Answers: 200 {@code {ok, matched:true, id, text, running}} with the newest matching line;
 * 408 {@code timeout} when nothing matched in time (which does not mean the command did not run);
 * 409 {@code pane_not_running} when the shell exited, or the pane closed, before a match; 400 for
 * a malformed body; and whatever {@code pane.read} refused with (403 {@code not_owned}, 404
 * {@code pane_not_found}, ...).
 */
final class PaneWait {

    static final long DEFAULT_TIMEOUT_MS = 30_000L;
    /** The one ceiling on a wait; {@code launcherctl pane wait} checks the same number. */
    static final long MAX_TIMEOUT_MS = 300_000L;
    static final long POLL_MS = 250L;
    /** Waits running at once; one more is refused with 429 rather than queued. */
    static final int MAX_CONCURRENT = 4;

    /** One {@code pane.read}: the dispatcher's answer for these arguments. */
    interface Reader {
        @NonNull JSONObject read(@NonNull JSONObject arguments) throws JSONException;
    }

    interface Clock {
        long nowMs();

        void sleepMs(long ms) throws InterruptedException;
    }

    static final Clock SYSTEM_CLOCK = new Clock() {
        @Override public long nowMs() {
            return System.nanoTime() / 1_000_000L;
        }

        @Override public void sleepMs(long ms) throws InterruptedException {
            Thread.sleep(ms);
        }
    };

    @NonNull final String id;
    @Nullable private final String literal;
    @Nullable private final Pattern regex;
    final long timeoutMs;
    /** Lines to read per poll, or null for {@code pane.read}'s default. */
    @Nullable private final Integer lines;

    private PaneWait(@NonNull String id, @Nullable String literal, @Nullable Pattern regex,
                     long timeoutMs, @Nullable Integer lines) {
        this.id = id;
        this.literal = literal;
        this.regex = regex;
        this.timeoutMs = timeoutMs;
        this.lines = lines;
    }

    /** The wait {@code body} asks for on pane {@code id}, or null with the 400 left in {@code error[0]}. */
    @Nullable
    static PaneWait parse(@NonNull String id, @NonNull JSONObject body, @NonNull JSONObject[] error) {
        String literal = textField(body, "match");
        String expression = textField(body, "regex");
        if (literal != null && expression != null) {
            error[0] = error(400, "bad_request", "Give 'match' or 'regex', not both");
            return null;
        }
        if (literal == null && expression == null) {
            error[0] = error(400, "bad_request", "Missing 'match' (text to look for) or 'regex'");
            return null;
        }
        Pattern regex = null;
        if (expression != null) {
            try {
                regex = Pattern.compile(expression);
            } catch (PatternSyntaxException e) {
                error[0] = error(400, "bad_request",
                    "'regex' is not a valid regular expression: " + e.getDescription());
                return null;
            }
        }
        long timeoutMs = DEFAULT_TIMEOUT_MS;
        if (isGiven(body, "timeout_ms")) {
            Long parsed = wholeNumber(body.opt("timeout_ms"));
            if (parsed == null || parsed < 0 || parsed > MAX_TIMEOUT_MS) {
                error[0] = error(400, "bad_request",
                    "'timeout_ms' must be a whole number of milliseconds from 0 to " + MAX_TIMEOUT_MS);
                return null;
            }
            timeoutMs = parsed;
        }
        Integer lines = null;
        if (isGiven(body, "lines")) {
            Long parsed = wholeNumber(body.opt("lines"));
            if (parsed == null || parsed < 1) {
                error[0] = error(400, "bad_request", "'lines' must be a whole number, at least 1");
                return null;
            }
            lines = (int) Math.min(Integer.MAX_VALUE, parsed);
        }
        return new PaneWait(id, literal, regex, timeoutMs, lines);
    }

    /** Polls until a match, the deadline, the shell's end, or a refusal from {@code pane.read}. */
    @NonNull
    JSONObject run(@NonNull Reader reader, @NonNull Clock clock) throws JSONException {
        long deadline = clock.nowMs() + timeoutMs;
        boolean seen = false;
        boolean running = true;
        while (true) {
            JSONObject snapshot = reader.read(readArguments());
            if (snapshot.optBoolean("ok", false)) {
                seen = true;
                running = snapshot.optBoolean("running", true);
                String line = matchingLine(snapshot.optString("text", ""));
                if (line != null) return matched(line, running);
                if (!running) {
                    // The shell has ended: one more look, for output that landed with its exit.
                    JSONObject last = reader.read(readArguments());
                    String lastLine = last.optBoolean("ok", false)
                        ? matchingLine(last.optString("text", "")) : null;
                    if (lastLine != null) return matched(lastLine, false);
                    return error(409, "pane_not_running",
                        "The shell in that pane exited before the text appeared");
                }
            } else {
                int status = snapshot.optInt("_statusCode", 500);
                if (seen && status == 404) {
                    return error(409, "pane_not_running", "The pane closed before the text appeared");
                }
                // 503/504: the UI thread was busy for a moment, which is no answer; try again.
                if (status != 503 && status != 504) return snapshot;
            }
            long left = deadline - clock.nowMs();
            if (left <= 0) {
                JSONObject timedOut = error(408, "timeout", "The text did not appear within "
                    + timeoutMs + " ms; whatever runs in the pane may still be going");
                timedOut.put("matched", false);
                timedOut.put("running", running);
                return timedOut;
            }
            try {
                clock.sleepMs(Math.min(POLL_MS, left));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error(503, "interrupted", "The launcher stopped its local API while waiting");
            }
        }
    }

    /** The newest line of {@code text} that matches, or null. */
    @Nullable
    String matchingLine(@NonNull String text) {
        int end = text.length();
        while (end >= 0) {
            int start = text.lastIndexOf('\n', end - 1) + 1;
            String line = text.substring(start, end);
            if (literal != null ? line.contains(literal) : regex.matcher(line).find()) return line;
            if (start == 0) return null;
            end = start - 1;
        }
        return null;
    }

    @NonNull
    private JSONObject readArguments() throws JSONException {
        JSONObject arguments = new JSONObject().put("id", id);
        if (lines != null) arguments.put("lines", lines);
        return arguments;
    }

    @NonNull
    private JSONObject matched(@NonNull String line, boolean running) throws JSONException {
        return new JSONObject().put("ok", true).put("_statusCode", 200).put("matched", true)
            .put("id", id).put("text", line).put("running", running);
    }

    /** A non-empty string field, or null when it is absent, null or empty. */
    @Nullable
    private static String textField(@NonNull JSONObject body, @NonNull String key) {
        Object value = body.opt(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) return null;
        return (String) value;
    }

    private static boolean isGiven(@NonNull JSONObject body, @NonNull String key) {
        Object value = body.opt(key);
        return value != null && value != JSONObject.NULL
            && !(value instanceof String && ((String) value).trim().isEmpty());
    }

    /** A whole number from a JSON number or a numeric string; null for anything else. */
    @Nullable
    static Long wholeNumber(@Nullable Object raw) {
        double value;
        if (raw instanceof Number) {
            value = ((Number) raw).doubleValue();
        } else if (raw instanceof String) {
            try {
                value = Double.parseDouble(((String) raw).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value != Math.floor(value)
                || Math.abs(value) > 1e15) {
            return null;
        }
        return (long) value;
    }

    @NonNull
    static JSONObject error(int statusCode, @NonNull String code, @NonNull String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", false);
            json.put("error", code);
            json.put("message", message);
            json.put("_statusCode", statusCode);
        } catch (JSONException ignored) {
        }
        return json;
    }
}
