package com.termux.app.launcher;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.x11.X11Apps;

/**
 * What opening an app does on the Terl desktop (#435), given what it is and whether a window of it
 * is already open: the bar's search (the command palette under Terl), the bar's pinned apps and
 * the A–Z rail all open an app this way. Pure.
 */
public final class AppOpen {

    /** What an app is, for opening. */
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

    private AppOpen() {}

    /** What {@code ref} is, for opening. */
    @NonNull
    public static Target targetOf(@NonNull AppRef ref) {
        if (LauncherEntries.TERMINAL.equals(LauncherEntries.idOf(ref))) return Target.TERMINAL;
        if (LauncherEntries.isEntry(ref)) return Target.LAUNCHER_ENTRY;
        return X11Apps.isLinuxApp(ref) ? Target.LINUX_APP : Target.ANDROID_APP;
    }

    /** A tap on an app. */
    @NonNull
    public static Action tap(@NonNull Target target, boolean windowOpen) {
        switch (target) {
            case TERMINAL: return windowOpen ? Action.GO_TO_WINDOW : Action.NEW_TERMINAL;
            case LINUX_APP: return windowOpen ? Action.GO_TO_WINDOW : Action.LAUNCH_NEW_WINDOW;
            case ANDROID_APP: return Action.LAUNCH_OVER;
            default: return Action.LAUNCH_ENTRY;
        }
    }

    /** What a search row says at its end about its tap (the prototype's right-side words). */
    public enum Hint {
        /** Nothing: a launch whose result is plain from the row itself. */
        NONE,
        /** All apps: the tap opens the drawer. */
        OPEN_DRAWER,
        /** The tap opens a new shell window. */
        NEW_WINDOW,
        /** The tap goes to the window already open. */
        OPEN
    }

    /**
     * The hint for a row whose tap does {@code action}; {@code allApps} for the All apps row,
     * which has no action of its own. A launch (a Linux app with no window, an Android app, the
     * launcher's own entries) says nothing, as the prototype's do.
     */
    @NonNull
    public static Hint hint(boolean allApps, @NonNull Action action) {
        if (allApps) return Hint.OPEN_DRAWER;
        switch (action) {
            case NEW_TERMINAL: return Hint.NEW_WINDOW;
            case GO_TO_WINDOW: return Hint.OPEN;
            default: return Hint.NONE;
        }
    }
}
