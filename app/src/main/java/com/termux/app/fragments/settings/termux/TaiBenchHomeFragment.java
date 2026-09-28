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
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelStore;
import com.termux.app.activities.SettingsActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The benchmark's Home (spec Screen 1): a device card, the leaderboard under Speed | First word
 * | Memory tabs that re-sort the same entries, the broken entries apart, a "Run a benchmark"
 * button and when the last run was. {@link #open} is the one entry point the wiring slice calls:
 * it lands on the Run screen while a run is going, on Choose with one model preselected when
 * asked for a model, and here otherwise. Hosted by {@link SettingsActivity} like the Model centre.
 */
@Keep
public class TaiBenchHomeFragment extends Fragment implements TaiBenchListAdapter.Factory, TaiBenchSession.Listener {
    private static final int TYPE_BANNER = 0;
    private static final int TYPE_DEVICE = 1;
    private static final int TYPE_TABS = 2;
    private static final int TYPE_ROW = 3;
    private static final int TYPE_SECTION = 4;
    private static final int TYPE_NOTE = 5;
    private static final int TYPE_EMPTY = 6;
    private static final int TYPE_ACTION = 7;
    private static final String STATE_TAB = "tai_bench_tab";

    /** The device card's facts, gathered off the main thread. */
    private static final class DeviceFacts {
        @NonNull String soc = "";
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
    @NonNull private TaiBenchLeaderboard.Tab tab = TaiBenchLeaderboard.Tab.SPEED;
    @Nullable private TaiBenchLeaderboard.Board board;
    @Nullable private DeviceFacts facts;
    private int lastSeenEntries = -1;

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

    /** The versions this build is, for the "older version" mark. */
    @NonNull
    static TaiBenchLeaderboard.Versions versions() {
        return new TaiBenchLeaderboard.Versions(BuildConfig.VERSION_NAME, BuildConfig.LITERT_LM_VERSION, MnnTaiRuntime.RUNTIME_VERSION);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            int index = savedInstanceState.getInt(STATE_TAB, 0);
            TaiBenchLeaderboard.Tab[] tabs = TaiBenchLeaderboard.Tab.values();
            tab = tabs[Math.max(0, Math.min(tabs.length - 1, index))];
        }
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
        return list;
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, tab.ordinal());
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
            TaiBenchLeaderboard.Board finalBoard = loaded;
            handler.post(() -> {
                if (!isAdded()) return;
                if (finalBoard != null) board = finalBoard;
                facts = gathered;
                rebuild();
            });
        });
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
        DeviceFacts d = facts;
        String deviceSignature = d == null ? "" : d.soc + '|' + d.ramClassBytes + '|' + d.freeRamBytes + '|' + d.freeStorageBytes
            + '|' + d.batteryPercent + '|' + d.charging + '|' + d.thermalStatus;
        items.add(new TaiBenchListAdapter.Item(TYPE_DEVICE, "device", deviceSignature, d));
        TaiBenchLeaderboard.Board b = board;
        if (b == null || b.empty()) {
            items.add(new TaiBenchListAdapter.Item(TYPE_EMPTY, "empty", b == null ? "loading" : "empty", b == null));
        } else {
            items.add(new TaiBenchListAdapter.Item(TYPE_TABS, "tabs", "tabs|" + tab, tab));
            for (TaiBenchLeaderboard.Row row : TaiBenchLeaderboard.sorted(b.ranked, tab)) {
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
        items.add(new TaiBenchListAdapter.Item(TYPE_ACTION, "action", "action|" + lastRun + '|' + session.isActive(), lastRun));
        adapter.submit(items);
    }

    @NonNull
    private String rowSignature(@NonNull TaiBenchLeaderboard.Row row) {
        return row.recordId + '|' + row.rank + '|' + tab + '|' + row.installed;
    }

    // ---- rows ----

    @NonNull
    @Override
    public View create(@NonNull ViewGroup parent, int type) {
        Context context = parent.getContext();
        switch (type) {
            case TYPE_BANNER: return createBanner(context);
            case TYPE_DEVICE: return createDevice(context);
            case TYPE_TABS: return createTabs(context);
            case TYPE_ROW: return createRow(context);
            case TYPE_SECTION: return TaiBenchViews.sectionHeader(context, "", "");
            case TYPE_NOTE: return createNote(context);
            case TYPE_EMPTY: return createEmpty(context);
            default: return createAction(context);
        }
    }

    @Override
    public void bind(@NonNull View view, @NonNull TaiBenchListAdapter.Item item) {
        switch (item.type) {
            case TYPE_BANNER: ((TextView) view.findViewById(R.id.tai_bench_text)).setText((String) item.data); break;
            case TYPE_DEVICE: bindDevice(view, (DeviceFacts) item.data); break;
            case TYPE_TABS: bindTabs(view, (TaiBenchLeaderboard.Tab) item.data); break;
            case TYPE_ROW: bindRow(view, (TaiBenchLeaderboard.Row) item.data); break;
            case TYPE_SECTION: bindSection(view, (String) item.data); break;
            case TYPE_NOTE: ((TextView) view.findViewById(R.id.tai_bench_text)).setText((String) item.data); break;
            case TYPE_EMPTY: bindEmpty(view, Boolean.TRUE.equals(item.data)); break;
            default: bindAction(view, (String) item.data); break;
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
        String[][] lines = {
            {getString(R.string.tai_bench_device_soc), d.soc.isEmpty() ? none : d.soc},
            {getString(R.string.tai_bench_device_ram), d.ramClassBytes > 0L
                ? getString(R.string.tai_bench_device_ram_class, Math.round(d.ramClassBytes / (double) (1024L * 1024L * 1024L))) : none},
            {getString(R.string.tai_bench_device_free_ram), TaiBenchViews.bytes(context, d.freeRamBytes)},
            {getString(R.string.tai_bench_device_storage), d.freeStorageBytes > 0L
                ? getString(R.string.tai_centre_free_space, TaiModelCentreRows.formatBytes(d.freeStorageBytes)) : none},
            {getString(R.string.tai_bench_device_battery), TaiBenchViews.batteryLabel(context, d.batteryPercent, d.charging)},
            {getString(R.string.tai_bench_device_heat), TaiBenchViews.heatLabel(context, d.thermalStatus)},
        };
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
    private View createTabs(@NonNull Context context) {
        FrameLayout frame = new FrameLayout(context);
        frame.setPadding(TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 14), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 6));
        TaiSegmentedTabs tabs = new TaiSegmentedTabs(context);
        tabs.setId(R.id.tai_bench_tabs);
        tabs.setContentDescription(getString(R.string.tai_bench_tabs_desc));
        tabs.setLabels(getString(R.string.tai_bench_tab_speed), getString(R.string.tai_bench_tab_first_word), getString(R.string.tai_bench_tab_memory));
        tabs.setOnSegmentSelectedListener(index -> {
            TaiBenchLeaderboard.Tab[] values = TaiBenchLeaderboard.Tab.values();
            if (index < 0 || index >= values.length || values[index] == tab) return;
            tab = values[index];
            rebuild();
        });
        frame.addView(tabs, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TaiBenchViews.dp(context, 42)));
        return frame;
    }

    private void bindTabs(@NonNull View view, @NonNull TaiBenchLeaderboard.Tab selected) {
        TaiSegmentedTabs tabs = view.findViewById(R.id.tai_bench_tabs);
        tabs.select(selected.ordinal(), tabs.selectedIndex() >= 0);
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
        TextView figure = TaiBenchViews.figure(context, "", 17f);
        figure.setId(R.id.tai_bench_figure);
        end.addView(figure);
        TextView verdict = TaiBenchViews.pill(context, "", TaiModelCentreRows.Tone.NEUTRAL);
        verdict.setId(R.id.tai_bench_verdict);
        LinearLayout.LayoutParams verdictParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verdictParams.topMargin = TaiBenchViews.dp(context, 4);
        end.addView(verdict, verdictParams);
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
        if (row.olderVersion) addMark(marks, getString(R.string.tai_bench_mark_older_version), TaiModelCentreRows.Tone.NEUTRAL);
        if (!row.installed) addMark(marks, getString(R.string.tai_bench_mark_not_installed), TaiModelCentreRows.Tone.NEUTRAL);
        marks.setVisibility(marks.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        TextView figure = view.findViewById(R.id.tai_bench_figure);
        switch (tab) {
            case FIRST_WORD: figure.setText(TaiBenchViews.millis(context, row.firstWordMs)); break;
            case MEMORY: figure.setText(TaiBenchViews.bytes(context, row.memBytes)); break;
            default: figure.setText(TaiBenchViews.tps(context, row.writingTps)); break;
        }
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

    @NonNull
    private View createAction(@NonNull Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 8));
        TextView run = TaiBenchViews.goButton(context, getString(R.string.tai_bench_run_action));
        run.setId(R.id.tai_bench_action);
        run.setMinHeight(TaiBenchViews.dp(context, 44));
        run.setOnClickListener(v -> {
            TaiMotion.tick(v);
            openChoose();
        });
        column.addView(run, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView last = TaiBenchViews.body(context, "");
        last.setId(R.id.tai_bench_text);
        last.setGravity(Gravity.CENTER);
        column.addView(last, TaiBenchViews.block(context, 8));
        return column;
    }

    private void bindAction(@NonNull View view, @NonNull String lastRun) {
        TextView last = view.findViewById(R.id.tai_bench_text);
        last.setText(lastRun);
        last.setVisibility(lastRun.isEmpty() ? View.GONE : View.VISIBLE);
        View run = view.findViewById(R.id.tai_bench_action);
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
