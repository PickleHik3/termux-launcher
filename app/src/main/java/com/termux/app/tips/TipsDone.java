package com.termux.app.tips;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;
import java.util.Set;

/** The hints that are gone (their gesture done, or dismissed), and how they are stored. */
public final class TipsDone {

    private static final TipsDone NONE = new TipsDone(EnumSet.noneOf(Tip.class));

    @NonNull private final Set<Tip> mDone;

    private TipsDone(@NonNull Set<Tip> done) {
        mDone = done;
    }

    /** No hint is gone. */
    @NonNull
    public static TipsDone none() {
        return NONE;
    }

    /** Reads the stored form; a name no hint has is dropped, null and blank are none. */
    @NonNull
    public static TipsDone parse(@Nullable String stored) {
        if (stored == null || stored.trim().isEmpty()) return NONE;
        Set<Tip> done = EnumSet.noneOf(Tip.class);
        for (String key : stored.split(",")) {
            Tip tip = Tip.of(key.trim());
            if (tip != null) done.add(tip);
        }
        return new TipsDone(done);
    }

    /** The stored form: hint names in order, separated by commas; empty for none. */
    @NonNull
    public String format() {
        StringBuilder out = new StringBuilder();
        for (Tip tip : mDone) {
            if (out.length() > 0) out.append(',');
            out.append(tip.key);
        }
        return out.toString();
    }

    public boolean has(@NonNull Tip tip) {
        return mDone.contains(tip);
    }

    /** These, and {@code tip} too. */
    @NonNull
    public TipsDone with(@NonNull Tip tip) {
        if (mDone.contains(tip)) return this;
        Set<Tip> done = EnumSet.noneOf(Tip.class);
        done.addAll(mDone);
        done.add(tip);
        return new TipsDone(done);
    }

    /**
     * The hint to show now: the first one not gone, when Home is in front and nothing else holds
     * the screen; null otherwise. The slide hint is passed over (not gone) while there is nothing
     * to slide between: one mark, or the rail with none.
     */
    @Nullable
    public Tip next(boolean homeInFront, boolean screenBusy, boolean canSlide) {
        if (!homeInFront || screenBusy) return null;
        for (Tip tip : Tip.values()) {
            if (mDone.contains(tip) || (tip == Tip.MARK_SLIDE && !canSlide)) continue;
            return tip;
        }
        return null;
    }

    /** Whether a hint is still to come, whatever holds the screen now. */
    public boolean anyLeft(boolean canSlide) {
        return next(true, false, canSlide) != null;
    }
}
