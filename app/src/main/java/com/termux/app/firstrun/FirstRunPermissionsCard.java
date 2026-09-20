package com.termux.app.firstrun;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the first-run permissions card offers, and whether it is offered at all.
 *
 * <p>The three things the launcher wants on the way in — the wallpaper read, the weather's rough
 * location, the Linux display — used to be three dialogs in a row, each raised from the last one's
 * result, so a fresh install opened on a stack of system prompts before the user had seen the
 * launcher at all. They are one card now, the way an established app asks: a row per item, each
 * with what it is for and a control, and one Continue.
 *
 * <p>Pure, so the whole decision table below — granted, denied or never asked, on a fresh install
 * or an update — is a unit test rather than a run through the installer.
 */
public final class FirstRunPermissionsCard {

    /** The three things the card asks about, in the order it reads them. */
    public enum Item {
        /** Reading the system wallpaper, which is what the glass bands are coloured from. */
        WALLPAPER,
        /** Coarse location, which is all the weather widget needs. */
        WEATHER,
        /** The embedded Linux display, which is a setting rather than a permission. */
        DISPLAY
    }

    /** Where one runtime permission stands. */
    public enum State {
        /** Never asked for on this install. */
        NOT_ASKED,
        /** The user allowed it. */
        GRANTED,
        /** The user refused it, here or in the system settings. */
        DENIED
    }

    /** What tapping a row's button has to do. */
    public enum Tap {
        /** Raise the system permission dialog. */
        REQUEST,
        /**
         * Open this app's settings page. Android stops showing the dialog after the second
         * refusal, and a button that silently does nothing is worse than no button at all.
         */
        OPEN_SETTINGS,
        /** Nothing to do: the row is already satisfied. */
        NONE
    }

    /** One row of the card: what it is called, what it is for, and where it stands. */
    public static final class Row {

        public final Item item;
        @StringRes public final int titleRes;
        @StringRes public final int copyRes;
        /**
         * Where the row stands. A switch row reads {@link State#GRANTED} while it is on and
         * {@link State#NOT_ASKED} while it is off — it is never refused, only left alone.
         */
        public final State state;
        /** Whether the control is a switch rather than a button. */
        public final boolean isSwitch;

        Row(Item item, @StringRes int titleRes, @StringRes int copyRes, State state,
            boolean isSwitch) {
            this.item = item;
            this.titleRes = titleRes;
            this.copyRes = copyRes;
            this.state = state;
            this.isSwitch = isSwitch;
        }

        /** Whether a switch row is on. */
        public boolean isOn() {
            return isSwitch && state == State.GRANTED;
        }

        /**
         * Whether this row carries a button. A permission already granted shows its state and
         * nothing to press; a refused one keeps the button, so a second tap can ask again.
         */
        public boolean hasButton() {
            return !isSwitch && state != State.GRANTED;
        }

        /** The button's label, or 0 on a row that carries none. */
        @StringRes
        public int buttonRes() {
            return hasButton() ? R.string.first_run_permissions_allow : 0;
        }

        /** The word beside a permission row that has already been answered, or 0. */
        @StringRes
        public int statusRes() {
            if (isSwitch) return 0;
            switch (state) {
                case GRANTED: return R.string.first_run_permissions_allowed;
                case DENIED: return R.string.first_run_permissions_denied;
                default: return 0;
            }
        }
    }

    /**
     * Whether the card comes up.
     *
     * <p>Decision (user, 2026-09-20): it is the first thing on a fresh install, before the tour's
     * welcome card, whatever state the permissions happen to be in — it is how the launcher
     * introduces itself. An install that has already been through the old chain sees it only when
     * something on it is still ungranted, and only once: an update is not an excuse to ask again
     * for what was already refused twice.
     *
     * @param firstRunChainDone whether this install has already been through the first-run chain
     * @param cardSeen          whether this card has already been shown and answered
     * @param wallpaper         where the wallpaper read stands
     * @param weather           where the location permission stands, or null when the weather
     *                          widget is switched off and the row is not offered at all
     * @param replay            whether Settings asked for the tour again. A replay is the tour and
     *                          nothing else — the user asked to be walked through the launcher,
     *                          not to be asked for permissions a second time — so it answers no
     *                          before anything else is considered.
     */
    public static boolean shouldShow(boolean firstRunChainDone, boolean cardSeen,
                                     @NonNull State wallpaper, @Nullable State weather,
                                     boolean replay) {
        if (replay) return false;
        if (!firstRunChainDone) return true;
        if (cardSeen) return false;
        return wallpaper != State.GRANTED || (weather != null && weather != State.GRANTED);
    }

    /**
     * The card's rows, in order. A row is left out rather than shown dead: there is no weather row
     * on an install with the weather widget switched off, and no display row in a build with no
     * display server in it.
     *
     * @param weather        where the location permission stands, or null for no weather row
     * @param displayOffered whether this build carries a display server at all
     * @param displayOn      whether the embedded display is switched on right now
     */
    @NonNull
    public static List<Row> rows(@NonNull State wallpaper, @Nullable State weather,
                                 boolean displayOffered, boolean displayOn) {
        List<Row> rows = new ArrayList<>(3);
        rows.add(new Row(Item.WALLPAPER, R.string.first_run_permissions_wallpaper_title,
            R.string.first_run_permissions_wallpaper_copy, wallpaper, false));
        if (weather != null)
            rows.add(new Row(Item.WEATHER, R.string.first_run_permissions_weather_title,
                R.string.first_run_permissions_weather_copy, weather, false));
        if (displayOffered)
            rows.add(new Row(Item.DISPLAY, R.string.first_run_permissions_display_title,
                R.string.first_run_permissions_display_copy,
                displayOn ? State.GRANTED : State.NOT_ASKED, true));
        return Collections.unmodifiableList(rows);
    }

    /**
     * What a tap on a permission row's button does.
     *
     * @param canAskAgain whether the system will still raise its own dialog for this permission
     */
    @NonNull
    public static Tap tapFor(@NonNull State state, boolean canAskAgain) {
        if (state == State.GRANTED) return Tap.NONE;
        return canAskAgain ? Tap.REQUEST : Tap.OPEN_SETTINGS;
    }

    private FirstRunPermissionsCard() {}
}
