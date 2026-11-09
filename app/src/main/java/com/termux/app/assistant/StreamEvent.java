package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * One streamed chat-completions event, read for what the Assistant needs: the next piece of the
 * answer, whether the answer is finished, or the error that ended it.
 */
public final class StreamEvent {
    /** The text added by this event; empty when none. */
    @NonNull public final String delta;
    /** True when the event carries a finish reason. */
    public final boolean finished;
    /** An {@link AssistantMessage} {@code ERROR_*} code when the event is an error, else null. */
    @Nullable public final String errorCode;
    /** The runtime's own code for the error, kept for logs. */
    @Nullable public final String rawErrorCode;

    private StreamEvent(@NonNull String delta, boolean finished, @Nullable String errorCode,
                        @Nullable String rawErrorCode) {
        this.delta = delta;
        this.finished = finished;
        this.errorCode = errorCode;
        this.rawErrorCode = rawErrorCode;
    }

    public boolean isError() {
        return errorCode != null;
    }

    @NonNull
    public static StreamEvent parse(@NonNull JSONObject event) {
        JSONObject error = event.optJSONObject("error");
        boolean failed = event.has("ok") && !event.optBoolean("ok", true);
        if (error != null || failed || (event.has("error") && !event.isNull("error"))) {
            String raw = error != null ? error.optString("code", "") : "";
            if (raw.isEmpty()) raw = event.optString("error_code", event.optString("code", ""));
            if (raw.isEmpty() && error == null) raw = event.optString("error", "");
            return new StreamEvent("", false, mapErrorCode(raw), raw.isEmpty() ? null : raw);
        }
        String delta = "";
        boolean finished = false;
        JSONArray choices = event.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            if (choice != null) {
                JSONObject d = choice.optJSONObject("delta");
                if (d != null && d.has("content") && !d.isNull("content")) delta = d.optString("content", "");
                finished = choice.has("finish_reason") && !choice.isNull("finish_reason");
            }
        }
        return new StreamEvent(delta, finished, null, null);
    }

    /** Maps the runtime's error code to the code the page words. Never null. */
    @NonNull
    public static String mapErrorCode(@Nullable String raw) {
        String code = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        switch (code) {
            case "tai_runtime_crashed":
            case "tai_runtime_unavailable":
            case "tai_runtime_send_failed":
            case "tai_runtime_transport_failed":
            case "tai_runtime_stream_failed":
            case "tai_runtime_interrupted":
                return AssistantMessage.ERROR_RUNTIME_DIED;
            case "tai_runtime_timeout":
                return AssistantMessage.ERROR_TIMEOUT;
            case "model_not_installed":
            case "model_not_found":
            case "model_file_missing":
                return AssistantMessage.ERROR_MODEL_NOT_DOWNLOADED;
            case "model_not_loaded":
                return AssistantMessage.ERROR_MODEL_NOT_LOADED;
            case "insufficient_memory":
            case "low_available_memory":
            case "available_memory_unknown":
            case "out_of_memory":
                return AssistantMessage.ERROR_NOT_ENOUGH_MEMORY;
            default:
                return code.contains("memory") ? AssistantMessage.ERROR_NOT_ENOUGH_MEMORY
                    : code.contains("timeout") ? AssistantMessage.ERROR_TIMEOUT
                    : AssistantMessage.ERROR_OTHER;
        }
    }
}
