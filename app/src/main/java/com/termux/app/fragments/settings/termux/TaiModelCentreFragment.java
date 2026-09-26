package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;

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
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.notice.AppNotice;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Model centre (D2): one screen that gets, downloads and manages every model, chat and
 * speech. A link bar on top (paste a Hugging Face link, or pick a file), the live Downloads with
 * pause, resume, "start now", retry and cancel, then one list under an Installed | Chat | Speech
 * segmented control: what is on the phone, and what the catalogue offers that is not.
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
    public static final String SEGMENT_INSTALLED = "installed";
    public static final String SEGMENT_CHAT = "chat";
    public static final String SEGMENT_SPEECH = "speech";
    private static final String[] SEGMENTS = {SEGMENT_INSTALLED, SEGMENT_CHAT, SEGMENT_SPEECH};
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
    @Nullable private String defaultId;
    @Nullable private String voiceId;
    @Nullable private String loadedId;
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

    static int segmentIndex(@Nullable String segment) {
        for (int i = 0; i < SEGMENTS.length; i++) {
            if (SEGMENTS[i].equals(segment)) return i;
        }
        return 0;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        segment = segmentIndex(arguments == null ? null : arguments.getString(SettingsActivity.EXTRA_INITIAL_PLACE));
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
        return list;
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
            if (!TaiSpeechModels.isSpeechModel(spec)) chat.add(spec);
        }
        installedChat = chat;
        installedSpeech = TaiSpeechModels.installed(store);
        TaiSettings settings = new TaiSettings(context);
        defaultId = settings.getDefaultAssistantModel();
        TaiModelSpec active = TaiSpeechModels.chooseActive(settings.getSttModelId(), installedSpeech);
        voiceId = active == null ? null : active.id;
        parallel = settings.getDownloadParallel();
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

    // ---- the list ----

    private void rebuild() {
        Context context = getContext();
        if (context == null) return;
        List<TaiModelCentreAdapter.Item> items = new ArrayList<>();
        items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_LINK, "link",
            "link|" + linkText + "|" + linkError, new TaiModelCentreAdapter.LinkBar(linkText, linkError), false));

        Set<String> busy = addDownloads(context, items);
        addBanners(context, items);

        CharSequence[] labels = {
            getString(R.string.tai_centre_segment_installed),
            getString(R.string.tai_centre_segment_chat),
            getString(R.string.tai_centre_segment_speech)};
        items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_SEGMENTS, "segments", "segments|" + segment,
            new TaiModelCentreAdapter.Segments(segment, labels), false));
        switch (SEGMENTS[segment]) {
            case SEGMENT_CHAT: addCatalogue(context, items, TaiModelCatalog.chatEntries().values(), false, busy); break;
            case SEGMENT_SPEECH: addCatalogue(context, items, TaiModelCatalog.speechEntries().values(), true, busy); break;
            default: addInstalled(context, items); break;
        }
        items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_SETTING, "parallel", "parallel|" + parallel,
            parallel, false));
        adapter.submit(items);
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
                boolean speech = item.isSpeech() || TaiModelCatalog.speechEntries().containsKey(item.modelId);
                String title = centreName(item.modelId, item.displayName, item.record.optString("path", ""), speech);
                String subtitle = kindLine(context, speech, item.totalBytes > 0L ? item.totalBytes : catalogueSize(item.modelId),
                    !TaiModelCatalog.entries().containsKey(item.modelId));
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
            boolean speech = entry.getValue();
            TaiModelSpec spec = installedAll.get(modelId);
            String name = spec == null ? modelId : centreName(modelId, spec.displayName, spec.localPath, speech);
            String action;
            if (speech) action = modelId.equals(voiceId) ? "" : getString(R.string.tai_centre_use_voice);
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
            row.subtitle = kindLine(context, false, spec.sizeBytes, !TaiModelCatalog.entries().containsKey(spec.id));
            row.pillPrimary = spec.id.equals(loadedId) ? getString(R.string.tai_centre_pill_in_use) : "";
            row.pillSecondary = spec.id.equals(defaultId) ? getString(R.string.tai_centre_pill_default) : "";
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
            addModelRow(items, row);
        }
        if (chat.isEmpty() && speech.isEmpty()) {
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_EMPTY, "empty-installed", "empty-installed",
                new TaiModelCentreAdapter.Empty(getString(R.string.tai_centre_empty_installed_title),
                    getString(R.string.tai_centre_empty_installed_summary)), false));
        }
    }

    private void addCatalogue(@NonNull Context context, @NonNull List<TaiModelCentreAdapter.Item> items,
                              @NonNull Iterable<TaiModelCatalog.CatalogEntry> entries, boolean speech,
                              @NonNull Set<String> busy) {
        int added = 0;
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            // What is on the phone is under Installed; what is on its way is under Downloads.
            if (installedAll.containsKey(entry.modelId) || busy.contains(entry.modelId)) continue;
            TaiModelCentreAdapter.ModelRow row = new TaiModelCentreAdapter.ModelRow(entry.modelId, speech, null, entry);
            row.title = speech ? centreName(entry.modelId, entry.displayName, null, true) : entry.displayName;
            String size = entry.sizeEstimate == null || entry.sizeEstimate.isEmpty()
                ? TaiModelCentreRows.formatBytes(entry.sizeBytes) : entry.sizeEstimate;
            StringBuilder subtitle = new StringBuilder(getString(speech ? R.string.tai_centre_kind_speech : R.string.tai_centre_kind_chat))
                .append(" · ").append(size);
            if (entry.ramTier != null && !entry.ramTier.isEmpty()) subtitle.append(" · ").append(entry.ramTier);
            row.subtitle = subtitle.toString();
            row.installable = entry.downloadAvailable;
            row.installing = installing.contains(entry.modelId);
            String error = errors.get(entry.modelId);
            if (error != null) {
                row.note = error;
                row.noteIsError = true;
            } else if (TaiModelCatalog.PARAKEET_TDT_V3_ID.equals(entry.modelId)) {
                String warning = TaiSpeechActions.parakeetRamWarning(context, deviceMemory(context));
                row.note = warning == null ? "" : warning;
            } else if (TaiSpeechActions.isSmall(entry.modelId) && deviceMemory(context) > 0L
                && deviceMemory(context) < TaiSpeechActions.SMALL_MIN_MEMORY_BYTES) {
                row.note = getString(R.string.tai_centre_small_ram_note);
            } else if (!entry.downloadAvailable) {
                row.note = entry.unavailableReason == null ? "" : entry.unavailableReason;
            }
            addModelRow(items, row);
            added++;
        }
        if (added == 0) {
            String key = speech ? "empty-speech" : "empty-chat";
            items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_EMPTY, key, key,
                new TaiModelCentreAdapter.Empty("", getString(speech ? R.string.tai_centre_empty_speech
                    : R.string.tai_centre_empty_chat)), false));
        }
    }

    private static void addModelRow(@NonNull List<TaiModelCentreAdapter.Item> items, @NonNull TaiModelCentreAdapter.ModelRow row) {
        items.add(new TaiModelCentreAdapter.Item(TaiModelCentreAdapter.TYPE_MODEL, row.modelId, row.signature(), row, false));
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

    @Override
    public void onInstall(@NonNull TaiModelCentreAdapter.ModelRow row, @NonNull View source) {
        Context context = getContext();
        TaiModelCatalog.CatalogEntry entry = row.entry;
        if (context == null || entry == null || row.installing) return;
        TaiMotion.tick(source);
        if (row.speech && TaiSpeechActions.isWhisper(entry.modelId)) {
            TaiSpeechInstallSheet.show(context, entry.modelId, new TaiSettings(context).getSttWindowSeconds(),
                deviceMemory(context), (modelId, window) -> install(modelId, true, window, source));
            return;
        }
        install(entry.modelId, row.speech, row.speech ? TaiSpeechActions.PARAKEET_WINDOW_SECONDS : 0, source);
    }

    private void install(@NonNull String modelId, boolean speech, int windowSeconds, @NonNull View source) {
        Context context = getContext();
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (context == null || entry == null) return;
        TaiModelSpec already = installedAll.get(modelId);
        if (already != null) {
            // The sheet can land on a size and language that is already here: that is a choice
            // of voice model, not a download.
            if (speech) useForVoice(context, already);
            return;
        }
        long needed = windowSeconds > 0 ? entry.withWindow(windowSeconds).sizeBytes : entry.sizeBytes;
        File volume = modelsVolume(context);
        String refusal = spaceRefusal(context, volume.getUsableSpace(), volume.getTotalSpace(), needed);
        if (refusal != null) {
            // Said on the row itself, before anything starts; the pill shakes where it was tapped.
            errors.put(modelId, refusal);
            TaiMotion.shake(source);
            rebuild();
            return;
        }
        errors.remove(modelId);
        installing.add(modelId);
        rebuild();
        if (executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        boolean firstVoiceModel = speech && installedSpeech.isEmpty();
        executor.execute(() -> {
            JSONObject result = null;
            try {
                if (!speech) {
                    result = TaiManager.getInstance(app).downloadCatalogModel(modelId);
                } else if (firstVoiceModel && noPendingVoiceChoice(app)) {
                    // The first speech model on the phone becomes the one voice input uses as
                    // soon as it lands; later ones wait for "Use for voice".
                    result = TaiSpeechModels.startDownload(app, modelId, windowSeconds);
                } else {
                    result = TaiManager.getInstance(app).downloadSpeechModel(modelId, windowSeconds);
                }
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                installing.remove(modelId);
                if (!isAdded()) return;
                if (finalResult == null || !finalResult.optBoolean("ok", false)) {
                    errors.put(modelId, finalResult == null ? getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", getString(R.string.termux_ai_model_action_failed)));
                }
                rebuild();
            });
        });
    }

    /** A window switch or an earlier first download already holds the one pending voice choice. */
    private static boolean noPendingVoiceChoice(@NonNull Context context) {
        String pending = new TaiSettings(context).getSttPendingDownloadJson();
        return pending == null || pending.isEmpty();
    }

    // ---- installed models ----

    @Override
    public void onModelMenu(@NonNull TaiModelCentreAdapter.ModelRow row, @NonNull View anchor) {
        Context context = getContext();
        TaiModelSpec spec = row.installed;
        if (context == null || spec == null) return;
        PopupMenu menu = new PopupMenu(context, anchor);
        Menu items = menu.getMenu();
        if (row.speech) {
            if (!spec.id.equals(voiceId)) items.add(Menu.NONE, 1, Menu.NONE, R.string.tai_centre_use_voice);
            int other = TaiSpeechActions.otherWindow(spec);
            if (other > 0) items.add(Menu.NONE, 2, Menu.NONE, R.string.speech_model_action_window);
            items.add(Menu.NONE, 3, Menu.NONE, R.string.speech_model_action_delete);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: useForVoice(context, spec); break;
                    case 2: TaiSpeechActions.showWindowDialog(context, spec, other, this::rebuild); break;
                    default: confirmDeleteSpeech(context, spec); break;
                }
                return true;
            });
        } else {
            boolean loaded = spec.id.equals(loadedId);
            if (!loaded) items.add(Menu.NONE, 1, Menu.NONE, R.string.termux_ai_model_load_action);
            if (!spec.id.equals(defaultId)) items.add(Menu.NONE, 2, Menu.NONE, R.string.termux_ai_model_set_active_action);
            items.add(Menu.NONE, 3, Menu.NONE, R.string.termux_ai_model_tune_action);
            items.add(Menu.NONE, 4, Menu.NONE, loaded ? R.string.termux_ai_model_delete_action_loaded
                : R.string.termux_ai_model_delete_action);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: loadModel(context, spec.id); break;
                    case 2: setDefault(context, spec.id); break;
                    case 3: openParameters(spec); break;
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
            .setMessage(R.string.termux_ai_model_delete_message)
            .setPositiveButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, spec.id, null))
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
            .setMessage(message)
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
