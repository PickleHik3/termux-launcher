package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.widget.EditText;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.statusbar.WeatherGeocoder;
import com.termux.app.statusbar.WeatherPlaceSearchView;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Picks the place the weather follows, from live Open-Meteo search results under a text field.
 * The search itself is {@link WeatherPlaceSearchView}, which the first-run card uses too; this is
 * the dialog around it.
 *
 * <p>Tapping a result stores its label and coordinates together, so the weather is fetched for
 * exactly the place that was tapped without searching again. "Use device location" clears both.
 */
final class WeatherPlacePickerDialog {

    private WeatherPlacePickerDialog() {}

    /** Shows the picker; {@code onChanged} runs after a place is picked or cleared. */
    static void show(@NonNull Context context, @Nullable Runnable onChanged) {
        float density = context.getResources().getDisplayMetrics().density;
        int padH = Math.round(24 * density);
        WeatherPlaceSearchView search = new WeatherPlaceSearchView(context);
        search.setPadding(padH, Math.round(8 * density), padH, 0);

        // A custom view is not scrolled by the dialog; wrap it so eight results and the buttons
        // stay reachable above the keyboard.
        ScrollView scroll = new ScrollView(context);
        scroll.addView(search);

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        String current = preferences == null ? "" : preferences.getStatusWidgetWeatherLocation();
        search.showStatus(current.isEmpty()
            ? context.getString(R.string.settings_weather_location_summary_device)
            : context.getString(R.string.settings_weather_location_current, current));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_weather_location_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null);
        // Only offered when there is something to go back from.
        if (!current.isEmpty()) {
            builder.setNeutralButton(R.string.settings_weather_location_use_device, (d, w) -> {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                if (prefs != null) prefs.clearStatusWidgetWeatherPlace();
                if (onChanged != null) onChanged.run();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> search.cancelSearch());
        search.setListener(new WeatherPlaceSearchView.Listener() {
            @Override
            public void onPlacePicked(@NonNull WeatherGeocoder.Result place) {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                if (prefs != null)
                    prefs.setStatusWidgetWeatherPlace(place.label(), place.latitude, place.longitude);
                if (onChanged != null) onChanged.run();
                dialog.dismiss();
            }

            @Override
            public void onSearchFocusChanged(@NonNull EditText field, boolean focused) {
                // A dialog window brings the system keyboard up for its own field.
            }
        });
        dialog.setOnShowListener(d -> search.field().requestFocus());
        dialog.show();
    }
}
