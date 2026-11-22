package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decision 11's choice of the one player the media session mirrors:
 * <ul>
 *   <li>among the players that are playing, the one that most recently changed to playing;</li>
 *   <li>with none playing, the one shown so far if it is paused, else the paused one that played
 *       most recently;</li>
 *   <li>with none playing or paused, none.</li>
 * </ul>
 * Pure; not thread-safe (the media bridge keeps it on the main thread).
 */
final class PlayerPick {

    private static final class Entry {
        @NonNull PlayerState state;
        /** When it last changed to playing, on this class's own clock; 0 for never. */
        long playingSince;

        Entry(@NonNull PlayerState state) {
            this.state = state;
        }
    }

    private final Map<String, Entry> players = new LinkedHashMap<>();
    private long clock;
    @Nullable private String shown;

    /** A player's new state. */
    void update(@NonNull PlayerState state) {
        Entry entry = players.get(state.name);
        boolean wasPlaying = entry != null && entry.state.status == PlayerState.Status.PLAYING;
        if (entry == null) {
            entry = new Entry(state);
            players.put(state.name, entry);
        } else {
            entry.state = state;
        }
        if (state.status == PlayerState.Status.PLAYING && !wasPlaying) entry.playingSince = ++clock;
    }

    /** A {@code Seeked} for a player; ignored for one never reported. */
    void seeked(@NonNull String name, long positionMs) {
        Entry entry = players.get(name);
        if (entry != null) entry.state = entry.state.seekedTo(positionMs);
    }

    void remove(@NonNull String name) {
        players.remove(name);
    }

    void clear() {
        players.clear();
        shown = null;
    }

    @Nullable
    PlayerState get(@NonNull String name) {
        Entry entry = players.get(name);
        return entry == null ? null : entry.state;
    }

    /** The names of the players that are playing now. */
    @NonNull
    List<String> playing() {
        List<String> names = new ArrayList<>();
        for (Entry entry : players.values()) {
            if (entry.state.status == PlayerState.Status.PLAYING) names.add(entry.state.name);
        }
        return names;
    }

    /** The player to show now, which is remembered as the one shown; null for none. */
    @Nullable
    PlayerState pick() {
        Entry best = null;
        for (Entry entry : players.values()) {
            if (entry.state.status != PlayerState.Status.PLAYING) continue;
            if (best == null || entry.playingSince > best.playingSince) best = entry;
        }
        if (best == null) {
            Entry current = shown == null ? null : players.get(shown);
            if (current != null && current.state.status == PlayerState.Status.PAUSED) {
                best = current;
            } else {
                for (Entry entry : players.values()) {
                    if (entry.state.status != PlayerState.Status.PAUSED) continue;
                    if (best == null || entry.playingSince > best.playingSince) best = entry;
                }
            }
        }
        shown = best == null ? null : best.state.name;
        return best == null ? null : best.state;
    }
}
