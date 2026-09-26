package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keyboard → Voice input → Speech model, the picker (D5): every installed speech-to-text model as
 * a selectable card (engine · size · window, "In use" on the chosen one), a "Change window" on the
 * models that have another window, and the idle-unload option. Downloading and deleting live in
 * the Model centre; "Get more in Model centre" opens it on its Speech segment, and so does the
 * empty state's hint. The state itself lives in {@link TaiSpeechModels}; this screen shows it and
 * asks for changes. Cards are keyed by model id and updated in place.
 *
 * <p>A window switch started here downloads in the background; the card says so, and the screen
 * follows it through {@link TaiDownloadHub} (never by polling), redrawing when a download changes
 * status.
 */
@Keep
public class SpeechModelPreferencesFragment extends MaterialPreferenceFragment implements TaiDownloadHub.Listener {
    static final String ROW_KEY_PREFIX = "speech_model_row_";
    private static final String KEY_INSTALLED_CATEGORY = "speech_model_installed";
    private static final String KEY_EMPTY = "speech_model_empty";
    static final String KEY_GET_MORE = "speech_model_get_more";
    private static final String KEY_IDLE_UNLOAD = "speech_model_idle_unload";

    /** The download statuses as of the last redraw; a push that changes none of them is skipped. */
    @NonNull private String lastStatuses = "";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.speech_model_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference getMore = findPreference(KEY_GET_MORE);
        if (getMore != null) getMore.setOnPreferenceClickListener(preference -> {
            TaiModelCentreFragment.open(getActivity(), TaiModelCentreFragment.SEGMENT_SPEECH);
            return true;
        });
        Preference idleUnload = findPreference(KEY_IDLE_UNLOAD);
        if (idleUnload != null) idleUnload.setOnPreferenceClickListener(preference -> {
            showIdleUnloadDialog(context);
            return true;
        });
        refresh(context);
    }

    @Override
    public void onStart() {
        super.onStart();
        Context context = getContext();
        if (context != null) TaiDownloadHub.get(context).addListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_keyboard_voice_model_title);
        Context context = getContext();
        if (context != null) refresh(context);
    }

    @Override
    public void onStop() {
        Context context = getContext();
        if (context != null) TaiDownloadHub.get(context).removeListener(this);
        super.onStop();
    }

    @Override
    public void onDownloadsChanged(@NonNull List<TaiDownloadHub.Snapshot> downloads) {
        Context context = getContext();
        if (context == null || !isAdded()) return;
        StringBuilder statuses = new StringBuilder();
        for (TaiDownloadHub.Snapshot item : downloads) {
            if (item.isSpeech()) statuses.append(item.modelId).append('=').append(item.status).append(';');
        }
        // Progress ticks carry nothing this screen shows; only a status change redraws it.
        if (statuses.toString().equals(lastStatuses)) return;
        lastStatuses = statuses.toString();
        refresh(context);
    }

    // ---- cards ----

    /** Applies whatever a finished download decided, then redraws the cards in place. */
    void refresh(@NonNull Context context) {
        TaiSpeechActions.announce(context, TaiSpeechModels.settlePending(context));
        PreferenceCategory category = findPreference(KEY_INSTALLED_CATEGORY);
        if (category == null) return;
        TaiModelStore store = new TaiModelStore(context);
        TaiSettings settings = new TaiSettings(context);
        List<TaiModelSpec> installed = TaiSpeechModels.installed(store);
        TaiModelSpec active = TaiSpeechModels.chooseActive(settings.getSttModelId(), installed);

        Set<String> keep = new HashSet<>();
        int order = 1;
        for (TaiModelSpec spec : installed) {
            SpeechModelCardPreference card = cardFor(context, category, spec.id);
            keep.add(card.getKey());
            card.setOrder(order++);
            bindCard(context, card, spec, active != null && active.id.equals(spec.id), store);
        }
        for (int i = category.getPreferenceCount() - 1; i >= 0; i--) {
            Preference preference = category.getPreference(i);
            String key = preference.getKey();
            if (key != null && key.startsWith(ROW_KEY_PREFIX) && !keep.contains(key)) category.removePreference(preference);
        }
        Preference empty = findPreference(KEY_EMPTY);
        if (empty != null) empty.setVisible(keep.isEmpty());
        Preference idleUnload = findPreference(KEY_IDLE_UNLOAD);
        if (idleUnload != null) idleUnload.setSummary(idleUnloadSummary(settings.getSttIdleUnloadMinutes()));
    }

    @NonNull
    private SpeechModelCardPreference cardFor(@NonNull Context context, @NonNull PreferenceCategory category,
                                              @NonNull String modelId) {
        String key = ROW_KEY_PREFIX + modelId;
        Preference existing = category.findPreference(key);
        if (existing instanceof SpeechModelCardPreference) return (SpeechModelCardPreference) existing;
        SpeechModelCardPreference card = new SpeechModelCardPreference(context);
        card.setKey(key);
        category.addPreference(card);
        return card;
    }

    private void bindCard(@NonNull Context context, @NonNull SpeechModelCardPreference card, @NonNull TaiModelSpec spec,
                          boolean active, @NonNull TaiModelStore store) {
        card.setTitle(TaiSpeechModels.plainName(spec));
        card.setSummary(installedSummary(spec));
        card.setChosen(active);
        JSONObject download = TaiSpeechModels.findDownload(store, spec.id);
        boolean switching = download != null && TaiSpeechModels.isDownloadActive(download.optString("status", ""));
        int otherWindow = TaiSpeechActions.otherWindow(spec);
        if (switching) {
            // The other window's graph is on its way; the installed one stays in use until it lands.
            card.setStatus(getString(R.string.speech_picker_switching,
                TaiSpeechModels.windowSeconds(download.optString("path", ""))));
            card.setWindowAction(null, null);
        } else {
            card.setStatus(null);
            card.setWindowAction(otherWindow > 0 ? getString(R.string.speech_model_action_window) : null,
                otherWindow > 0 ? view -> TaiSpeechActions.showWindowDialog(context, spec, otherWindow, () -> refresh(context)) : null);
        }
        card.setOnPreferenceClickListener(preference -> {
            if (!card.isChosen()) {
                TaiSpeechModels.activate(new TaiSettings(context), spec.id);
                AppNotice.show(context, getString(R.string.speech_model_now_using, TaiSpeechModels.plainName(spec)), false);
                refresh(context);
            }
            return true;
        });
    }

    /** "Whisper · 97 MB · 10-second window", "Parakeet · 586 MB · 5-second window": engine, size, window when known. */
    @NonNull
    String installedSummary(@NonNull TaiModelSpec spec) {
        return TaiSpeechActions.installedSummary(requireContext(), spec);
    }

    // ---- idle unload ----

    /** Picks how long an idle speech model stays loaded; 0 keeps it until memory pressure evicts it. */
    private void showIdleUnloadDialog(@NonNull Context context) {
        String[] labels = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_entries);
        String[] values = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_values);
        TaiSettings settings = new TaiSettings(context);
        String current = String.valueOf(settings.getSttIdleUnloadMinutes());
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_model_idle_unload_title)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                settings.setSttIdleUnloadMinutes(Integer.parseInt(values[which]));
                refresh(context);
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    private String idleUnloadSummary(int minutes) {
        String[] labels = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_entries);
        String[] values = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_values);
        String label = minutes <= 0 ? getString(R.string.termux_ai_disabled) : minutes + " min";
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(String.valueOf(minutes))) label = labels[i];
        }
        return getString(R.string.speech_model_idle_unload_summary, label);
    }
}
