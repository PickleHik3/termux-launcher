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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.ai.MnnTaiRuntime;
import com.termux.ai.TaiBenchGuardRules;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiDeviceConditions;
import com.termux.ai.TaiFeatureCheck;
import com.termux.ai.TaiFeatureCheckRunner;
import com.termux.ai.TaiFeatureCheckStore;
import com.termux.ai.TaiFeaturePlan;
import com.termux.ai.TaiFeaturePlans;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiTierPolicy;
import com.termux.app.activities.SettingsActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The benchmark's Home (spec Screen 1): "Your features" on top (each feature in use with its feature
 * check's one figure and verdict, and "Check my features"), a device card, the one leaderboard list
 * (ranked by verdict, then decode speed, then the first reply), the broken entries apart, and a bar
 * floating over the list's foot with the "Run a benchmark" button and when the last run was. {@link #open} is the one entry point the wiring slice calls:
 * it lands on the Run screen while a run is going, on Choose with one model preselected when
 * asked for a model, and here otherwise. Hosted by {@link SettingsActivity} like the Model centre.
 */
@Keep
public class TaiBenchHomeFragment extends Fragment implements TaiBenchListAdapter.Factory, TaiBenchSession.Listener {
    private static final int TYPE_BANNER = 0;
    private static final int TYPE_DEVICE = 1;
    private static final int TYPE_ROW = 3;
    private static final int TYPE_SECTION = 4;
    private static final int TYPE_NOTE = 5;
    private static final int TYPE_EMPTY = 6;
    private static final int TYPE_FEATURES = 7;
    /** The initial place that opens the Check sheet for one feature: {@code check:<feature id>}. */
    static final String PLACE_CHECK = "check:";

    /** One row of "Your features": a feature in use, its model, and its latest feature check. */
    static final class FeatureRow {
        @NonNull final TaiFunction feature;
        @NonNull final String modelId;
        @NonNull final String modelName;
        /** The plan's accelerator for a chat feature; {@code null} for the CPU-only ones. */
        @Nullable final String accelerator;
        /** The latest check of the plan's setup, else of any setup of this model; {@code null} when never checked. */
        @Nullable final JSONObject record;
        /** The record was measured on another model file or runtime version. */
        final boolean stale;

        FeatureRow(@NonNull TaiFunction feature, @NonNull String modelId, @NonNull String modelName,
                   @Nullable String accelerator, @Nullable JSONObject record, boolean stale) {
            this.feature = feature;
            this.modelId = modelId;
            this.modelName = modelName;
            this.accelerator = accelerator;
            this.record = record;
            this.stale = stale;
        }

        @NonNull
        String signature() {
            return feature + "|" + modelId + "|" + accelerator + "|" + (record == null ? "" : record.optString("id", "")
                + record.optLong("timestamp", 0L)) + "|" + stale;
        }
    }

