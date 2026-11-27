package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every reading that has a value now, and which one the slot shows. Pure. A reading with no value
 * (no GPU load read, no weather yet, no media session) is not offered: a tap skips it, and a
 * remembered choice that is not offered shows the next one that is.
 */
public final class ReadingSet {

    @NonNull private final Map<ReadingKind, ReadingValue> mValues = new EnumMap<>(ReadingKind.class);
    /** Kinds whose first answer is awaited: counted as offered, so a tap-chosen one is not jumped over. */
    @NonNull private final java.util.Set<ReadingKind> mPending = java.util.EnumSet.noneOf(ReadingKind.class);

    /**
     * Marks {@code kind} as awaiting its source's first answer (its sampler just started, say): until
     * it answers, with a value or with none, it stands in the cycle as if it had one, so a chosen
     * reading is not replaced by the next while it loads. True when that changed what is shown.
     */
    public boolean expect(@NonNull ReadingKind kind, boolean awaiting) {
        return awaiting ? mPending.add(kind) : mPending.remove(kind);
    }

    /** Sets {@code kind}'s value, or takes the reading away with null. True when it changed. */
    public boolean put(@NonNull ReadingKind kind, @Nullable ReadingValue value) {
        mPending.remove(kind);
        ReadingValue before = mValues.get(kind);
        if (value == null) return mValues.remove(kind) != null;
        if (value.equals(before)) return false;
        mValues.put(kind, value);
        return true;
    }

    @Nullable
    public ReadingValue get(@NonNull ReadingKind kind) {
        return mValues.get(kind);
    }

    public boolean has(@NonNull ReadingKind kind) {
        return mValues.containsKey(kind);
    }

    /**
     * The reading to show when {@code chosen} is the one remembered: it, when it has a value, else
     * the next one that has, in cycle order; with none, {@code chosen} stands empty ({@code "--"}).
     */
    @NonNull
    public ReadingValue shown(@NonNull ReadingKind chosen) {
        ReadingKind kind = effective(chosen);
        ReadingValue value = mValues.get(kind);
        return value != null ? value
            : new ReadingValue(kind, ReadingValue.UNKNOWN_NUMBER, "");
    }

    /** The kind {@link #shown} shows for {@code chosen}. */
    @NonNull
    public ReadingKind effective(@NonNull ReadingKind chosen) {
        if (offered(chosen)) return chosen;
        ReadingKind next = after(chosen);
        return next == null ? chosen : next;
    }

    /**
     * What a tap makes the choice, when {@code chosen} is remembered: the next kind after the one
     * shown that has a value; the one shown when it is the only one.
     */
    @NonNull
    public ReadingKind next(@NonNull ReadingKind chosen) {
        ReadingKind shown = effective(chosen);
        ReadingKind next = after(shown);
        return next == null ? shown : next;
    }

    /** The first kind after {@code from} (wrapping, {@code from} last) that has a value; null if none. */
    @Nullable
    private ReadingKind after(@NonNull ReadingKind from) {
        ReadingKind[] all = ReadingKind.values();
        for (int step = 1; step <= all.length; step++) {
            ReadingKind kind = all[(from.ordinal() + step) % all.length];
            if (offered(kind)) return kind;
        }
        return null;
    }

    private boolean offered(@NonNull ReadingKind kind) {
        return mValues.containsKey(kind) || mPending.contains(kind);
    }
}
