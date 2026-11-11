package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.terminal.ProgramStatus;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Decides whether a program-status record that was just raised is worth an in-app notice: only
 * blocked, done and error; not again for the same session, id and state until that id's state has
 * changed; and at most one per session in {@link #WINDOW_MS}, the rest dropped rather than queued.
 * Time is passed in so the rules are testable.
 */
public final class ProgramStatusAlerts {

    static final long WINDOW_MS = 2000L;

    private final Map<Object, Map<String, ProgramStatus.State>> mLastAlerted = new IdentityHashMap<>();
    private final Map<Object, Long> mLastAt = new IdentityHashMap<>();

    /** Whether {@code record} should alert now; when true it is recorded as alerted. */
    public boolean shouldAlert(@NonNull Object session, @NonNull ProgramStatus record, long nowMs) {
        ProgramStatus.State s = record.state;
        if (s != ProgramStatus.State.BLOCKED && s != ProgramStatus.State.DONE
            && s != ProgramStatus.State.ERROR) return false;
        Map<String, ProgramStatus.State> byId = mLastAlerted.get(session);
        if (byId != null && byId.get(record.id) == s) return false;
        Long last = mLastAt.get(session);
        if (last != null && nowMs - last < WINDOW_MS) return false;
        if (byId == null) {
            byId = new HashMap<>();
            mLastAlerted.put(session, byId);
        }
        byId.put(record.id, s);
        mLastAt.put(session, nowMs);
        return true;
    }

    /** The session ended. */
    public void forget(@NonNull Object session) {
        mLastAlerted.remove(session);
        mLastAt.remove(session);
    }
}
