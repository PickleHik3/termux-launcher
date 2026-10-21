package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.TimeZone;

/**
 * The battery level hour by hour over the last day, kept across restarts for the Device widget's
 * 24 h chart and its drain rate. One slot per local hour holds the latest level seen in it (0..100, or
 * {@link #EMPTY} for an hour nothing was heard in); the ring slides as hours pass, so a slot
 * always means the same wall-clock hour. Writes happen only when a slot's value changes.
 */
public final class BatteryHistoryStore {
    public static final int SLOTS = 24;
    public static final int EMPTY = -1;

    static final String PREFS = "builtin_battery_history";
    private static final String KEY_HOUR = "anchor_hour";
    private static final String KEY_LEVELS = "levels";

    @NonNull private final Context context;
    @Nullable private SharedPreferences prefs;
    private boolean loaded;
    /** The local hour (see {@link DeviceWidgetFormats#localHour}) the last slot stands for. */
    private long anchorHour;
    @NonNull private int[] levels = emptySlots();

    public BatteryHistoryStore(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Records {@code level} for the hour {@code nowMillis} falls in; persists only on change. */
    public void record(int level, long nowMillis) {
        if (level < 0 || level > 100) return;
        load();
        long hour = DeviceWidgetFormats.localHour(nowMillis, TimeZone.getDefault());
        int[] next = shifted(levels, anchorHour, hour);
        boolean moved = hour != anchorHour;
        boolean changed = next[SLOTS - 1] != level;
        next[SLOTS - 1] = level;
        levels = next;
        anchorHour = hour;
        if (moved || changed) save();
    }

    /** The {@link #SLOTS} hours ending with the one {@code nowMillis} falls in, oldest first. */
    @NonNull public int[] levels(long nowMillis) {
        load();
        return shifted(levels, anchorHour, DeviceWidgetFormats.localHour(nowMillis, TimeZone.getDefault()));
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            anchorHour = prefs.getLong(KEY_HOUR, 0L);
            levels = decode(prefs.getString(KEY_LEVELS, null));
        } catch (RuntimeException ignored) {
            // A history that cannot be read starts empty; the chart fills as hours pass.
            levels = emptySlots();
        }
    }

    private void save() {
        if (prefs == null) return;
        try {
            prefs.edit().putLong(KEY_HOUR, anchorHour).putString(KEY_LEVELS, encode(levels)).apply();
        } catch (RuntimeException ignored) { }
    }

    // ----- pure ring arithmetic -------------------------------------------------------------

    @NonNull static int[] emptySlots() {
        int[] slots = new int[SLOTS];
        Arrays.fill(slots, EMPTY);
        return slots;
    }

    /**
     * {@code levels}, whose last slot is hour {@code from}, re-anchored so its last slot is hour
     * {@code to}: older hours slide off the front, hours not yet heard are empty. A clock that went
     * backwards (or a gap longer than the ring) leaves only what still fits.
     */
    @NonNull static int[] shifted(@NonNull int[] levels, long from, long to) {
        int[] out = emptySlots();
        long delta = to - from;
        if (delta <= -SLOTS || delta >= SLOTS) return out;
        for (int i = 0; i < SLOTS; i++) {
            long source = i + delta;
            if (source >= 0 && source < levels.length && source < SLOTS) {
                out[i] = levels[(int) source];
            }
        }
        return out;
    }

    /** How many of the newest hours the drain rate is read over, at most. */
    static final int DRAIN_HOURS = 6;

    /**
     * How fast the battery has been going down, in whole percent an hour, from the newest run of
     * heard hours in which the level only fell or held (oldest first, as {@link #levels} gives
     * them), over at most {@link #DRAIN_HOURS}. -1 while that is unknown: the newest hour is
     * empty, the run is under an hour long, or nothing was used; and -1 below half a percent an
     * hour, which rounds to no whole percent, so the Device line says what it says without a rate
     * rather than overstating a slow drain as 1%/h.
     */
    static int drainPerHour(@NonNull int[] levels) {
        int last = levels.length - 1;
        if (last < 1 || levels[last] < 0) return -1;
        int first = last;
        while (first > 0 && last - first < DRAIN_HOURS && levels[first - 1] >= 0
            && levels[first - 1] >= levels[first]) {
            first--;
        }
        int hours = last - first;
        int drop = levels[first] - levels[last];
        if (hours < 1 || drop <= 0) return -1;
        int rate = Math.round(drop / (float) hours);
        return rate < 1 ? -1 : rate;
    }

    @NonNull static String encode(@NonNull int[] levels) {
        StringBuilder out = new StringBuilder(SLOTS * 3);
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) out.append(',');
            out.append(levels[i]);
        }
        return out.toString();
    }

    /** The slots in {@code value}, any malformed or out-of-range entry read as empty. */
    @NonNull static int[] decode(@Nullable String value) {
        int[] out = emptySlots();
        if (value == null || value.isEmpty()) return out;
        String[] parts = value.split(",");
        for (int i = 0; i < Math.min(parts.length, SLOTS); i++) {
            try {
                int level = Integer.parseInt(parts[i].trim());
                out[i] = level >= 0 && level <= 100 ? level : EMPTY;
            } catch (NumberFormatException ignored) {
                out[i] = EMPTY;
            }
        }
        return out;
    }
}
