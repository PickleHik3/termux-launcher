package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The status bar's own page: the 12-hour choice and the CPU/memory/weather cards, with the place
 * the weather follows when it should not follow the device. Media and pinned
 * notifications, and the rules that decide which ones stay pinned, live on the Notifications page;
 * the last row here links to it.
 *
 * <p>Splits the status half out of the old combined Terminal &amp; status page; the terminal half
 * is now {@link TerminalPreferencesFragment}. The clock's face and position, and the status bar's
 * surface, are set in the Appearance editor, so this page repeats none of them.
 */
@Keep
public final class StatusBarPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(TerminalIOPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.status_bar_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        StatusWidgetPrivilegedGate.attach(context, findPreference("status_widget_cpu"));
        StatusWidgetPrivilegedGate.attach(context, findPreference("status_widget_ram"));
        bindWeatherPlace(findPreference("status_widget_weather_location"));
    }

    /**
     * The row reads back the place the weather follows, or says it follows the device when none is
     * set; an empty summary would leave the user guessing which of the two is in effect. A tap opens
     * the picker, which stores the place itself (label and coordinates together), so the row is not
     * persisted through the data store.
     */
    private void bindWeatherPlace(@Nullable Preference place) {
        if (place == null) return;
        updateWeatherPlaceSummary(place);
        place.setOnPreferenceClickListener(preference -> {
            // The fragment's own context: the dialog takes its Material theme from the activity.
            Context context = getContext();
            if (context == null) return false;
            WeatherPlacePickerDialog.show(context, () -> updateWeatherPlaceSummary(preference));
            return true;
        });
    }

    private static void updateWeatherPlaceSummary(@NonNull Preference place) {
        Context context = place.getContext();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        String label = preferences == null ? "" : preferences.getStatusWidgetWeatherLocation();
        place.setSummary(label.isEmpty()
            ? context.getString(R.string.settings_weather_location_summary_device) : label);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_status_bar);
        }
    }
}
