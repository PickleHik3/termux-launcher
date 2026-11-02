package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.LayoutVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Customization › Layout (appearance-cards overhaul §2): one switch per element for the whole
 * app, written to Home and to the Workspace. What the switch reads, and what a change to it asks
 * first. No views: held by JVM tests.
 *
 * <p>A side is {@code null} where it does not wear the element (Home never wears the extra keys).
 * The switch reads on only when every side that wears the element shows it. A change asks first
 * when the two sides disagree: the side the change would move "has its own layout", and the
 * dialog offers Cancel or Override.</p>
 */
public final class LayoutOverride {

    private LayoutOverride() {}

    /** The two page kinds a Customization write lands on; the Workspace is Terminal and Display. */
    public enum Side { HOME, WORKSPACE }

    /** What the switch shows, and the summary under the element's name. */
    public enum Reading {
        /** On on every side that wears it. */
        ON,
        /** Off on every side that wears it. */
        OFF,
        /** Shown on Home only: the switch reads off. */
        HOME_ONLY,
        /** Shown on the Workspace only: the switch reads off. */
        WORKSPACE_ONLY;

        public boolean switchOn() {
            return this == ON;
        }
    }

    @NonNull
    public static Reading read(@Nullable Boolean home, @Nullable Boolean workspace) {
        if (home == null && workspace == null) return Reading.OFF;
        if (home == null) return workspace ? Reading.ON : Reading.OFF;
        if (workspace == null) return home ? Reading.ON : Reading.OFF;
        if (home && workspace) return Reading.ON;
        if (!home && !workspace) return Reading.OFF;
        return home ? Reading.HOME_ONLY : Reading.WORKSPACE_ONLY;
    }

    /** What a change to {@code want} does. */
    public static final class Decision {
        /** Whether the dialog asks first (Cancel · Override). */
        public final boolean ask;
        /** The side whose own value the change would overwrite, named by the dialog; or none. */
        @Nullable public final Side own;
        /** The sides written: at once, or on Override. */
        @NonNull public final Set<Side> writes;

        Decision(boolean ask, @Nullable Side own, @NonNull Set<Side> writes) {
            this.ask = ask;
            this.own = own;
            this.writes = Collections.unmodifiableSet(writes);
        }
    }

    /** The change of the switch to {@code want}, from the sides' values. */
    @NonNull
    public static Decision decide(@Nullable Boolean home, @Nullable Boolean workspace,
                                  boolean want) {
        EnumSet<Side> moving = EnumSet.noneOf(Side.class);
        if (home != null && home != want) moving.add(Side.HOME);
        if (workspace != null && workspace != want) moving.add(Side.WORKSPACE);
        boolean disagree = home != null && workspace != null && !home.equals(workspace);
        if (!disagree || moving.isEmpty())
            return new Decision(false, null, moving);
        // Two sides that disagree: exactly one of them moves.
        return new Decision(true, moving.iterator().next(), moving);
    }

    /** The layouts a write to {@code sides} lands on: Home's, the Workspace's normal and Display. */
    @NonNull
    public static List<LayoutVariant> variantsOf(@NonNull Set<Side> sides) {
        List<LayoutVariant> out = new ArrayList<>(3);
        if (sides.contains(Side.HOME)) out.add(LayoutVariant.HOME);
        if (sides.contains(Side.WORKSPACE)) {
            out.add(LayoutVariant.NORMAL);
            out.add(LayoutVariant.DISPLAY);
        }
        return out;
    }
}
