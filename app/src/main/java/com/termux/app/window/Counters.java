package com.termux.app.window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The window's one counter table and where its events are logged (ADR 0028 section 5). Holds and
 * guards count here while the motion log is on; the table is dumped after the ring by
 * {@code launcherctl motion-log dump} and reset by {@code motion-log clear}. {@code window} imports
 * nothing app-level, so the motion log hands over its ring line sink and its switch
 * ({@link #enable}).
 */
public final class Counters {

    /** Where a counted event also leaves a line, in the motion log's ring. */
    public interface Ring {
        void line(@NonNull String event, @Nullable String kv);
    }

    /** Read at every site before any string is built; off, an event costs one read. */
    public static volatile boolean enabled;

    private static final CounterTable TABLE = new CounterTable();
    @Nullable private static volatile Ring sRing;

    private Counters() {
    }

    /** Turns counting on or off together with the motion log; {@code ring} is where lines go. */
    public static void enable(boolean on, @Nullable Ring ring) {
        enabled = on;
        sRing = on ? ring : null;
    }

    /** Tallies {@code name} now. Callers guard on {@link #enabled} before they build a name. */
    public static void count(@NonNull String name, @Nullable String detail) {
        TABLE.count(System.currentTimeMillis(), name, detail);
    }

    /** One ring line, when the motion log is on. */
    public static void ring(@NonNull String event, @Nullable String kv) {
        Ring ring = sRing;
        if (enabled && ring != null) ring.line(event, kv);
    }

    /** A guard fired: counted under {@code guard} and written to the ring, never thrown. */
    public static void violation(@NonNull String guard, @NonNull String detail) {
        count(guard, detail);
        ring("guard", guard + " " + detail);
    }

    public static long countOf(@NonNull String name) {
        return TABLE.countOf(name);
    }

    @NonNull
    public static List<String> lines() {
        return TABLE.lines();
    }

    public static void clear() {
        TABLE.clear();
    }
}
