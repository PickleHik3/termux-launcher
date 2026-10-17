package com.termux.app.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;

/**
 * The launcher's own drawer entries: **Terminal**, which goes to the Workspace, and **Display**,
 * which opens the bare display (ADR 0008). Each is an {@link AppRef} under the reserved
 * {@link #PACKAGE}, the way a Linux app is one under {@code X11Apps.PACKAGE}, so everything keyed
 * by {@code stableId} (ranking, pins, folders, search) takes them unchanged; the few places that
 * ask Android about a package check {@link #isEntry} first.
 */
public final class LauncherEntries {

    /** Never a real Android package name: colons are not allowed in one. */
    public static final String PACKAGE = "launcher:entry";

    /** The Terminal entry: the Workspace at the window last shown; a long press opens a window. */
    public static final String TERMINAL = "terminal";

    /** The Display entry, under Linux Apps: the bare display, started if none is up. */
    public static final String DISPLAY = "display";

    private LauncherEntries() {}

    @NonNull
    public static AppRef terminal() {
        return new AppRef(PACKAGE, TERMINAL);
    }

    @NonNull
    public static AppRef display() {
        return new AppRef(PACKAGE, DISPLAY);
    }

    public static boolean isEntry(@Nullable AppRef ref) {
        return ref != null && PACKAGE.equals(ref.packageName);
    }

    /** Which entry a ref names, or null for any other app. */
    @Nullable
    public static String idOf(@Nullable AppRef ref) {
        return isEntry(ref) ? ref.activityName : null;
    }

    /** Whether the entry {@code ref} names offers New float here: a Home float on the page in view. */
    public static boolean hasHomeFloat(@Nullable AppRef ref) {
        return TERMINAL.equals(idOf(ref));
    }

    /** Whether the entry {@code ref} names has a long-press action of its own (New window). */
    public static boolean hasAlternate(@Nullable AppRef ref) {
        return TERMINAL.equals(idOf(ref));
    }
}
