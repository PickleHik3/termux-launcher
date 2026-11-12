package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the user allowed programs to do with clipboard reads. A session grant lives in memory until
 * that pane's session ends; an "always" grant is a program name kept in the app's preferences.
 * Resetting clears the "always" names only: a session grant is a choice about a pane that is
 * still open, and the settings row is about what the app remembers across restarts.
 */
public final class ClipboardReadGrants {

    @Nullable private final TermuxAppSharedPreferences mPreferences;
    private final Map<Object, Set<String>> mSession = new IdentityHashMap<>();

    /** @param preferences where the "always" names are kept; null keeps none. */
    public ClipboardReadGrants(@Nullable TermuxAppSharedPreferences preferences) {
        mPreferences = preferences;
    }

    public void allowForSession(@NonNull Object session, @NonNull String programName) {
        Set<String> names = mSession.get(session);
        if (names == null) {
            names = new HashSet<>();
            mSession.put(session, names);
        }
        names.add(programName);
    }

    public void allowAlways(@NonNull String programName) {
        if (mPreferences != null) mPreferences.addClipboardReadAlways(programName);
    }

    public boolean isSessionGranted(@NonNull Object session, @Nullable String programName) {
        if (programName == null) return false;
        Set<String> names = mSession.get(session);
        return names != null && names.contains(programName);
    }

    public boolean isAlwaysGranted(@Nullable String programName) {
        return programName != null && mPreferences != null
            && mPreferences.getClipboardReadAlways().contains(programName);
    }

    public void forgetSession(@NonNull Object session) {
        mSession.remove(session);
    }

    /** Clears the stored "always" names; session grants stay. */
    public void resetAlways() {
        if (mPreferences != null) mPreferences.clearClipboardReadAlways();
    }
}
