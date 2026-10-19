package com.termux.app.fragments.settings.termux;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.launcher.home.HomeGestureActions;
import com.termux.app.launcher.home.HomeGestures;
import com.termux.app.launcher.home.HomeSettings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Home › Gestures: one list row per gesture, its choices filled from
 * {@link HomeGestures#choices} each time the page shows, so an action that needs the accessibility
 * service or Shizuku appears as soon as one is turned on, and a row below says what to turn on
 * while neither is. Picking Open an app asks which app first; the row then names it.
 */
final class HomeGesturesRows {

    static final String KEY_HELPER = "home_gestures_helper";

    private HomeGesturesRows() {}

    /** Fills every gesture row for what the phone offers now; call on create and on resume. */
    static void bind(@NonNull PreferenceFragmentCompat page) {
        Context context = page.getContext();
        if (context == null) return;
        HomeGestures.Availability availability = availability(context);
        for (HomeGestures.Gesture gesture : HomeGestures.Gesture.values()) {
            Preference found = page.findPreference(gesture.key);
            if (!(found instanceof ListPreference)) continue;
            ListPreference row = (ListPreference) found;
            HomeGestures.Action current = HomeSettings.gestureAction(context, gesture);
            List<HomeGestures.Action> choices = HomeGestures.choices(current, availability);
            CharSequence[] entries = new CharSequence[choices.size()];
            CharSequence[] values = new CharSequence[choices.size()];
            for (int i = 0; i < choices.size(); i++) {
                entries[i] = context.getString(titleOf(choices.get(i)));
                values[i] = HomeGestures.storedValue(gesture, choices.get(i));
            }
            row.setEntries(entries);
            row.setEntryValues(values);
            String stored = HomeGestures.storedValue(gesture, current);
            if (!stored.equals(row.getValue())) row.setValue(stored);
            row.setSummary(summaryOf(context, gesture, current));
            row.setOnPreferenceChangeListener((preference, newValue) -> {
                HomeGestures.Action picked = HomeGestures.parse(gesture,
                    newValue == null ? null : newValue.toString());
                if (picked == HomeGestures.Action.APP) {
                    chooseApp(page, gesture, row);
                    return false;
                }
                row.setSummary(summaryOf(context, gesture, picked));
                return true;
            });
        }
        Preference helper = page.findPreference(KEY_HELPER);
        if (helper != null) {
            helper.setVisible(HomeGestures.helperMissing(availability));
            helper.setOnPreferenceClickListener(preference -> {
                try {
                    page.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    // Nothing to open on this phone; the row's sentence still says what to do.
                }
                return true;
            });
        }
    }

    @NonNull
    static HomeGestures.Availability availability(@NonNull Context context) {
        return new HomeGestures.Availability(HomeGestureActions.helperAvailable(context),
            isDrawerEnabled(context));
    }

    private static boolean isDrawerEnabled(@NonNull Context context) {
        return TermuxStylePreferencesDataStore.getInstance(context).getBoolean(
            "app_launcher_drawer_enabled", true);
    }

    @StringRes
    static int titleOf(@NonNull HomeGestures.Action action) {
        switch (action) {
            case PALETTE: return R.string.settings_home_gesture_action_palette;
            case DRAWER: return R.string.settings_home_gesture_action_drawer;
            case NOTIFICATIONS: return R.string.settings_home_gesture_action_notifications;
            case QUICK_SETTINGS: return R.string.settings_home_gesture_action_quick_settings;
            case LOCK_SCREEN: return R.string.settings_home_gesture_action_lock;
            case TERMINAL: return R.string.settings_home_gesture_action_terminal;
            case APP: return R.string.settings_home_gesture_action_app;
            case NONE:
            default: return R.string.settings_home_gesture_action_none;
        }
    }

    @NonNull
    private static CharSequence summaryOf(@NonNull Context context, @NonNull HomeGestures.Gesture gesture,
                                          @NonNull HomeGestures.Action action) {
        if (action == HomeGestures.Action.APP) {
            CharSequence label = appLabel(context, HomeSettings.gestureApp(context, gesture));
            if (label != null) return context.getString(R.string.settings_home_gesture_opens_app, label);
        }
        return context.getString(titleOf(action));
    }

    @Nullable
    private static CharSequence appLabel(@NonNull Context context, @Nullable String app) {
        ComponentName component = app == null ? null : ComponentName.unflattenFromString(app);
        if (component == null) return null;
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
            return info.loadLabel(context.getPackageManager());
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Asks which app the gesture opens: the phone's launchable apps by name, read off the main
     * thread. A pick stores the app and sets the row to Open an app; cancelling changes nothing.
     */
    private static void chooseApp(@NonNull Fragment page, @NonNull HomeGestures.Gesture gesture,
                                  @NonNull ListPreference row) {
        Context context = page.getContext();
        if (context == null) return;
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<String> labels = new ArrayList<>();
            List<String> components = new ArrayList<>();
            loadApps(app, labels, components);
            main.post(() -> {
                Context live = page.getContext();
                if (live == null || !page.isAdded() || labels.isEmpty()) return;
                new MaterialAlertDialogBuilder(live)
                    .setTitle(R.string.settings_home_gesture_pick_app)
                    .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                        HomeSettings.putString(live, gesture.appKey(), components.get(which));
                        // Straight to the value: the row's change listener is what asked.
                        row.setValue(HomeGestures.storedValue(gesture, HomeGestures.Action.APP));
                        row.setSummary(summaryOf(live, gesture, HomeGestures.Action.APP));
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        }, "home-gesture-apps").start();
    }

    private static void loadApps(@NonNull Context context, @NonNull List<String> labels,
                                 @NonNull List<String> components) {
        PackageManager pm = context.getPackageManager();
        Intent launchable = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(launchable, 0);
        } catch (RuntimeException e) {
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            String label = String.valueOf(info.loadLabel(pm));
            rows.add(new String[] {label, new ComponentName(info.activityInfo.packageName,
                info.activityInfo.name).flattenToString()});
        }
        Collator collator = Collator.getInstance();
        rows.sort((a, b) -> collator.compare(a[0], b[0]));
        for (String[] row : rows) {
            labels.add(row[0]);
            components.add(row[1]);
        }
    }
}
