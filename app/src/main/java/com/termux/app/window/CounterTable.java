package com.termux.app.window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A tally per name: a count, the first and last time and the last detail. It lives outside the
 * motion log's ring, so a count survives the ring rolling over and a day of use is one table (ADR
 * 0028 section 5). Names are the few fixed names of holds, guards and invariants, so the table
 * stays small; a detail is cut to {@link #DETAIL_MAX} characters.
 */
public final class CounterTable {

    public static final int DETAIL_MAX = 80;

    private static final class Entry {
        long count;
        long first;
        long last;
        String detail = "";
    }

    private final Map<String, Entry> mEntries = new TreeMap<>();

    /** Tallies one occurrence of {@code name} at {@code epochMs}; a null detail keeps the last one. */
    public synchronized void count(long epochMs, @NonNull String name, @Nullable String detail) {
        Entry e = mEntries.get(name);
        if (e == null) {
            e = new Entry();
            e.first = epochMs;
            mEntries.put(name, e);
        }
        e.count++;
        e.last = epochMs;
        if (detail != null) {
            e.detail = detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail;
        }
    }

    public synchronized long countOf(@NonNull String name) {
        Entry e = mEntries.get(name);
        return e == null ? 0L : e.count;
    }

    public synchronized boolean isEmpty() {
        return mEntries.isEmpty();
    }

    /** One line per name, in name order: {@code name count=N first=<epoch ms> last=<epoch ms> detail=<...>}. */
    @NonNull
    public synchronized List<String> lines() {
        List<String> out = new ArrayList<>(mEntries.size());
        for (Map.Entry<String, Entry> e : mEntries.entrySet()) {
            Entry c = e.getValue();
            out.add(e.getKey() + " count=" + c.count + " first=" + c.first + " last=" + c.last
                + " detail=" + c.detail);
        }
        return out;
    }

    public synchronized void clear() {
        mEntries.clear();
    }
}
