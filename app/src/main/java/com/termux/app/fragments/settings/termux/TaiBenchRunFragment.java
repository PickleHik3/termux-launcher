package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.ai.TaiBenchSuite;
import com.termux.app.activities.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Run (spec Screens 4 and 5): a stepper per model and phase (the downloads first, when the run
 * has any), the live view (prompt in grey, the streaming reply in monospace, a token counter)
 * that folds away, the tiles (first word, reading, writing with its sparkline, free RAM, heat,
 * battery), and Stop, which keeps the phases that finished. The cool-down is a state of this
 * screen: how long the run has waited against its cap, the heat and headroom the guard read, the
 * model that just finished with its rank and a "New best" pill, and "Skip the wait".
 *
 * <p>The screen only draws {@link TaiBenchSession}'s state; it attaches in onStart and detaches
 * in onStop, and the run goes on without it. The screen stays on while it is visible
 * ({@code FLAG_KEEP_SCREEN_ON}). A fixed view tree updated in place rather than a list: the
 * tiles and the live view change up to twenty times a second.
 */
@Keep
public class TaiBenchRunFragment extends Fragment implements TaiBenchSession.Listener {
    private static final long TICK_MS = 1_000L;
    private static final String STATE_FOLDED = "tai_bench_live_folded";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            TaiBenchRunState state = TaiBenchSession.get().state();
            if (state.phase == TaiBenchRunState.Phase.WAITING) {
                bindWait(state);
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private TextView headline;
    private TextView headlineSub;
    private TextView stop;
    private TextView done;
    private LinearLayout stepper;
    private View waitCard;
    private TextView waitTitle;
    private TextView waitFacts;
    private LinearProgressIndicator waitBar;
    private TextView waitCountdown;
    private LinearLayout waitFinished;
    private TextView skip;
    private View liveCard;
    private TextView liveToggle;
    private TaiBenchLiveView live;
    private TextView tileFirstWord;
    private TextView tileReading;
    private TextView tileWriting;
    private TaiBenchSparklineView sparkline;
    private TextView tileRam;
    private TextView tileHeat;
    private TextView tileBattery;
    private TextView errorText;
    private boolean folded;
    private long drawnVersion = -1L;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) folded = savedInstanceState.getBoolean(STATE_FOLDED, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setId(R.id.tai_bench_scroll);
        scroll.setFillViewport(true);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, TaiBenchViews.dp(context, 8), 0, TaiBenchViews.dp(context, 32));
        scroll.addView(column, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The headline card: preset and progress, with Stop.
        TaiBenchViews.Card head = TaiBenchViews.card(context);
        LinearLayout headRow = new LinearLayout(context);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headText = new LinearLayout(context);
        headText.setOrientation(LinearLayout.VERTICAL);
        headline = TaiBenchViews.title(context, "");
        headText.addView(headline);
        headlineSub = TaiBenchViews.mono(context, "");
        headText.addView(headlineSub, TaiBenchViews.block(context, 2));
        headRow.addView(headText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stop = TaiBenchViews.errorButton(context, getString(R.string.tai_bench_stop));
        stop.setOnClickListener(v -> confirmStop());
        headRow.addView(stop);
        done = TaiBenchViews.goButton(context, getString(R.string.tai_bench_see_results));
        done.setOnClickListener(v -> openHome());
        done.setVisibility(View.GONE);
        headRow.addView(done);
        head.core.addView(headRow);
        errorText = TaiBenchViews.body(context, "");
        errorText.setTextColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorError));
        errorText.setVisibility(View.GONE);
        head.core.addView(errorText, TaiBenchViews.block(context, 6));
        column.addView(head.outer);

