package com.termux.app.surfaces;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Hands the Appearance surface from an activity going down for a configuration relaunch (new
 * wallpaper colours, the theme, the font scale) to the one Android builds next. The process
 * outlives the relaunch, so the {@link AppearanceSurfaceController.Relaunch} waits in memory and
 * the saved state names which handover is its own. One per activity.
 *
 * <p>A handover the next activity took stays its own until the surface has actually opened from
 * it ({@link #claim}): a relaunch can follow the first resume before the posted reopen ever ran
 * (the reopen waits for the window's first layout, the relaunch is already queued), and that
 * save passes the handover on rather than losing it.</p>
 */
public final class AppearanceRelaunchHandover {

    static final String ARG_ID = "appearance_relaunch";
    static final String ARG_FROM_SETTINGS = "appearance_from_settings";

    @Nullable private static AppearanceSurfaceController.Relaunch sCarried;
    private static int sId;
    private static int sSeq;

    /** The handover this activity last made, so its own later save may withdraw only that one. */
    private int mSavedId;
    /** A handover this activity took on creation and has not opened yet. */
    @Nullable private AppearanceSurfaceController.Relaunch mPending;
    private boolean mPendingFromSettings;

    /**
     * Every save calls this, the one at a stop included (a relaunch of a stopped activity reuses
     * the state saved at the stop). {@code open} is the surface as it stands, null while closed;
     * then a handover not yet opened is passed on, and otherwise this activity's earlier one is
     * withdrawn.
     */
    public void save(@Nullable AppearanceSurfaceController.Relaunch open, boolean openFromSettings,
                     @NonNull Bundle out) {
        AppearanceSurfaceController.Relaunch carried = open;
        boolean fromSettings = openFromSettings;
        if (carried == null && mPending != null) {
            carried = mPending;
            fromSettings = mPendingFromSettings;
        }
        if (carried == null) {
            if (mSavedId != 0 && mSavedId == sId) {
                sCarried = null;
                sId = 0;
            }
            mSavedId = 0;
            return;
        }
        int id = ++sSeq;
        sCarried = carried;
        sId = id;
        mSavedId = id;
        out.putInt(ARG_ID, id);
        out.putBoolean(ARG_FROM_SETTINGS, fromSettings);
    }

    /** Takes the handover {@code saved} names, when this process still holds it. */
    public void take(@NonNull Bundle saved) {
        int id = saved.getInt(ARG_ID, 0);
        if (id == 0 || id != sId)
            return;
        mPending = sCarried;
        mPendingFromSettings = saved.getBoolean(ARG_FROM_SETTINGS, false);
        sCarried = null;
        sId = 0;
    }

    /** The handover waiting to be opened, or null. */
    @Nullable
    public AppearanceSurfaceController.Relaunch pending() {
        return mPending;
    }

    /** Whether the waiting handover's surface was opened from Settings. */
    public boolean pendingFromSettings() {
        return mPendingFromSettings;
    }

    /**
     * The reopen of {@code carried} is running now: true when it is still the waiting handover,
     * which it then stops being; false when it was opened or passed on already.
     */
    public boolean claim(@NonNull AppearanceSurfaceController.Relaunch carried) {
        if (mPending != carried)
            return false;
        mPending = null;
        return true;
    }

    @VisibleForTesting
    static void resetForTest() {
        sCarried = null;
        sId = 0;
    }
}
