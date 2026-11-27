package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What each reading reads now, and which one the slot shows. Pure. Every reading is offered to a
 * tap unless its source is known to have none (the GPU read no load, no media session, no weather):
 * one with no value yet is simply loading, shown as {@code "--"} until its source answers, and a
 * tap onto it is what starts that source. A reading known to be missing is skipped until
 * {@link #forgetMissing} (the slot came back, the card opened) offers it again.
 */
public final class ReadingSet {

    @NonNull private final Map<ReadingKind, ReadingValue> mValues = new EnumMap<>(ReadingKind.class);
    @NonNull private final Set<ReadingKind> mMissing = EnumSet.noneOf(ReadingKind.class);

    /**
     * Sets {@code kind}'s value, or answers "none" with null, which marks it missing. True when the
     * value changed.
     */
    public boolean put(@NonNull ReadingKind kind, @Nullable ReadingValue value) {
        ReadingValue before = mValues.get(kind);
        if (value == null) {
            mMissing.add(kind);
            return mValues.remove(kind) != null;
        }
        mMissing.remove(kind);
        if (value.equals(before)) return false;
        mValues.put(kind, value);
        return true;
    }

    /** Forgets {@code kind}'s value because its source stopped; it stays offered, loading. */
    public void drop(@NonNull ReadingKind kind) {
        mValues.remove(kind);
    }

    /** Offers every reading again, the ones known to be missing included. */
    public void forgetMissing() {
        mMissing.clear();
    }

    @Nullable
    public ReadingValue get(@NonNull ReadingKind kind) {
        return mValues.get(kind);
    }

    public boolean has(@NonNull ReadingKind kind) {
        return mValues.containsKey(kind);
    }

    /** Whether {@code kind}'s source has answered that it has none. */
    public boolean isMissing(@NonNull ReadingKind kind) {
        return mMissing.contains(kind);
    }

    /** The reading to show when {@code chosen} is remembered: its value, or {@code "--"} while it loads. */
    @NonNull
    public ReadingValue shown(@NonNull ReadingKind chosen) {
        ReadingKind kind = effective(chosen);
        ReadingValue value = mValues.get(kind);
        return value != null ? value : new ReadingValue(kind, ReadingValue.UNKNOWN_NUMBER, "");
    }

    /** The kind {@link #shown} shows for {@code chosen}: it, unless it is missing, then the next one offered. */
    @NonNull
    public ReadingKind effective(@NonNull ReadingKind chosen) {
        if (!mMissing.contains(chosen)) return chosen;
        ReadingKind next = after(chosen);
        return next == null ? chosen : next;
    }

    /** What a tap makes the choice: the next offered kind after the one shown; the one shown if alone. */
    @NonNull
    public ReadingKind next(@NonNull ReadingKind chosen) {
        ReadingKind shown = effective(chosen);
        ReadingKind next = after(shown);
        return next == null ? shown : next;
    }

    /** The first kind after {@code from} (wrapping, {@code from} last) not known to be missing; null if none. */
    @Nullable
    private ReadingKind after(@NonNull ReadingKind from) {
        ReadingKind[] all = ReadingKind.values();
        for (int step = 1; step <= all.length; step++) {
            ReadingKind kind = all[(from.ordinal() + step) % all.length];
            if (!mMissing.contains(kind)) return kind;
        }
        return null;
    }
}
