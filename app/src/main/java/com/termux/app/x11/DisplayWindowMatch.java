package com.termux.app.x11;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Which open window on the display belongs to an app: the same trace the drawer needs when a
 * second tap should bring the app's window forward rather than start another copy. An app does
 * not say which class its windows carry, so the guesses go from the sure one (the desktop file's
 * {@code StartupWMClass}) to the likely ones (the program it runs, then its desktop-file name).
 */
public final class DisplayWindowMatch {

    private DisplayWindowMatch() {}

    /**
     * The id of the window that belongs to the app, or -1. Matched on the window's class, ignoring
     * case; a window without a class never matches. Of several matches the last one wins, that
     * being the newest window.
     */
    public static int windowFor(@NonNull List<X11WindowList.Window> windows,
                                @NonNull String startupWmClass, @NonNull String exec,
                                @NonNull String desktopId) {
        for (String guess : guesses(startupWmClass, exec, desktopId)) {
            for (int i = windows.size() - 1; i >= 0; i--) {
                X11WindowList.Window window = windows.get(i);
                if (!window.wmClass.isEmpty() && window.wmClass.equalsIgnoreCase(guess)) {
                    return window.id;
                }
            }
        }
        return -1;
    }

    /** The classes to look for, most certain first; never empty strings. */
    @NonNull
    static java.util.List<String> guesses(@NonNull String startupWmClass, @NonNull String exec,
                                          @NonNull String desktopId) {
        java.util.List<String> out = new java.util.ArrayList<>();
        add(out, startupWmClass.trim());
        add(out, programName(exec));
        String id = desktopId.endsWith(".desktop")
            ? desktopId.substring(0, desktopId.length() - ".desktop".length()) : desktopId;
        add(out, id);
        add(out, id.substring(id.lastIndexOf('.') + 1));
        return out;
    }

    private static void add(@NonNull java.util.List<String> out, @NonNull String guess) {
        if (guess.isEmpty()) return;
        for (String have : out) if (have.toLowerCase(Locale.ROOT).equals(guess.toLowerCase(Locale.ROOT))) return;
        out.add(guess);
    }

    /** The basename of the first real word of an Exec line: past {@code env} and {@code VAR=x}. */
    @NonNull
    static String programName(@NonNull String exec) {
        String[] words = exec.trim().split("\\s+");
        for (String word : words) {
            if (word.isEmpty() || word.equals("env") || word.matches("[A-Za-z_][A-Za-z0-9_]*=.*")) {
                continue;
            }
            return word.substring(word.lastIndexOf('/') + 1);
        }
        return "";
    }
}
