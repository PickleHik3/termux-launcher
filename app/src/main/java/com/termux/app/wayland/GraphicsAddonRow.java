package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.AppEdition;

/**
 * The "Graphics add-on" row of Set up Linux apps: whether it shows, what it says for each
 * {@link MesaWaylandOverlay.State} and what its button does. Pure; the screen applies the answer.
 */
public final class GraphicsAddonRow {

    /** What the row's button does. */
    public enum Action {
        /** No button: nothing to do (installed, or the packages it builds on are missing). */
        NONE,
        /** Copy the command that installs or updates the add-on. */
        COPY_INSTALL
    }

    /** The command that installs or updates the add-on, run in a terminal. */
    public static final String INSTALL_COMMAND = "tlstore install " + MesaWaylandOverlay.ITEM_ID;

    public static final class Row {
        @StringRes public final int summary;
        @NonNull public final Action action;

        Row(@StringRes int summary, @NonNull Action action) {
            this.summary = summary;
            this.action = action;
        }
    }

    private GraphicsAddonRow() {}

    /** The row's title; the same for every state. */
    @StringRes
    public static int title() {
        return R.string.settings_gui_apps_addon_title;
    }

    /**
     * Whether the row shows: only in a build with Wayland Display, with Wayland Display chosen,
     * on the edition the add-on is built for (the stock package).
     */
    public static boolean shows(boolean waylandBuilt, @NonNull LinuxDisplayBackend display,
                                @NonNull AppEdition edition) {
        return waylandBuilt && display == LinuxDisplayBackend.WAYLAND && edition == AppEdition.TERMUX;
    }

    /**
     * The row for {@code state}. {@code listed} is whether the store's catalogue offers the
     * add-on; an add-on that is not installed and not offered says so instead of offering it.
     */
    @NonNull
    public static Row of(@NonNull MesaWaylandOverlay.State state, boolean listed) {
        switch (state) {
            case USE:
                return new Row(R.string.settings_gui_apps_addon_installed, Action.NONE);
            case NO_MESA:
                return new Row(R.string.settings_gui_apps_addon_no_mesa, Action.NONE);
            case MISMATCH:
                return listed
                    ? new Row(R.string.settings_gui_apps_addon_update, Action.COPY_INSTALL)
                    : new Row(R.string.settings_gui_apps_addon_unavailable, Action.NONE);
            case MISSING:
            default:
                return listed
                    ? new Row(R.string.settings_gui_apps_addon_install, Action.COPY_INSTALL)
                    : new Row(R.string.settings_gui_apps_addon_unavailable, Action.NONE);
        }
    }

    /** Whether any of the catalogue texts (the refreshed one, the shipped one) names the add-on. */
    public static boolean listed(@NonNull String... catalogues) {
        for (@Nullable String text : catalogues) {
            if (text != null && text.contains(MesaWaylandOverlay.ITEM_ID)) return true;
        }
        return false;
    }
}
