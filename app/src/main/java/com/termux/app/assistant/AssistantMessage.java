package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    public AssistantMessage(long id, long chatId, @NonNull String role, @NonNull String text,
                            @NonNull String state, @Nullable String errorCode, long createdMs) {
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
}
