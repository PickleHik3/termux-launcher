package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;
import com.termux.app.tour.TourEdition;
import com.termux.app.x11.DistroSetupStore;
import com.termux.app.x11.GuiAppsSetup;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.TermuxConstants;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * "Get GUI apps": the two ways to have an app with a window on this phone, and the one command
 * that takes whichever the user picked.
 *
 * <p>The screen installs nothing itself. It builds a command with {@link GuiAppsSetup}, puts it on
 * the clipboard and steps out of the way, and the user pastes it into the terminal. That is the
 * whole design: the work is minutes of downloading and one password typed in, and a terminal shows
 * both far better than a progress bar that can only say "wait" and "sorry".
 *
 * <p>The choices are ordinary preferences in the launcher's own preferences file, so coming back
 * to the screen finds what was picked last time rather than the defaults again.
 */
@Keep
public final class GuiAppsSetupPreferencesFragment extends MaterialPreferenceFragment {

    static final String KEY_HINT = "gui_apps_hint";
    static final String KEY_ROUTE = "gui_apps_route";
    static final String KEY_DISTRO = "gui_apps_distro";
    static final String KEY_COPY = "gui_apps_copy";

    /** How long the launcher is given to come forward before the notice is raised on it. */
    private static final long NOTICE_DELAY_MS = 350L;

    /** The Intent that opens this screen from outside Settings. */
    @NonNull
    public static Intent intent(@NonNull Context context) {
        return SettingsActivity.createFragmentIntent(context,
            GuiAppsSetupPreferencesFragment.class, R.string.settings_gui_apps_title);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setSharedPreferencesName(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION);
        setPreferencesFromResource(R.xml.gui_apps_setup_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);

        List<GuiAppsSetup.Route> routes =
            GuiAppsSetup.routesFor(TourEdition.of(context.getPackageName()));
        ListPreference route = findPreference(KEY_ROUTE);
        if (routes.size() == 1) {
            // Nothing to choose: hide the row, and force the stored value to the one route this
            // edition offers, so a choice stored before the gate existed cannot build a command
            // for a route the screen no longer even shows.
            String only = routes.get(0).key;
            if (route != null) {
                route.setVisible(false);
                route.setValue(only);
            }
            applyDistroRow(only);
            Preference hint = findPreference(KEY_HINT);
            if (hint != null) hint.setSummary(R.string.settings_gui_apps_intro_distro_only);
        } else if (route != null) {
            applyDistroRow(route.getValue());
            route.setOnPreferenceChangeListener((preference, value) -> {
                applyDistroRow(String.valueOf(value));
                return true;
            });
        }
        Preference copy = findPreference(KEY_COPY);
        if (copy != null) copy.setOnPreferenceClickListener(preference -> {
            copyCommand(preference.getContext());
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_gui_apps_title);
    }

    /** Which Linux to put inside is only a question for the route that puts one inside. */
    private void applyDistroRow(@Nullable String route) {
        Preference distro = findPreference(KEY_DISTRO);
        if (distro != null) {
            distro.setVisible(GuiAppsSetup.Route.of(route) == GuiAppsSetup.Route.DISTRO);
        }
    }

    /**
     * The one action: the command goes on the clipboard, Settings closes so the terminal is in
     * front, and the notice follows it there.
     *
     * <p>The notice is raised after the close rather than before it, and against the application
     * rather than this activity, so the pill lands on the launcher the user is being sent to
     * instead of flashing on a screen that is already going away.
     */
    private void copyCommand(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        ShareUtils.copyTextToClipboard(appContext,
            getString(R.string.settings_gui_apps_clipboard_label), command(), null);
        rememberTheOfferWasAnswered(appContext);
        Activity activity = getActivity();
        if (activity != null) activity.finish();
        // A handler of its own, not the fragment's: finish() above can run onDestroy() inside
        // the delay, and the fragment's handler is cleared there, which would drop the notice.
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> AppNotice.show(appContext, R.string.settings_gui_apps_copied), NOTICE_DELAY_MS);
    }

    /**
     * Copying is what answers the Display place's offer, so the place stops making it.
     *
     * <p>It is recorded here rather than when the screen opened: coming this far is the user
     * having what they came for, and someone who looked at the screen and backed out of it should
     * still be offered it. The situation is read fresh, so a change to it since the offer was made
     * is what gets remembered, and any later change is a new situation and is offered once more.
     */
    private void rememberTheOfferWasAnswered(@NonNull Context appContext) {
        try {
            DistroSetupStore.dismissCurrent(appContext);
        } catch (RuntimeException e) {
            // Nothing the user asked for depends on this; the command is already on the clipboard.
        }
    }

    /** What the screen currently adds up to. */
    @NonNull
    String command() {
        ListPreference route = findPreference(KEY_ROUTE);
        ListPreference distro = findPreference(KEY_DISTRO);
        return GuiAppsSetup.command(
            GuiAppsSetup.Route.of(route == null ? null : route.getValue()),
            GuiAppsSetup.Distro.of(distro == null ? null : distro.getValue()),
            starters());
    }

    /** The ticked apps, read off their rows. */
    @NonNull
    private Set<GuiAppsSetup.StarterApp> starters() {
        Set<GuiAppsSetup.StarterApp> ticked = EnumSet.noneOf(GuiAppsSetup.StarterApp.class);
        for (GuiAppsSetup.StarterApp app : GuiAppsSetup.StarterApp.values()) {
            Preference row = findPreference("gui_apps_starter_" + app.key);
            if (row instanceof TwoStatePreference && ((TwoStatePreference) row).isChecked()) {
                ticked.add(app);
            }
        }
        return ticked;
    }
}
