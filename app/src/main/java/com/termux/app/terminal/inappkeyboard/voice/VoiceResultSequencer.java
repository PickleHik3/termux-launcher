package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Hands segment results back in the order the segments were spoken. Segments are numbered as they
 * close; a result that arrives before an earlier segment's waits for it, so text is never inserted
 * out of order even if a later segment transcribes first. Not thread-safe: one thread offers.
 */
public final class VoiceResultSequencer<T> {

    private final TreeMap<Integer, T> pending = new TreeMap<>();
    private int next;

    /** Offers result {@code sequence}; returns every result now deliverable, in order (possibly none). */
    @NonNull
    public List<T> offer(int sequence, @NonNull T result) {
        pending.put(sequence, result);
        ArrayList<T> ready = new ArrayList<>();
        while (!pending.isEmpty() && pending.firstKey() == next) {
            ready.add(pending.pollFirstEntry().getValue());
            next++;
        }
        return ready;
    }

    /** Results still waiting for an earlier one. */
    public int pendingCount() {
        return pending.size();
    }
}
