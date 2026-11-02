package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Customization › Layout (appearance-cards overhaul §2): one switch per element for the whole
 * app, written to Home and to the Workspace. What the switch reads, and what a change to it asks
 * first. No views: held by JVM tests.
 *
 * <p>A side is {@code null} where it does not wear the element (Home never wears the extra keys).
 * The switch reads on only when every side that wears the element shows it. A change asks first
 * when the sides disagree and the change would move a side: that side "has its own layout". The
 * dialog's Keep button applies the change only to the sides that are not kept; it is offered only
 * when that writes something, so it is never a second Cancel.</p>
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
        /** Whether the dialog asks first. */
        public final boolean ask;
        /** The sides whose own value the change would overwrite; named by the dialog's title. */
        @NonNull public final Set<Side> own;
        /** Override, or the change itself when nothing is asked: the sides written. */
        @NonNull public final Set<Side> overrideWrites;
        /** Keep: the sides written when the {@link #own} ones keep their value. */
        @NonNull public final Set<Side> keepWrites;

        Decision(boolean ask, @NonNull Set<Side> own, @NonNull Set<Side> overrideWrites,
                 @NonNull Set<Side> keepWrites) {
            this.ask = ask;
            this.own = Collections.unmodifiableSet(own);
            this.overrideWrites = Collections.unmodifiableSet(overrideWrites);
            this.keepWrites = Collections.unmodifiableSet(keepWrites);
        }

        /** Whether the dialog offers Keep: only when Keep writes something. */
        public boolean offersKeep() {
            return ask && !keepWrites.isEmpty();
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
            return new Decision(false, EnumSet.noneOf(Side.class), moving,
                EnumSet.noneOf(Side.class));
        EnumSet<Side> keep = EnumSet.copyOf(moving);
        keep.removeAll(moving);
        return new Decision(true, moving, moving, keep);
    }
}
