package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** What to open: a window's kind and what it is restored from. */
public final class WindowSpec {

    @NonNull final WindowKind kind;
    @Nullable final String name;
    @Nullable final String workingDirectory;
    @Nullable final String appId;

    private WindowSpec(@NonNull WindowKind kind, @Nullable String name,
                       @Nullable String workingDirectory, @Nullable String appId) {
        this.kind = kind;
        this.name = name;
        this.workingDirectory = workingDirectory;
        this.appId = appId;
    }

    /** A shell starting in {@code workingDirectory} (null: the home directory). */
    @NonNull
    public static WindowSpec shell(@Nullable String workingDirectory) {
        return new WindowSpec(WindowKind.SHELL, null, workingDirectory, null);
    }

    /** A Linux app, known by its app id. */
    @NonNull
    public static WindowSpec linuxApp(@NonNull String appId, @Nullable String name) {
        return new WindowSpec(WindowKind.LINUX_APP, name, null, appId);
    }

    @NonNull
    public static WindowSpec display() {
        return new WindowSpec(WindowKind.DISPLAY, null, null, null);
    }

    @NonNull
    public static WindowSpec assistant() {
        return new WindowSpec(WindowKind.ASSISTANT, null, null, null);
    }
}
