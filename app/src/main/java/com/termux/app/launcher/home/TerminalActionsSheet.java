package com.termux.app.launcher.home;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.surfaces.EditorDoor;

import java.util.ArrayList;
import java.util.List;

/**
 * The terminal's More menu as rows for the shared bottom sheet ({@link HomeActionsSheet}): the
 * session's actions in the first group, the launcher's in the second.
 */
public final class TerminalActionsSheet {

    /** One More item, in the order the sheet stands. */
    public enum Action {
        COMMAND_PALETTE(0, R.string.action_command_palette, R.drawable.ic_symbol_command_palette),
        SELECT_URL(0, R.string.action_select_url, R.drawable.ic_symbol_link),
        SHARE_TRANSCRIPT(0, R.string.action_share_transcript, R.drawable.ic_symbol_share),
        RESET(0, R.string.action_reset_terminal, R.drawable.ic_symbol_restart),
        KILL_PROCESS(0, R.string.action_kill_process, R.drawable.ic_symbol_stop),
        APPEARANCE(1, R.string.action_wallpaper_style, R.drawable.ic_symbol_palette),
        /** Opens the wallpaper picker, with or without a Home page. */
        SET_WALLPAPER(1, R.string.home_actions_wallpaper, R.drawable.ic_symbol_wallpaper),
        /** Shows or hides the wallpaper behind the terminal. */
        SHOW_WALLPAPER(1, R.string.action_enable_background_image,
            R.drawable.ic_ed_show_wallpaper),
        STYLE(1, R.string.action_style_terminal, R.drawable.ic_symbol_tune),
        SETTINGS(1, R.string.action_open_settings, R.drawable.ic_settings);

        public final int group;
        @StringRes public final int label;
        @DrawableRes public final int icon;

        Action(int group, @StringRes int label, @DrawableRes int icon) {
            this.group = group;
            this.label = label;
            this.icon = icon;
        }

        /** The editor door this item opens, or null for an item that opens no door of its own. */
        @Nullable
        public EditorDoor door() {
            return this == SET_WALLPAPER ? EditorDoor.TERMINAL_SHEET_WALLPAPER : null;
        }

        /** Whether the row opens the Appearance editor, which waits for the sheet to slide away. */
        public boolean opensEditor() {
            return this == APPEARANCE || this == SET_WALLPAPER;
        }

        /**
         * Whether tapping the row starts loading the wallpaper picker while the sheet slides
         * away ({@link HomeActionsSheetPolicy#preparesWallpaper}).
         */
        public boolean preparesWallpaper() {
            EditorDoor door = door();
            return door != null && door.opensWallpaperPicker();
        }
    }

    private TerminalActionsSheet() {}

    /**
     * The rows for the current state: Show wallpaper says what a tap will do, Style stands only
     * while the companion is installed, and Kill process names the session's pid.
     */
    @NonNull
    public static List<ActionSheetRow> rows(@NonNull Context context, boolean wallpaperOn,
                                            boolean styleAvailable, int pid) {
        List<ActionSheetRow> rows = new ArrayList<>();
        for (Action action : Action.values()) {
            if (action == Action.STYLE && !styleAvailable) continue;
            CharSequence label;
            if (action == Action.KILL_PROCESS) {
                label = context.getString(action.label, pid);
            } else if (action == Action.SHOW_WALLPAPER) {
                label = context.getString(wallpaperOn
                    ? R.string.action_disable_background_image
                    : R.string.action_enable_background_image);
            } else {
                label = context.getString(action.label);
            }
            rows.add(new ActionSheetRow(action, label, action.icon, false, action.group,
                action.opensEditor()));
        }
        return rows;
    }
}
