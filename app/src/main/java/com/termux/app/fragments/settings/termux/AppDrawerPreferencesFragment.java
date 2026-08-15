package com.termux.app.fragments.settings.termux;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.ai.TaiModelSpec;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.launcher.data.LauncherCategoryPasteNotification;
import com.termux.app.launcher.data.LauncherCategorySortProgress;
import com.termux.app.launcher.data.LauncherCategorySortPrompt;
import com.termux.app.launcher.data.LauncherCategorySortService;
import com.termux.app.launcher.data.LauncherCategorySortState;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dedicated drawer controls. Visibility changes immediately; persistence drives live apply.
 *
 * <p>Only the view type is a preference. Icon size and the per-view column/row counts were removed:
 * each view resolves its geometry from the plane's width, and the category cards size their preview
 * icons to fill the card, so a user-chosen size could only reintroduce dead space.
 *
 * <p>Categorization is not a preference either: it is a run, so its two rows are click actions and
 * their state is polled off {@link LauncherCategorySortService}'s static progress fields.
 */
@Keep
public final class AppDrawerPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_CATEGORY_SORT = "app_launcher_category_sort";
    private static final String KEY_CATEGORY_REFRESH = "app_launcher_category_refresh";
    private static final String KEY_VIEW_TYPE = "app_launcher_drawer_view_type";
    /** The only drawer layout that renders categories, so the only one the two rows apply to. */
    private static final String VIEW_TYPE_CATEGORIES = "categories";
    private static final long POLL_INTERVAL_MS = 700L;
    private static final int REQUEST_POST_NOTIFICATIONS = 4711;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService appListExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "app-drawer-category-apps");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            Context context = getContext();
            if (context == null) return;
            updateRefreshSummary(context);
            if (LauncherCategorySortService.isRunning()) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            } else {
                // The run ended between two ticks: the summary above already shows the terminal
                // state, so all that is left is the failure the service could not report itself.
                surfaceTerminalError(context);
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.app_drawer_preferences, rootKey);
        configureCategoryPreferences(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context == null) return;
        // The layout can also be changed from the launcher itself, so re-gate on every resume, and
        // off the store rather than the bound preference — the bound value is a stale cache then.
        applyCategoryVisibility(storedViewType());
        updateRefreshSummary(context);
        if (LauncherCategorySortService.isRunning()) handler.postDelayed(refreshRunnable, POLL_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        appListExecutor.shutdownNow();
        super.onDestroy();
    }

    private void configureCategoryPreferences(@NonNull Context context) {
        ListPreference viewType = findPreference(KEY_VIEW_TYPE);
        if (viewType != null) {
            applyCategoryVisibility(storedViewType());
            viewType.setOnPreferenceChangeListener((preference, newValue) -> {
                // Fired before the new value is stored, so gate on the incoming one.
                applyCategoryVisibility(newValue == null ? null : newValue.toString());
                return true;
            });
        }
        Preference sort = findPreference(KEY_CATEGORY_SORT);
        if (sort != null) {
            sort.setPersistent(false);
            sort.setOnPreferenceClickListener(preference -> {
                openChooser(context);
                return true;
            });
        }
        Preference refresh = findPreference(KEY_CATEGORY_REFRESH);
        if (refresh != null) {
            refresh.setPersistent(false);
            refresh.setOnPreferenceClickListener(preference -> {
                startRefresh(context);
                return true;
            });
        }
        updateRefreshSummary(context);
    }

    @Nullable
    private String storedViewType() {
        androidx.preference.PreferenceDataStore store =
            getPreferenceManager().getPreferenceDataStore();
        if (store == null) {
            ListPreference viewType = findPreference(KEY_VIEW_TYPE);
            return viewType == null ? null : viewType.getValue();
        }
        return store.getString(KEY_VIEW_TYPE, null);
    }

    /**
     * Categorization only feeds the categories drawer, so on the other two layouts the rows are
     * hidden rather than disabled: nothing on this screen could make them do anything there.
     */
    private void applyCategoryVisibility(@Nullable String viewType) {
        boolean categories = VIEW_TYPE_CATEGORIES.equals(viewType);
        Preference sort = findPreference(KEY_CATEGORY_SORT);
        if (sort != null) sort.setVisible(categories);
        Preference refresh = findPreference(KEY_CATEGORY_REFRESH);
        if (refresh != null) refresh.setVisible(categories);
    }

    /** Loads the catalogue off the main thread — it is a blocking package-manager walk. */
    private void openChooser(@NonNull Context context) {
        requestNotificationPermissionIfNeeded(context);
        appListExecutor.execute(() -> {
            List<LauncherCategorySortPrompt.AppEntry> apps = CategorySortDialogs.loadApps(context);
            handler.post(() -> {
                if (!isAdded()) return;
                Context current = getContext();
                if (current == null) return;
                CategorySortDialogs.showChooser(current, apps,
                    () -> startSort(current, CategorySortDialogs.resolveModel(current)),
                    () -> updateRefreshSummary(current));
            });
        });
    }

    /**
     * Re-running is the common case, so it skips the chooser — unless no usable model is installed,
     * in which case the chooser is the screen that explains why and offers the paste route.
     */
    private void startRefresh(@NonNull Context context) {
        TaiModelSpec model = CategorySortDialogs.resolveModel(context);
        if (model == null || CategorySortDialogs.unavailableReason(context, model) != null) {
            openChooser(context);
            return;
        }
        startSort(context, model);
    }

    private void startSort(@NonNull Context context, @Nullable TaiModelSpec model) {
        if (model == null) {
            openChooser(context);
            return;
        }
        Intent intent = new Intent(context, LauncherCategorySortService.class);
        intent.setAction(LauncherCategorySortService.ACTION_SORT);
        intent.putExtra(LauncherCategorySortService.EXTRA_MODEL_ID, model.id);
        ContextCompat.startForegroundService(context, intent);
        updateRefreshSummary(context);
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, POLL_INTERVAL_MS);
    }

    private void updateRefreshSummary(@NonNull Context context) {
        CategorySortProgressPreference refresh = findPreference(KEY_CATEGORY_REFRESH);
        if (refresh == null) return;
        if (LauncherCategorySortService.isRunning()) {
            String phase = LauncherCategorySortService.getPhase();
            int processed = LauncherCategorySortService.getProcessed();
            int total = LauncherCategorySortService.getTotal();
            refresh.setSummary(getString(
                LauncherCategorySortProgress.hint(phase, processed, total), processed, total));
            refresh.setProgress(LauncherCategorySortProgress.percent(phase, processed, total), true,
                LauncherCategorySortProgress.isIndeterminate(phase));
            return;
        }
        refresh.setProgress(0, false, false);
        // A run that ended while the user was elsewhere: the row is re-created on their return, so
        // the last run's own words are what it should say, not the generic last-run line.
        String outcome = LauncherCategorySortService.getOutcome();
        if (outcome != null && !outcome.trim().isEmpty()) {
            refresh.setSummary(outcome);
            return;
        }
        LauncherCategorySortState state = new LauncherCategorySortState(context);
        if (!state.hasRun()) {
            refresh.setSummary(R.string.settings_app_drawer_category_refresh_never);
            return;
        }
        // Locale-formatted: a hardcoded pattern is wrong everywhere outside en-US.
        refresh.setSummary(getString(R.string.settings_app_drawer_category_refresh_last_run,
            DateUtils.formatDateTime(context, state.getLastRunEpochMs(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL),
            state.getAppCount()));
    }

    /**
     * The paste route's return leg is a notification with an inline reply, so ask for the
     * permission before the chooser rather than after the user has already copied the prompt and
     * left. Denial is not fatal: the paste-back dialog still works for a user who stays here.
     */
    private void requestNotificationPermissionIfNeeded(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (LauncherCategoryPasteNotification.canPost(context)) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            // Notifications are off for the whole app: a runtime prompt cannot fix that, and
            // sending the user to system settings mid-flow would be worse than the dialog route.
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
            REQUEST_POST_NOTIFICATIONS);
    }

    private void surfaceTerminalError(@NonNull Context context) {
        String error = LauncherCategorySortService.getErrorMessage();
        if (error == null || error.trim().isEmpty()) return;
        Toast.makeText(context,
            getString(R.string.settings_app_drawer_category_sort_failed, error),
            Toast.LENGTH_LONG).show();
    }
}
