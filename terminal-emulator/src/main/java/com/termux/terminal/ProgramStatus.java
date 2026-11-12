package com.termux.terminal;

/**
 * One OSC 7501 program-status record as a program last reported it for its id. Immutable; every
 * report replaces the record whole. Title, message and app have had invisible formatting
 * characters removed (see {@link ProgramStatusStore}); length caps for display are the app's job.
 */
public final class ProgramStatus {

    public enum State { IDLE, WORKING, DONE, BLOCKED, ERROR }

    public enum Kind { NONE, PERMISSION, QUESTION, AUTH }

    public final State state;
    public final Kind kind;
    /** 0..100, or -1 for none; only meaningful for WORKING and BLOCKED. */
    public final int progress;
    /** The record's id; empty for the root record. */
    public final String id;
    /** As reported; empty when the report carried none. See {@link ProgramStatusStore#resolvedApp}. */
    public final String app;
    public final String title;
    public final String message;
    /** Ordinal of the write that stored this record, from the store's counter (not a clock). */
    public final long updateSeq;

    ProgramStatus(State state, Kind kind, int progress, String id, String app, String title, String message,
                  long updateSeq) {
        this.state = state;
        this.kind = kind;
        this.progress = progress;
        this.id = id;
        this.app = app;
        this.title = title;
        this.message = message;
        this.updateSeq = updateSeq;
    }

    @Override
    public String toString() {
        return "ProgramStatus{" + state + " " + kind + " progress=" + progress + " id='" + id + "' app='" + app
            + "' title='" + title + "' message='" + message + "' seq=" + updateSeq + "}";
    }
}
