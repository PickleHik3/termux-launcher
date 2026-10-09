package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiBenchStore;
import com.termux.ai.TaiManager;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.notice.AppNotice;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Result (spec Screen 6) for one leaderboard entry (a model on one processor, its key as
 * {@link SettingsActivity#EXTRA_INITIAL_PLACE}): the verdict with the three plain numbers (starts
 * replying, writes, reads a long page) and the memory, the details (each with its range, load, the
 * sanity check when it failed), the Chat reply collapsed, the history chart of that entry's kept
 * runs split where the app or runtime version changed, "Run this model again" (Choose with only this model selected) and "Delete this model's results"
 * ({@link TaiManager#clearBenchmarks} for the model, every processor).
 */
@Keep
public class TaiBenchResultFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-bench-result");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull private String key = "";
    private LinearLayout column;

    @NonNull
    public static Bundle arguments(@NonNull String key) {
        Bundle arguments = new Bundle();
        arguments.putString(SettingsActivity.EXTRA_INITIAL_PLACE, key);
        return arguments;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        String place = arguments == null ? null : arguments.getString(SettingsActivity.EXTRA_INITIAL_PLACE);
        key = place == null ? "" : place;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setId(R.id.tai_bench_scroll);
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, TaiBenchViews.dp(context, 8), 0, TaiBenchViews.dp(context, 32));
        scroll.addView(column, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(TaiBenchViews.body(context, getString(R.string.tai_bench_loading)), padded(context));
        return scroll;
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.tai_bench_result_title);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        Context context = getContext();
        if (context == null || executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            JSONObject benchmarks = null;
            try {
                benchmarks = TaiManager.getInstance(app).benchmarks();
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject loaded = benchmarks;
            handler.post(() -> {
                if (!isAdded()) return;
                render(loaded);
            });
        });
    }

    private void render(@Nullable JSONObject benchmarks) {
        Context context = getContext();
        if (context == null || column == null) return;
        column.removeAllViews();
        TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks, TaiBenchHomeFragment.versions());
        TaiBenchLeaderboard.Row row = TaiBenchLeaderboard.find(board, key);
        JSONObject record = TaiBenchLeaderboard.latestMeasured(benchmarks, key);
        if (record == null) record = TaiBenchLeaderboard.latestRecord(benchmarks, key);
        if (record == null) {
            column.addView(TaiBenchViews.body(context, getString(R.string.tai_bench_result_missing)), padded(context));
            return;
        }
        String modelId = record.optString("modelId", "");
        String displayName = record.optString("displayName", modelId);
        String backend = record.optString("backend", "");
        String accelerator = record.optString("accelerator", "");
        boolean speculative = record.optBoolean("speculative", false);
        JSONObject phases = record.optJSONObject("phases");
        JSONObject chat = phases == null ? null : phases.optJSONObject("chat");
        JSONObject longInput = phases == null ? null : phases.optJSONObject("longInput");
        JSONObject load = phases == null ? null : phases.optJSONObject("load");
        JSONObject check = record.optJSONObject("check");
        JSONObject conditions = record.optJSONObject("conditions");
        double decodeTps = TaiBenchStore.median(chat, "decodeTps", Double.NaN);
        double ttftMs = TaiBenchStore.median(chat, "ttftMs", Double.NaN);
        double readMs = TaiBenchStore.median(longInput, "readMs", Double.NaN);
        String verdict = row != null ? row.verdict : record.isNull("verdict") ? null : record.optString("verdict", null);
        boolean installed = record.optBoolean("installed", true);

        // The headline.
        TaiBenchViews.Card head = TaiBenchViews.card(context);
        head.core.addView(TaiBenchViews.title(context, displayName));
        LinearLayout pills = TaiBenchViews.row(context, 4, TaiBenchViews.backendPill(context, backend),
            TaiBenchViews.pill(context, TaiBenchViews.processorLabel(accelerator), TaiModelCentreRows.Tone.NEUTRAL));
        if (speculative) pills.addView(TaiBenchViews.pill(context, getString(R.string.tai_bench_mark_draft), TaiModelCentreRows.Tone.NEUTRAL), pillParams(context));
        if (row != null && row.rank > 0) pills.addView(TaiBenchViews.pill(context, getString(R.string.tai_bench_rank, row.rank), TaiModelCentreRows.Tone.NEUTRAL), pillParams(context));
        if (!installed) pills.addView(TaiBenchViews.pill(context, getString(R.string.tai_bench_mark_not_installed), TaiModelCentreRows.Tone.NEUTRAL), pillParams(context));
        head.core.addView(pills, TaiBenchViews.block(context, 6));
        String verdictLabel = TaiBenchViews.verdictLabel(context, verdict);
        if (!verdictLabel.isEmpty()) pills.addView(TaiBenchViews.pill(context, verdictLabel, TaiBenchViews.verdictTone(verdict)), pillParams(context));
        head.core.addView(TaiBenchViews.body(context, TaiBenchViews.summaryLine(context, decodeTps, ttftMs, readMs)), TaiBenchViews.block(context, 4));
        long mem = TaiBenchStore.memoryBytes(phases);
        if (mem > 0L) head.core.addView(TaiBenchViews.body(context, TaiBenchViews.memoryLine(context, mem)), TaiBenchViews.block(context, 2));
        StringBuilder when = new StringBuilder(TaiBenchViews.ago(record.optLong("timestamp", 0L)));
        when.append(" · ").append(TaiBenchViews.presetLabel(context, com.termux.ai.TaiBenchSuite.Preset.fromId(record.optString("preset", null))));
        if (row != null) {
            if (row.charging) when.append(" · ").append(getString(R.string.tai_bench_mark_charging));
            if (row.warmStart) when.append(" · ").append(getString(R.string.tai_bench_mark_warm_start));
            if (row.lowBattery) when.append(" · ").append(getString(R.string.tai_bench_mark_low_battery));
            if (row.olderVersion) when.append(" · ").append(getString(R.string.tai_bench_mark_older_version));
        }
        if (!TaiBenchStore.STATUS_COMPLETE.equals(record.optString("status", ""))) {
            when.append(" · ").append(getString(R.string.tai_bench_result_status, record.optString("status", "")));
        }
        head.core.addView(TaiBenchViews.mono(context, when), TaiBenchViews.block(context, 6));
        column.addView(head.outer);

        // The details.
        column.addView(TaiBenchViews.sectionHeader(context, getString(R.string.tai_bench_result_details_header), ""));
        TaiBenchViews.Card details = TaiBenchViews.card(context);
        JSONObject ttftSeries = chat == null ? null : chat.optJSONObject("ttftMs");
        JSONObject decodeSeries = chat == null ? null : chat.optJSONObject("decodeTps");
        JSONObject readSeries = longInput == null ? null : longInput.optJSONObject("readMs");
        addDetail(details.core, getString(R.string.tai_bench_result_starts), TaiBenchViews.waitSeconds(context, ttftMs) + rangeSeconds(context, ttftSeries));
        addDetail(details.core, getString(R.string.tai_bench_result_writes), TaiBenchViews.tps(context, decodeTps) + rangeTps(context, decodeSeries)
            + limitNote(TaiBenchRunState.tokenLimitHit(chat)));
        addDetail(details.core, getString(R.string.tai_bench_result_reads), TaiBenchViews.waitSeconds(context, readMs) + rangeSeconds(context, readSeries)
            + promptTokens(longInput) + (longInput != null && longInput.optBoolean("truncated", false)
                ? " · " + getString(R.string.tai_bench_result_truncated) : ""));
        addDetail(details.core, getString(R.string.tai_bench_phase_load), load == null ? TaiBenchViews.millis(context, Double.NaN)
            : TaiBenchViews.millis(context, load.optLong("ms", 0L)));
        addDetail(details.core, getString(R.string.tai_bench_result_memory), TaiBenchViews.bytes(context, mem)
            + (longInput != null && longInput.optLong("peakPssBytes", -1L) > 0L ? " · " + getString(R.string.tai_bench_result_memory_pss) : ""));
        int passed = check == null ? 0 : check.optInt("passed", 0);
        int total = check == null ? 0 : check.optInt("total", 0);
        // The sanity check is silent unless a question failed; then the verdict is Broken and this says how many passed.
        if (total > 0 && passed < total) {
            addDetail(details.core, getString(R.string.tai_bench_test_sanity), getString(R.string.tai_bench_result_check, passed, total)
                + limitNote(TaiBenchRunState.checkTokenLimitHit(check)));
        }
        if (conditions != null) {
            String battery = conditions.isNull("batteryStart") ? getString(R.string.tai_bench_none)
                : getString(R.string.tai_bench_result_battery_span, conditions.optInt("batteryStart", 0), conditions.optInt("batteryEnd", conditions.optInt("batteryStart", 0)));
            addDetail(details.core, getString(R.string.tai_bench_device_battery), battery);
            String heat = conditions.isNull("thermalStart") ? getString(R.string.tai_bench_none)
                : TaiBenchViews.heatLabel(context, conditions.optString("thermalStart", null)) + " → "
                + TaiBenchViews.heatLabel(context, conditions.isNull("thermalEnd") ? null : conditions.optString("thermalEnd", null));
            addDetail(details.core, getString(R.string.tai_bench_device_heat), heat);
        }
        addDetail(details.core, getString(R.string.tai_bench_result_versions), getString(R.string.tai_bench_result_versions_value,
            record.optString("appVersion", ""), TaiBenchViews.backendLabel(context, backend), record.optString("runtimeVersion", "")));
        column.addView(details.outer);

        // What the model wrote for the Chat test, folded away until asked for.
        String reply = chat == null ? "" : chat.optString("reply", "").trim();
        if (!reply.isEmpty()) {
            column.addView(TaiBenchViews.sectionHeader(context, getString(R.string.tai_bench_result_reply_header), ""));
            TaiBenchViews.Card replyCard = TaiBenchViews.card(context);
            TextView replyText = TaiBenchViews.mono(context, reply);
            replyText.setTextIsSelectable(true);
            replyText.setVisibility(View.GONE);
            TextView toggle = TaiBenchViews.ghostButton(context, getString(R.string.tai_bench_live_show));
            toggle.setOnClickListener(v -> {
                boolean show = replyText.getVisibility() != View.VISIBLE;
                replyText.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show ? R.string.tai_bench_live_hide : R.string.tai_bench_live_show);
            });
            LinearLayout replyHead = new LinearLayout(context);
            replyHead.setOrientation(LinearLayout.HORIZONTAL);
            replyHead.setGravity(Gravity.CENTER_VERTICAL);
            replyHead.addView(TaiBenchViews.body(context, com.termux.ai.TaiBenchSuite.CHAT_PROMPT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            replyHead.addView(toggle);
            replyCard.core.addView(replyHead);
            replyCard.core.addView(replyText, TaiBenchViews.block(context, 8));
            column.addView(replyCard.outer);
        }

        // The history.
        List<TaiBenchLeaderboard.Point> history = TaiBenchLeaderboard.history(benchmarks, key);
        column.addView(TaiBenchViews.sectionHeader(context, getString(R.string.tai_bench_result_history_header),
            getResources().getQuantityString(R.plurals.tai_bench_result_runs, history.size(), history.size())));
        TaiBenchViews.Card chart = TaiBenchViews.card(context);
        if (history.size() < 2) {
            chart.core.addView(TaiBenchViews.body(context, getString(R.string.tai_bench_result_history_empty)));
        } else {
            TaiBenchHistoryView view = new TaiBenchHistoryView(context);
            view.setHistory(history);
            chart.core.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TaiBenchViews.dp(context, 96)));
            TaiBenchLeaderboard.Point first = history.get(0);
            TaiBenchLeaderboard.Point last = history.get(history.size() - 1);
            String span = getString(R.string.tai_bench_result_history_span, TaiBenchViews.tps(context, first.decodeTps),
                TaiBenchViews.tps(context, last.decodeTps));
            if (!TaiBenchLeaderboard.dividers(history).isEmpty()) span += " · " + getString(R.string.tai_bench_result_history_dividers);
            chart.core.addView(TaiBenchViews.mono(context, span), TaiBenchViews.block(context, 6));
        }
        column.addView(chart.outer);

        // The actions.
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 16), 0);
        TextView again = TaiBenchViews.goButton(context, getString(R.string.tai_bench_run_again));
        again.setMinHeight(TaiBenchViews.dp(context, 44));
        TaiBenchViews.setEnabled(again, !TaiBenchSession.get().isActive());
        again.setOnClickListener(v -> {
            TaiMotion.tick(v);
            TaiBenchHomeFragment.open(getActivity(), modelId);
        });
        actions.addView(again, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView delete = TaiBenchViews.errorButton(context, getString(R.string.tai_bench_delete_results));
        delete.setMinHeight(TaiBenchViews.dp(context, 40));
        delete.setOnClickListener(v -> confirmDelete(modelId, displayName));
        actions.addView(delete, TaiBenchViews.block(context, 8));
        column.addView(actions);
    }

    private void addDetail(@NonNull LinearLayout core, @NonNull String label, @NonNull String value) {
        Context context = core.getContext();
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setBaselineAligned(true);
        TextView name = TaiBenchViews.mono(context, label);
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView figure = TaiBenchViews.mono(context, value);
        figure.setTextColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurface));
        figure.setGravity(Gravity.END);
        line.addView(figure, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        core.addView(line, core.getChildCount() == 0 ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            : TaiBenchViews.block(context, 6));
    }

    @NonNull
    private String rangeSeconds(@NonNull Context context, @Nullable JSONObject series) {
        if (series == null || series.optInt("runs", 0) < 2) return "";
        return " (" + TaiBenchViews.waitSeconds(context, series.optDouble("min", Double.NaN)) + "–" + TaiBenchViews.waitSeconds(context, series.optDouble("max", Double.NaN)) + ")";
    }

    @NonNull
    private String rangeTps(@NonNull Context context, @Nullable JSONObject series) {
        if (series == null || series.optInt("runs", 0) < 2) return "";
        return " (" + TaiBenchViews.tps(context, series.optDouble("min", Double.NaN)) + "–" + TaiBenchViews.tps(context, series.optDouble("max", Double.NaN)) + ")";
    }

    @NonNull
    private String promptTokens(@Nullable JSONObject longInput) {
        int tokens = longInput == null ? 0 : longInput.optInt("promptTokens", 0);
        return tokens > 0 ? " · " + getString(R.string.tai_bench_result_prompt_tokens, tokens) : "";
    }

    /** " · stopped at the 128-token limit" for a phase whose reply ended at the bench's cap; empty for {@code 0}. */
    @NonNull
    private String limitNote(int limit) {
        return limit > 0 ? " · " + getString(R.string.tai_bench_token_limit, limit) : "";
    }

    @NonNull
    private static LinearLayout.LayoutParams pillParams(@NonNull Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(TaiBenchViews.dp(context, 4));
        return params;
    }

    @NonNull
    private static LinearLayout.LayoutParams padded(@NonNull Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int pad = TaiBenchViews.dp(context, 22);
        params.setMargins(pad, pad, pad, pad);
        return params;
    }

    private void confirmDelete(@NonNull String modelId, @NonNull String displayName) {
        Context context = getContext();
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.tai_bench_delete_results_title, displayName))
            .setMessage(R.string.tai_bench_delete_results_message)
            .setPositiveButton(R.string.tai_bench_delete_results_confirm, (dialog, which) -> deleteResults(modelId))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteResults(@NonNull String modelId) {
        Context context = getContext();
        if (context == null || executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            JSONObject result = null;
            try {
                result = TaiManager.getInstance(app).clearBenchmarks(new JSONObject().put("modelId", modelId).toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context current = getContext();
                if (current == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    AppNotice.show(current, getString(R.string.tai_bench_delete_results_done, finalResult.optInt("removed", 0)), false);
                    getParentFragmentManager().popBackStack();
                } else {
                    AppNotice.show(current, finalResult == null ? getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", getString(R.string.termux_ai_model_action_failed)), true);
                }
            });
        });
    }
}
