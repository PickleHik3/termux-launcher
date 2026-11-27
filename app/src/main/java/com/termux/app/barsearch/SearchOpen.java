package com.termux.app.barsearch;

import androidx.annotation.NonNull;

/**
 * What opening a search result does (#435), given what it is and whether a window of it is
 * already open. Pure.
 */
public final class SearchOpen {

    /** What a result is, for opening. */
    public enum Target {
        TERMINAL,
        LINUX_APP,
        /** An Android app: opens full screen over the launcher; the launcher keeps no window for it. */
        ANDROID_APP,
        /** Display and Assistant: the launcher's own open path (one window each, gone to when open). */
        LAUNCHER_ENTRY
    }

    public enum Action {
        NONE,
        /** A new shell window after the focused window. */
        NEW_TERMINAL,
        /** The app's open window, focused. */
        GO_TO_WINDOW,
        /** The app started again: its window lands after the focused window. */
        LAUNCH_NEW_WINDOW,
        /** Android's launch, over the launcher. */
        LAUNCH_OVER,
        LAUNCH_ENTRY
    }

    private SearchOpen() {}

    /** A tap on a result. */
    @NonNull
    public static Action tap(@NonNull Target target, boolean windowOpen) {
        switch (target) {
            case TERMINAL: return windowOpen ? Action.GO_TO_WINDOW : Action.NEW_TERMINAL;
            case LINUX_APP: return windowOpen ? Action.GO_TO_WINDOW : Action.LAUNCH_NEW_WINDOW;
            case ANDROID_APP: return Action.LAUNCH_OVER;
            default: return Action.LAUNCH_ENTRY;
        }
    }

    /** The action a hold on a result offers as "New window here", or NONE when it offers nothing. */
    @NonNull
    public static Action hold(@NonNull Target target, boolean windowOpen) {
        if (!windowOpen) return Action.NONE;
        switch (target) {
            case TERMINAL: return Action.NEW_TERMINAL;
            case LINUX_APP: return Action.LAUNCH_NEW_WINDOW;
            default: return Action.NONE;
        }
    }

    /**
     * Whether a Linux window of class {@code windowApp} (named {@code windowAppName} by the display's
     * app list, when it knows it) is a window of the catalogue app with desktop file {@code desktopFile}
     * and name {@code label}: the same name, or the class is the desktop file's name.
     */
    public static boolean windowIsOf(@NonNull String windowApp, @androidx.annotation.Nullable String windowAppName,
                                     @NonNull String desktopFile, @NonNull String label) {
        if (windowApp.isEmpty()) return false;
        if (windowAppName != null && !windowAppName.isEmpty() && windowAppName.equalsIgnoreCase(label)) return true;
        String base = desktopFile.endsWith(".desktop")
            ? desktopFile.substring(0, desktopFile.length() - ".desktop".length()) : desktopFile;
        return windowApp.equalsIgnoreCase(base) || windowApp.equalsIgnoreCase(label);
    }
}
