package com.termux.app.launcher.widget.builtin;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The terminal widget's rules, as arithmetic on what its record keeps: which shell a widget
 * coming on screen shows, which running shells belong to widgets (and so are never adopted as
 * Workspace windows), which widgets' shells end because the widget is gone, and when removing one
 * asks first. Pure; {@link TerminalWidgetCoordinator} reads the world and applies the answers.
 *
 * <p>A widget keeps its shell in its record's settings, under state keys
 * ({@link BuiltinWidgetView#isStateKey}): the running shell's handle, the directory it was last
 * in, and whether it ended on its own. The settings sheet carries them through untouched, and a
 * change to them never rebuilds the widget.</p>
 */
public final class TerminalWidgetPolicy {

    /** The running shell's handle, while the widget has one. */
    public static final String KEY_SHELL = BuiltinWidgetView.STATE_KEY_PREFIX + "shell";
    /** The directory the shell was last in: where a fresh shell starts after the process died. */
    public static final String KEY_CWD = BuiltinWidgetView.STATE_KEY_PREFIX + "cwd";
    /** Set while the shell ended on its own and no new one has been asked for. */
    public static final String KEY_ENDED = BuiltinWidgetView.STATE_KEY_PREFIX + "ended";
    /** {@link #KEY_ENDED}'s value while set. */
    public static final String ENDED = "1";

    private TerminalWidgetPolicy() {}

    /** What a widget coming on screen shows. */
    public enum Start {
        /** The shells are not ready (the service is not bound, or the start is not settled). */
        WAIT,
        /** Its shell ended and no new one was asked for: the way to start one. */
        ENDED,
        /** Its shell is still running and in no window: that shell, as it was. */
        REATTACH,
        /** A new shell, in the directory the widget remembers. */
        FRESH
    }

    /**
     * {@link Start} for a widget whose record says {@code ended}, whose saved shell is
     * {@code savedAlive} and, if so, already shown by some window ({@code savedTaken}).
     */
    @NonNull
    public static Start start(boolean ready, boolean ended, boolean savedAlive, boolean savedTaken) {
        if (!ready) return Start.WAIT;
        if (ended) return Start.ENDED;
        if (savedAlive && !savedTaken) return Start.REATTACH;
        return Start.FRESH;
    }

    /** Where a fresh shell starts: the remembered directory while it still exists, else the default (null). */
    @Nullable
    public static String startCwd(@Nullable String savedCwd, boolean savedExists) {
        if (savedCwd == null || savedCwd.isEmpty() || !savedExists) return null;
        return savedCwd;
    }

    /** Whether a record's settings say its shell ended on its own. */
    public static boolean isEnded(@Nullable Bundle config) {
        return config != null && ENDED.equals(config.getString(KEY_ENDED));
    }

    /**
     * The handles of the shells terminal widgets hold, from each widget's settings by id. A
     * shell named here is a widget's even while its widget is off screen and nothing shows it.
     */
    @NonNull
    public static Set<String> claimedHandles(@NonNull Map<Integer, Bundle> widgets) {
        Set<String> out = new HashSet<>();
        for (Bundle config : widgets.values()) {
            String handle = config == null ? null : config.getString(KEY_SHELL);
            if (handle != null && !handle.isEmpty()) out.add(handle);
        }
        return out;
    }

    /**
     * Whether a running shell belongs to a terminal widget: shown by one now ({@code hosted}),
     * or named by one's record. Such a shell is never made a window of the Workspace.
     */
    public static boolean isWidgetShell(boolean hosted, @Nullable String handle,
                                        @NonNull Set<String> claimed) {
        return hosted || (handle != null && claimed.contains(handle));
    }

    /**
     * The widgets whose shells end because the widget is gone from Home: those hosting a float
     * ({@code hosting}) that are no longer terminal widgets anywhere on Home ({@code live}).
     */
    @NonNull
    public static List<Integer> gone(@NonNull Collection<Integer> hosting,
                                     @NonNull Collection<Integer> live) {
        List<Integer> out = new ArrayList<>();
        for (Integer id : hosting) if (!live.contains(id)) out.add(id);
        return out;
    }

    /**
     * Whether removing the widget asks first, as closing a window does: only while its shell is
     * known to be running something ({@code idle} false). Unknown (null) does not ask.
     */
    public static boolean removalAsks(@Nullable Boolean idle) {
        return idle != null && !idle;
    }
}
