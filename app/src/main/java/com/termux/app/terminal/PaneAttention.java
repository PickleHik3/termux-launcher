package com.termux.app.terminal;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Which panes are asking for the user, and why. One method sets it ({@link #set}); every cause
 * that means "look at this pane" goes through it: the terminal bell (which OSC 9, OSC 777, OSC 99
 * and {@code launcherctl notify} all ring), an agent reporting {@code blocked}, and a progress
 * report in the error state. A pane is asking while any cause holds.
 *
 * <p>Keyed by pane id (the shell's pid), plain Java so the rules are JVM-testable. Main thread only.
 */
public final class PaneAttention {

    /** Why a pane wants the user; each is its own bit so causes clear independently. */
    public enum Cause {
        BELL, BLOCKED, PROGRESS_ERROR;

        int bit() { return 1 << ordinal(); }
    }

    /** Told when a pane starts or stops asking. */
    public interface Listener {
        void onPaneAttentionChanged(int paneId);
    }

    private final Map<Integer, Integer> mCauses = new HashMap<>();
    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** Raise or drop one cause for a pane. The listener fires only when the pane's answer flips. */
    public void set(int paneId, @NonNull Cause cause, boolean on) {
        Integer held = mCauses.get(paneId);
        int before = held == null ? 0 : held;
        int after = on ? before | cause.bit() : before & ~cause.bit();
        if (after == before) return;
        if (after == 0) mCauses.remove(paneId);
        else mCauses.put(paneId, after);
        if ((before == 0) != (after == 0) && mListener != null) mListener.onPaneAttentionChanged(paneId);
    }

    public boolean isSet(int paneId) {
        return mCauses.containsKey(paneId);
    }

    /** The user focused the pane: every cause is acknowledged. */
    public void clear(int paneId) {
        if (mCauses.remove(paneId) != null && mListener != null) mListener.onPaneAttentionChanged(paneId);
    }

    /** Forget panes that no longer exist. */
    public void retain(@NonNull Set<Integer> livePaneIds) {
        for (Iterator<Integer> it = mCauses.keySet().iterator(); it.hasNext(); ) {
            if (!livePaneIds.contains(it.next())) it.remove();
        }
    }
}
