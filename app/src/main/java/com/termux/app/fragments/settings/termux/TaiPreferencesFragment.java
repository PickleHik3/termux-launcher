package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusCardPreference;
import com.termux.app.notice.AppNotice;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * On-device AI overview: a short runtime card, the rows about models and voice, and the doors to
 * the Runtime, Local API and Advanced pages. Nothing secret is shown here; the pages that show
 * the endpoint or the logs keep themselves out of screenshots.
 */
@Keep
public class TaiPreferencesFragment extends MaterialPreferenceFragment implements TaiDownloadHub.Listener {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TaiRuntimePoll runtimePoll = new TaiRuntimePoll(this::isAdded);
    /** Model count for the Model centre row; re-read only when a download changes status. */
    private int installedCount = -1;
    @NonNull private String downloadStatuses = "";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        getPreferenceManager().setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_ai_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configureModelCentreRow();
        configureWelcomeCardRow();
        configureBenchmarkRow();
        configureHuggingFaceToken();
        openOnClick("tai_runtime_controls", TaiRuntimePreferencesFragment.class, R.string.termux_ai_runtime_screen_title);
        openOnClick("tai_voice_input", KeyboardVoicePreferencesFragment.class, R.string.settings_keyboard_sub_voice_title);
        // A bring-your-own-key server; its own screen, pushed in place so Back returns here.
        openOnClick("tai_remote_provider", TaiRemotePreferencesFragment.class, R.string.tai_remote_screen_title);
        openOnClick("tai_local_api", TaiApiPreferencesFragment.class, R.string.termux_ai_local_api_title);
        openOnClick("tai_advanced", TaiAdvancedPreferencesFragment.class, R.string.termux_ai_zone_advanced_title);
    }

    private void openOnClick(String key, Class<? extends Fragment> page, @StringRes int titleRes) {
        Preference row = findPreference(key);
        if (row == null) return;
        row.setOnPreferenceClickListener(preference -> {
            if (getActivity() instanceof SettingsActivity) {
                ((SettingsActivity) getActivity()).openScreen(page, titleRes, null);
            }
            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Context context = getContext();
        // The Model centre row's summary and progress line follow the hub's pushes; no polling.
        if (context != null) TaiDownloadHub.get(context).addListener(this);
    }

    @Override
    public void onStop() {
        Context context = getContext();
        if (context != null) TaiDownloadHub.get(context).removeListener(this);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.termux_ai_preferences_title);
        Context context = getContext();
        if (context != null) {
            runtimePoll.start(context, this::updateRuntimeStatus);
            refreshBenchmarkRow(context);
            refreshRemoteRow(context);
        }
    }

    @Override
    public void onPause() {
        runtimePoll.stop();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        runtimePoll.shutdown();
        super.onDestroy();
    }

    /** The concise card: what the runtime is doing, what is loaded and when it unloads. */
    private void updateRuntimeStatus(@Nullable JSONObject runtimeStatus) {
        Preference status = findPreference("tai_runtime_status");
        if (!(status instanceof StatusCardPreference)) return;
        TaiRuntimeStatusText.Headline headline = TaiRuntimeStatusText.headline(runtimeStatus);
        ((StatusCardPreference) status).setStatus(getString(headline.label), headline.active);
        String body = TaiRuntimeStatusText.brief(runtimeStatus);
        if (body.isEmpty()) status.setSummary(R.string.termux_ai_runtime_status_summary);
        else status.setSummary(body);
    }

    private void configureModelCentreRow() {
        Preference centre = findPreference("tai_model_centre");
        if (centre == null) return;
        centre.setOnPreferenceClickListener(preference -> {
            TaiModelCentreFragment.open(getActivity(), TaiModelCentreFragment.SEGMENT_INSTALLED);
            return true;
        });
    }

    /** Reopens the "What runs on this phone" card any time; it does not count as the automatic raise. */
    private void configureWelcomeCardRow() {
        Preference row = findPreference("tai_welcome_card");
        if (row == null) return;
        row.setOnPreferenceClickListener(preference -> {
            if (getActivity() != null) {
                com.termux.app.firstrun.TaiWelcomeCardHost.show(getActivity(), false, null);
            }
            return true;
        });
    }

    private void configureBenchmarkRow() {
        Preference row = findPreference("tai_benchmark");
        if (row == null) return;
        row.setOnPreferenceClickListener(preference -> {
            TaiBenchHomeFragment.open(getActivity(), null);
            return true;
        });
    }

    /**
     * Reads the last run and the fastest ranked speed off the main thread and, when there is a
     * result to show, appends it to the Benchmark row's summary. Cheap enough for {@link #onResume};
     * left at the plain summary otherwise.
     */
    private void refreshBenchmarkRow(@NonNull Context context) {
        Preference row = findPreference("tai_benchmark");
        if (row == null) return;
        Context appContext = context.getApplicationContext();
        runtimePoll.run(() -> {
            JSONObject benchmarks;
            try {
                benchmarks = TaiManager.getInstance(appContext).benchmarks();
            } catch (JSONException | RuntimeException e) {
                benchmarks = null;
            }
            TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks, TaiBenchHomeFragment.versions());
            double bestTps = board.bestTps();
            long lastRunMs = board.lastRunMs;
            handler.post(() -> {
                if (!isAdded()) return;
                Preference current = findPreference("tai_benchmark");
                if (current == null) return;
                if (bestTps > 0.0 && lastRunMs > 0L) {
                    current.setSummary(getString(R.string.tai_bench_pref_summary_run,
                        TaiBenchLeaderboard.formatTpsValue(bestTps), TaiBenchViews.ago(lastRunMs)));
                } else {
                    current.setSummary(R.string.tai_bench_pref_summary);
                }
            });
        });
    }

    /** Keeps the Model centre row's "N installed · M downloading" and its progress line current. */
    @Override
    public void onDownloadsChanged(@NonNull List<TaiDownloadHub.Snapshot> downloads) {
        Context context = getContext();
        TaiModelCentreRowPreference row = findPreference("tai_model_centre");
        if (context == null || row == null) return;
        StringBuilder statuses = new StringBuilder();
        List<TaiModelCentreRows.Input> inputs = new ArrayList<>(downloads.size());
        for (TaiDownloadHub.Snapshot item : downloads) {
            inputs.add(TaiModelCentreRows.Input.of(item));
            statuses.append(item.id).append('=').append(item.status).append(';');
        }
        // A finished download changes the installed count; a progress tick does not, so the
        // store is read only when some status moved.
        if (installedCount < 0 || !statuses.toString().equals(downloadStatuses)) {
            installedCount = new TaiModelStore(context).getInstalledUserModels().size();
            downloadStatuses = statuses.toString();
        }
        TaiModelCentreRows.Summary summary = TaiModelCentreRows.summarize(inputs);
        String text;
        if (summary.downloading > 0) text = getString(R.string.tai_model_centre_row_summary_busy, installedCount, summary.downloading);
        else if (summary.paused > 0) text = getString(R.string.tai_model_centre_row_summary_paused, installedCount, summary.paused);
        else text = getString(R.string.tai_model_centre_row_summary, installedCount);
        if (!TextUtils.equals(text, row.getSummary())) row.setSummary(text);
        row.setProgress(summary.downloading > 0, summary.progress);
    }

    /** "model on host" once a remote model is set up; the plain invitation otherwise. */
    private void refreshRemoteRow(@NonNull Context context) {
        Preference row = findPreference("tai_remote_provider");
        if (row == null) return;
        String summary = TaiRemotePreferencesFragment.rowSummary(context);
        row.setSummary(summary != null ? summary : getString(R.string.tai_remote_row_summary_off));
    }

    private void configureHuggingFaceToken() {
        Preference token = findPreference(TaiSettings.KEY_HUGGINGFACE_TOKEN);
        if (token == null) return;
        updateHuggingFaceTokenSummary(token);
        token.setOnPreferenceClickListener(preference -> {
            Context context = getContext();
            if (context == null) return true;
            TaiHuggingFaceTokenDialog.show(context, () -> {
                updateHuggingFaceTokenSummary(token);
                AppNotice.show(context, R.string.termux_ai_huggingface_token_saved, false);
            });
            return true;
        });
    }

    private void updateHuggingFaceTokenSummary(@NonNull Preference preference) {
        String value = preference.getContext()
            .getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TaiSettings.KEY_HUGGINGFACE_TOKEN, "");
        preference.setSummary(value == null || value.trim().isEmpty()
            ? getString(R.string.termux_ai_huggingface_token_summary)
            : getString(R.string.termux_ai_huggingface_token_set_summary));
    }
}
