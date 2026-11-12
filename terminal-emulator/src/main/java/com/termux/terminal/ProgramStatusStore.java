package com.termux.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The OSC 7501 records of one terminal, keyed by id (empty = root). Main thread only. The library
 * parses and validates each report; everything stateful lives here: replacement, clearing by
 * subtree, app inheritance, lifetime, the record cap. There are no timers: a record leaves only
 * through a clear, a replacement, a lifetime event ({@link #onPromptStart}, {@link #onProcessExit},
 * {@link #markSeen}) or the cap.
 */
public final class ProgramStatusStore {

    /** The spec requires 64; 256 is its stated ceiling. */
    public static final int MAX_RECORDS = 256;

    /** The state number the library reports for a clear (GHOSTTY_PROGRAM_STATUS_STATE_CLEAR). */
    public static final int STATE_CLEAR = 5;

    private final Map<String, ProgramStatus> mRecords = new HashMap<>();
    private long mSeq;

    /**
     * One report. State and kind are the library's numbers (idle 0, working 1, done 2, blocked 3,
     * error 4, clear 5; none 0, permission 1, question 2, auth 3). Returns whether the store changed.
     */
    public boolean apply(int state, int kind, int progress, String id, String app, String title, String message) {
        if (id == null) id = "";
        if (state == STATE_CLEAR) return clear(id);
        if (state < 0 || state >= ProgramStatus.State.values().length) return false;
        ProgramStatus.State s = ProgramStatus.State.values()[state];
        ProgramStatus.Kind k = kind >= 0 && kind < ProgramStatus.Kind.values().length
            ? ProgramStatus.Kind.values()[kind] : ProgramStatus.Kind.NONE;
        if (progress < -1 || progress > 100) progress = -1;
        if (!mRecords.containsKey(id) && mRecords.size() >= MAX_RECORDS) evictOldest();
        mRecords.put(id, new ProgramStatus(s, k, progress, id, strip(app), strip(title), strip(message), ++mSeq));
        return true;
    }

    /** The record just stored by the last {@link #apply} for {@code id}, or null (after a clear). */
    public ProgramStatus get(String id) {
        return mRecords.get(id);
    }

    private boolean clear(String id) {
        if (id.isEmpty()) {
            if (mRecords.isEmpty()) return false;
            mRecords.clear();
            return true;
        }
        String prefix = id + "/";
        return mRecords.keySet().removeIf(key -> key.equals(id) || key.startsWith(prefix));
    }

    private void evictOldest() {
        String oldest = null;
        long oldestSeq = Long.MAX_VALUE;
        for (ProgramStatus r : mRecords.values()) {
            if (r.updateSeq < oldestSeq) {
                oldestSeq = r.updateSeq;
                oldest = r.id;
            }
        }
        if (oldest != null) mRecords.remove(oldest);
    }

    /**
     * Text without invisible formatting characters (category Cf: bidi overrides, zero-width marks,
     * BOM, soft hyphen, tags) and the line and paragraph separators, which could disguise or break
     * a label drawn outside the grid. Works on code points so surrogate pairs survive.
     */
    static String strip(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int type = Character.getType(cp);
            boolean drop = type == Character.FORMAT || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
            if (drop && out == null) out = new StringBuilder(text.length()).append(text, 0, i);
            if (!drop && out != null) out.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return out == null ? text : out.toString();
    }

    /** The app of {@code r}, else the nearest ancestor record's that has one (root included), else empty. */
    public String resolvedApp(ProgramStatus r) {
        if (r == null) return "";
        if (!r.app.isEmpty()) return r.app;
        String id = r.id;
        while (!id.isEmpty()) {
            int slash = id.lastIndexOf('/');
            id = slash < 0 ? "" : id.substring(0, slash);
            ProgramStatus ancestor = mRecords.get(id);
            if (ancestor != null && !ancestor.app.isEmpty()) return ancestor.app;
        }
        return "";
    }

    /** A new shell prompt: working and blocked records end, idle, done and error stay. */
    public boolean onPromptStart() {
        return mRecords.values().removeIf(r -> r.state == ProgramStatus.State.WORKING
            || r.state == ProgramStatus.State.BLOCKED);
    }

    /** The process ended: the same lifetime rule as a prompt. */
    public boolean onProcessExit() {
        return onPromptStart();
    }

    /** The user has seen the results: done and error records go. */
    public boolean markSeen() {
        return mRecords.values().removeIf(r -> r.state == ProgramStatus.State.DONE
            || r.state == ProgramStatus.State.ERROR);
    }

    public boolean clearAll() {
        return clear("");
    }

    private static int urgency(ProgramStatus.State s) {
        switch (s) {
            case BLOCKED: return 4;
            case ERROR: return 3;
            case WORKING: return 2;
            case DONE: return 1;
            default: return 0;
        }
    }

    /** The record to show: blocked, error, working, done, idle in that order; the latest update breaks a tie. */
    public ProgramStatus mostUrgent() {
        ProgramStatus best = null;
        for (ProgramStatus r : mRecords.values()) {
            if (best == null) { best = r; continue; }
            int a = urgency(r.state), b = urgency(best.state);
            if (a > b || (a == b && r.updateSeq > best.updateSeq)) best = r;
        }
        return best;
    }

    /** A snapshot, in no particular order. */
    public List<ProgramStatus> records() {
        return Collections.unmodifiableList(new ArrayList<>(mRecords.values()));
    }

    public int size() {
        return mRecords.size();
    }
}