        // The cool-down card, shown while the guard has the run waiting.
        TaiBenchViews.Card wait = TaiBenchViews.card(context);
        waitCard = wait.outer;
        waitTitle = TaiBenchViews.title(context, "");
        wait.core.addView(waitTitle);
        waitFacts = TaiBenchViews.mono(context, "");
        wait.core.addView(waitFacts, TaiBenchViews.block(context, 4));
        waitBar = new LinearProgressIndicator(context);
        waitBar.setIndicatorColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorPrimary));
        waitBar.setTrackColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorSurfacePanel));
        waitBar.setMax(1000);
        wait.core.addView(waitBar, TaiBenchViews.block(context, 10));
        waitCountdown = TaiBenchViews.body(context, "");
        wait.core.addView(waitCountdown, TaiBenchViews.block(context, 6));
        waitFinished = new LinearLayout(context);
        waitFinished.setOrientation(LinearLayout.HORIZONTAL);
        waitFinished.setGravity(Gravity.CENTER_VERTICAL);
        wait.core.addView(waitFinished, TaiBenchViews.block(context, 10));
        skip = TaiBenchViews.ghostButton(context, getString(R.string.tai_bench_skip_wait));
        skip.setOnClickListener(v -> {
            TaiMotion.tick(v);
            TaiBenchSession.get().skipWait(v.getContext());
        });
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        skipParams.topMargin = TaiBenchViews.dp(context, 10);
        skipParams.gravity = Gravity.END;
        wait.core.addView(skip, skipParams);
        waitCard.setVisibility(View.GONE);
        column.addView(waitCard);

        // The stepper.
        column.addView(TaiBenchViews.sectionHeader(context, getString(R.string.tai_bench_stepper_header), ""));
        TaiBenchViews.Card steps = TaiBenchViews.card(context);
        stepper = steps.core;
        column.addView(steps.outer);

        // The live view, foldable.
        TaiBenchViews.Card liveShell = TaiBenchViews.card(context);
        liveCard = liveShell.outer;
        LinearLayout liveHead = new LinearLayout(context);
        liveHead.setOrientation(LinearLayout.HORIZONTAL);
        liveHead.setGravity(Gravity.CENTER_VERTICAL);
        liveHead.addView(TaiBenchViews.title(context, getString(R.string.tai_bench_live_header)),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        liveToggle = TaiBenchViews.ghostButton(context, "");
        liveToggle.setMinHeight(TaiBenchViews.dp(context, 30));
        liveToggle.setOnClickListener(v -> {
            folded = !folded;
            bindFold();
        });
        liveHead.addView(liveToggle);
        liveShell.core.addView(liveHead);
        live = new TaiBenchLiveView(context);
        liveShell.core.addView(live, TaiBenchViews.block(context, 8));
        column.addView(liveCard);

        // The tiles, two to a row.
        column.addView(TaiBenchViews.sectionHeader(context, getString(R.string.tai_bench_tiles_header), ""));
        LinearLayout tiles = new LinearLayout(context);
        tiles.setOrientation(LinearLayout.VERTICAL);
        tiles.setPadding(TaiBenchViews.dp(context, 12), 0, TaiBenchViews.dp(context, 12), 0);
        tileFirstWord = TaiBenchViews.figure(context, "", 20f);
        tileReading = TaiBenchViews.figure(context, "", 20f);
        tileWriting = TaiBenchViews.figure(context, "", 20f);
        sparkline = new TaiBenchSparklineView(context);
        tileRam = TaiBenchViews.figure(context, "", 20f);
        tileHeat = TaiBenchViews.figure(context, "", 20f);
        tileBattery = TaiBenchViews.figure(context, "", 20f);
        tiles.addView(tileRow(context,
            tile(context, getString(R.string.tai_bench_tile_first_word), tileFirstWord, null),
            tile(context, getString(R.string.tai_bench_tile_reading), tileReading, null)));
        tiles.addView(tileRow(context,
            tile(context, getString(R.string.tai_bench_tile_writing), tileWriting, sparkline),
            tile(context, getString(R.string.tai_bench_tile_free_ram), tileRam, null)));
        tiles.addView(tileRow(context,
            tile(context, getString(R.string.tai_bench_tile_heat), tileHeat, null),
            tile(context, getString(R.string.tai_bench_tile_battery), tileBattery, null)));
        column.addView(tiles);
        bindFold();
        return scroll;
    }

    @NonNull
    private LinearLayout tileRow(@NonNull Context context, @NonNull View a, @NonNull View b) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(a, left);
        row.addView(b, right);
        return row;
    }

    @NonNull
    private View tile(@NonNull Context context, @NonNull String label, @NonNull TextView figure, @Nullable View extra) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.outer.setPadding(TaiBenchViews.dp(context, 4), TaiBenchViews.dp(context, 3), TaiBenchViews.dp(context, 4), TaiBenchViews.dp(context, 3));
        TextView caption = TaiBenchViews.mono(context, label);
        card.core.addView(caption);
        card.core.addView(figure, TaiBenchViews.block(context, 4));
        if (extra != null) card.core.addView(extra, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TaiBenchViews.dp(context, 22)));
        return card.outer;
    }

    @Override
    public void onStart() {
        super.onStart();
        keepScreenOn(true);
        TaiBenchSession.get().addListener(this);
        drawnVersion = -1L;
        onBenchStateChanged(TaiBenchSession.get().state());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.tai_bench_run_title);
    }

    @Override
    public void onStop() {
        TaiBenchSession.get().removeListener(this);
        handler.removeCallbacks(tick);
        keepScreenOn(false);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_FOLDED, folded);
    }

    private void keepScreenOn(boolean on) {
        Activity activity = getActivity();
        if (activity == null) return;
        if (on) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---- drawing the state ----

    @Override
    public void onBenchStateChanged(@NonNull TaiBenchRunState state) {
        if (!isAdded() || getView() == null) return;
        if (state.version == drawnVersion) return;
        drawnVersion = state.version;
        Context context = requireContext();
        bindHeadline(context, state);
        bindStepper(context, state);
        bindWait(state);
        live.bind(state.live);
        liveCard.setVisibility(state.entries.isEmpty() && !state.live.active ? View.GONE : View.VISIBLE);
        bindTiles(context, state);
    }

    private void bindHeadline(@NonNull Context context, @NonNull TaiBenchRunState state) {
        TaiBenchSuite.Preset preset = TaiBenchSuite.Preset.fromId(state.presetId);
        int total = Math.max(state.entries.size(), state.planned.size());
        int finished = 0;
        for (TaiBenchRunState.Entry entry : state.entries) if (entry.finished()) finished++;
        String sub = getString(R.string.tai_bench_headline_sub, TaiBenchViews.presetLabel(context, preset), finished, total);
        String title;
        boolean over = state.finished();
        switch (state.phase) {
            case DOWNLOADING: title = getString(R.string.tai_bench_headline_downloading); break;
            case WAITING: title = getString(R.string.tai_bench_headline_waiting); break;
            case STOPPING: title = getString(R.string.tai_bench_headline_stopping); break;
            case DONE: title = getString(R.string.tai_bench_headline_done); break;
            case STOPPED: title = getString(R.string.tai_bench_headline_stopped, stopReasonText(state.stopReason)); break;
            case FAILED: title = getString(R.string.tai_bench_headline_failed); break;
            case IDLE: title = getString(R.string.tai_bench_headline_idle); break;
            default: title = getString(R.string.tai_bench_headline_running); break;
        }
        headline.setText(title);
        headlineSub.setText(sub);
        stop.setVisibility(over || state.phase == TaiBenchRunState.Phase.IDLE ? View.GONE : View.VISIBLE);
        TaiBenchViews.setEnabled(stop, state.phase != TaiBenchRunState.Phase.STOPPING);
        done.setVisibility(over ? View.VISIBLE : View.GONE);
        String error = state.errorMessage == null ? "" : errorText(context, state);
        errorText.setText(error);
        errorText.setVisibility(error.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** A {@code conditions_not_met} refusal names the reason and the reading; anything else is its message. */
    @NonNull
    private String errorText(@NonNull Context context, @NonNull TaiBenchRunState state) {
        if ("conditions_not_met".equals(state.errorCode) && state.errorDetail != null) {
            String reason = state.errorDetail.optString("reason", "");
            if ("battery_low".equals(reason)) {
                return getString(R.string.tai_bench_error_battery, state.errorDetail.optInt("batteryPercent", -1));
            }
            if ("too_hot".equals(reason)) return getString(R.string.tai_bench_error_hot);
        }
        return state.errorMessage == null ? "" : state.errorMessage;
    }

    @NonNull
    private String stopReasonText(@Nullable String reason) {
        if (reason == null) return "";
        switch (reason) {
            case "cancelled": return getString(R.string.tai_bench_stopped_by_you);
            case "battery_low": return getString(R.string.tai_bench_stopped_battery);
            case "thermal":
            case "thermal_timeout": return getString(R.string.tai_bench_stopped_thermal);
            case "unloaded": return getString(R.string.tai_bench_stopped_unloaded);
            default: return reason;
        }
    }

    private void bindStepper(@NonNull Context context, @NonNull TaiBenchRunState state) {
        List<View> rows = new ArrayList<>();
        for (TaiBenchRunState.Download download : state.downloads) {
            String glyph = download.status == TaiBenchRunState.StepStatus.DONE ? "✓"
                : download.status == TaiBenchRunState.StepStatus.FAILED ? "✗" : "●";
            String detail;
            if (download.status == TaiBenchRunState.StepStatus.FAILED) {
                detail = download.reason == null ? getString(R.string.tai_bench_step_download_failed) : download.reason;
            } else if (download.totalBytes > 0L) {
                detail = getString(R.string.tai_centre_meta_progress, TaiModelCentreRows.formatBytes(download.bytesRead),
                    TaiModelCentreRows.formatBytes(download.totalBytes));
            } else {
                detail = TaiModelCentreRows.formatBytes(download.bytesRead);
            }
            rows.add(stepRow(context, glyph, getString(R.string.tai_bench_step_download, download.displayName), detail,
                download.status == TaiBenchRunState.StepStatus.RUNNING, null));
        }
        TaiBenchSuite.Preset preset = TaiBenchSuite.Preset.fromId(state.presetId);
        for (TaiBenchRunState.Entry entry : state.entries) {
            String glyph;
            switch (entry.status) {
                case DONE: glyph = "✓"; break;
                case SKIPPED: glyph = "–"; break;
                case STOPPED: glyph = "✗"; break;
                default: glyph = "●"; break;
            }
            String title = entry.displayName + " · " + TaiBenchViews.processorLabel(entry.accelerator)
                + (entry.speculative ? " · " + getString(R.string.tai_bench_mark_draft) : "");
            String detail;
            if (entry.status == TaiBenchRunState.EntryStatus.SKIPPED) {
                detail = getString(R.string.tai_bench_step_skipped, entry.reason == null ? "" : entry.reason);
            } else if (entry.status == TaiBenchRunState.EntryStatus.STOPPED) {
                detail = entry.reason == null ? getString(R.string.tai_bench_step_stopped) : entry.reason;
            } else if (entry.status == TaiBenchRunState.EntryStatus.DONE) {
                detail = TaiBenchViews.tps(context, entry.writingTps)
                    + (entry.verdict == null ? "" : " · " + TaiBenchViews.verdictLabel(context, entry.verdict))
                    + (entry.rank > 0 ? " · " + getString(R.string.tai_bench_rank, entry.rank) : "");
            } else {
                detail = phasesLine(context, entry, preset);
            }
            String key = entry.finished() && entry.status != TaiBenchRunState.EntryStatus.SKIPPED ? entry.key : null;
            rows.add(stepRow(context, glyph, title, detail, entry.status == TaiBenchRunState.EntryStatus.RUNNING, key));
        }
        for (TaiBenchRunState.Planned planned : state.pending()) {
            rows.add(stepRow(context, "○", planned.displayName, getString(planned.download ? R.string.tai_bench_step_pending_download
                : R.string.tai_bench_step_pending), false, null));
        }
        stepper.removeAllViews();
        for (int i = 0; i < rows.size(); i++) {
            stepper.addView(rows.get(i), i == 0 ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : TaiBenchViews.block(context, 8));
        }
        if (rows.isEmpty()) stepper.addView(TaiBenchViews.body(context, getString(R.string.tai_bench_loading)));
    }

    /** "load ✓ · warm-up ✓ · reading 2/3 · first word · writing · check", the phases in the preset's order. */
    @NonNull
    private String phasesLine(@NonNull Context context, @NonNull TaiBenchRunState.Entry entry, @Nullable TaiBenchSuite.Preset preset) {
        StringBuilder line = new StringBuilder();
        for (String phase : TaiBenchRunState.phasesFor(preset)) {
            if (line.length() > 0) line.append(" · ");
            line.append(TaiBenchViews.phaseLabel(context, phase));
            TaiBenchRunState.Step step = entry.steps.get(phase);
            if (step == null) continue;
            if (step.status == TaiBenchRunState.StepStatus.DONE) line.append(" ✓");
            else if (step.status == TaiBenchRunState.StepStatus.FAILED) line.append(" ✗");
            else if (step.runs > 1) line.append(' ').append(Math.max(1, step.run)).append('/').append(step.runs);
            else line.append(" …");
        }
        for (Map.Entry<String, TaiBenchRunState.Step> extra : entry.steps.entrySet()) {
            if (!TaiBenchRunState.phasesFor(preset).contains(extra.getKey())) {
                line.append(" · ").append(TaiBenchViews.phaseLabel(context, extra.getKey()));
            }
        }
        if (entry.cacheRebuilt) line.append(" · ").append(getString(R.string.tai_bench_step_cache_rebuilt));
        return line.toString();
    }

    @NonNull
    private View stepRow(@NonNull Context context, @NonNull String glyph, @NonNull String title, @NonNull String detail,
                         boolean running, @Nullable String resultKey) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        TextView mark = TaiBenchViews.mono(context, glyph);
        mark.setMinWidth(TaiBenchViews.dp(context, 20));
        mark.setTextColor(TaiBenchViews.color(context, running ? com.termux.shared.R.attr.termuxColorPrimary
            : com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        row.addView(mark);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView name = TaiBenchViews.title(context, title);
        if (!running) name.setAlpha(0.8f);
        column.addView(name);
        TextView sub = TaiBenchViews.mono(context, detail);
        sub.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        column.addView(sub, TaiBenchViews.block(context, 2));
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (resultKey != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> openResult(resultKey));
        }
        return row;
    }

    private void bindWait(@NonNull TaiBenchRunState state) {
        Context context = getContext();
        if (context == null || waitCard == null) return;
        TaiBenchRunState.Wait wait = state.wait;
        boolean waiting = state.phase == TaiBenchRunState.Phase.WAITING && wait != null;
        waitCard.setVisibility(waiting ? View.VISIBLE : View.GONE);
        if (!waiting) {
            handler.removeCallbacks(tick);
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = wait.elapsedMs(now);
        long cap = wait.capMs();
        waitTitle.setText(wait.cooldown() ? getString(R.string.tai_bench_wait_cooldown_title) : getString(R.string.tai_bench_wait_thermal_title));
        StringBuilder facts = new StringBuilder(getString(R.string.tai_bench_wait_heat, TaiBenchViews.heatLabel(context, wait.thermalStatus)));
        if (!Double.isNaN(wait.headroom)) facts.append(" · ").append(getString(R.string.tai_bench_wait_headroom, String.format(java.util.Locale.US, "%.2f", wait.headroom)));
        waitFacts.setText(facts);
        waitBar.setProgressCompat((int) (elapsed * 1000L / Math.max(1L, cap)), !TaiMotion.reduced(context));
        waitCountdown.setText(getString(wait.cooldown() ? R.string.tai_bench_wait_countdown : R.string.tai_bench_wait_countdown_thermal,
            TaiBenchViews.clock(elapsed), TaiBenchViews.clock(cap - elapsed)));
        waitFinished.removeAllViews();
        TaiBenchRunState.Entry last = state.lastFinished;
        if (last != null && last.status == TaiBenchRunState.EntryStatus.DONE) {
            TextView text = TaiBenchViews.body(context, getString(R.string.tai_bench_wait_finished, last.displayName,
                TaiBenchViews.processorLabel(last.accelerator), TaiBenchViews.tps(context, last.writingTps))
                + (last.rank > 0 ? " · " + getString(R.string.tai_bench_rank, last.rank) : ""));
            waitFinished.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (last.newBest) {
                TextView best = TaiBenchViews.pill(context, getString(R.string.tai_bench_new_best), TaiModelCentreRows.Tone.ACCENT);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMarginStart(TaiBenchViews.dp(context, 8));
                waitFinished.addView(best, params);
            }
        }
        waitFinished.setVisibility(waitFinished.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        skip.setVisibility(wait.cooldown() ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);
    }

    private void bindFold() {
        if (live == null) return;
        live.setVisibility(folded ? View.GONE : View.VISIBLE);
        liveToggle.setText(folded ? R.string.tai_bench_live_show : R.string.tai_bench_live_hide);
    }

    private void bindTiles(@NonNull Context context, @NonNull TaiBenchRunState state) {
        tileFirstWord.setText(TaiBenchViews.millis(context, state.firstWordMs));
        tileReading.setText(TaiBenchViews.tps(context, state.readingTps));
        double writing = state.live.active && (TaiBenchSuite.PHASE_WRITING.equals(state.live.phase) || TaiBenchSuite.PHASE_SUSTAINED.equals(state.live.phase))
            && state.live.tps > 0.0 ? state.live.tps : state.writingTps;
        tileWriting.setText(TaiBenchViews.tps(context, writing));
        sparkline.setSeries(state.series);
        tileRam.setText(TaiBenchViews.bytes(context, state.conditions.freeRamBytes));
        tileHeat.setText(TaiBenchViews.heatLabel(context, state.conditions.thermalStatus));
        tileBattery.setText(TaiBenchViews.batteryLabel(context, state.conditions.batteryPercent, state.conditions.charging));
    }

    // ---- actions ----

    private void confirmStop() {
        Context context = getContext();
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tai_bench_stop_title)
            .setMessage(R.string.tai_bench_stop_message)
            .setPositiveButton(R.string.tai_bench_stop, (dialog, which) -> TaiBenchSession.get().stop(context))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openHome() {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).openScreen(TaiBenchHomeFragment.class, R.string.tai_bench_title, null);
        }
    }

    private void openResult(@NonNull String key) {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).openScreen(TaiBenchResultFragment.class, R.string.tai_bench_result_title,
                TaiBenchResultFragment.arguments(key));
        }
    }
}
