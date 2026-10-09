package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import androidx.appcompat.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiDownloadEngine;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiDownloadQueue;
import com.termux.ai.TaiFeatureCheckRunner;
import com.termux.ai.TaiFeatureCheckStore;
import com.termux.ai.TaiFeaturePlans;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.ai.TaiTierPolicy;
import com.termux.ai.TaiReadAloud;
import com.termux.ai.TaiTtsModels;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.notice.AppNotice;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Model centre: one screen that shows what each function uses and gets, downloads and manages
 * every model (tai-device-tiers spec §4). A device line on top, the live Downloads with pause,
 * resume, "start now", retry and cancel, then one list under a Functions | Installed | Get models
 * segmented control: a row per function that opens its picker sheet, what is on the phone with the
 * functions that use it, and what the catalogue offers (with the link bar for any other model).
 *
 * <p>A plain {@link Fragment} over a RecyclerView rather than a preference screen: the rows are
 * nested cards with several live controls each (a bar, two round buttons, a pill), the top has a
 * text field, and the list re-renders at the hub's 5 Hz. Preferences would need a custom subclass
 * per row and still rebuild through {@code notifyChanged()}, while one adapter with DiffUtil
 * rebinds exactly the row that moved. SettingsActivity hosts any Fragment from this package (the
 * keyboard colour editor is one), and {@link SettingsActivity#openScreen} pushes it like any
 * other page.
 *
 * <p>Everything live comes from {@link TaiDownloadHub}: the fragment subscribes in onStart and
 * unsubscribes in onStop, and never polls. Store reads (the installed models) happen only when a
 * download changes status, not on every progress tick.
 */
@Keep
public class TaiModelCentreFragment extends Fragment
    implements TaiDownloadHub.Listener, TaiImportFlow.Host, TaiModelCentreAdapter.Callbacks {

    /** Deep-link segments, passed as {@link SettingsActivity#EXTRA_INITIAL_PLACE}. */
    public static final String SEGMENT_FUNCTIONS = "functions";
    public static final String SEGMENT_INSTALLED = "installed";
    public static final String SEGMENT_GET = "get";
    /** The old Chat and Speech segments are one Get models segment now; the names still open it. */
    public static final String SEGMENT_CHAT = SEGMENT_GET;
    public static final String SEGMENT_SPEECH = SEGMENT_GET;
    private static final String[] SEGMENTS = {SEGMENT_FUNCTIONS, SEGMENT_INSTALLED, SEGMENT_GET};
    private static final int SEGMENT_FUNCTIONS_INDEX = 0;
    private static final int SEGMENT_INSTALLED_INDEX = 1;
    private static final int SEGMENT_GET_INDEX = 2;
    /** How long a row brought into view by a deep link keeps its ring. */
    private static final long HIGHLIGHT_MS = 2400L;
    private static final String STATE_SEGMENT = "tai_centre_segment";
    private static final String STATE_LINK = "tai_centre_link";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "tai-model-centre");
        thread.setDaemon(true);
        return thread;
    });
    private final TaiImportFlow importFlow = new TaiImportFlow(this);
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), importFlow::onFileSelected);
    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocumentTree(), importFlow::onFolderSelected);
    private final TaiModelCentreAdapter adapter = new TaiModelCentreAdapter(this);

    private int segment;
    @NonNull private String linkText = "";
    @NonNull private String linkError = "";

    @NonNull private List<TaiDownloadHub.Snapshot> downloads = Collections.emptyList();
    /** Status per transfer id as of the last push, to see what just changed. */
    @NonNull private Map<String, String> statuses = new HashMap<>();
    /** Transfer ids that were already there when the screen opened: they do not "arrive". */
    private final Set<String> knownTransfers = new HashSet<>();
    private final Set<String> shownTransfers = new HashSet<>();
    private boolean firstPush = true;

    /** Catalogue ids whose Install request is on its way (the pill shows a ring). */
    private final Set<String> installing = new HashSet<>();
    /** A line under a catalogue row: the space refusal, or why the request failed. */
    private final Map<String, String> errors = new HashMap<>();
    /** Downloads that finished while the screen was open, by model id, with whether each is speech. */
    private final LinkedHashMap<String, Boolean> banners = new LinkedHashMap<>();

    @NonNull private Map<String, TaiModelSpec> installedAll = Collections.emptyMap();
    @NonNull private List<TaiModelSpec> installedChat = Collections.emptyList();
    @NonNull private List<TaiModelSpec> installedSpeech = Collections.emptyList();
    /** Installed text-to-image models; their own "Image generation" group under Installed, never in a chat list. */
    @NonNull private List<TaiModelSpec> installedImage = Collections.emptyList();
    /** Installed speech-output (voice) models; shown under Installed and never as chat or speech-to-text. */
    @NonNull private List<TaiModelSpec> installedVoice = Collections.emptyList();
    /** The resolver for this phone's picks, rebuilt with the installed models; the Functions rows read it. */
    @Nullable private TaiFunctionModels functionModels;
    @NonNull private String deviceLine = "";
    @NonNull private List<TaiFunctionRows.FunctionRow> functionRows = Collections.emptyList();
    /** "Used by: ..." per installed model id, empty text for a model no function uses. */
    @NonNull private Map<String, String> usedBy = Collections.emptyMap();
    /** What deleting each installed model would leave each function on; see {@link TaiFunctionRows#deleteWarning}. */
    @NonNull private Map<String, String> deleteWarnings = Collections.emptyMap();
    /** Bumped per {@link #loadFunctions}, so only the newest read is applied. */
    private int functionsGeneration;
    @NonNull private String tidyLevel = "polished";
    /** A model row a deep link asked for: scrolled to and ringed on the next {@link #rebuild}, then cleared. */
    @Nullable private String pendingScrollKey;
    /** The row ringed right now, until {@link #HIGHLIGHT_MS} passes. */
    @Nullable private String highlightKey;
    @Nullable private RecyclerView listView;
    @Nullable private String defaultId;
    @Nullable private String voiceId;
    @Nullable private String loadedId;
    /** Best ranked writing speed per model id, for the quiet speed pill; refreshed on {@link #onStart}. */
    @NonNull private Map<String, Double> benchmarkSpeeds = Collections.emptyMap();
    private int parallel = TaiDownloadQueue.DEFAULT_PARALLEL;
    private long deviceMemoryBytes = -1L;

    /** Arguments that open the centre on one segment. */
    @NonNull
    public static Bundle arguments(@NonNull String segment) {
        Bundle arguments = new Bundle();
        arguments.putString(SettingsActivity.EXTRA_INITIAL_PLACE, segment);
        return arguments;
    }

    /**
     * Opens the centre on a segment: pushed in place inside Settings (so Back returns to the
     * screen that asked), or launched into Settings from anywhere else.
     */
    public static void open(@Nullable Activity activity, @NonNull String segment) {
        if (activity == null) return;
        if (activity instanceof SettingsActivity) {
            ((SettingsActivity) activity).openScreen(TaiModelCentreFragment.class, R.string.tai_model_centre_title,
                arguments(segment));
        } else {
            activity.startActivity(SettingsActivity.createFragmentIntent(activity, TaiModelCentreFragment.class,
                R.string.tai_model_centre_title, segment, null));
        }
    }

    /** The segment a deep link names; the old "chat" and "speech" are Get models, anything else Functions. */
    static int segmentIndex(@Nullable String segment) {
        if (SEGMENT_INSTALLED.equals(segment)) return SEGMENT_INSTALLED_INDEX;
        if (SEGMENT_GET.equals(segment) || "chat".equals(segment) || "speech".equals(segment)) return SEGMENT_GET_INDEX;
        return SEGMENT_FUNCTIONS_INDEX;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        segment = segmentIndex(arguments == null ? null : arguments.getString(SettingsActivity.EXTRA_INITIAL_PLACE));
        // Only a fresh open scrolls: a rotation or a return from the back stack keeps where the list was.
        if (savedInstanceState == null && arguments != null) {
            String scrollTo = arguments.getString(SettingsActivity.EXTRA_SCROLL_TO_KEY);
            if (scrollTo != null && !scrollTo.isEmpty()) pendingScrollKey = scrollTo;
        }
        if (savedInstanceState != null) {
            segment = savedInstanceState.getInt(STATE_SEGMENT, segment);
            linkText = savedInstanceState.getString(STATE_LINK, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        RecyclerView list = new RecyclerView(context);
        list.setId(R.id.tai_centre_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Math.round(32 * getResources().getDisplayMetrics().density));
        if (TaiMotion.reduced(context)) {
            list.setItemAnimator(null);
        } else {
            // Rows moving between sections slide and fade (transforms and opacity only); a row
            // whose content changed is rebound in place, never cross-faded, so a progress tick
            // cannot flicker.
            DefaultItemAnimator animator = new DefaultItemAnimator();
            animator.setSupportsChangeAnimations(false);
            animator.setMoveDuration(TaiMotion.ARRIVE_MS);
            animator.setAddDuration(220L);
            animator.setRemoveDuration(180L);
            list.setItemAnimator(animator);
        }
        list.setAdapter(adapter);
        listView = list;
        return list;
    }

    @Override
    public void onDestroyView() {
        listView = null;
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        Context context = getContext();
        if (context == null) return;
        loadInstalled(context);
        rebuild();
        TaiDownloadHub.get(context).addListener(this);
        fetchLoadedModel(context);
        fetchBenchmarkSpeeds(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.tai_model_centre_title);
    }

    @Override
    public void onStop() {
        Context context = getContext();
        if (context != null) TaiDownloadHub.get(context).removeListener(this);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SEGMENT, segment);
        outState.putString(STATE_LINK, linkText);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    // ---- the hub ----

    @Override
    public void onDownloadsChanged(@NonNull List<TaiDownloadHub.Snapshot> next) {
        Context context = getContext();
        if (context == null || !isAdded()) return;
        boolean statusChanged = next.size() != statuses.size();
        Map<String, String> now = new HashMap<>();
        for (TaiDownloadHub.Snapshot item : next) {
            now.put(item.id, item.status);
            String before = statuses.get(item.id);
            if (!item.status.equals(before)) statusChanged = true;
            // A record that was on its way and is now installed leaves a follow-up banner; one
            // that was already installed when the screen opened does not.
            if (!firstPush && before != null && !TaiModelStore.STATE_INSTALLED.equals(before)
                && TaiModelStore.STATE_INSTALLED.equals(item.status)) {
                banners.remove(item.modelId);
                banners.put(item.modelId, item.isSpeech());
            }
        }
        if (firstPush) knownTransfers.addAll(now.keySet());
        firstPush = false;
        statuses = now;
        downloads = next;
        if (statusChanged) loadInstalled(context);
        rebuild();
    }

    private void loadInstalled(@NonNull Context context) {
        TaiModelStore store = new TaiModelStore(context);
        store.pruneMissingUserModels();
        installedAll = store.getInstalledUserModels();
        List<TaiModelSpec> chat = new ArrayList<>();
        for (TaiModelSpec spec : installedAll.values()) {
            if (!TaiSpeechModels.isSpeechModel(spec) && !TaiTtsModels.isTtsModel(spec) && !spec.isImageGeneration()
                && !spec.isVisionTool()) chat.add(spec);
        }
        installedChat = chat;
        installedImage = TaiModelCentreRows.imageModels(installedAll.values());
        installedSpeech = TaiSpeechModels.installed(store);
        installedVoice = TaiTtsModels.installed(store);
        // Read aloud is offered only with a voice installed; an install or delete here changes that.
        TaiReadAloud.invalidateAvailability();
        TaiSettings settings = new TaiSettings(context);
        defaultId = settings.getDefaultAssistantModel();
        TaiModelSpec active = TaiSpeechModels.chooseActive(settings.getSttModelId(), installedSpeech);
        voiceId = active == null ? null : active.id;
        parallel = settings.getDownloadParallel();
        loadFunctions(context);
    }

    /**
     * The Functions rows, the device line, the "Used by" lines and the delete warnings: resolved once
     * per change of what is installed, never on a progress tick. Each row and warning is the function's
     * feature load plan, so the Centre says what a load does; the plans read the model store, the
     * settings and the evidence files, so this runs off the main thread and the newest read wins.
     */
    private void loadFunctions(@NonNull Context context) {
        if (executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        Map<String, TaiModelSpec> installed = installedAll;
        int generation = ++functionsGeneration;
        executor.execute(() -> {
            TaiFunctionModels models = TaiFunctionModels.forContext(app);
            TaiFeaturePlans plans = TaiFeaturePlans.forContext(app, models);
            TaiFunctionRows.Planner planner = plans::plan;
            TaiFunctionLabels labels = new TaiFunctionLabels(app, installed);
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, true);
            String level = prefs == null ? "polished" : prefs.getInAppKeyboardVoicePolishLevel();
            List<TaiFunctionRows.FunctionRow> rows = withCheckOffers(app, plans,
                TaiFunctionRows.functionRows(models, planner, labels, level));
            TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(app);
            String soc = capabilities.socModel == null || capabilities.socModel.trim().isEmpty() ? capabilities.model : capabilities.socModel;
            String device = TaiFunctionRows.deviceLine(models.env(), soc, Build.VERSION.RELEASE);
            Map<String, String> used = new HashMap<>();
            Map<String, String> warnings = new HashMap<>();
            for (String id : installed.keySet()) {
                used.put(id, TaiFunctionRows.usedByLine(models.usedBy(id), labels));
                String warning = TaiFunctionRows.deleteWarning(models, planner, id, labels);
                if (!warning.isEmpty()) warnings.put(id, warning);
            }
            handler.post(() -> {
                if (!isAdded() || generation != functionsGeneration) return;
                functionModels = models;
                tidyLevel = level;
                functionRows = rows;
                deviceLine = device;
                usedBy = used;
                deleteWarnings = warnings;
                rebuild();
            });
        });
    }

    /** The runtime status is a blocking IPC; read it off the main thread for the "In use" pill. */
    private void fetchLoadedModel(@NonNull Context context) {
        if (executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            String id = null;
            try {
                JSONObject runtime = TaiManager.getInstance(app).runtimeStatus().optJSONObject("runtime");
                if (runtime != null && (runtime.optBoolean("loaded", false) || "loading".equals(runtime.optString("state", "")))) {
                    String loaded = runtime.optString("loadedModelId", "");
                    id = loaded.isEmpty() ? null : loaded;
                }
            } catch (JSONException | RuntimeException ignored) {
            }
            String finalId = id;
            handler.post(() -> {
                if (!isAdded()) return;
                loadedId = finalId;
                rebuild();
            });
        });
    }

    /** The benchmark leaderboard is a blocking read; fetch each chat row's speed pill off the main thread. */
    private void fetchBenchmarkSpeeds(@NonNull Context context) {
        if (executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            JSONObject benchmarks;
            try {
                benchmarks = TaiManager.getInstance(app).benchmarks();
            } catch (JSONException | RuntimeException e) {
                benchmarks = null;
            }
            Map<String, Double> speeds = TaiBenchLeaderboard.bestSpeedByModel(benchmarks);
            handler.post(() -> {
                if (!isAdded()) return;
                benchmarkSpeeds = speeds;
                rebuild();
            });
        });
    }

    // ---- the list ----

    private void rebuild() {
        Context context = getContext();
        if (context == null) return;
        // A deep link's row is ringed from the rebuild that shows it; the ring clears itself after a moment.
        String scrollTarget = null;
        if (pendingScrollKey != null && segment != SEGMENT_FUNCTIONS_INDEX) {
            scrollTarget = pendingScrollKey;
            highlightKey = pendingScrollKey;
            pendingScrollKey = null;
            handler.postDelayed(() -> {
                highlightKey = null;
                if (isAdded()) rebuild();
            }, HIGHLIGHT_MS);
        }
        List<TaiModelCentreAdapter.Item> items = new ArrayList<>();
        items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_HEADER, "header", "header|" + deviceLine,
            new TaiModelCentreAdapter.Header(deviceLine), false));

        Set<String> busy = addDownloads(context, items);
        addBanners(context, items);

        CharSequence[] labels = {
            getString(R.string.tai_fn_segment_functions),
            getString(R.string.tai_centre_segment_installed),
            getString(R.string.tai_fn_segment_get)};
        items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_SEGMENTS, "segments", "segments|" + segment,
            new TaiModelCentreAdapter.Segments(segment, labels), false));
        switch (segment) {
            case SEGMENT_INSTALLED_INDEX:
                addInstalled(context, items);
                break;
            case SEGMENT_GET_INDEX:
                // The import bar leads the segment: a link or a file is how any model outside the catalogue arrives.
                items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_LINK, "link",
                    "link|" + linkText + "|" + linkError, new TaiModelCentreAdapter.LinkBar(linkText, linkError), false));
                addGetModels(context, items, busy);
                break;
            default:
                addFunctions(items);
                break;
        }
        if (segment != SEGMENT_FUNCTIONS_INDEX) {
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_SETTING, "parallel", "parallel|" + parallel,
                parallel, false));
        }
        adapter.submit(items);
        if (scrollTarget != null) scrollToModel(scrollTarget);
    }

    /** The catalogue as the Get models grouping reads it (what each entry is, by capability). */
    @NonNull
    private static List<TaiFunctionRows.CatalogItem> catalogueItems() {
        List<TaiFunctionRows.CatalogItem> out = new ArrayList<>();
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            Set<String> capabilities = new LinkedHashSet<>(entry.capabilities);
            capabilities.addAll(entry.sourceCapabilities);
            capabilities.addAll(entry.endpointCapabilities);
            out.add(new TaiFunctionRows.CatalogItem(new TaiFunctionModels.ModelInfo(entry.modelId, entry.sizeBytes,
                capabilities, entry.backend), entry.downloadAvailable));
        }
        return out;
    }

    /**
     * The Get models list: the catalogue under Assistants, Speech, Voice output and Search, minus what
     * is installed or downloading. Image generation has no group (spec §3.6). Each entry says its size, how it fits this phone and what
     * it can serve.
     */
    private void addGetModels(@NonNull Context context, @NonNull List<TaiModelCentreAdapter.Item> items,
                              @NonNull Set<String> busy) {
        TaiFunctionModels models = functionModels;
        if (models == null) return;
        TaiTierPolicy.Env env = models.env();
        Set<String> skip = new HashSet<>(installedAll.keySet());
        skip.addAll(busy);
        Map<TaiFunctionRows.Group, List<TaiFunctionRows.GetEntry>> groups = TaiFunctionRows.getModels(env, catalogueItems(), skip);
        if (groups.isEmpty()) {
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_EMPTY, "empty-get", "empty-get",
                new TaiModelCentreAdapter.Empty("", getString(R.string.tai_fn_get_empty)), false));
            return;
        }
        TaiFunctionLabels labels = new TaiFunctionLabels(context, installedAll);
        for (Map.Entry<TaiFunctionRows.Group, List<TaiFunctionRows.GetEntry>> group : groups.entrySet()) {
            String header = getString(groupTitle(group.getKey()));
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_SECTION, "get-" + group.getKey().name(),
                "get-" + group.getKey().name() + "|" + header, new TaiModelCentreAdapter.Section(header, "", ""), false));
            for (TaiFunctionRows.GetEntry get : group.getValue()) {
                TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(get.item.info.id);
                if (entry != null) addModelRow(items, getRow(context, labels, env, get, entry));
            }
        }
    }

    private static int groupTitle(@NonNull TaiFunctionRows.Group group) {
        switch (group) {
            case SPEECH: return R.string.tai_fn_group_speech;
            case VOICE_OUTPUT: return R.string.tai_fn_group_voice;
            case SEARCH: return R.string.tai_fn_group_search;
            default: return R.string.tai_fn_group_assistants;
        }
    }

    /** One catalogue row: kind and size, the fit line, "For: ...", the notes the old catalogue rows carried. */
    @NonNull
    private TaiModelCentreAdapter.ModelRow getRow(@NonNull Context context, @NonNull TaiFunctionLabels labels,
                                                  @NonNull TaiTierPolicy.Env env, @NonNull TaiFunctionRows.GetEntry get,
                                                  @NonNull TaiModelCatalog.CatalogEntry entry) {
        TaiFunctionRows.Group group = get.group;
        boolean speech = group == TaiFunctionRows.Group.SPEECH || group == TaiFunctionRows.Group.VOICE_OUTPUT;
        TaiModelCentreAdapter.ModelRow row = new TaiModelCentreAdapter.ModelRow(entry.modelId, speech, null, entry);
        row.voiceOutput = group == TaiFunctionRows.Group.VOICE_OUTPUT;
        row.highlighted = entry.modelId.equals(highlightKey);
        row.title = group == TaiFunctionRows.Group.SPEECH ? centreName(entry.modelId, entry.displayName, null, true) : entry.displayName;
        String size = entry.sizeEstimate == null || entry.sizeEstimate.isEmpty()
            ? TaiModelCentreRows.formatBytes(entry.sizeBytes) : entry.sizeEstimate;
        switch (group) {
            case SPEECH:
                row.subtitle = getString(R.string.tai_centre_kind_speech) + " · " + size;
                break;
            case VOICE_OUTPUT:
                row.subtitle = getString(R.string.tai_centre_kind_voice) + " · " + size + " · " + getString(R.string.tai_centre_voice_names);
                break;
            case SEARCH:
                row.subtitle = getString(R.string.tai_centre_kind_embeddings) + " · " + size;
                break;
            default:
                row.subtitle = getString(R.string.tai_centre_kind_chat) + " · " + size;
                break;
        }
        if (group == TaiFunctionRows.Group.ASSISTANTS || group == TaiFunctionRows.Group.SEARCH) {
            row.pillBackend = backendPill(entry.backend);
        }
        row.pillPrimary = get.suggested ? getString(R.string.tai_fn_pill_suggested) : "";
        List<String> lines = new ArrayList<>();
        String fit = TaiFunctionRows.fitLine(get.fit, labels);
        if (!fit.isEmpty()) lines.add(fit);
        String forLine = TaiFunctionRows.forLine(env, get.item.info, labels);
        if (!forLine.isEmpty()) lines.add(forLine);
        row.extra = TaiFunctionRows.join(lines, "\n");
        row.installable = entry.downloadAvailable;
        row.installing = installing.contains(entry.modelId);

        String error = errors.get(entry.modelId);
        boolean gatedNote = false;
        if (error != null) {
            row.note = error;
            row.noteIsError = true;
        } else if (TaiModelCatalog.PARAKEET_TDT_V3_ID.equals(entry.modelId)) {
            String warning = TaiSpeechActions.parakeetRamWarning(context, deviceMemory(context));
            row.note = warning == null ? "" : warning;
        } else if (group == TaiFunctionRows.Group.SPEECH && TaiSpeechActions.isSmall(entry.modelId) && deviceMemory(context) > 0L
            && deviceMemory(context) < TaiSpeechActions.SMALL_MIN_MEMORY_BYTES) {
            row.note = getString(R.string.tai_centre_small_ram_note);
        } else if (!entry.downloadAvailable) {
            row.note = entry.unavailableReason == null ? "" : entry.unavailableReason;
        } else if (entry.gated && new TaiSettings(context).getHuggingFaceToken().trim().isEmpty()) {
            row.note = getString(R.string.tai_centre_gated_note);
            gatedNote = true;
        }
        row.tokenAction = TaiModelCentreRows.showsTokenAction(gatedNote, error);
        return row;
    }

    /** One row per function the platform allows; a tap opens that function's picker sheet. */
    private void addFunctions(@NonNull List<TaiModelCentreAdapter.Item> items) {
        for (TaiFunctionRows.FunctionRow row : functionRows) {
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_FUNCTION, row.function.name(),
                row.signature(), row, false));
        }
    }

    /** Brings a deep-linked model row to the top of the list once it is there. */
    private void scrollToModel(@NonNull String modelId) {
        RecyclerView list = listView;
        int position = adapter.positionOfModel(modelId);
        if (list == null || position < 0) return;
        float density = getResources().getDisplayMetrics().density;
        list.post(() -> {
            RecyclerView.LayoutManager manager = list.getLayoutManager();
            if (manager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) manager).scrollToPositionWithOffset(position, Math.round(16 * density));
            } else {
                list.scrollToPosition(position);
            }
        });
    }

    /** The Downloads section, while anything is on its way, paused or failed. Returns the model
     *  ids it lists, which the catalogue segments leave out. */
    @NonNull
    private Set<String> addDownloads(@NonNull Context context, @NonNull List<TaiModelCentreAdapter.Item> items) {
        List<TaiDownloadHub.Snapshot> shown = new ArrayList<>();
        for (TaiDownloadHub.Snapshot item : downloads) {
            if (TaiModelCentreRows.isShown(TaiModelCentreRows.Input.of(item))) shown.add(item);
        }
        // Running first, then the line in order, then what waits for the person.
        Collections.sort(shown, (a, b) -> {
            int rank = Integer.compare(rank(a), rank(b));
            if (rank != 0) return rank;
            return Integer.compare(a.queuePosition, b.queuePosition);
        });
        Set<String> ids = new HashSet<>();
        Set<String> busy = new HashSet<>();
        if (!shown.isEmpty()) {
            int running = 0;
            int waiting = 0;
            for (TaiDownloadHub.Snapshot item : shown) {
                TaiModelCentreRows.Phase phase = TaiModelCentreRows.phaseOf(TaiModelCentreRows.Input.of(item));
                if (phase == TaiModelCentreRows.Phase.DOWNLOADING || phase == TaiModelCentreRows.Phase.CHECKING) running++;
                else if (phase == TaiModelCentreRows.Phase.WAITING) waiting++;
            }
            String slots = running == 0 && waiting == 0 ? ""
                : waiting > 0 ? getString(R.string.tai_centre_slots_waiting, running, parallel, waiting)
                : getString(R.string.tai_centre_slots, running, parallel);
            String free = getString(R.string.tai_centre_free_space, TaiModelCentreRows.formatBytes(freeBytes(context)));
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_SECTION, "downloads",
                "downloads|" + free + "|" + slots,
                new TaiModelCentreAdapter.Section(getString(R.string.tai_centre_downloads_header), free, slots), false));
            for (TaiDownloadHub.Snapshot item : shown) {
                ids.add(item.id);
                busy.add(item.modelId);
                TaiModelCentreRows.State state = TaiModelCentreRows.stateFor(context, item);
                boolean voice = TaiModelCatalog.ttsEntries().containsKey(item.modelId);
                boolean speech = voice || item.isSpeech() || TaiModelCatalog.speechEntries().containsKey(item.modelId);
                String title = voice ? item.displayName
                    : centreName(item.modelId, item.displayName, item.record.optString("path", ""), speech);
                long size = item.totalBytes > 0L ? item.totalBytes : catalogueSize(item.modelId);
                String subtitle = voice ? voiceKindLine(context, size)
                    : TaiModelCatalog.embeddingEntries().containsKey(item.modelId) ? embeddingKindLine(context, size)
                    : kindLine(context, speech, size, !TaiModelCatalog.entries().containsKey(item.modelId));
                String signature = title + '|' + subtitle + '|' + state.phase + '|' + state.pill + '|' + state.metaStart
                    + '|' + state.metaEnd + '|' + state.bar + '|' + state.progress + '|' + state.actions;
                items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_DOWNLOAD, item.id, signature,
                    new TaiModelCentreAdapter.DownloadRow(item, state, title, subtitle, speech),
                    !knownTransfers.contains(item.id)));
            }
        }
        // A row that left (cancelled, installed) may come back later; let it arrive again then.
        for (String id : shownTransfers) {
            if (!ids.contains(id)) {
                adapter.forgetArrival(TaiModelCentreAdapter.TYPE_DOWNLOAD, id);
                knownTransfers.remove(id);
            }
        }
        shownTransfers.clear();
        shownTransfers.addAll(ids);
        return busy;
    }

    private static int rank(@NonNull TaiDownloadHub.Snapshot item) {
        switch (TaiModelCentreRows.phaseOf(TaiModelCentreRows.Input.of(item))) {
            case DOWNLOADING: return 0;
            case CHECKING: return 0;
            case WAITING: return 1;
            case PAUSED: return 2;
            default: return 3;
        }
    }

    private void addBanners(@NonNull Context context, @NonNull List<TaiModelCentreAdapter.Item> items) {
        for (Map.Entry<String, Boolean> entry : banners.entrySet()) {
            String modelId = entry.getKey();
            // A voice model needs no "use as" choice: it simply speaks once installed.
            if (TaiModelCatalog.ttsEntries().containsKey(modelId)) continue;
            boolean speech = entry.getValue();
            TaiModelSpec spec = installedAll.get(modelId);
            String name = spec == null ? modelId : centreName(modelId, spec.displayName, spec.localPath, speech);
            String action;
            if (speech) action = modelId.equals(voiceId) ? "" : getString(R.string.tai_centre_use_voice);
            else if (spec != null && isEmbedder(spec)) action = ""; // served on demand, never a chat default
            else action = modelId.equals(defaultId) ? "" : getString(R.string.tai_centre_use_default);
            String text = getString(R.string.tai_centre_installed_banner, name);
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_BANNER, modelId, text + '|' + action,
                new TaiModelCentreAdapter.Banner(modelId, speech, text, action), true));
        }
    }

    private void addInstalled(@NonNull Context context, @NonNull List<TaiModelCentreAdapter.Item> items) {
        List<TaiModelSpec> chat = new ArrayList<>(installedChat);
        // The default first, then by name: the model a person uses most sits on top.
        Collections.sort(chat, (a, b) -> {
            boolean ad = a.id.equals(defaultId);
            boolean bd = b.id.equals(defaultId);
            if (ad != bd) return ad ? -1 : 1;
            return a.displayName.compareToIgnoreCase(b.displayName);
        });
        for (TaiModelSpec spec : chat) {
            TaiModelCentreAdapter.ModelRow row = new TaiModelCentreAdapter.ModelRow(spec.id, false, spec, TaiModelCatalog.get(spec.id));
            row.title = spec.displayName;
            boolean embedder = isEmbedder(spec);
            row.subtitle = embedder ? embeddingKindLine(context, spec.sizeBytes)
                : kindLine(context, false, spec.sizeBytes, !TaiModelCatalog.entries().containsKey(spec.id));
            row.pillPrimary = embedder ? "" : spec.id.equals(loadedId) ? getString(R.string.tai_centre_pill_in_use) : "";
            row.pillSecondary = !embedder && spec.id.equals(defaultId) ? getString(R.string.tai_centre_pill_default) : "";
            row.pillBackend = backendPill(spec.backend);
            Double speed = benchmarkSpeeds.get(spec.id);
            row.pillSpeed = speed == null ? "" : getString(R.string.tai_bench_tps, TaiBenchLeaderboard.formatTpsValue(speed));
            row.extra = usedBy.getOrDefault(spec.id, "");
            addModelRow(items, row);
        }
        List<TaiModelSpec> speech = new ArrayList<>(installedSpeech);
        Collections.sort(speech, (a, b) -> {
            boolean av = a.id.equals(voiceId);
            boolean bv = b.id.equals(voiceId);
            return av == bv ? 0 : av ? -1 : 1;
        });
        for (TaiModelSpec spec : speech) {
            TaiModelCentreAdapter.ModelRow row = new TaiModelCentreAdapter.ModelRow(spec.id, true, spec, TaiModelCatalog.get(spec.id));
            row.title = centreName(spec.id, spec.displayName, spec.localPath, true);
            int window = TaiSpeechModels.windowSeconds(spec);
            StringBuilder subtitle = new StringBuilder(kindLine(context, true, spec.sizeBytes, false));
            if (window > 0) subtitle.append(" · ").append(getString(R.string.speech_model_window_summary, window));
            if (spec.id.equals(voiceId)) subtitle.append(" · ").append(getString(R.string.tai_centre_meta_voice_input));
            row.subtitle = subtitle.toString();
            row.pillPrimary = spec.id.equals(voiceId) ? getString(R.string.tai_centre_pill_in_use) : "";
            row.extra = usedBy.getOrDefault(spec.id, "");
            addModelRow(items, row);
        }
        for (TaiModelSpec spec : installedVoice) {
            TaiModelCentreAdapter.ModelRow row = new TaiModelCentreAdapter.ModelRow(spec.id, true, spec, TaiModelCatalog.get(spec.id));
            row.voiceOutput = true;
            row.title = spec.displayName;
            row.subtitle = voiceKindLine(context, spec.sizeBytes) + " · " + new TaiSettings(context).getTtsVoice();
            row.extra = usedBy.getOrDefault(spec.id, "");
            addModelRow(items, row);
        }
        if (!installedImage.isEmpty()) {
            String header = getString(R.string.tai_centre_image_header);
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_SECTION, "image-models", "image-models|" + header,
                new TaiModelCentreAdapter.Section(header, "", getString(R.string.tai_centre_image_sub)), false));
            for (TaiModelSpec spec : installedImage) {
                TaiModelCentreAdapter.ModelRow row = new TaiModelCentreAdapter.ModelRow(spec.id, false, spec, null);
                row.image = true;
                row.title = spec.displayName;
                StringBuilder subtitle = new StringBuilder(getString(R.string.tai_centre_kind_image))
                    .append(" · ").append(TaiModelCentreRows.imageFamily(spec.architecture));
                if (spec.sizeBytes > 0L) subtitle.append(" · ").append(TaiModelCentreRows.formatBytes(spec.sizeBytes));
                row.subtitle = subtitle.toString();
                addModelRow(items, row);
            }
        }
        if (chat.isEmpty() && speech.isEmpty() && installedVoice.isEmpty() && installedImage.isEmpty()) {
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_EMPTY, "empty-installed", "empty-installed",
                new TaiModelCentreAdapter.Empty(getString(R.string.tai_centre_empty_installed_title),
                    getString(R.string.tai_fn_empty_installed_summary)), false));
        }
    }

    private void addModelRow(@NonNull List<TaiModelCentreAdapter.Item> items, @NonNull TaiModelCentreAdapter.ModelRow row) {
        if (row.modelId.equals(highlightKey)) row.highlighted = true;
        items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_MODEL, row.modelId, row.signature(), row, false));
    }

    /** An installed embedding-only model: served on demand, so no load, default or tuning. */
    private static boolean isEmbedder(@NonNull TaiModelSpec spec) {
        return spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
            && !spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT);
    }

    /** "embeddings · 175 MB". */
    @NonNull
    private static String embeddingKindLine(@NonNull Context context, long sizeBytes) {
        StringBuilder line = new StringBuilder(context.getString(R.string.tai_centre_kind_embeddings));
        if (sizeBytes > 0L) line.append(" · ").append(TaiModelCentreRows.formatBytes(sizeBytes));
        return line.toString();
    }

    /** "voice · 90 MB". */
    @NonNull
    private static String voiceKindLine(@NonNull Context context, long sizeBytes) {
        StringBuilder line = new StringBuilder(context.getString(R.string.tai_centre_kind_voice));
        if (sizeBytes > 0L) line.append(" · ").append(TaiModelCentreRows.formatBytes(sizeBytes));
        return line.toString();
    }

    /** "chat · 2.4 GB", "speech · 97 MB", "chat · 2.1 GB · from link". */
    @NonNull
    private String kindLine(@NonNull Context context, boolean speech, long sizeBytes, boolean fromLink) {
        StringBuilder line = new StringBuilder(context.getString(speech ? R.string.tai_centre_kind_speech : R.string.tai_centre_kind_chat));
        if (sizeBytes > 0L) line.append(" · ").append(TaiModelCentreRows.formatBytes(sizeBytes));
        if (fromLink) line.append(" · ").append(context.getString(R.string.tai_centre_kind_from_link));
        return line.toString();
    }

    /**
     * A speech model's name as the centre shows it: the engine, then the plain size and language
     * ("Whisper Base · English", "Parakeet · Many languages"), so the rows read alike whatever
     * the catalogue calls them. Chat models keep their own name.
     */
    @NonNull
    static String centreName(@NonNull String modelId, @Nullable String displayName, @Nullable String path, boolean speech) {
        if (!speech) return displayName == null || displayName.isEmpty() ? modelId : displayName;
        String plain = TaiSpeechModels.plainName(modelId, displayName, path);
        return TaiSpeechActions.isWhisper(modelId) ? "Whisper " + plain : plain;
    }

    /** The row's backend pill text ("LiteRT", "MNN"), or "" for a backend this build does not name. */
    @NonNull
    private String backendPill(@Nullable String backend) {
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(backend)) return getString(R.string.tai_centre_pill_backend_litert);
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) return getString(R.string.tai_centre_pill_backend_mnn);
        return "";
    }

    private static long catalogueSize(@NonNull String modelId) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        return entry == null ? 0L : entry.sizeBytes;
    }

    private long deviceMemory(@NonNull Context context) {
        if (deviceMemoryBytes < 0L) deviceMemoryBytes = TaiDeviceCapabilities.detect(context).memoryBytes;
        return deviceMemoryBytes;
    }

    /** The volume the models directory lives on (the nearest existing parent, for a fresh install). */
    @NonNull
    private static File modelsVolume(@NonNull Context context) {
        File probe = new TaiModelStore(context).getModelsDirectory();
        while (probe != null && !probe.exists()) probe = probe.getParentFile();
        return probe == null ? context.getFilesDir() : probe;
    }

    private static long freeBytes(@NonNull Context context) {
        return modelsVolume(context).getUsableSpace();
    }

    /**
     * The pre-check Install runs before it asks for anything: the same rule the engine applies
     * when a download starts (the file plus a reserve, so the phone is never filled to the last
     * byte). Returns the refusal line, or null when it fits.
     */
    @Nullable
    @VisibleForTesting
    static String spaceRefusal(@NonNull Context context, long usableBytes, long totalBytes, long neededBytes) {
        TaiDownloadQueue.SpaceCheck space = TaiDownloadQueue.checkSpace(usableBytes, totalBytes, neededBytes, 0L);
        if (space.fits) return null;
        return context.getString(R.string.tai_centre_needs_space,
            TaiModelCentreRows.formatBytes(space.requiredBytes), TaiModelCentreRows.formatBytes(space.freeBytes));
    }

    // ---- link bar ----

    @Override
    public void onLinkAdd(@NonNull String text, @NonNull View source) {
        Context context = getContext();
        if (context == null) return;
        if (text.trim().isEmpty()) {
            linkError = getString(R.string.tai_centre_link_empty);
            TaiMotion.shake(source);
            rebuild();
            return;
        }
        String error = importFlow.submitLink(text);
        if (error != null) {
            linkError = error;
            TaiMotion.shake(source);
        } else {
            linkError = "";
            linkText = "";
            hideKeyboard(source);
        }
        rebuild();
    }

    @Override
    public void onLinkFile() {
        importFlow.startFile();
    }

    @Override
    public void onLinkMore(@NonNull View anchor) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(Menu.NONE, 1, Menu.NONE, R.string.tai_centre_link_folder);
        menu.setOnMenuItemClickListener(item -> {
            importFlow.startFolder();
            return true;
        });
        menu.show();
    }

    @Override
    public void onLinkTextChanged(@NonNull String text) {
        linkText = text;
        // The refusal was about the old text; it goes as soon as the text changes. The list is
        // rebuilt only when there was one to clear, so typing never rebinds the field.
        if (!linkError.isEmpty()) {
            linkError = "";
            handler.post(this::rebuild);
        }
    }

    private void hideKeyboard(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // ---- segments and settings ----

    @Override
    public void onSegmentSelected(int index) {
        if (index < 0 || index >= SEGMENTS.length || index == segment) return;
        segment = index;
        rebuild();
    }

    @Override
    public void onParallelSelected(int value) {
        Context context = getContext();
        if (context == null) return;
        new TaiSettings(context).setDownloadParallel(value);
        parallel = new TaiSettings(context).getDownloadParallel();
        // A raised limit starts the next in line now, not at the next status change.
        TaiDownloadEngine.getInstance(context).pump();
        rebuild();
    }

    // ---- downloads ----

    @Override
    public void onDownloadAction(@NonNull TaiDownloadHub.Snapshot snapshot, @NonNull TaiModelCentreRows.Action action,
                                 @NonNull View source) {
        Context context = getContext();
        if (context == null) return;
        if (action == TaiModelCentreRows.Action.PAUSE) TaiMotion.tick(source);
        if (action == TaiModelCentreRows.Action.CANCEL && snapshot.bytesRead > 0L
            && !TaiModelStore.STATE_FAILED.equals(snapshot.status)) {
            // Cancel deletes what has arrived; a mis-tap on a 3 GB download deserves a question.
            new MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.tai_centre_cancel_title, snapshot.displayName))
                .setMessage(getString(R.string.tai_centre_cancel_message, TaiModelCentreRows.formatBytes(snapshot.bytesRead)))
                .setPositiveButton(R.string.tai_centre_cancel_confirm, (dialog, which) -> runDownloadAction(context, snapshot, action))
                .setNegativeButton(R.string.tai_centre_cancel_keep, null)
                .show();
            return;
        }
        runDownloadAction(context, snapshot, action);
    }

    private void runDownloadAction(@NonNull Context context, @NonNull TaiDownloadHub.Snapshot snapshot,
                                   @NonNull TaiModelCentreRows.Action action) {
        if (executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        String modelId = snapshot.modelId;
        executor.execute(() -> {
            JSONObject result = null;
            try {
                String body = new JSONObject().put("modelId", modelId).toString();
                TaiManager manager = TaiManager.getInstance(app);
                switch (action) {
                    case PAUSE: result = manager.pauseDownload(body); break;
                    case RESUME:
                    case RETRY: result = manager.resumeDownload(body); break;
                    case START_NOW: result = manager.prioritizeDownload(body); break;
                    default: result = manager.cancelDownload(body); break;
                }
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context current = getContext();
                if (current == null) return;
                // Success needs no word: the hub pushes the new state to the row at once.
                if (finalResult == null || !finalResult.optBoolean("ok", false)) {
                    AppNotice.show(current, finalResult == null ? getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", getString(R.string.termux_ai_model_action_failed)), true);
                }
            });
        });
    }

    // ---- install ----

    /** Where an install request stands; always delivered on the main thread. */
    interface InstallListener {
        /** {@code installing}: the request is on its way. Else done, with {@code error} null on success. */
        void onInstallState(@NonNull String modelId, boolean installing, @Nullable String error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService INSTALLER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-model-install");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInstall(@NonNull TaiModelCentreAdapter.ModelRow row, @NonNull View source) {
        Context context = getContext();
        TaiModelCatalog.CatalogEntry entry = row.entry;
        if (context == null || entry == null || row.installing) return;
        TaiMotion.tick(source);
        installEntry(context, entry, (modelId, working, error) -> {
            if (!isAdded()) return;
            if (working) {
                // Said on the row itself, before anything starts; the pill shakes where it was tapped.
                errors.remove(modelId);
                installing.add(modelId);
            } else {
                installing.remove(modelId);
                if (error != null) {
                    errors.put(modelId, error);
                    if (source.isAttachedToWindow()) TaiMotion.shake(source);
                } else {
                    loadInstalled(requireContext());
                }
            }
            rebuild();
        });
    }

    /**
     * Starts the download of a catalogue entry, the way the Centre's Install button and the function
     * picker sheet both do: a voice model is a plain download, a Whisper model first asks size,
     * language and window, anything else just starts. {@code context} must be an activity (the Whisper
     * sheet is a dialog).
     */
    static void installEntry(@NonNull Context context, @NonNull TaiModelCatalog.CatalogEntry entry,
                             @NonNull InstallListener listener) {
        String modelId = entry.modelId;
        if (TaiModelCatalog.ttsEntries().containsKey(modelId)) {
            // A voice model needs no window and no "use for" choice.
            requestInstall(context, modelId, false, 0, listener);
            return;
        }
        boolean speech = entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)
            || TaiModelCatalog.speechEntries().containsKey(modelId);
        if (speech && TaiSpeechActions.isWhisper(modelId)) {
            TaiSpeechInstallSheet.show(context, modelId, new TaiSettings(context).getSttWindowSeconds(),
                TaiDeviceCapabilities.detect(context).memoryBytes,
                (chosen, window) -> requestInstall(context, chosen, true, window, listener));
            return;
        }
        requestInstall(context, modelId, speech, speech ? TaiSpeechActions.PARAKEET_WINDOW_SECONDS : 0, listener);
    }

    /**
     * The request itself: the space pre-check the engine would apply anyway (so a refusal is said
     * before anything starts), then the download call off the main thread. A speech model that is
     * already here (the Whisper sheet can land on one) is a choice of voice model, not a download.
     */
    static void requestInstall(@NonNull Context context, @NonNull String modelId, boolean speech, int windowSeconds,
                               @NonNull InstallListener listener) {
        Context app = context.getApplicationContext();
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return;
        TaiModelSpec already = new TaiModelStore(app).getInstalledUserModels().get(modelId);
        if (already != null) {
            if (speech) {
                TaiSpeechModels.activate(new TaiSettings(app), already.id);
                AppNotice.show(app, app.getString(R.string.tai_centre_now_voice,
                    centreName(already.id, already.displayName, already.localPath, true)), false);
            }
            listener.onInstallState(modelId, false, null);
            return;
        }
        long needed = windowSeconds > 0 ? entry.withWindow(windowSeconds).sizeBytes : entry.sizeBytes;
        File volume = modelsVolume(app);
        String refusal = spaceRefusal(app, volume.getUsableSpace(), volume.getTotalSpace(), needed);
        if (refusal != null) {
            listener.onInstallState(modelId, false, refusal);
            return;
        }
        listener.onInstallState(modelId, true, null);
        boolean firstVoiceModel = speech && TaiSpeechModels.installed(new TaiModelStore(app)).isEmpty();
        INSTALLER.execute(() -> {
            JSONObject result = null;
            try {
                if (!speech) {
                    result = TaiManager.getInstance(app).downloadCatalogModel(modelId);
                } else if (firstVoiceModel && noPendingVoiceChoice(app)) {
                    // The first speech model on the phone becomes the one voice input uses as
                    // soon as it lands; later ones wait for "Use for...".
                    result = TaiSpeechModels.startDownload(app, modelId, windowSeconds);
                } else {
                    result = TaiManager.getInstance(app).downloadSpeechModel(modelId, windowSeconds);
                }
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            MAIN.post(() -> {
                String failure = null;
                if (finalResult == null || !finalResult.optBoolean("ok", false)) {
                    failure = finalResult == null ? app.getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", app.getString(R.string.termux_ai_model_action_failed));
                }
                listener.onInstallState(modelId, false, failure);
            });
        });
    }

    /** A window switch or an earlier first download already holds the one pending voice choice. */
    private static boolean noPendingVoiceChoice(@NonNull Context context) {
        String pending = new TaiSettings(context).getSttPendingDownloadJson();
        return pending == null || pending.isEmpty();
    }

    // ---- installed models ----

    /** The row's Delete button: the confirmation the row's kind needs, as its menu's Delete opens. */
    @Override
    public void onModelDelete(@NonNull TaiModelCentreAdapter.ModelRow row) {
        Context context = getContext();
        TaiModelSpec spec = row.installed;
        if (context == null || spec == null) return;
        confirmDelete(context, row, spec);
    }

    private void confirmDelete(@NonNull Context context, @NonNull TaiModelCentreAdapter.ModelRow row,
                               @NonNull TaiModelSpec spec) {
        if (row.voiceOutput) {
            confirmDeleteVoice(context, spec);
        } else if (row.speech) {
            confirmDeleteSpeech(context, spec);
        } else if (!row.image && !isEmbedder(spec) && spec.id.equals(loadedId)) {
            AppNotice.show(context, R.string.termux_ai_model_delete_loaded_warning, true);
        } else {
            confirmDeleteChat(context, spec);
        }
    }

    @Override
    public void onModelMenu(@NonNull TaiModelCentreAdapter.ModelRow row, @NonNull View anchor) {
        Context context = getContext();
        TaiModelSpec spec = row.installed;
        if (context == null || spec == null) return;
        PopupMenu menu = new PopupMenu(context, anchor);
        Menu items = menu.getMenu();
        if (row.image) {
            // Served on demand by the image routes: nothing to load, make default, tune or bench.
            items.add(Menu.NONE, 4, Menu.NONE, R.string.termux_ai_model_delete_action);
            menu.setOnMenuItemClickListener(item -> {
                confirmDeleteChat(context, spec);
                return true;
            });
        } else if (row.voiceOutput) {
            items.add(Menu.NONE, 1, Menu.NONE, R.string.tai_centre_voice_settings);
            items.add(Menu.NONE, 2, Menu.NONE, R.string.speech_model_action_delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) openVoiceSettings();
                else confirmDeleteVoice(context, spec);
                return true;
            });
        } else if (row.speech) {
            if (!servedFunctions(spec).isEmpty()) items.add(Menu.NONE, 1, Menu.NONE, R.string.tai_fn_use_for);
            int other = TaiSpeechActions.otherWindow(spec);
            if (other > 0) items.add(Menu.NONE, 2, Menu.NONE, R.string.speech_model_action_window);
            items.add(Menu.NONE, 3, Menu.NONE, R.string.speech_model_action_delete);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: showUseFor(context, spec); break;
                    case 2: TaiSpeechActions.showWindowDialog(context, spec, other, this::rebuild); break;
                    default: confirmDeleteSpeech(context, spec); break;
                }
                return true;
            });
        } else if (isEmbedder(spec)) {
            // Served on demand by /v1/embeddings: nothing to load, make default, tune or bench.
            if (!servedFunctions(spec).isEmpty()) items.add(Menu.NONE, 1, Menu.NONE, R.string.tai_fn_use_for);
            items.add(Menu.NONE, 4, Menu.NONE, R.string.termux_ai_model_delete_action);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) showUseFor(context, spec);
                else confirmDeleteChat(context, spec);
                return true;
            });
        } else {
            boolean loaded = spec.id.equals(loadedId);
            if (!loaded) items.add(Menu.NONE, 1, Menu.NONE, R.string.termux_ai_model_load_action);
            if (!servedFunctions(spec).isEmpty()) items.add(Menu.NONE, 2, Menu.NONE, R.string.tai_fn_use_for);
            items.add(Menu.NONE, 3, Menu.NONE, R.string.termux_ai_model_tune_action);
            items.add(Menu.NONE, 5, Menu.NONE, R.string.tai_bench_title);
            items.add(Menu.NONE, 4, Menu.NONE, loaded ? R.string.termux_ai_model_delete_action_loaded
                : R.string.termux_ai_model_delete_action);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: loadModel(context, spec.id); break;
                    case 2: showUseFor(context, spec); break;
                    case 3: openParameters(spec); break;
                    case 5: TaiBenchHomeFragment.open(getActivity(), spec.id); break;
                    default:
                        if (loaded) AppNotice.show(context, R.string.termux_ai_model_delete_loaded_warning, true);
                        else confirmDeleteChat(context, spec);
                        break;
                }
                return true;
            });
        }
        menu.show();
    }

    /** The functions an installed model can serve on this phone (none for a cut-out or scene model). */
    @NonNull
    private List<TaiFunction> servedFunctions(@NonNull TaiModelSpec spec) {
        TaiFunctionModels models = functionModels;
        if (models == null) return Collections.emptyList();
        Set<String> capabilities = new LinkedHashSet<>(spec.capabilities);
        capabilities.addAll(spec.sourceCapabilities);
        return TaiFunctionRows.servedBy(models.env(),
            new TaiFunctionModels.ModelInfo(spec.id, spec.sizeBytes, capabilities, spec.backend));
    }

    /**
     * The one "Use for..." item: a small chooser of the functions the model can serve. The choice is
     * written at once and the function's picker sheet opens on it, to adjust GPU or CPU, the extras or
     * to undo.
     */
    private void showUseFor(@NonNull Context context, @NonNull TaiModelSpec spec) {
        TaiFunctionModels models = functionModels;
        List<TaiFunction> functions = servedFunctions(spec);
        if (models == null || functions.isEmpty()) {
            AppNotice.show(context, R.string.tai_fn_use_for_none, true);
            return;
        }
        TaiFunctionLabels labels = new TaiFunctionLabels(context, installedAll);
        String[] names = new String[functions.size()];
        for (int i = 0; i < names.length; i++) names[i] = labels.functionName(functions.get(i));
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.tai_fn_use_for_title, labels.modelName(spec.id)))
            .setItems(names, (dialog, which) -> {
                TaiFunction function = functions.get(which);
                models.set(function, spec.id);
                if (function == TaiFunction.VOICE_TYPING) TaiSpeechModels.activate(new TaiSettings(context), spec.id);
                loadInstalled(context);
                rebuild();
                TaiFunctionPickerSheet.show(this, function, changed -> onFunctionChanged());
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * The feature check's one-time offer (decision 5) on the first row whose feature runs a model on this phone
     * that has never been checked for it, until the offer is taken or dismissed; the next row's offer follows
     * once this one is handled, so an upgrade never shows them all at once. Reads the results file: off the
     * main thread.
     */
    @NonNull
    private static List<TaiFunctionRows.FunctionRow> withCheckOffers(@NonNull Context app, @NonNull TaiFeaturePlans plans,
                                                                     @NonNull List<TaiFunctionRows.FunctionRow> rows) {
        List<TaiFunctionRows.FunctionRow> out = new ArrayList<>(rows.size());
        try {
            List<TaiFunction> inUse = TaiFeatureCheckRunner.featuresInUse(plans);
            List<org.json.JSONObject> records = TaiFeatureCheckStore.in(app.getFilesDir()).records();
            TaiSettings settings = new TaiSettings(app);
            for (TaiFunctionRows.FunctionRow row : rows) {
                String model = inUse.contains(row.function) ? plans.plan(row.function).modelId : null;
                boolean offer = model != null && !settings.isFeatureCheckOffered(row.function, model)
                    && !checked(records, row.function, model);
                out.add(row.withOffer(offer));
            }
        } catch (RuntimeException e) {
            return rows;
        }
        return firstOfferOnly(out);
    }

    /** The rows with only the first offer left on: one offer at a time, the next after this one is handled. */
    @NonNull
    static List<TaiFunctionRows.FunctionRow> firstOfferOnly(@NonNull List<TaiFunctionRows.FunctionRow> rows) {
        List<TaiFunctionRows.FunctionRow> out = new ArrayList<>(rows.size());
        boolean offered = false;
        for (TaiFunctionRows.FunctionRow row : rows) {
            out.add(row.withOffer(row.offerCheck && !offered));
            offered |= row.offerCheck;
        }
        return out;
    }

    /** Whether any check of {@code feature} on {@code modelId} is on file. */
    private static boolean checked(@NonNull List<org.json.JSONObject> records, @NonNull TaiFunction feature, @NonNull String modelId) {
        String base = com.termux.ai.TaiFeatureCheck.baseModelId(modelId);
        for (org.json.JSONObject record : records) {
            if (feature.id().equals(record.optString("feature", ""))
                && base.equals(com.termux.ai.TaiFeatureCheck.baseModelId(record.optString("modelId", "")))) return true;
        }
        return false;
    }

    /** "Check how it runs on this phone": taken once, then the benchmark opens with that feature's check ready. */
    @Override
    public void onFunctionCheckOffer(@NonNull TaiFunction function) {
        if (markCheckOfferHandled(function, false)) TaiBenchHomeFragment.openFeatureCheck(getActivity(), function);
    }

    /** "Not now" on the offer: it is not made again for this feature, and the next feature's offer shows. */
    @Override
    public void onFunctionCheckOfferDismissed(@NonNull TaiFunction function) {
        markCheckOfferHandled(function, true);
    }

    /** Remembers the offer as handled off the main thread; {@code reload} then redraws the rows. False without a context. */
    private boolean markCheckOfferHandled(@NonNull TaiFunction function, boolean reload) {
        Context context = getContext();
        TaiFunctionModels models = functionModels;
        if (context == null || models == null || executor.isShutdown()) return false;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            String model = TaiFeaturePlans.forContext(app, models).plan(function).modelId;
            if (model != null) new TaiSettings(app).markFeatureCheckOffered(function, model);
            if (reload) handler.post(() -> {
                if (isAdded()) loadFunctions(app);
            });
        });
        return true;
    }

    @Override
    public void onFunctionClicked(@NonNull TaiFunction function) {
        TaiFunctionPickerSheet.show(this, function, changed -> onFunctionChanged());
    }

    /** A pick changed in the sheet: the Functions rows and the "Used by" lines follow. */
    private void onFunctionChanged() {
        Context context = getContext();
        if (context == null || !isAdded()) return;
        loadInstalled(context);
        rebuild();
    }

    @Override
    public void onModelBenchmark(@NonNull TaiModelCentreAdapter.ModelRow row) {
        TaiBenchHomeFragment.open(getActivity(), row.modelId);
    }

    /** Saving a token clears the row's refusal; the person taps Install again, nothing starts on its own. */
    @Override
    public void onAddToken(@NonNull TaiModelCentreAdapter.ModelRow row) {
        Context context = getContext();
        if (context == null) return;
        TaiHuggingFaceTokenDialog.show(context, () -> {
            errors.remove(row.modelId);
            rebuild();
        });
    }

    /** Saving a token starts the refused download again, as Retry would. */
    @Override
    public void onAddToken(@NonNull TaiDownloadHub.Snapshot snapshot) {
        Context context = getContext();
        if (context == null) return;
        TaiHuggingFaceTokenDialog.show(context, () -> {
            Context current = getContext();
            if (current != null && isAdded()) runDownloadAction(current, snapshot, TaiModelCentreRows.Action.RETRY);
        });
    }

    @Override
    public void onBannerAction(@NonNull TaiModelCentreAdapter.Banner banner) {
        Context context = getContext();
        if (context == null) return;
        banners.remove(banner.modelId);
        TaiModelSpec spec = installedAll.get(banner.modelId);
        if (banner.speech && spec != null) useForVoice(context, spec);
        else if (!banner.speech) setDefault(context, banner.modelId);
        rebuild();
    }

    @Override
    public void onBannerDismiss(@NonNull TaiModelCentreAdapter.Banner banner) {
        banners.remove(banner.modelId);
        rebuild();
    }

    private void useForVoice(@NonNull Context context, @NonNull TaiModelSpec spec) {
        TaiSpeechModels.activate(new TaiSettings(context), spec.id);
        AppNotice.show(context, getString(R.string.tai_centre_now_voice, centreName(spec.id, spec.displayName, spec.localPath, true)), false);
        loadInstalled(context);
        rebuild();
    }

    private void setDefault(@NonNull Context context, @NonNull String modelId) {
        TaiModelSpec model = new TaiModelStore(context).getInstalledUserModels().get(modelId);
        TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(context);
        if (model != null && TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend) && !capabilities.mnnSupported) {
            String reason = capabilities.mnnUnsupportedReason;
            AppNotice.show(context, reason == null ? getString(R.string.termux_ai_mnn_runtime_pending) : reason, true);
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, modelId).apply();
        AppNotice.show(context, R.string.termux_ai_model_active_saved, false);
        loadInstalled(context);
        rebuild();
    }

    private void loadModel(@NonNull Context context, @NonNull String modelId) {
        if (executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            JSONObject result = null;
            try {
                result = TaiManager.getInstance(app).loadModel(new JSONObject().put("model", modelId).toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context current = getContext();
                if (current == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    AppNotice.show(current, R.string.termux_ai_model_loaded, false);
                } else {
                    AppNotice.show(current, finalResult == null ? getString(R.string.termux_ai_runtime_action_failed)
                        : finalResult.optString("message", getString(R.string.termux_ai_runtime_action_failed)), true);
                }
                fetchLoadedModel(current);
            });
        });
    }

    private void openParameters(@NonNull TaiModelSpec spec) {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).openScreen(TaiParameterPreferencesFragment.class,
                R.string.termux_ai_parameters_defaults_title, TaiParameterPreferencesFragment.argumentsForModel(spec));
        }
    }

    private void confirmDeleteChat(@NonNull Context context, @NonNull TaiModelSpec spec) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.tai_centre_delete_chat_title, spec.displayName))
            .setMessage(deleteMessage(context, getString(R.string.termux_ai_model_delete_message), spec.id))
            .setPositiveButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, spec.id, null))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * The confirmation text, with the functions that use the model and what each falls back to
     * appended when the model is in use (spec §4.2).
     */
    @NonNull
    private String deleteMessage(@NonNull Context context, @NonNull String plain, @NonNull String modelId) {
        // Worked out with the rows, off the main thread: see loadFunctions.
        String warning = deleteWarnings.get(modelId);
        return warning == null || warning.isEmpty() ? plain : plain + "\n\n" + warning;
    }

    /** The voice picker lives with the speech settings (Keyboard > Voice input > Speech model). */
    private void openVoiceSettings() {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).openScreen(SpeechModelPreferencesFragment.class,
                R.string.settings_keyboard_voice_model_title, null);
        }
    }

    private void confirmDeleteVoice(@NonNull Context context, @NonNull TaiModelSpec spec) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.tai_centre_delete_voice_title, spec.displayName))
            .setMessage(deleteMessage(context, getString(R.string.tai_centre_delete_voice_message), spec.id))
            .setPositiveButton(R.string.speech_model_action_delete, (dialog, which) -> {
                TaiReadAloud.stop(context);
                deleteModel(context, spec.id, null);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void confirmDeleteSpeech(@NonNull Context context, @NonNull TaiModelSpec spec) {
        boolean active = spec.id.equals(voiceId);
        TaiModelSpec next = TaiSpeechActions.nextAfter(spec, installedSpeech);
        String message;
        if (!active) message = getString(R.string.speech_model_delete_message_plain);
        else if (next != null) message = getString(R.string.speech_model_delete_message_other, TaiSpeechModels.plainName(next));
        else message = getString(R.string.speech_model_delete_message_none);
        String nextId = active ? (next == null ? "" : next.id) : null;
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.speech_model_delete_title, centreName(spec.id, spec.displayName, spec.localPath, true)))
            .setMessage(deleteMessage(context, message, spec.id))
            .setPositiveButton(R.string.speech_model_action_delete, (dialog, which) -> deleteModel(context, spec.id, nextId))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * Deletes a model's files and records off the main thread (the manager checks the runtime's
     * state first, which can wait on the runtime process). {@code nextVoiceId} is the speech model
     * that takes over voice input (empty for none), or null when voice input is not affected.
     */
    private void deleteModel(@NonNull Context context, @NonNull String modelId, @Nullable String nextVoiceId) {
        if (executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            JSONObject result = null;
            try {
                TaiManager manager = TaiManager.getInstance(app);
                JSONObject download = new TaiModelStore(app).findDownloadForModel(modelId);
                if (download != null && TaiSpeechModels.isDownloadActive(download.optString("status", ""))) {
                    manager.cancelDownload(new JSONObject().put("modelId", modelId).toString());
                }
                result = manager.deleteModel(new JSONObject().put("modelId", modelId).put("confirm", true).toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context current = getContext();
                if (current == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    if (nextVoiceId != null) new TaiSettings(current).setSttModelId(nextVoiceId);
                    AppNotice.show(current, finalResult.optBoolean("deleted", false)
                        ? R.string.termux_ai_model_deleted : R.string.termux_ai_model_delete_missing, false);
                } else {
                    AppNotice.show(current, finalResult == null ? getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", getString(R.string.termux_ai_model_action_failed)), true);
                }
                banners.remove(modelId);
                loadInstalled(current);
                rebuild();
                // Deleting drops the download record behind the hub's back; tell it.
                TaiDownloadHub.get(current).refresh();
            });
        });
    }

    // ---- TaiImportFlow.Host ----

    @Override
    @Nullable
    public Context context() {
        return isAdded() ? getContext() : null;
    }

    @Override
    @NonNull
    public ExecutorService executor() {
        return executor;
    }

    @Override
    @NonNull
    public Handler handler() {
        return handler;
    }

    @Override
    public void pickFile() {
        modelPicker.launch(TaiImportFlow.pickerMimeTypes());
    }

    @Override
    public void pickFolder() {
        folderPicker.launch(null);
    }

    @Override
    public void openUrl(@NonNull String url) {
        Context context = getContext();
        if (context == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            AppNotice.show(context, url, true);
        }
    }

    @Override
    public void setDefaultModel(@NonNull String modelId) {
        Context context = getContext();
        if (context != null) setDefault(context, modelId);
    }

    @Override
    public void modelsChanged() {
        Context context = getContext();
        if (context == null) return;
        loadInstalled(context);
        rebuild();
    }

    /** The current segment's name, for tests. */
    @VisibleForTesting
    @NonNull
    String currentSegment() {
        return SEGMENTS[segment];
    }
}
