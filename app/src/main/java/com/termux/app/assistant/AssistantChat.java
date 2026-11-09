package com.termux.app.assistant;

import androidx.annotation.NonNull;

/** One chat in the Assistant's history. */
public final class AssistantChat {
    public final long id;
    @NonNull public final String title;
    public final long createdMs;
    public final long updatedMs;

    public AssistantChat(long id, @NonNull String title, long createdMs, long updatedMs) {
        this.id = id;
        this.title = title;
        this.createdMs = createdMs;
        this.updatedMs = updatedMs;
    }
}
