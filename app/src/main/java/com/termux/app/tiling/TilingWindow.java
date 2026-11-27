package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One window of the tiling desktop. Its id is unique within the stack and grows with each window
 * opened, so a smaller id has been open longer. The stack owns every change to it.
 */
public final class TilingWindow {

    private final long id;
    @NonNull private final WindowKind kind;
    @Nullable String name;
    @Nullable String workingDirectory;
    @Nullable String appId;
    long area;
    boolean floating;
    boolean pinned;

    TilingWindow(long id, @NonNull WindowKind kind, @Nullable String name,
                 @Nullable String workingDirectory, @Nullable String appId) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.workingDirectory = workingDirectory;
        this.appId = appId;
    }

    public long id() {
        return id;
    }

    @NonNull
    public WindowKind kind() {
        return kind;
    }

    /** The window's short name, or null to show the kind's own. */
    @Nullable
    public String name() {
        return name;
    }

    /** A shell's working directory; null for the other kinds or the home directory. */
    @Nullable
    public String workingDirectory() {
        return workingDirectory;
    }

    /** A Linux app's id; null for the other kinds. */
    @Nullable
    public String appId() {
        return appId;
    }

    /** The area the layout last reported for it, in any unit the layout keeps consistent. */
    public long area() {
        return area;
    }

    public boolean isFloat() {
        return floating;
    }

    /** A Float shown on every Workspace. */
    public boolean isPinned() {
        return pinned;
    }
}