    /** The device card's facts, gathered off the main thread. */
    private static final class DeviceFacts {
        @NonNull String soc = "";
        @Nullable String gpu;
        long ramClassBytes;
        long freeRamBytes = -1L;
        long freeStorageBytes = -1L;
        int batteryPercent = -1;
        boolean charging;
        @Nullable String thermalStatus;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-bench-home");
        thread.setDaemon(true);
        return thread;
    });
    private final TaiBenchListAdapter adapter = new TaiBenchListAdapter(this);
    @Nullable private TaiBenchLeaderboard.Board board;
    @Nullable private DeviceFacts facts;
    @Nullable private View bar;
    private int lastSeenEntries = -1;
    /** "Your features"; empty until read, and when no feature runs a model on this phone. */
    @NonNull private List<FeatureRow> features = new ArrayList<>();
    /** A feature whose Check sheet opens once its row is read (the Model Centre's offer). */
    @Nullable private TaiFunction pendingCheck;

    /**
     * Opens the benchmark: the Run screen while a run is going, Choose with only {@code modelId}
     * selected when one is named, Home otherwise. Pushed in place inside Settings, launched into
     * Settings from anywhere else.
     */
    public static void open(@Nullable Activity activity, @Nullable String modelId) {
        if (activity == null) return;
        Class<? extends Fragment> screen;
        int title;
        String place = null;
        if (TaiBenchSession.get().isActive()) {
            screen = TaiBenchRunFragment.class;
            title = R.string.tai_bench_run_title;
        } else if (modelId != null && !modelId.isEmpty()) {
            screen = TaiBenchChooseFragment.class;
            title = R.string.tai_bench_choose_title;
            place = modelId;
        } else {
            screen = TaiBenchHomeFragment.class;
            title = R.string.tai_bench_title;
        }
        if (activity instanceof SettingsActivity) {
            Bundle arguments = null;
            if (place != null) {
                arguments = new Bundle();
                arguments.putString(SettingsActivity.EXTRA_INITIAL_PLACE, place);
            }
            ((SettingsActivity) activity).openScreen(screen, title, arguments);
        } else {
            activity.startActivity(SettingsActivity.createFragmentIntent(activity, screen, title, place, null));
        }
    }

    /** Opens the benchmark's Home with the Check sheet up for {@code feature}: the Model Centre's one-time offer. */
    public static void openFeatureCheck(@Nullable Activity activity, @NonNull TaiFunction feature) {
        if (activity == null) return;
        String place = PLACE_CHECK + feature.id();
        if (activity instanceof SettingsActivity) {
            Bundle arguments = new Bundle();
            arguments.putString(SettingsActivity.EXTRA_INITIAL_PLACE, place);
            ((SettingsActivity) activity).openScreen(TaiBenchHomeFragment.class, R.string.tai_bench_title, arguments);
        } else {
            activity.startActivity(SettingsActivity.createFragmentIntent(activity, TaiBenchHomeFragment.class,
                R.string.tai_bench_title, place, null));
        }
    }

    /** The versions this build is, for the "older version" mark. */
    @NonNull
    static TaiBenchLeaderboard.Versions versions() {
        return new TaiBenchLeaderboard.Versions(BuildConfig.VERSION_NAME, BuildConfig.LITERT_LM_VERSION, MnnTaiRuntime.RUNTIME_VERSION);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        String place = arguments == null || savedInstanceState != null ? null : arguments.getString(SettingsActivity.EXTRA_INITIAL_PLACE);
        if (place != null && place.startsWith(PLACE_CHECK)) pendingCheck = TaiFunction.fromId(place.substring(PLACE_CHECK.length()));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        RecyclerView list = new RecyclerView(context);
        list.setId(R.id.tai_bench_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, TaiBenchViews.dp(context, 32));
        if (TaiMotion.reduced(context)) {
            list.setItemAnimator(null);
        } else {
            DefaultItemAnimator animator = new DefaultItemAnimator();
            animator.setSupportsChangeAnimations(false);
            animator.setMoveDuration(TaiMotion.ARRIVE_MS);
            list.setItemAnimator(animator);
        }
        list.setAdapter(adapter);
        FrameLayout root = new FrameLayout(context);
        root.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View floating = createBar(context);
        root.addView(floating, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        // The last row scrolls clear of the bar.
        floating.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int pad = (bottom - top) + TaiBenchViews.dp(context, 8);
            if (list.getPaddingBottom() != pad) list.setPadding(0, 0, 0, pad);
        });
        bar = floating;
        return root;
    }

    @Override
    public void onDestroyView() {
        bar = null;
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        rebuild();
        TaiBenchSession.get().addListener(this);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.tai_bench_title);
    }

    @Override
    public void onStop() {
        TaiBenchSession.get().removeListener(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBenchStateChanged(@NonNull TaiBenchRunState state) {
        // A run going on behind this screen: the banner follows it, and the board reloads as
        // entries land and when it ends.
        int entries = state.entries.size();
        boolean reload = entries != lastSeenEntries || state.finished();
        lastSeenEntries = state.finished() ? -1 : entries;
        if (reload) refresh();
        else rebuild();
    }

    /** Reads the store and the device off the main thread (the runtime status is an IPC). */
    private void refresh() {
        Context context = getContext();
        if (context == null || executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            TaiBenchLeaderboard.Board loaded = null;
            try {
                loaded = TaiBenchLeaderboard.read(TaiManager.getInstance(app).benchmarks(), versions());
            } catch (JSONException | RuntimeException ignored) {
            }
            DeviceFacts gathered = gather(app);
            List<FeatureRow> rows = gatherFeatures(app);
            TaiBenchLeaderboard.Board finalBoard = loaded;
            handler.post(() -> {
                if (!isAdded()) return;
                if (finalBoard != null) board = finalBoard;
                facts = gathered;
                features = rows;
                rebuild();
                offerPendingCheck();
            });
        });
    }

    /**
     * "Your features": each feature the check can measure here ({@link TaiFeatureCheckRunner#featuresInUse}),
     * with its model and its latest check. Reads the plans, the model store and the results file.
     */
    @NonNull
    private static List<FeatureRow> gatherFeatures(@NonNull Context app) {
        List<FeatureRow> rows = new ArrayList<>();
        try {
            TaiFeaturePlans plans = TaiFeaturePlans.forContext(app);
            List<TaiFunction> inUse = TaiFeatureCheckRunner.featuresInUse(plans);
            if (inUse.isEmpty()) return rows;
            TaiModelStore store = new TaiModelStore(app);
            Map<String, TaiModelSpec> installed = new LinkedHashMap<>(store.getDownloadedReadableModels());
            installed.putAll(store.getInstalledUserModels());
            TaiFunctionLabels labels = new TaiFunctionLabels(app, installed);
            List<JSONObject> latest = TaiFeatureCheckStore.latest(TaiFeatureCheckStore.in(app.getFilesDir()).records());
            for (TaiFunction feature : inUse) {
                TaiFeaturePlan plan = plans.plan(feature);
                if (plan.modelId == null) continue;
                TaiModelSpec spec = installed.get(TaiFeatureCheck.baseModelId(plan.modelId));
                if (spec == null) spec = installed.get(plan.modelId);
                if (spec == null) continue;
                JSONObject record = latestFor(latest, feature, spec, plan);
                String current = TaiFeatureCheckStore.stalenessKey(spec);
                boolean stale = record != null && TaiFeatureCheck.isStale(record.optString("staleKey", ""), current);
                rows.add(new FeatureRow(feature, spec.id, labels.modelName(spec.id),
                    feature.usesChatModel() ? plan.accelerator : null, record, stale));
            }
        } catch (RuntimeException ignored) {
            // No section rather than a broken one; the bench below still reads.
        }
        return rows;
    }

    private static boolean sameModel(@NonNull JSONObject record, @NonNull TaiFunction feature, @NonNull TaiModelSpec spec) {
        return feature.id().equals(record.optString("feature", "")) && spec.backend.equals(record.optString("backend", ""))
            && TaiFeatureCheck.baseModelId(spec.id).equals(TaiFeatureCheck.baseModelId(record.optString("modelId", "")));
    }

    /** The newest record of the plan's own setup, else the newest of any setup of the feature on this model. */
    @Nullable
    private static JSONObject latestFor(@NonNull List<JSONObject> latest, @NonNull TaiFunction feature,
                                        @NonNull TaiModelSpec spec, @NonNull TaiFeaturePlan plan) {
        JSONObject own = null;
        JSONObject any = null;
        String accelerator = plan.accelerator == null ? TaiTierPolicy.ACCEL_CPU : plan.accelerator.toLowerCase(Locale.ROOT);
        boolean speculative = Boolean.TRUE.equals(plan.speculative);
        for (JSONObject record : latest) {
            if (!sameModel(record, feature, spec)) continue;
            long at = record.optLong("timestamp", 0L);
            if (any == null || at >= any.optLong("timestamp", 0L)) any = record;
            boolean setup = !feature.usesChatModel() || (accelerator.equals(record.optString("accelerator", ""))
                && speculative == record.optBoolean("speculative", false));
            if (setup && (own == null || at >= own.optLong("timestamp", 0L))) own = record;
        }
        return own != null ? own : any;
    }

    @NonNull
    private static DeviceFacts gather(@NonNull Context app) {
        DeviceFacts facts = new DeviceFacts();
        try {
            TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(app);
            facts.soc = device.socModel == null ? "" : device.socModel;
            facts.ramClassBytes = device.memoryBytes;
            facts.freeRamBytes = device.availableMemoryBytes;
        } catch (RuntimeException ignored) {
        }
        facts.gpu = TaiGpuName.get();
        try {
            File probe = new TaiModelStore(app).getModelsDirectory();
            while (probe != null && !probe.exists()) probe = probe.getParentFile();
            facts.freeStorageBytes = (probe == null ? app.getFilesDir() : probe).getUsableSpace();
        } catch (RuntimeException ignored) {
        }
        try {
            TaiBenchGuardRules.Snapshot snapshot = new TaiDeviceConditions(app).snapshot();
            facts.batteryPercent = snapshot.batteryPercent;
            facts.charging = snapshot.charging;
            facts.thermalStatus = TaiBenchGuardRules.thermalStatusName(snapshot.thermalStatus);
        } catch (RuntimeException ignored) {
        }
        return facts;
    }

    // ---- the list ----

    private void rebuild() {
        Context context = getContext();
        if (context == null) return;
        List<TaiBenchListAdapter.Item> items = new ArrayList<>();
        TaiBenchSession session = TaiBenchSession.get();
        if (session.isActive()) {
            TaiBenchRunState state = session.state();
            String text = getString(R.string.tai_bench_banner_running, state.entries.size(), Math.max(state.entries.size(), state.planned.size()));
            items.add(new TaiBenchListAdapter.Item(TYPE_BANNER, "running", text, text));
        }
        if (!features.isEmpty()) {
            StringBuilder signature = new StringBuilder().append(session.isActive());
            for (FeatureRow row : features) signature.append('|').append(row.signature());
            items.add(new TaiBenchListAdapter.Item(TYPE_FEATURES, "features", signature.toString(), features));
        }
        DeviceFacts d = facts;
        String deviceSignature = d == null ? "" : d.soc + '|' + d.gpu + '|' + d.ramClassBytes + '|' + d.freeRamBytes + '|' + d.freeStorageBytes
            + '|' + d.batteryPercent + '|' + d.charging + '|' + d.thermalStatus;
        items.add(new TaiBenchListAdapter.Item(TYPE_DEVICE, "device", deviceSignature, d));
        TaiBenchLeaderboard.Board b = board;
        if (b == null || b.empty()) {
            items.add(new TaiBenchListAdapter.Item(TYPE_EMPTY, "empty", b == null ? "loading" : "empty", b == null));
        } else {
            for (TaiBenchLeaderboard.Row row : b.ranked) {
                items.add(new TaiBenchListAdapter.Item(TYPE_ROW, row.key, rowSignature(row), row));
            }
            if (!b.broken.isEmpty()) {
                String header = getString(R.string.tai_bench_broken_header);
                items.add(new TaiBenchListAdapter.Item(TYPE_SECTION, "broken", header, header));
                for (TaiBenchLeaderboard.Row row : b.broken) {
                    items.add(new TaiBenchListAdapter.Item(TYPE_ROW, row.key, rowSignature(row), row));
                }
            }
            if (b.otherVersionRecords > 0) {
                String note = getResources().getQuantityString(R.plurals.tai_bench_other_versions, b.otherVersionRecords, b.otherVersionRecords);
                items.add(new TaiBenchListAdapter.Item(TYPE_NOTE, "versions", note, note));
            }
        }
        String lastRun = b == null || b.lastRunMs <= 0L ? "" : getString(R.string.tai_bench_last_run, TaiBenchViews.ago(b.lastRunMs));
        adapter.submit(items);
        bindBar(lastRun);
    }

    @NonNull
    private String rowSignature(@NonNull TaiBenchLeaderboard.Row row) {
        return row.recordId + '|' + row.rank + '|' + row.installed;
    }

    // ---- rows ----

    @NonNull
    @Override
    public View create(@NonNull ViewGroup parent, int type) {
        Context context = parent.getContext();
        switch (type) {
            case TYPE_BANNER: return createBanner(context);
            case TYPE_DEVICE: return createDevice(context);
            case TYPE_ROW: return createRow(context);
            case TYPE_SECTION: return TaiBenchViews.sectionHeader(context, "", "");
            case TYPE_NOTE: return createNote(context);
            case TYPE_FEATURES: return createFeatures(context);
            default: return createEmpty(context);
        }
    }

    @Override
    public void bind(@NonNull View view, @NonNull TaiBenchListAdapter.Item item) {
        switch (item.type) {
            case TYPE_BANNER: ((TextView) view.findViewById(R.id.tai_bench_text)).setText((String) item.data); break;
            case TYPE_DEVICE: bindDevice(view, (DeviceFacts) item.data); break;
            case TYPE_ROW: bindRow(view, (TaiBenchLeaderboard.Row) item.data); break;
            case TYPE_SECTION: bindSection(view, (String) item.data); break;
            case TYPE_NOTE: ((TextView) view.findViewById(R.id.tai_bench_text)).setText((String) item.data); break;
            case TYPE_FEATURES: bindFeatures(view, features); break;
            default: bindEmpty(view, Boolean.TRUE.equals(item.data)); break;
        }
    }

    @NonNull
    private View createBanner(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        TextView text = TaiBenchViews.title(context, "");
        text.setId(R.id.tai_bench_text);
        TextView open = TaiBenchViews.goButton(context, getString(R.string.tai_bench_banner_open));
        open.setOnClickListener(v -> openRun());
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(open);
        card.core.addView(row);
        card.core.setOnClickListener(v -> openRun());
        return card.outer;
    }

    @NonNull
    private View createDevice(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.core.addView(TaiBenchViews.title(context, getString(R.string.tai_bench_device_title)));
        TextView facts = TaiBenchViews.mono(context, "");
        facts.setId(R.id.tai_bench_text);
        facts.setLineSpacing(0f, 1.25f);
        card.core.addView(facts, TaiBenchViews.block(context, 6));
        return card.outer;
    }

    private void bindDevice(@NonNull View view, @Nullable DeviceFacts d) {
        Context context = view.getContext();
        TextView facts = view.findViewById(R.id.tai_bench_text);
        if (d == null) {
            facts.setText(R.string.tai_bench_device_reading);
            return;
        }
        String none = getString(R.string.tai_bench_none);
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[] {getString(R.string.tai_bench_device_soc), d.soc.isEmpty() ? none : d.soc});
        // Only where a GL context named it; see TaiGpuName.
        if (d.gpu != null) lines.add(new String[] {getString(R.string.tai_bench_device_gpu), d.gpu});
        String[][] rest = {
            {getString(R.string.tai_bench_device_ram), d.ramClassBytes > 0L
                ? getString(R.string.tai_bench_device_ram_class, Math.round(d.ramClassBytes / (double) (1024L * 1024L * 1024L))) : none},
            {getString(R.string.tai_bench_device_free_ram), TaiBenchViews.bytes(context, d.freeRamBytes)},
            {getString(R.string.tai_bench_device_storage), d.freeStorageBytes > 0L
                ? getString(R.string.tai_centre_free_space, TaiModelCentreRows.formatBytes(d.freeStorageBytes)) : none},
            {getString(R.string.tai_bench_device_battery), TaiBenchViews.batteryLabel(context, d.batteryPercent, d.charging)},
            {getString(R.string.tai_bench_device_heat), TaiBenchViews.heatLabel(context, d.thermalStatus)},
        };
        java.util.Collections.addAll(lines, rest);
        int width = 0;
        for (String[] line : lines) width = Math.max(width, line[0].length());
        StringBuilder text = new StringBuilder();
        for (String[] line : lines) {
            if (text.length() > 0) text.append('\n');
            // Monospace, so padding the label to the widest one lines the values up.
            text.append(line[0]);
            for (int i = line[0].length(); i < width + 2; i++) text.append(' ');
            text.append(line[1]);
        }
        facts.setText(text);
    }

    @NonNull
    private View createRow(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView rank = TaiBenchViews.mono(context, "");
        rank.setId(R.id.tai_bench_rank);
        rank.setMinWidth(TaiBenchViews.dp(context, 24));
        line.addView(rank);
        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(LinearLayout.VERTICAL);
        TextView title = TaiBenchViews.title(context, "");
        title.setId(R.id.tai_bench_title);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        middle.addView(title);
        TextView sub = TaiBenchViews.mono(context, "");
        sub.setId(R.id.tai_bench_subtitle);
        middle.addView(sub);
        TextView numbers = TaiBenchViews.body(context, "");
        numbers.setId(R.id.tai_bench_numbers);
        middle.addView(numbers, TaiBenchViews.block(context, 3));
        LinearLayout marks = new LinearLayout(context);
        marks.setId(R.id.tai_bench_marks);
        marks.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams marksParams = TaiBenchViews.block(context, 5);
        middle.addView(marks, marksParams);
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        middleParams.setMarginStart(TaiBenchViews.dp(context, 6));
        line.addView(middle, middleParams);
        LinearLayout end = new LinearLayout(context);
        end.setOrientation(LinearLayout.VERTICAL);
        end.setGravity(Gravity.END);
        TextView verdict = TaiBenchViews.pill(context, "", TaiModelCentreRows.Tone.NEUTRAL);
        verdict.setId(R.id.tai_bench_verdict);
        end.addView(verdict);
        TextView memory = TaiBenchViews.mono(context, "");
        memory.setId(R.id.tai_bench_figure);
        LinearLayout.LayoutParams memoryParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        memoryParams.topMargin = TaiBenchViews.dp(context, 4);
        end.addView(memory, memoryParams);
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endParams.setMarginStart(TaiBenchViews.dp(context, 10));
        line.addView(end, endParams);
        card.core.addView(line);
        card.core.setClickable(true);
        card.core.setFocusable(true);
        return card.outer;
    }

    private void bindRow(@NonNull View view, @NonNull TaiBenchLeaderboard.Row row) {
        Context context = view.getContext();
        TextView rank = view.findViewById(R.id.tai_bench_rank);
        rank.setText(row.rank > 0 ? row.rank + "." : "");
        rank.setVisibility(row.rank > 0 ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.tai_bench_title)).setText(row.displayName);
        StringBuilder sub = new StringBuilder(TaiBenchViews.backendLabel(context, row.backend))
            .append(" · ").append(TaiBenchViews.processorLabel(row.accelerator));
        if (row.speculative) sub.append(" · ").append(getString(R.string.tai_bench_mark_draft));
        sub.append(" · ").append(TaiBenchViews.ago(row.timestamp));
        ((TextView) view.findViewById(R.id.tai_bench_subtitle)).setText(sub);
        LinearLayout marks = view.findViewById(R.id.tai_bench_marks);
        marks.removeAllViews();
        if (row.charging) addMark(marks, getString(R.string.tai_bench_mark_charging), TaiModelCentreRows.Tone.NEUTRAL);
        if (row.warmStart) addMark(marks, getString(R.string.tai_bench_mark_warm_start), TaiModelCentreRows.Tone.WARN);
        if (row.lowBattery) addMark(marks, getString(R.string.tai_bench_mark_low_battery), TaiModelCentreRows.Tone.WARN);
        if (row.powerSave) addMark(marks, getString(R.string.tai_bench_mark_battery_saver), TaiModelCentreRows.Tone.WARN);
        if (row.screenOff) addMark(marks, getString(R.string.tai_bench_mark_screen_off), TaiModelCentreRows.Tone.WARN);
        if (row.olderVersion) addMark(marks, getString(R.string.tai_bench_mark_older_version), TaiModelCentreRows.Tone.NEUTRAL);
        if (!row.installed) addMark(marks, getString(R.string.tai_bench_mark_not_installed), TaiModelCentreRows.Tone.NEUTRAL);
        marks.setVisibility(marks.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        TextView numbers = view.findViewById(R.id.tai_bench_numbers);
        numbers.setText(row.crashed ? "" : TaiBenchViews.summaryLine(context, row.decodeTps, row.ttftMs, row.readMs));
        numbers.setVisibility(row.crashed ? View.GONE : View.VISIBLE);
        TextView memory = view.findViewById(R.id.tai_bench_figure);
        String memoryText = TaiBenchViews.memoryLine(context, row.memBytes);
        memory.setText(memoryText);
        memory.setVisibility(memoryText.isEmpty() ? View.GONE : View.VISIBLE);
        TextView verdict = view.findViewById(R.id.tai_bench_verdict);
        String label = TaiBenchViews.verdictLabel(context, row.verdict);
        verdict.setText(label);
        verdict.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        TaiModelCentreAdapter.tonePill(verdict, TaiBenchViews.verdictTone(row.verdict));
        View core = ((ViewGroup) ((ViewGroup) view).getChildAt(0)).getChildAt(0);
        core.setOnClickListener(v -> openResult(row.key));
        core.setContentDescription(getString(R.string.tai_bench_row_desc, row.displayName));
    }

    private void addMark(@NonNull LinearLayout marks, @NonNull String text, @NonNull TaiModelCentreRows.Tone tone) {
        Context context = marks.getContext();
        TextView pill = TaiBenchViews.pill(context, text, tone);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (marks.getChildCount() > 0) params.setMarginStart(TaiBenchViews.dp(context, 4));
        marks.addView(pill, params);
    }

    private void bindSection(@NonNull View view, @NonNull String title) {
        ((TextView) ((ViewGroup) view).getChildAt(0)).setText(title);
    }

    @NonNull
    private View createNote(@NonNull Context context) {
        TextView note = TaiBenchViews.body(context, "");
        note.setId(R.id.tai_bench_text);
        note.setPadding(TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 8), TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 4));
        return note;
    }

    @NonNull
    private View createEmpty(@NonNull Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 24), TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 12));
        TextView title = TaiBenchViews.title(context, "");
        title.setId(R.id.tai_bench_title);
        title.setGravity(Gravity.CENTER);
        column.addView(title);
        TextView summary = TaiBenchViews.body(context, "");
        summary.setId(R.id.tai_bench_text);
        summary.setGravity(Gravity.CENTER);
        column.addView(summary, TaiBenchViews.block(context, 4));
        return column;
    }

    private void bindEmpty(@NonNull View view, boolean loading) {
        ((TextView) view.findViewById(R.id.tai_bench_title)).setText(loading ? "" : getString(R.string.tai_bench_empty_title));
        ((TextView) view.findViewById(R.id.tai_bench_text)).setText(loading ? getString(R.string.tai_bench_loading)
            : getString(R.string.tai_bench_empty_summary));
    }

    // ---- your features ----

    @NonNull
    private View createFeatures(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.core.addView(TaiBenchViews.title(context, getString(R.string.tai_check_section_title)));
        card.core.addView(TaiBenchViews.body(context, getString(R.string.tai_check_section_summary)), TaiBenchViews.block(context, 2));
        LinearLayout rows = new LinearLayout(context);
        rows.setId(R.id.tai_check_rows);
        rows.setOrientation(LinearLayout.VERTICAL);
        card.core.addView(rows, TaiBenchViews.block(context, 6));
        TextView check = TaiBenchViews.goButton(context, getString(R.string.tai_check_action));
        check.setId(R.id.tai_check_action);
        check.setMinHeight(TaiBenchViews.dp(context, 44));
        check.setOnClickListener(v -> {
            TaiMotion.tick(v);
            showCheckSheet(featureList(features));
        });
        card.core.addView(check, TaiBenchViews.block(context, 12));
        return card.outer;
    }

    private void bindFeatures(@NonNull View view, @NonNull List<FeatureRow> rows) {
        Context context = view.getContext();
        LinearLayout list = view.findViewById(R.id.tai_check_rows);
        list.removeAllViews();
        for (FeatureRow row : rows) {
            list.addView(featureRow(context, row), TaiBenchViews.block(context, 10));
        }
        TaiBenchViews.setEnabled(view.findViewById(R.id.tai_check_action), !TaiBenchSession.get().isActive());
    }

    /** One feature: its name, the model and processor, the one figure, and Smooth / Usable / Slow. */
    @NonNull
    private View featureRow(@NonNull Context context, @NonNull FeatureRow row) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(LinearLayout.VERTICAL);
        TextView name = TaiBenchViews.title(context, TaiBenchViews.featureName(context, row.feature));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        middle.addView(name);
        String setup = row.modelName + (row.accelerator == null ? "" : " · " + TaiBenchViews.processorLabel(row.accelerator));
        TextView sub = TaiBenchViews.mono(context, setup);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        middle.addView(sub);
        String figure = figureText(row);
        middle.addView(TaiBenchViews.body(context, figure), TaiBenchViews.block(context, 3));
        if (row.stale) middle.addView(TaiBenchViews.body(context, getString(R.string.tai_check_older_version)), TaiBenchViews.block(context, 3));
        line.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String verdict = row.record == null || row.stale || row.record.isNull("verdict") ? "" : row.record.optString("verdict", "");
        String label = TaiBenchViews.verdictLabel(context, verdict);
        if (!label.isEmpty()) {
            TextView pill = TaiBenchViews.pill(context, label, TaiBenchViews.verdictTone(verdict));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(TaiBenchViews.dp(context, 10));
            line.addView(pill, params);
        }
        // A result from an older model file or runtime is greyed: the plan no longer reads it.
        line.setAlpha(row.stale ? STALE_ALPHA : 1f);
        line.setContentDescription(getString(R.string.tai_check_row_desc, TaiBenchViews.featureName(context, row.feature), figure));
        return line;
    }

    /** How faint a stale row is drawn. */
    private static final float STALE_ALPHA = 0.55f;

    @NonNull
    private String figureText(@NonNull FeatureRow row) {
        JSONObject record = row.record;
        if (record == null) return getString(R.string.tai_check_not_checked);
        String status = record.optString("status", "");
        if (!TaiFeatureCheck.STATUS_COMPLETE.equals(status)) {
            return getString(wasStopped(status) ? R.string.tai_check_stopped : R.string.tai_check_could_not_run);
        }
        if (!record.optBoolean("passed", false)) return getString(R.string.tai_check_wrong_answers);
        String figure = TaiBenchViews.featureFigure(requireContext(), row.feature, record.optDouble("speed", 0.0));
        return figure.isEmpty() ? getString(R.string.tai_check_could_not_run) : figure;
    }

    /**
     * A run the user or the guard ended, as opposed to one that failed: the runner files both under
     * {@code stopped:}, so the reason tells them apart (the stop button, heat, battery, leaving the screen,
     * an unload).
     */
    static boolean wasStopped(@NonNull String status) {
        switch (status) {
            case "stopped:cancelled":
            case "stopped:thermal":
            case "stopped:thermal_timeout":
            case "stopped:battery_low":
            case "stopped:left":
            case "stopped:unloaded":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static List<TaiFunction> featureList(@NonNull List<FeatureRow> rows) {
        List<TaiFunction> out = new ArrayList<>();
        for (FeatureRow row : rows) out.add(row.feature);
        return out;
    }

    /** The bench's Check sheet (battery, heat, battery saver, other downloads) for {@code chosen}; Start runs the check. */
    private void showCheckSheet(@NonNull List<TaiFunction> chosen) {
        Context context = getContext();
        if (context == null || chosen.isEmpty() || TaiBenchSession.get().isActive()) return;
        List<TaiBenchSession.Model> models = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (TaiFunction feature : chosen) {
            labels.add(TaiBenchViews.featureName(context, feature));
            for (FeatureRow row : features) {
                if (row.feature == feature) models.add(new TaiBenchSession.Model(row.modelId, row.modelName, true, 0L));
            }
        }
        String summary = getResources().getQuantityString(R.plurals.tai_check_sheet_summary, chosen.size(), chosen.size());
        TaiBenchCheckSheet.show(context, summary, TaiFeatureCheckRunner.PRESET_ID, false, models, plan -> {
            if (TaiBenchSession.get().startFeatureCheck(context, chosen, labels)) openRun();
        });
    }

    /** The Model Centre's offer opened this screen for one feature: its sheet comes up once the rows are read. */
    private void offerPendingCheck() {
        TaiFunction feature = pendingCheck;
        if (feature == null) return;
        pendingCheck = null;
        for (FeatureRow row : features) {
            if (row.feature == feature) {
                List<TaiFunction> one = new ArrayList<>();
                one.add(feature);
                showCheckSheet(one);
                return;
            }
        }
    }

    // ---- the floating bar ----

    @NonNull
    private View createBar(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.outer.setPadding(TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 6), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 12));
        card.shell.setElevation(TaiBenchViews.dp(context, 6));
        TextView last = TaiBenchViews.mono(context, "");
        last.setId(R.id.tai_bench_text);
        card.core.addView(last);
        TextView run = TaiBenchViews.goButton(context, getString(R.string.tai_bench_run_action));
        run.setId(R.id.tai_bench_action);
        run.setMinHeight(TaiBenchViews.dp(context, 44));
        run.setOnClickListener(v -> {
            TaiMotion.tick(v);
            openChoose();
        });
        card.core.addView(run, TaiBenchViews.block(context, 10));
        return card.outer;
    }

    private void bindBar(@NonNull String lastRun) {
        View view = bar;
        if (view == null) return;
        TextView last = view.findViewById(R.id.tai_bench_text);
        last.setText(lastRun);
        last.setVisibility(lastRun.isEmpty() ? View.GONE : View.VISIBLE);
        View run = view.findViewById(R.id.tai_bench_action);
        // With no summary line above it, the button needs no gap either.
        ((ViewGroup.MarginLayoutParams) run.getLayoutParams()).topMargin = lastRun.isEmpty() ? 0 : TaiBenchViews.dp(view.getContext(), 10);
        run.requestLayout();
        TaiBenchViews.setEnabled(run, !TaiBenchSession.get().isActive());
    }

    // ---- navigation ----

    private void openChoose() {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).openScreen(TaiBenchChooseFragment.class, R.string.tai_bench_choose_title, null);
        }
    }

    private void openRun() {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).openScreen(TaiBenchRunFragment.class, R.string.tai_bench_run_title, null);
        }
    }

    private void openResult(@NonNull String key) {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).openScreen(TaiBenchResultFragment.class, R.string.tai_bench_result_title,
                TaiBenchResultFragment.arguments(key));
        }
    }

    /** The rows on show, for tests: the adapter's item count. */
    int itemCountForTest() {
        return adapter.getItemCount();
    }

    /** Whether the store has been read once, for tests. */
    boolean boardLoadedForTest() {
        return board != null;
    }

    /** Whether the board on show has any ranked or broken row, for tests. */
    boolean hasRowsForTest() {
        return board != null && !board.empty();
    }
}
