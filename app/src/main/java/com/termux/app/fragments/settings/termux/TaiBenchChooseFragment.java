package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSpeechModels;
import com.termux.ai.TaiTtsModels;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.notice.AppNotice;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Choose (spec Screen 2): the Quick | Standard | Thorough selector, the installed chat models
 * first, then "Worth a download" (the catalogue's recommended entries that pass the filter), each
 * row with backend, size, processors and the estimated time; the hidden models counted with
 * "Show why"; a footer with the model count, the total time and the download size, and
 * Continue, which opens the Check sheet. Opened with a model id as
 * {@link SettingsActivity#EXTRA_INITIAL_PLACE}, only that model starts selected.
 */
@Keep
public class TaiBenchChooseFragment extends Fragment implements TaiBenchListAdapter.Factory {
    private static final int TYPE_PRESET = 0;
    private static final int TYPE_SECTION = 1;
    private static final int TYPE_MODEL = 2;
    private static final int TYPE_HIDDEN = 3;
    private static final int TYPE_REASON = 4;
    private static final int TYPE_EMPTY = 5;
    private static final int TYPE_FOOTER = 6;
    private static final String STATE_PRESET = "tai_bench_preset";
    private static final String STATE_SELECTED = "tai_bench_selected";
    private static final String STATE_SHOW_WHY = "tai_bench_show_why";
    private static final String STATE_TOUCHED = "tai_bench_touched";

    /** One row's worth of a model, filter applied. */
    private static final class Offer {
        @NonNull final TaiBenchChoice.Candidate candidate;
        @NonNull final TaiBenchChoice.Verdict verdict;

        Offer(@NonNull TaiBenchChoice.Candidate candidate, @NonNull TaiBenchChoice.Verdict verdict) {
            this.candidate = candidate;
            this.verdict = verdict;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-bench-choose");
        thread.setDaemon(true);
        return thread;
    });
    private final TaiBenchListAdapter adapter = new TaiBenchListAdapter(this);
    @NonNull private TaiBenchSuite.Preset preset = TaiBenchSuite.Preset.STANDARD;
    @Nullable private TaiBenchChoice.Device device;
    @NonNull private List<Offer> installed = Collections.emptyList();
    @NonNull private List<Offer> downloads = Collections.emptyList();
    @NonNull private List<Offer> hidden = Collections.emptyList();
    private final Set<String> selected = new LinkedHashSet<>();
    /** The selection has been made (by the person, or by the preselect argument); the default no longer applies. */
    private boolean touched;
    private boolean showWhy;
    @Nullable private String preselect;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        preselect = arguments == null ? null : arguments.getString(SettingsActivity.EXTRA_INITIAL_PLACE);
        if (preselect != null && preselect.isEmpty()) preselect = null;
        if (savedInstanceState != null) {
            TaiBenchSuite.Preset saved = TaiBenchSuite.Preset.fromId(savedInstanceState.getString(STATE_PRESET));
            if (saved != null) preset = saved;
            List<String> ids = savedInstanceState.getStringArrayList(STATE_SELECTED);
            if (ids != null) selected.addAll(ids);
            showWhy = savedInstanceState.getBoolean(STATE_SHOW_WHY, false);
            touched = savedInstanceState.getBoolean(STATE_TOUCHED, false);
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
            list.setItemAnimator(animator);
        }
        list.setAdapter(adapter);
        return list;
    }

    @Override
    public void onStart() {
        super.onStart();
        rebuild();
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.tai_bench_choose_title);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PRESET, preset.id);
        outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(selected));
        outState.putBoolean(STATE_SHOW_WHY, showWhy);
        outState.putBoolean(STATE_TOUCHED, touched);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    /** Reads the installed models, the catalogue and the device off the main thread, then filters. */
    private void load() {
        Context context = getContext();
        if (context == null || executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            TaiBenchChoice.Device seen = readDevice(app);
            List<Offer> shownInstalled = new ArrayList<>();
            List<Offer> shownDownloads = new ArrayList<>();
            List<Offer> hiddenOnes = new ArrayList<>();
            TaiModelStore store = new TaiModelStore(app);
            store.pruneMissingUserModels();
            List<TaiModelSpec> chat = new ArrayList<>();
            for (TaiModelSpec spec : store.getInstalledUserModels().values()) {
                if (TaiSpeechModels.isSpeechModel(spec) || TaiTtsModels.isTtsModel(spec)) continue;
                if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) continue;
                chat.add(spec);
            }
            Collections.sort(chat, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
            Set<String> installedIds = new LinkedHashSet<>();
            for (TaiModelSpec spec : chat) {
                installedIds.add(spec.id);
                TaiBenchChoice.Candidate candidate = new TaiBenchChoice.Candidate(spec.id, spec.displayName, spec.backend,
                    spec.sizeBytes, true, false, spec.localPath);
                TaiBenchChoice.Verdict verdict = TaiBenchChoice.judge(candidate, seen);
                (verdict.fit == TaiBenchChoice.Fit.HIDDEN ? hiddenOnes : shownInstalled).add(new Offer(candidate, verdict));
            }
            for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.chatEntries().values()) {
                if (installedIds.contains(entry.modelId) || !entry.recommended || !entry.downloadAvailable) continue;
                TaiBenchChoice.Candidate candidate = new TaiBenchChoice.Candidate(entry.modelId, entry.displayName, entry.backend,
                    entry.sizeBytes, false, true, entry.artifactPath);
                TaiBenchChoice.Verdict verdict = TaiBenchChoice.judge(candidate, seen);
                (verdict.fit == TaiBenchChoice.Fit.HIDDEN ? hiddenOnes : shownDownloads).add(new Offer(candidate, verdict));
            }
            handler.post(() -> {
                if (!isAdded()) return;
                device = seen;
                installed = shownInstalled;
                downloads = shownDownloads;
                hidden = hiddenOnes;
                applyDefaultSelection();
                rebuild();
            });
        });
    }

    @NonNull
    private static TaiBenchChoice.Device readDevice(@NonNull Context app) {
        long memory = 0L;
        String soc = null;
        boolean mnn = false;
        boolean gpu = false;
        try {
            TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(app);
            memory = capabilities.memoryBytes;
            soc = capabilities.socModel;
            mnn = capabilities.mnnSupported;
            gpu = capabilities.supportsAccelerator("gpu");
        } catch (RuntimeException ignored) {
        }
        long free = -1L;
        try {
            File probe = new TaiModelStore(app).getModelsDirectory();
            while (probe != null && !probe.exists()) probe = probe.getParentFile();
            free = (probe == null ? app.getFilesDir() : probe).getUsableSpace();
        } catch (RuntimeException ignored) {
        }
        return new TaiBenchChoice.Device(memory, free, soc, mnn, gpu);
    }

    /** Untouched: every installed model is in; a preselected model is the only one in. */
    private void applyDefaultSelection() {
        if (touched) return;
        selected.clear();
        if (preselect != null) {
            for (Offer offer : installed) if (offer.candidate.modelId.equals(preselect)) selected.add(preselect);
            for (Offer offer : downloads) if (offer.candidate.modelId.equals(preselect)) selected.add(preselect);
            touched = !selected.isEmpty();
            if (touched) return;
        }
        for (Offer offer : installed) selected.add(offer.candidate.modelId);
    }

    // ---- the list ----

    private void rebuild() {
        Context context = getContext();
        if (context == null) return;
        List<TaiBenchListAdapter.Item> items = new ArrayList<>();
        items.add(new TaiBenchListAdapter.Item(TYPE_PRESET, "preset", "preset|" + preset.id, preset));
        TaiBenchChoice.Device seen = device;
        if (seen == null) {
            items.add(new TaiBenchListAdapter.Item(TYPE_EMPTY, "loading", "loading", getString(R.string.tai_bench_loading)));
            adapter.submit(items);
            return;
        }
        String installedHeader = getString(R.string.tai_bench_section_installed);
        items.add(new TaiBenchListAdapter.Item(TYPE_SECTION, "installed", installedHeader, installedHeader));
        if (installed.isEmpty()) {
            items.add(new TaiBenchListAdapter.Item(TYPE_EMPTY, "empty-installed", "empty-installed", getString(R.string.tai_bench_empty_installed)));
        }
        for (Offer offer : installed) items.add(modelItem(offer, seen));
        if (!downloads.isEmpty()) {
            String header = getString(R.string.tai_bench_section_downloads);
            items.add(new TaiBenchListAdapter.Item(TYPE_SECTION, "downloads", header, header));
            for (Offer offer : downloads) items.add(modelItem(offer, seen));
        }
        if (!hidden.isEmpty()) {
            items.add(new TaiBenchListAdapter.Item(TYPE_HIDDEN, "hidden", "hidden|" + hidden.size() + '|' + showWhy, hidden.size()));
            if (showWhy) {
                for (Offer offer : hidden) {
                    items.add(new TaiBenchListAdapter.Item(TYPE_REASON, "why-" + offer.candidate.modelId,
                        offer.candidate.modelId + '|' + offer.verdict.reason, offer));
                }
            }
        }
        int count = 0;
        long totalMs = 0L;
        long bytes = 0L;
        for (Offer offer : selectedOffers()) {
            count++;
            totalMs += TaiBenchChoice.estimateMs(preset, seen);
            if (!offer.candidate.installed) bytes += Math.max(0L, offer.candidate.sizeBytes);
        }
        String footer = count + "|" + totalMs + "|" + bytes;
        items.add(new TaiBenchListAdapter.Item(TYPE_FOOTER, "footer", footer, new long[] {count, totalMs, bytes}));
        adapter.submit(items);
    }

    @NonNull
    private TaiBenchListAdapter.Item modelItem(@NonNull Offer offer, @NonNull TaiBenchChoice.Device seen) {
        boolean on = selected.contains(offer.candidate.modelId);
        String signature = offer.candidate.modelId + '|' + on + '|' + preset.id + '|' + offer.verdict.fit + '|' + seen.gpuSupported;
        return new TaiBenchListAdapter.Item(TYPE_MODEL, offer.candidate.modelId, signature, offer);
    }

    @NonNull
    private List<Offer> selectedOffers() {
        List<Offer> result = new ArrayList<>();
        for (Offer offer : installed) if (selected.contains(offer.candidate.modelId)) result.add(offer);
        for (Offer offer : downloads) if (selected.contains(offer.candidate.modelId)) result.add(offer);
        return result;
    }

    // ---- rows ----

    @NonNull
    @Override
    public View create(@NonNull ViewGroup parent, int type) {
        Context context = parent.getContext();
        switch (type) {
            case TYPE_PRESET: return createPreset(context);
            case TYPE_SECTION: return TaiBenchViews.sectionHeader(context, "", "");
            case TYPE_MODEL: return createModel(context);
            case TYPE_HIDDEN: return createHidden(context);
            case TYPE_REASON: return createReason(context);
            case TYPE_EMPTY: return createEmpty(context);
            default: return createFooter(context);
        }
    }

    @Override
    public void bind(@NonNull View view, @NonNull TaiBenchListAdapter.Item item) {
        switch (item.type) {
            case TYPE_PRESET: bindPreset(view, (TaiBenchSuite.Preset) item.data); break;
            case TYPE_SECTION: ((TextView) ((ViewGroup) view).getChildAt(0)).setText((String) item.data); break;
            case TYPE_MODEL: bindModel(view, (Offer) item.data); break;
            case TYPE_HIDDEN: bindHidden(view, (Integer) item.data); break;
            case TYPE_REASON: bindReason(view, (Offer) item.data); break;
            case TYPE_EMPTY: ((TextView) view).setText((String) item.data); break;
            default: bindFooter(view, (long[]) item.data); break;
        }
    }

    @NonNull
    private View createPreset(@NonNull Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 14), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 4));
        TaiSegmentedTabs tabs = new TaiSegmentedTabs(context);
        tabs.setId(R.id.tai_bench_tabs);
        tabs.setContentDescription(getString(R.string.tai_bench_preset_desc));
        tabs.setLabels(getString(R.string.tai_bench_preset_quick), getString(R.string.tai_bench_preset_standard), getString(R.string.tai_bench_preset_thorough));
        tabs.setOnSegmentSelectedListener(index -> {
            TaiBenchSuite.Preset[] values = TaiBenchSuite.Preset.values();
            if (index < 0 || index >= values.length || values[index] == preset) return;
            preset = values[index];
            rebuild();
        });
        column.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TaiBenchViews.dp(context, 42)));
        TextView hint = TaiBenchViews.body(context, "");
        hint.setId(R.id.tai_bench_text);
        hint.setPadding(TaiBenchViews.dp(context, 6), 0, TaiBenchViews.dp(context, 6), 0);
        column.addView(hint, TaiBenchViews.block(context, 8));
        return column;
    }

    private void bindPreset(@NonNull View view, @NonNull TaiBenchSuite.Preset chosen) {
        TaiSegmentedTabs tabs = view.findViewById(R.id.tai_bench_tabs);
        tabs.select(chosen.ordinal(), tabs.selectedIndex() >= 0);
        TextView hint = view.findViewById(R.id.tai_bench_text);
        switch (chosen) {
            case QUICK: hint.setText(R.string.tai_bench_preset_quick_hint); break;
            case THOROUGH: hint.setText(R.string.tai_bench_preset_thorough_hint); break;
            default: hint.setText(R.string.tai_bench_preset_standard_hint); break;
        }
    }

    @NonNull
    private View createModel(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout well = new FrameLayout(context);
        int size = TaiBenchViews.dp(context, 28);
        well.setBackgroundResource(R.drawable.tai_centre_round);
        ImageView check = new ImageView(context);
        check.setId(R.id.tai_bench_check);
        check.setImageResource(R.drawable.ic_tai_check);
        int glyph = TaiBenchViews.dp(context, 16);
        well.addView(check, new FrameLayout.LayoutParams(glyph, glyph, Gravity.CENTER));
        line.addView(well, new LinearLayout.LayoutParams(size, size));
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
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        middleParams.setMarginStart(TaiBenchViews.dp(context, 10));
        line.addView(middle, middleParams);
        TextView backend = TaiBenchViews.backendPill(context, "");
        backend.setId(R.id.tai_bench_marks);
        LinearLayout.LayoutParams backendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backendParams.setMarginStart(TaiBenchViews.dp(context, 8));
        line.addView(backend, backendParams);
        TextView tight = TaiBenchViews.pill(context, "", TaiModelCentreRows.Tone.WARN);
        tight.setId(R.id.tai_bench_verdict);
        LinearLayout.LayoutParams tightParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tightParams.setMarginStart(TaiBenchViews.dp(context, 4));
        line.addView(tight, tightParams);
        card.core.addView(line);
        card.core.setClickable(true);
        card.core.setFocusable(true);
        return card.outer;
    }

    private void bindModel(@NonNull View view, @NonNull Offer offer) {
        Context context = view.getContext();
        TaiBenchChoice.Device seen = device;
        boolean on = selected.contains(offer.candidate.modelId);
        ImageView check = view.findViewById(R.id.tai_bench_check);
        View well = (View) check.getParent();
        int fill = on ? TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorPrimary)
            : TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorSurfacePanelHighest);
        well.setBackgroundTintList(ColorStateList.valueOf(fill));
        check.setImageTintList(ColorStateList.valueOf(on ? TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnPrimary)
            : TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant)));
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        ((TextView) view.findViewById(R.id.tai_bench_title)).setText(offer.candidate.displayName);
        int processors = seen == null ? 1 : TaiBenchChoice.processors(preset, seen);
        StringBuilder sub = new StringBuilder();
        if (offer.candidate.sizeBytes > 0L) sub.append(TaiModelCentreRows.formatBytes(offer.candidate.sizeBytes)).append(" · ");
        sub.append(getString(processors == 2 ? R.string.tai_bench_processors_both : R.string.tai_bench_processors_cpu));
        sub.append(" · ").append(TaiBenchViews.duration(context, TaiBenchSuite.estimateMs(preset, processors)));
        if (!offer.candidate.installed) sub.append(" · ").append(getString(R.string.tai_bench_row_download));
        ((TextView) view.findViewById(R.id.tai_bench_subtitle)).setText(sub);
        TextView backend = view.findViewById(R.id.tai_bench_marks);
        String label = TaiBenchViews.backendLabel(context, offer.candidate.backend);
        backend.setText(label);
        backend.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        TextView tight = view.findViewById(R.id.tai_bench_verdict);
        boolean isTight = offer.verdict.fit == TaiBenchChoice.Fit.TIGHT;
        tight.setText(isTight ? getString(R.string.tai_bench_fit_tight) : "");
        tight.setVisibility(isTight ? View.VISIBLE : View.GONE);
        View core = ((ViewGroup) ((ViewGroup) view).getChildAt(0)).getChildAt(0);
        core.setContentDescription(getString(on ? R.string.tai_bench_row_selected_desc : R.string.tai_bench_row_unselected_desc,
            offer.candidate.displayName));
        core.setOnClickListener(v -> {
            TaiMotion.tick(v);
            touched = true;
            if (!selected.remove(offer.candidate.modelId)) selected.add(offer.candidate.modelId);
            rebuild();
        });
    }

    @NonNull
    private View createHidden(@NonNull Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 12), TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 4));
        TextView text = TaiBenchViews.body(context, "");
        text.setId(R.id.tai_bench_text);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView why = TaiBenchViews.ghostButton(context, "");
        why.setId(R.id.tai_bench_action);
        why.setMinHeight(TaiBenchViews.dp(context, 30));
        why.setOnClickListener(v -> {
            showWhy = !showWhy;
            rebuild();
        });
        row.addView(why);
        return row;
    }

    private void bindHidden(@NonNull View view, int count) {
        ((TextView) view.findViewById(R.id.tai_bench_text)).setText(getResources().getQuantityString(R.plurals.tai_bench_hidden_count, count, count));
        ((TextView) view.findViewById(R.id.tai_bench_action)).setText(showWhy ? R.string.tai_bench_hide_why : R.string.tai_bench_show_why);
    }

    @NonNull
    private View createReason(@NonNull Context context) {
        TextView line = TaiBenchViews.mono(context, "");
        line.setPadding(TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 3), TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 3));
        return line;
    }

    private void bindReason(@NonNull View view, @NonNull Offer offer) {
        ((TextView) view).setText(offer.candidate.displayName + " — " + reasonText(offer.verdict.reason));
    }

    @NonNull
    private String reasonText(@Nullable TaiBenchChoice.Reason reason) {
        if (reason == null) return "";
        switch (reason) {
            case TOO_BIG: return getString(R.string.tai_bench_reason_too_big);
            case NO_SPACE: return getString(R.string.tai_bench_reason_no_space);
            case WRONG_CHIP: return getString(R.string.tai_bench_reason_wrong_chip);
            default: return getString(R.string.tai_bench_reason_mnn);
        }
    }

    @NonNull
    private View createEmpty(@NonNull Context context) {
        TextView text = TaiBenchViews.body(context, "");
        text.setGravity(Gravity.CENTER);
        text.setPadding(TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 12), TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 12));
        return text;
    }

    @NonNull
    private View createFooter(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        TextView summary = TaiBenchViews.mono(context, "");
        summary.setId(R.id.tai_bench_text);
        card.core.addView(summary);
        TextView go = TaiBenchViews.goButton(context, getString(R.string.tai_bench_continue));
        go.setId(R.id.tai_bench_action);
        go.setMinHeight(TaiBenchViews.dp(context, 44));
        go.setOnClickListener(v -> {
            TaiMotion.tick(v);
            openCheck();
        });
        card.core.addView(go, TaiBenchViews.block(context, 10));
        return card.outer;
    }

    private void bindFooter(@NonNull View view, @NonNull long[] figures) {
        Context context = view.getContext();
        int count = (int) figures[0];
        StringBuilder text = new StringBuilder(getResources().getQuantityString(R.plurals.tai_bench_footer_models, count, count));
        if (count > 0) text.append(" · ").append(TaiBenchViews.duration(context, figures[1]));
        if (figures[2] > 0L) text.append(" · ").append(getString(R.string.tai_bench_footer_download, TaiModelCentreRows.formatBytes(figures[2])));
        ((TextView) view.findViewById(R.id.tai_bench_text)).setText(text);
        TaiBenchViews.setEnabled(view.findViewById(R.id.tai_bench_action), count > 0 && !TaiBenchSession.get().isActive());
    }

    // ---- Check and Start ----

    private void openCheck() {
        Context context = getContext();
        if (context == null) return;
        if (TaiBenchSession.get().isActive()) {
            AppNotice.show(context, R.string.tai_bench_already_running, true);
            return;
        }
        List<TaiBenchSession.Model> models = new ArrayList<>();
        for (Offer offer : selectedOffers()) {
            models.add(new TaiBenchSession.Model(offer.candidate.modelId, offer.candidate.displayName, offer.candidate.installed,
                offer.candidate.sizeBytes));
        }
        if (models.isEmpty()) return;
        TaiBenchCheckSheet.show(context, preset, models, this::start);
    }

    private void start(@NonNull TaiBenchSession.Plan plan) {
        Context context = getContext();
        if (context == null || executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        // The leaderboard's top speed is what a "New best" has to beat; read off the main thread.
        executor.execute(() -> {
            double best = 0.0;
            try {
                best = TaiBenchLeaderboard.read(TaiManager.getInstance(app).benchmarks(), TaiBenchHomeFragment.versions()).bestTps();
            } catch (JSONException | RuntimeException ignored) {
            }
            double finalBest = best;
            handler.post(() -> {
                if (!isAdded()) return;
                if (!TaiBenchSession.get().start(app, plan, finalBest)) {
                    AppNotice.show(getContext(), R.string.tai_bench_already_running, true);
                    return;
                }
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).openScreen(TaiBenchRunFragment.class, R.string.tai_bench_run_title, null);
                }
            });
        });
    }

    /** The ids selected now, for tests. */
    @NonNull
    Set<String> selectedForTest() {
        return new LinkedHashSet<>(selected);
    }
}
