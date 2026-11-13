package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/** One message of a chat, as stored. */
public final class AssistantMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static final String STATE_COMPLETE = "complete";
    public static final String STATE_STREAMING = "streaming";
    public static final String STATE_STOPPED = "stopped";
    public static final String STATE_ERROR = "error";
    public static final String STATE_WAITING = "waiting";

    /** The runtime process died or could not be reached. */
    public static final String ERROR_RUNTIME_DIED = "runtime_died";
    /** The model is not downloaded or its files are gone. */
    public static final String ERROR_MODEL_NOT_DOWNLOADED = "model_not_downloaded";
    /** The model is downloaded but not loaded, and the runtime does not load it on demand. */
    public static final String ERROR_MODEL_NOT_LOADED = "model_not_loaded";
    /** The phone has too little free memory to load or run the model. */
    public static final String ERROR_NOT_ENOUGH_MEMORY = "not_enough_memory";
    /** The runtime did not answer in time. */
    public static final String ERROR_TIMEOUT = "timeout";
    /** The stream ended without a single character of answer. */
    public static final String ERROR_EMPTY_ANSWER = "empty_answer";
    /** Anything else. */
    public static final String ERROR_OTHER = "error";

    public final long id;
    public final long chatId;
    @NonNull public final String role;
    @NonNull public final String text;
    @NonNull public final String state;
    @Nullable public final String errorCode;
    public final long createdMs;
    /** How long the answer took, from the start of the request (model load excluded); null when not measured. */
    @Nullable public final Long durationMs;
    /** Start of the request to the first token, in ms; null when not measured. */
    @Nullable public final Long ttftMs;
    /** Tokens the runtime counted in the answer; null when it sent no count. */
    @Nullable public final Integer outputTokens;

    public AssistantMessage(long id, long chatId, @NonNull String role, @NonNull String text,
                            @NonNull String state, @Nullable String errorCode, long createdMs) {
        this(id, chatId, role, text, state, errorCode, createdMs, null, null, null);
    }

    public AssistantMessage(long id, long chatId, @NonNull String role, @NonNull String text,
                            @NonNull String state, @Nullable String errorCode, long createdMs,
                            @Nullable Long durationMs, @Nullable Long ttftMs, @Nullable Integer outputTokens) {
        this.durationMs = durationMs;
        this.ttftMs = ttftMs;
        this.outputTokens = outputTokens;
        this.id = id;
        this.chatId = chatId;
        this.role = role;
        this.text = text;
        this.state = state;
        this.errorCode = errorCode;
        this.createdMs = createdMs;
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    /** Maps a Feature request error code to the code the page words. Never null. */
    @NonNull
    public static String errorFor(@Nullable String raw) {
        String code = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        switch (code) {
            case "tai_runtime_crashed":
            case "tai_runtime_unavailable":
            case "tai_runtime_send_failed":
            case "tai_runtime_transport_failed":
            case "tai_runtime_stream_failed":
            case "tai_runtime_interrupted":
                return ERROR_RUNTIME_DIED;
            case "tai_runtime_timeout":
                return ERROR_TIMEOUT;
            case "model_not_installed":
            case "model_not_found":
            case "model_file_missing":
                return ERROR_MODEL_NOT_DOWNLOADED;
            case "model_not_loaded":
                return ERROR_MODEL_NOT_LOADED;
            case "insufficient_memory":
            case "low_available_memory":
            case "available_memory_unknown":
            case "out_of_memory":
                return ERROR_NOT_ENOUGH_MEMORY;
            default:
                return code.contains("memory") ? ERROR_NOT_ENOUGH_MEMORY
                    : code.contains("timeout") ? ERROR_TIMEOUT
                    : ERROR_OTHER;
        }
    }
}
