package com.termux.app.terminal.extrakeys;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.io.ExtraKeysTarget;

/**
 * Which flight the Home pill makes at a settle: from Home to a page whose docked bar stands
 * ({@link Flight#TO_DOCK}), the other way ({@link Flight#TO_PILL}), or none. Pure: the activity
 * reads the facts before and after the settle and asks once.
 */
public final class HomePillFlightPlan {

    public enum Flight {
        /** Home to a Workspace page where the bar is docked: the pill becomes the bar. */
        TO_DOCK,
        /** A Workspace page with its bar docked to Home: the bar becomes the pill. */
        TO_PILL,
        /** Everything else, a Home page to another Home page included: the pill stays put. */
        NONE
    }

    private HomePillFlightPlan() {}

    /**
     * @param before the settled place before this settle
     * @param after the place the settle lands on
     * @param pillShownBefore the pill was on screen and free (not picked up, not docked for the
     *     keyboard, not already flying)
     * @param dockedBarVisibleBefore the docked bar was on screen before the settle
     * @param dockedBarAfter the arrangement stands the docked bar on {@code after}
     * @param switchOn Home's bar is on
     * @param motionAllowed the person has not asked for reduced motion
     * @param targetAfter where the extra keys go once the settle has landed
     */
    @NonNull
    public static Flight decide(@NonNull PageKind before, @NonNull PageKind after,
                                boolean pillShownBefore, boolean dockedBarVisibleBefore,
                                boolean dockedBarAfter, boolean switchOn, boolean motionAllowed,
                                @Nullable ExtraKeysTarget targetAfter) {
        if (!switchOn || !motionAllowed) return Flight.NONE;
        boolean fromHome = before == PageKind.HOME;
        boolean toHome = after == PageKind.HOME;
        if (fromHome && !toHome) {
            return pillShownBefore && dockedBarAfter ? Flight.TO_DOCK : Flight.NONE;
        }
        if (!fromHome && toHome) {
            return dockedBarVisibleBefore && targetAfter == ExtraKeysTarget.HOME
                ? Flight.TO_PILL : Flight.NONE;
        }
        return Flight.NONE;
    }
}
