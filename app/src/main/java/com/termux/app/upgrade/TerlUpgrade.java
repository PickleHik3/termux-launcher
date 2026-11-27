package com.termux.app.upgrade;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.app.place.LayoutReset;
import com.termux.app.terminal.TerminalWorkspaceStore;
import com.termux.app.tiling.TilingDesktopStore;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;

/**
 * The one-time upgrade from v1 to Terl (ADR 0037): the look and the layout start fresh, behaviour
 * settings that still have a row in Terl keep their values, retired settings go
 * ({@link TerlUpgradeFates}, {@link TerlUpgradePlan}). No appearance reset number is raised.
 *
 * <p>Besides the preferences it resets v1's layouts as a new install has them (Home's widget grid
 * stays, {@link LayoutReset}) and deletes v1's arrangement autosave and Terl's saved desktop, so
 * Terl opens on its own first arrangement. {@code $HOME} otherwise, {@code $PREFIX},
 * {@code termux.properties}, tlstore items, the Assistant's models and settings, notification
 * history, the wallpaper and Home's pages are not touched.
 */
public final class TerlUpgrade {

    private TerlUpgrade() {}

    private static final String LOG_TAG = "TerlUpgrade";

    /**
     * Runs the upgrade unless this install already has; never throws (a failure is logged and the
     * upgrade runs again next start). Call it in the start-up chain before the look steps, as
     * {@code AppearanceReset.runAtStartUp} is.
     *
     * <p>Nothing calls it yet: until the switch-over (#444) Terl is a developer switch over a v1
     * install, and v1 still uses what this removes. #444 calls it when Terl becomes the app.
     *
     * @return whether it ran on this call
     */
    public static boolean runOnce(@NonNull Context context) {
        try {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
            if (prefs == null) return false;
            return run(context, prefs, TermuxConstants.TERMUX_HOME_DIR);
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The upgrade to Terl failed", t);
            return false;
        }
    }

    @VisibleForTesting
    static boolean run(@NonNull Context context, @NonNull TermuxAppSharedPreferences prefs,
                       @NonNull File homeDir) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null) return false;
        TerlUpgradePlan plan = TerlUpgradePlan.of(store.getAll());
        if (!plan.due) return false;

        // The layouts are read while their flat copies are still stored, then written whole.
        try {
            LayoutReset.write(context, LayoutReset.prepare(prefs, context));
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The layouts were not reset", t);
        }
        File workspaces = new File(new File(homeDir, ".termux"), "workspaces");
        delete(new File(workspaces, TerminalWorkspaceStore.AUTOSAVE_FILE));
        delete(TilingDesktopStore.inHome(homeDir).file());

        // Last, in one commit with the marker: a start that dies before it runs the upgrade again.
        SharedPreferences.Editor editor = store.edit();
        for (String key : plan.removed) editor.remove(key);
        editor.putInt(TerlUpgradePlan.KEY_DONE, TerlUpgradePlan.VERSION);
        if (!editor.commit()) {
            Logger.logError(LOG_TAG, "The upgrade to Terl did not write; it runs next start");
            return false;
        }
        Logger.logInfo(LOG_TAG, "Upgraded to Terl: " + plan.removed.size() + " settings removed");
        return true;
    }

    private static void delete(@NonNull File file) {
        if (file.isFile() && !file.delete())
            Logger.logError(LOG_TAG, "Could not delete " + file.getName());
    }
}
