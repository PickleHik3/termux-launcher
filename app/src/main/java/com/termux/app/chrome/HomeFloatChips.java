package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home's own chips on the status bar (ADR 0030 section 2: floats are content of the Home page they
 * sit on): one per float on the Home page shown, in the order they were opened, each minimised
 * or not. The Workspace's window chips ({@link WindowChips}) are a different list on a different
 * strip; nothing here is shared with them. Also says which chip is selected (the float in focus)
 * and what a tap means (that float comes forward, or back from its chip).
 *
 * <p>Built afresh per refresh and per tap from the floats the activity holds. Pure: {@code W} is
 * the activity's window, never looked into.
 */
public final class HomeFloatChips<W> {

    /** One of Home's chips. */
    public static final class Chip<W> {
        @NonNull public final W window;
        /** When the float was opened; the chips stand in this order. */
        public final long opened;
        /** Whether the float is folded into its chip. */
        public final boolean minimised;
        final boolean focused;

        Chip(@NonNull W window, long opened, boolean minimised, boolean focused) {
            this.window = window;
            this.opened = opened;
            this.minimised = minimised;
            this.focused = focused;
        }

        @NonNull @Override public String toString() {
            return "Chip{" + window + " opened=" + opened + (minimised ? " minimised" : "")
                + (focused ? " focused" : "") + "}";
        }
    }

    private final int mShownPage;
    private final List<Chip<W>> mChips = new ArrayList<>(4);

    /** Home's chips for Home page {@code shownPage} (its position in the pager, from 0). */
    public HomeFloatChips(int shownPage) {
        mShownPage = shownPage;
    }

    /**
     * A float on Home page {@code homePage}: it gets a chip only when that is the page shown, in
     * the order of {@code opened} whatever order the floats are added in.
     */
    @NonNull
    public HomeFloatChips<W> add(@NonNull W window, long opened, int homePage, boolean minimised,
                                 boolean focused) {
        if (homePage < 0 || homePage != mShownPage) return this;
        int at = mChips.size();
        while (at > 0 && mChips.get(at - 1).opened > opened) at--;
        mChips.add(at, new Chip<>(window, opened, minimised, focused));
        return this;
    }

    /** The chips, in their order. */
    @NonNull
    public List<Chip<W>> chips() {
        return Collections.unmodifiableList(mChips);
    }

    public int size() {
        return mChips.size();
    }

    /** The chip of the float in focus, or -1: a minimised float is never the one in front. */
    public int selected() {
        for (int i = 0; i < mChips.size(); i++) {
            Chip<W> chip = mChips.get(i);
            if (chip.focused && !chip.minimised) return i;
        }
        return -1;
    }

    /** The chip standing for {@code window}, or -1: where its minimise flies to. */
    public int chipOf(@NonNull W window) {
        for (int i = 0; i < mChips.size(); i++) {
            if (window.equals(mChips.get(i).window)) return i;
        }
        return -1;
    }

    /**
     * What a tap on the chip at {@code index} brings forward, or back from minimised: its float,
     * already on the page shown, so the wall does not travel. Null for no chip.
     */
    @Nullable
    public W tap(int index) {
        return index < 0 || index >= mChips.size() ? null : mChips.get(index).window;
    }
}
