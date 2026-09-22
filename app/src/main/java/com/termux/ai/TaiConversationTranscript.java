package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * Protocol-neutral fingerprints of a chat transcript, used to decide whether a stateless
 * OpenAI-style request continues the conversation a runtime already holds in its KV cache.
 *
 * <p>Each non-system message becomes one string built from its role, its text content, and its
 * tool calls as {@code name(canonical-arguments)}. Client-generated noise that does not change the
 * prompt — tool-call ids, JSON key order, surrounding whitespace, {@code refusal: null} and other
 * extra fields — is left out, so the assistant message a client echoes back matches the one the
 * runtime produced.
 */
public final class TaiConversationTranscript {
    private static final char FIELD = '\u001f';
    private static final char CALL = '\u001e';

    private TaiConversationTranscript() {
    }

    /** Fingerprints of every user, assistant and tool message in order; system messages are skipped. */
    @NonNull
    public static List<String> fingerprints(@Nullable JSONArray messages) {
        if (messages == null) return Collections.emptyList();
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role", "user");
            if ("system".equals(role) || "developer".equals(role)) continue;
            result.add(fingerprint(role, message.opt("content"), message.optJSONArray("tool_calls")));
        }
        return result;
    }

    /** Fingerprint of the reply a runtime just produced, shaped like the message a client will echo. */
    @NonNull
    public static String assistantFingerprint(@Nullable String content, @Nullable JSONArray toolCalls) {
        return fingerprint("assistant", content, toolCalls);
    }

    /**
     * True when {@code request} is exactly {@code held} plus one trailing message — the only shape
     * a runtime can serve by sending that one message into the conversation it already holds.
     * A {@code null} {@code held} means the runtime's conversation was not built from a
     * transcript and can never be continued this way.
     */
    public static boolean continuesFrom(@Nullable List<String> held, @NonNull List<String> request) {
        if (held == null || request.size() != held.size() + 1) return false;
        for (int i = 0; i < held.size(); i++) {
            if (!held.get(i).equals(request.get(i))) return false;
        }
        return true;
    }

    @NonNull
    public static List<String> extend(@NonNull List<String> transcript, @NonNull String next) {
        ArrayList<String> result = new ArrayList<>(transcript.size() + 1);
        result.addAll(transcript);
        result.add(next);
        return Collections.unmodifiableList(result);
    }

    @NonNull
    private static String fingerprint(@NonNull String role, @Nullable Object content, @Nullable JSONArray toolCalls) {
        StringBuilder builder = new StringBuilder(role).append(FIELD).append(contentText(content));
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject call = toolCalls.optJSONObject(i);
                JSONObject function = call == null ? null : call.optJSONObject("function");
                if (function == null) continue;
                builder.append(CALL).append(function.optString("name", ""))
                    .append('(').append(canonicalArguments(function.opt("arguments"))).append(')');
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String contentText(@Nullable Object content) {
        if (content == null || JSONObject.NULL.equals(content)) return "";
        if (content instanceof JSONArray) {
            JSONArray parts = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                Object part = parts.opt(i);
                if (part instanceof JSONObject) {
                    JSONObject object = (JSONObject) part;
                    if ("text".equals(object.optString("type", ""))) {
                        builder.append(object.optString("text", ""));
                    } else {
                        // Media parts must not collide with each other or with text: keep the
                        // part's canonical JSON so a different image is a different transcript.
                        builder.append(FIELD).append(canonical(object));
                    }
                } else if (part != null && !JSONObject.NULL.equals(part)) {
                    builder.append(String.valueOf(part));
                }
            }
            return builder.toString().trim();
        }
        return String.valueOf(content).trim();
    }

    @NonNull
    private static String canonicalArguments(@Nullable Object arguments) {
        if (arguments == null || JSONObject.NULL.equals(arguments)) return "{}";
        if (arguments instanceof JSONObject) return canonical((JSONObject) arguments);
        String text = String.valueOf(arguments).trim();
        if (text.isEmpty()) return "{}";
        try {
            return canonical(new JSONObject(text));
        } catch (JSONException e) {
            return text;
        }
    }

    /** JSON with object keys sorted at every level, so key order never breaks a match. */
    @NonNull
    static String canonical(@NonNull JSONObject object) {
        return String.valueOf(sorted(object));
    }

    @NonNull
    private static Object sorted(@NonNull Object value) {
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            TreeMap<String, Object> ordered = new TreeMap<>();
            Iterator<String> keys = source.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                ordered.put(key, sorted(source.opt(key)));
            }
            JSONObject result = new JSONObject();
            for (java.util.Map.Entry<String, Object> entry : ordered.entrySet()) {
                try {
                    result.put(entry.getKey(), entry.getValue());
                } catch (JSONException ignored) {
                }
            }
            return result;
        }
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray result = new JSONArray();
            for (int i = 0; i < source.length(); i++) result.put(sorted(source.opt(i)));
            return result;
        }
        return value == null ? JSONObject.NULL : value;
    }
}
