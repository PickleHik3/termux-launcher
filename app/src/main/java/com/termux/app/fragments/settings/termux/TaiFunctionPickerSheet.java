package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiFeaturePlans;
import com.termux.ai.TaiFeaturePlan;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiGpuVerdict;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.ai.TaiTierPolicy;
import com.termux.ai.TaiTtsVoices;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The one shared model picker (tai-device-tiers spec §4.3), a bottom sheet per function: Automatic
 * with the tier's choice named, the models on this phone with a fit line, Remote, the choice
 * without a model, and the catalogue downloads, then the fallback chain and the function's extras
 * (cleanup level, voice and speed, the voice-typing window).
 *
 * <p>What the sheet lists and marks is decided by {@link TaiFunctionPickerModel}; this class draws it,
 * reads and writes the picks through {@link TaiFunctionModels} off the main thread, and tells the
 * caller's {@link Listener} after each change so its own screen can refresh. The keyboard settings,
 * the category-sort dialog and the Model Centre all open it.
 */
public final class TaiFunctionPickerSheet {
    /** Told after every change the sheet makes to a pick, an accelerator or an extra; on the main thread. */
    public interface Listener {
        void onChanged(@NonNull TaiFunction function);
    }

    private static final float[] SPEEDS = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-function-picker");
        thread.setDaemon(true);
        return thread;
    });

    /** Opens the picker for {@code function}. */
    public static void show(@NonNull FragmentActivity activity, @NonNull TaiFunction function) {
        show(activity, function, null);
    }

    /** Opens the picker for {@code function}; {@code listener} hears each change. */
    public static void show(@NonNull FragmentActivity activity, @NonNull TaiFunction function, @Nullable Listener listener) {
        new TaiFunctionPickerSheet(activity, function, listener).load(true);
    }

    /** Opens the picker from a fragment; nothing happens when the fragment is not attached. */
    public static void show(@NonNull Fragment fragment, @NonNull TaiFunction function) {
        show(fragment, function, null);
    }

    /** Opens the picker from a fragment; {@code listener} hears each change. */
    public static void show(@NonNull Fragment fragment, @NonNull TaiFunction function, @Nullable Listener listener) {
        FragmentActivity activity = fragment.getActivity();
        if (activity == null) return;
        show(activity, function, listener);
    }

    // ------------------------------------------------------------------------------- state

    /** What one load reads: the sheet's models, and the values behind the extras. */
    private static final class Loaded {
        TaiFunctionPickerModel.Model main;
        TaiFunctionLabels labels;
        String tidyLevel = TERMUX_APP.IN_APP_KEYBOARD_VOICE_POLISH_LEVEL_POLISHED;
        String ttsVoice = TaiTtsVoices.DEFAULT_VOICE;
        float ttsSpeed = TaiTtsVoices.DEFAULT_SPEED;
        /** The installed Whisper model voice typing uses, when it has a second window to switch to. */
        @Nullable TaiModelSpec windowSpec;
        int otherWindow;
    }

    private final FragmentActivity activity;
    private final TaiFunction function;
    @Nullable private final Listener listener;
    @Nullable private BottomSheetDialog dialog;
    @Nullable private View root;
    /** Catalogue ids asked for from this sheet: "Starting", then "Downloading" (the Centre shows progress). */
    private final Map<String, String> started = new HashMap<>();
    private final Set<String> starting = new HashSet<>();
    private final Map<String, String> errors = new HashMap<>();

    private TaiFunctionPickerSheet(@NonNull FragmentActivity activity, @NonNull TaiFunction function, @Nullable Listener listener) {
        this.activity = activity;
        this.function = function;
        this.listener = listener;
    }

    // ------------------------------------------------------------------------------- load

    /** Reads everything off the main thread, then draws on it. {@code first} creates the dialog. */
    private void load(boolean first) {
        final Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            Loaded loaded = read(app);
            MAIN.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (first || dialog == null) create();
                render(loaded);
            });
        });
    }

    @NonNull
    private Loaded read(@NonNull Context app) {
        Loaded loaded = new Loaded();
        TaiFunctionModels models = TaiFunctionModels.forContext(app);
        TaiModelStore store = new TaiModelStore(app);
        Map<String, TaiModelSpec> installed = new LinkedHashMap<>(store.getDownloadedReadableModels());
        installed.putAll(store.getInstalledUserModels());
        loaded.labels = new TaiFunctionLabels(app, installed);
        TaiFunctionModels.Remote remote = remoteOf(app);
        List<TaiFunctionRows.CatalogItem> catalogue = catalogue();
        // A download started here stays listed, marked Downloading; once it lands it moves to On this phone.
        Set<String> busy = new HashSet<>();
        // The plan as a load has it: the picks, the Parameters values, and this phone's measurements.
        TaiFeaturePlan plan = TaiFeaturePlans.forContext(app, models).plan(function);
        loaded.main = TaiFunctionPickerModel.build(function, models, remote, catalogue, busy, loaded.labels, plan);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, true);
        if (prefs != null) loaded.tidyLevel = prefs.getInAppKeyboardVoicePolishLevel();
        TaiSettings settings = new TaiSettings(app);
        loaded.ttsVoice = settings.getTtsVoice();
        loaded.ttsSpeed = settings.getTtsSpeed();
        if (function == TaiFunction.VOICE_TYPING) {
            TaiModelSpec spec = plan.modelId == null ? null : installed.get(plan.modelId);
            if (spec != null && TaiSpeechActions.isWhisper(spec.id) && TaiSpeechActions.otherWindow(spec) > 0) {
                loaded.windowSpec = spec;
                loaded.otherWindow = TaiSpeechActions.otherWindow(spec);
            }
        }
        return loaded;
    }

    @NonNull
    private static TaiFunctionModels.Remote remoteOf(@NonNull Context app) {
        final com.termux.ai.TaiRemoteProvider provider = new com.termux.ai.TaiRemoteProvider(app);
        return new TaiFunctionModels.Remote() {
            @Override public boolean configured() { return provider.isConfigured(); }
            @NonNull @Override public String modelId() { return provider.modelId(); }
            @Override public boolean understandsImages() { return provider.understandsImages(); }
            @Override public boolean prefersRemote() { return provider.prefersRemote(); }
        };
    }

    @NonNull
    private static List<TaiFunctionRows.CatalogItem> catalogue() {
        List<TaiFunctionRows.CatalogItem> out = new ArrayList<>();
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            Set<String> capabilities = new java.util.LinkedHashSet<>(entry.capabilities);
            capabilities.addAll(entry.sourceCapabilities);
            capabilities.addAll(entry.endpointCapabilities);
            out.add(new TaiFunctionRows.CatalogItem(new TaiFunctionModels.ModelInfo(entry.modelId, entry.sizeBytes,
                capabilities, entry.backend), entry.downloadAvailable));
        }
        return out;
    }

    // ------------------------------------------------------------------------------- writes

    /** Stores a pick off the main thread, then redraws and tells the listener. */
    private void applyPick(@NonNull TaiFunction target, @NonNull String value) {
        final Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            TaiFunctionModels models = TaiFunctionModels.forContext(app);
            models.set(target, value);
            // Voice typing's pick is the speech model key; activating also settles a pending first download.
            if (target == TaiFunction.VOICE_TYPING && !value.isEmpty() && !TaiFunctionModels.VALUE_OFF.equals(value)) {
                TaiSpeechModels.activate(new TaiSettings(app), value);
            }
            changed();
        });
    }

    private void applyAccelerator(@NonNull String accelerator) {
        final Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            TaiFunctionModels models = TaiFunctionModels.forContext(app);
            TaiFunctionPickerModel.AcceleratorWrite write =
                TaiFunctionPickerModel.acceleratorWrite(models.resolve(function), accelerator);
            if (write.modelId == null) {
                models.setAcceleratorPick(function, write.value);
            } else {
                new TaiSettings(app).setModelParameter(write.modelId, TaiSettings.FIELD_ACCELERATOR, write.value);
            }
            changed();
        });
    }

    /** After a write: redraw from fresh reads, and tell the caller. */
    private void changed() {
        MAIN.post(() -> {
            if (listener != null) listener.onChanged(function);
        });
        load(false);
    }

    // ------------------------------------------------------------------------------- views

    private void create() {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.sheet_tai_function_picker, null, false);
        sheet.setContentView(content);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.setOnDismissListener(d -> {
            dialog = null;
            root = null;
        });
        dialog = sheet;
        root = content;
        sheet.show();
    }

    private void render(@NonNull Loaded loaded) {
        View view = root;
        if (view == null) return;
        TaiFunctionLabels labels = loaded.labels;
        ((TextView) view.findViewById(R.id.tai_fn_sheet_title)).setText(labels.functionName(function));
        ((TextView) view.findViewById(R.id.tai_fn_sheet_subtitle)).setText(R.string.tai_fn_sheet_subtitle);
        LinearLayout body = view.findViewById(R.id.tai_fn_sheet_body);
        body.removeAllViews();
        addPlan(body, loaded.main.plan);
        renderModel(body, loaded.main, loaded);

        TextView chain = view.findViewById(R.id.tai_fn_sheet_chain);
        chain.setText(loaded.main.chainLine);
        chain.setVisibility(loaded.main.chainLine.isEmpty() ? View.GONE : View.VISIBLE);

        LinearLayout extras = view.findViewById(R.id.tai_fn_sheet_extras);
        extras.removeAllViews();
        renderExtras(extras, loaded);
    }

    /**
     * One model's sections into {@code into}.
     */
    private void renderModel(@NonNull LinearLayout into, @NonNull TaiFunctionPickerModel.Model model,
                             @NonNull Loaded loaded) {
        TaiFunction target = model.function;
        for (TaiFunctionPickerModel.Section section : TaiFunctionPickerModel.Section.values()) {
            List<TaiFunctionPickerModel.Entry> entries = model.entries(section);
            boolean empty = entries.isEmpty();
            if (empty && section != TaiFunctionPickerModel.Section.ON_PHONE) continue;
            addHeader(into, sectionTitle(section));
            if (empty) {
                addNote(into, getString(R.string.tai_fn_nothing_on_phone));
                continue;
            }
            for (TaiFunctionPickerModel.Entry entry : entries) addOption(into, entry, loaded, target);
            if (section == TaiFunctionPickerModel.Section.ON_PHONE && model.accelerator != null) {
                addAccelerator(into, model.accelerator);
            }
        }
    }

    /**
     * How the function runs now (its feature load plan): one line, the reason under it, and when this
     * phone measured a faster setup than the pick, a one-tap offer of it. The texts wrap, so a large
     * font scale never clips them.
     */
    private void addPlan(@NonNull LinearLayout into, @NonNull TaiFunctionPickerModel.PlanLine plan) {
        addHeader(into, R.string.tai_fn_plan_header);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(8));
        TextView line = text(com.google.android.material.R.attr.textAppearanceBodyLarge,
            com.google.android.material.R.attr.colorOnSurface);
        line.setText(plan.line);
        box.addView(line);
        TextView reason = text(com.google.android.material.R.attr.textAppearanceBodySmall,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        reason.setText(plan.reason);
        reason.setPadding(0, dp(2), 0, 0);
        box.addView(reason);
        String offerAccelerator = plan.offerAccelerator;
        if (plan.offer != null && offerAccelerator != null) {
            MaterialButton offer = new MaterialButton(activity, null, androidx.appcompat.R.attr.borderlessButtonStyle);
            offer.setAllCaps(false);
            offer.setText(plan.offer);
            offer.setOnClickListener(v -> applyAccelerator(offerAccelerator));
            box.addView(offer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        into.addView(box);
    }

    private void addAccelerator(@NonNull LinearLayout into, @NonNull TaiFunctionPickerModel.Accelerator accelerator) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, dp(4), pad, dp(8));
        TextView label = text(com.google.android.material.R.attr.textAppearanceLabelLarge,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        label.setText(R.string.tai_fn_runs_on);
        box.addView(label);
        if (accelerator.gpuOffered) {
            TaiSegmentedTabs tabs = new TaiSegmentedTabs(activity);
            tabs.setLabels(getString(R.string.tai_fn_gpu), getString(R.string.tai_fn_cpu));
            tabs.select(accelerator.gpuSelected ? 0 : 1, false);
            tabs.setOnSegmentSelectedListener(index -> applyAccelerator(index == 0 ? TaiTierPolicy.ACCEL_GPU : TaiTierPolicy.ACCEL_CPU));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(8);
            box.addView(tabs, params);
            if (accelerator.note != null) {
                TextView note = text(com.google.android.material.R.attr.textAppearanceBodySmall,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
                note.setText(accelerator.note);
                note.setPadding(0, dp(6), 0, 0);
                box.addView(note);
            }
            addGpuVerdictAction(box, accelerator.gpuSelected);
        } else {
            // No GPU path at all: the CPU is the only choice, so say so rather than offer a control.
            TextView only = text(com.google.android.material.R.attr.textAppearanceBodyMedium,
                com.google.android.material.R.attr.colorOnSurface);
            only.setText(R.string.tai_fn_cpu);
            box.addView(only);
        }
        into.addView(box);
    }

    /**
     * The user's half of the GPU check (tiers spec §2.3): "Answers look wrong?" records a failed GPU
     * verdict and moves this function to the CPU; once failed, "Try the GPU again" clears the verdict.
     */
    private void addGpuVerdictAction(@NonNull LinearLayout box, boolean gpuSelected) {
        final Context app = activity.getApplicationContext();
        boolean failed = TaiGpuVerdict.current(app, TaiPlatformCaps.cached(app)) == TaiGpuVerdict.State.FAILED;
        if (!failed && !gpuSelected) return;
        MaterialButton action = new MaterialButton(activity, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        action.setAllCaps(false);
        action.setText(failed ? R.string.tai_fn_gpu_retry : R.string.tai_fn_gpu_wrong);
        action.setOnClickListener(view -> WORKER.execute(() -> {
            if (failed) {
                TaiGpuVerdict.clear(app);
            } else {
                TaiGpuVerdict.markFailed(app);
                TaiFunctionModels.forContext(app).setAcceleratorPick(function, TaiTierPolicy.ACCEL_CPU);
            }
            changed();
        }));
        box.addView(action, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addOption(@NonNull LinearLayout into, @NonNull TaiFunctionPickerModel.Entry entry,
                           @NonNull Loaded loaded, @NonNull TaiFunction target) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_tai_function_option, into, false);
        TextView title = row.findViewById(R.id.tai_fn_option_title);
        TextView detail = row.findViewById(R.id.tai_fn_option_detail);
        TextView end = row.findViewById(R.id.tai_fn_option_end);
        View mark = row.findViewById(R.id.tai_fn_option_mark);
        title.setText(entry.title);
        mark.setVisibility(entry.selected ? View.VISIBLE : View.INVISIBLE);
        row.findViewById(R.id.tai_fn_option_warning).setVisibility(entry.warnBackground ? View.VISIBLE : View.GONE);

        String detailText = entry.detail;
        if (entry.section == TaiFunctionPickerModel.Section.GET) {
            String size = TaiModelCentreRows.formatBytes(entry.sizeBytes);
            detailText = entry.detail.isEmpty() ? size : getString(R.string.tai_fn_get_size, size, entry.detail);
            if (entry.suggested) detailText = detailText + " · " + getString(R.string.tai_fn_pill_suggested);
            String error = errors.get(entry.value);
            if (error != null) detailText = error;
            String state = starting.contains(entry.value) ? getString(R.string.tai_fn_get_starting)
                : started.containsKey(entry.value) ? getString(R.string.tai_fn_get_started) : getString(R.string.tai_fn_get_action);
            end.setText(state);
            end.setVisibility(View.VISIBLE);
        }
        detail.setText(detailText);
        detail.setVisibility(detailText.isEmpty() ? View.GONE : View.VISIBLE);
        if (entry.setupLink) {
            title.setTextColor(color(androidx.appcompat.R.attr.colorPrimary));
        }

        row.setOnClickListener(v -> {
            TaiMotion.tick(v);
            switch (entry.section) {
                case GET:
                    startDownload(entry);
                    break;
                case REMOTE:
                    if (entry.setupLink) openRemoteSettings();
                    else applyPick(target, entry.value);
                    break;
                default:
                    applyPick(target, entry.value);
                    break;
            }
        });
        into.addView(row);
    }

    private void startDownload(@NonNull TaiFunctionPickerModel.Entry entry) {
        TaiModelCatalog.CatalogEntry catalogue = TaiModelCatalog.get(entry.value);
        if (catalogue == null || starting.contains(entry.value) || started.containsKey(entry.value)) return;
        TaiModelCentreFragment.installEntry(activity, catalogue, (modelId, installing, error) -> {
            if (installing) {
                errors.remove(modelId);
                starting.add(modelId);
            } else {
                starting.remove(modelId);
                if (error != null) errors.put(modelId, error);
                else started.put(modelId, modelId);
            }
            if (root != null) load(false);
        });
    }

    private void openRemoteSettings() {
        BottomSheetDialog sheet = dialog;
        if (sheet != null) sheet.dismiss();
        if (activity instanceof SettingsActivity) {
            ((SettingsActivity) activity).openScreen(TaiRemotePreferencesFragment.class, R.string.tai_remote_screen_title, null);
        } else {
            activity.startActivity(SettingsActivity.createFragmentIntent(activity, TaiRemotePreferencesFragment.class,
                R.string.tai_remote_screen_title));
        }
    }

    // ------------------------------------------------------------------------------- extras

    private void renderExtras(@NonNull LinearLayout into, @NonNull Loaded loaded) {
        switch (function) {
            case TIDY_DICTATION:
                addHeader(into, getString(R.string.tai_fn_section_extras));
                addTidyLevel(into, loaded);
                break;
            case READ_ALOUD:
                addHeader(into, getString(R.string.tai_fn_section_extras));
                addVoiceRows(into, loaded);
                break;
            case VOICE_TYPING:
                if (loaded.windowSpec != null) {
                    addHeader(into, getString(R.string.tai_fn_section_extras));
                    addWindowRow(into, loaded);
                }
                break;
            default:
                break;
        }
    }

    private void addTidyLevel(@NonNull LinearLayout into, @NonNull Loaded loaded) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(8));
        TextView label = text(com.google.android.material.R.attr.textAppearanceBodyLarge, com.google.android.material.R.attr.colorOnSurface);
        label.setText(R.string.tai_fn_tidy_level);
        box.addView(label);
        TaiSegmentedTabs tabs = new TaiSegmentedTabs(activity);
        tabs.setLabels(getString(R.string.tai_fn_level_light), getString(R.string.tai_fn_level_polished));
        tabs.select(TERMUX_APP.IN_APP_KEYBOARD_VOICE_POLISH_LEVEL_LIGHT.equals(loaded.tidyLevel) ? 0 : 1, false);
        tabs.setOnSegmentSelectedListener(index -> {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(activity, true);
            if (prefs != null) {
                prefs.setInAppKeyboardVoicePolishLevel(index == 0 ? TERMUX_APP.IN_APP_KEYBOARD_VOICE_POLISH_LEVEL_LIGHT
                    : TERMUX_APP.IN_APP_KEYBOARD_VOICE_POLISH_LEVEL_POLISHED);
            }
            changed();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        box.addView(tabs, params);
        into.addView(box);
    }

    private void addVoiceRows(@NonNull LinearLayout into, @NonNull Loaded loaded) {
        addValueRow(into, getString(R.string.tai_fn_voice_title), loaded.ttsVoice, v -> {
            String[] voices = TaiTtsVoices.VOICES;
            int checked = 0;
            for (int i = 0; i < voices.length; i++) if (voices[i].equals(loaded.ttsVoice)) checked = i;
            new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tai_fn_voice_title)
                .setSingleChoiceItems(voices, checked, (d, which) -> {
                    new TaiSettings(activity).setTtsVoice(voices[which]);
                    d.dismiss();
                    changed();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
        addValueRow(into, getString(R.string.tai_fn_speed_title), getString(R.string.tai_fn_speed_value, speedLabel(loaded.ttsSpeed)), v -> {
            String[] labels = new String[SPEEDS.length];
            int checked = 0;
            for (int i = 0; i < SPEEDS.length; i++) {
                labels[i] = getString(R.string.tai_fn_speed_value, speedLabel(SPEEDS[i]));
                if (Math.abs(SPEEDS[i] - loaded.ttsSpeed) < Math.abs(SPEEDS[checked] - loaded.ttsSpeed)) checked = i;
            }
            new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tai_fn_speed_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    new TaiSettings(activity).setTtsSpeed(SPEEDS[which]);
                    d.dismiss();
                    changed();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
    }

    private void addWindowRow(@NonNull LinearLayout into, @NonNull Loaded loaded) {
        TaiModelSpec spec = loaded.windowSpec;
        if (spec == null) return;
        int current = TaiSpeechModels.windowSeconds(spec);
        addValueRow(into, getString(R.string.tai_fn_window_title), getString(R.string.tai_fn_window_value, current),
            v -> TaiSpeechActions.showWindowDialog(activity, spec, loaded.otherWindow, this::changed));
    }

    /** A title with its current value under it; a tap runs {@code onClick}. */
    private void addValueRow(@NonNull LinearLayout into, @NonNull String title, @NonNull String value,
                             @NonNull View.OnClickListener onClick) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_tai_function_option, into, false);
        row.findViewById(R.id.tai_fn_option_mark).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.tai_fn_option_title)).setText(title);
        TextView detail = row.findViewById(R.id.tai_fn_option_detail);
        detail.setText(value);
        detail.setVisibility(View.VISIBLE);
        row.setOnClickListener(onClick);
        into.addView(row);
    }

    @NonNull
    private static String speedLabel(float speed) {
        String text = String.format(Locale.US, "%.2f", speed);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    // ------------------------------------------------------------------------------- small views

    private int sectionTitle(@NonNull TaiFunctionPickerModel.Section section) {
        switch (section) {
            case ON_PHONE: return R.string.tai_fn_section_on_phone;
            case REMOTE: return R.string.tai_fn_section_remote;
            case WITHOUT: return R.string.tai_fn_section_without;
            case GET: return R.string.tai_fn_section_get;
            default: return R.string.tai_fn_section_automatic;
        }
    }

    private void addHeader(@NonNull LinearLayout into, int titleRes) {
        addHeader(into, getString(titleRes));
    }

    private void addHeader(@NonNull LinearLayout into, @NonNull String title) {
        TextView header = text(com.google.android.material.R.attr.textAppearanceTitleSmall, androidx.appcompat.R.attr.colorPrimary);
        header.setText(title);
        header.setAccessibilityHeading(true);
        header.setPadding(dp(16), dp(20), dp(16), dp(4));
        into.addView(header);
    }

    private void addNote(@NonNull LinearLayout into, @NonNull String note) {
        TextView view = text(com.google.android.material.R.attr.textAppearanceBodySmall,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        view.setText(note);
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
        into.addView(view);
    }

    @NonNull
    private TextView text(int appearanceAttr, int colorAttr) {
        TextView view = new TextView(activity);
        android.util.TypedValue value = new android.util.TypedValue();
        if (activity.getTheme().resolveAttribute(appearanceAttr, value, true)) view.setTextAppearance(value.resourceId);
        view.setTextColor(color(colorAttr));
        return view;
    }

    private int color(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        return activity.getTheme().resolveAttribute(attr, value, true) ? value.data : 0xFF808080;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @NonNull
    private String getString(int res, Object... args) {
        return activity.getString(res, args);
    }
}
