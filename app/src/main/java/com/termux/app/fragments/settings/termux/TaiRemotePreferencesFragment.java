package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiRemoteClient;
import com.termux.ai.TaiRemoteSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TAI settings → Remote model: the bring-your-own-key server. Every network call (model list,
 * image probe, connection test) runs on one background thread and lands back here only while the
 * fragment is attached. All state lives in {@link TaiRemoteSettings}; this screen only shows it.
 */
@Keep
public class TaiRemotePreferencesFragment extends MaterialPreferenceFragment {
    private static final String KEY_BASE_URL = "tai_remote_base_url";
    private static final String KEY_API_KEY = "tai_remote_api_key";
    private static final String KEY_MODEL = "tai_remote_model";
    private static final String KEY_IMAGES = "tai_remote_images";
    private static final String KEY_ROUTING = "tai_remote_routing_row";
    private static final String KEY_TEST = "tai_remote_test";
    private static final String KEY_REMOVE = "tai_remote_remove";

    static final String PRESET_OPENAI = "https://api.openai.com/v1";
    static final String PRESET_OPENROUTER = "https://openrouter.ai/api/v1";
    static final String PRESET_LOCAL = "http://127.0.0.1:11434/v1";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean probing;
    private boolean fetchingModels;
    @Nullable private String testSummary;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.tai_remote_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        onClick(KEY_BASE_URL, this::showBaseUrlDialog);
        onClick(KEY_API_KEY, this::showApiKeyDialog);
        onClick(KEY_MODEL, this::chooseModel);
        onClick(KEY_ROUTING, this::showRoutingDialog);
        onClick(KEY_TEST, this::testConnection);
        onClick(KEY_REMOVE, this::confirmRemove);
        SwitchPreferenceCompat images = findPreference(KEY_IMAGES);
        if (images != null) images.setOnPreferenceChangeListener((preference, value) -> {
            setImagesOverride(Boolean.TRUE.equals(value));
            return true;
        });
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.tai_remote_screen_title);
            // The key is masked, but its dialog is on this page; keep it out of screenshots.
            getActivity().getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        refresh();
    }

    @Override
    public void onPause() {
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void onClick(@NonNull String key, @NonNull java.util.function.Consumer<Context> action) {
        Preference preference = findPreference(key);
        if (preference == null) return;
        preference.setOnPreferenceClickListener(p -> {
            Context context = getContext();
            if (context != null) action.accept(context);
            return true;
        });
    }

    @Nullable
    private TaiRemoteSettings settings() {
        Context context = getContext();
        return context == null ? null : new TaiRemoteSettings(context);
    }

    /** Redraws every row's summary and enabled state from the saved settings. */
    private void refresh() {
        TaiRemoteSettings settings = settings();
        if (settings == null) return;
        String baseUrl = settings.baseUrl();
        boolean hasServer = TaiRemoteClient.checkUrl(baseUrl).allowed();
        String model = settings.modelId();

        Preference url = findPreference(KEY_BASE_URL);
        if (url != null) {
            url.setSummary(baseUrl.isEmpty() ? getString(R.string.tai_remote_base_url_summary_none)
                : TaiRemoteClient.isUnencrypted(baseUrl)
                ? getString(R.string.tai_remote_base_url_summary_unencrypted, baseUrl) : baseUrl);
        }
        Preference key = findPreference(KEY_API_KEY);
        if (key != null) {
            key.setSummary(settings.hasApiKey() ? R.string.tai_remote_api_key_summary_set
                : R.string.tai_remote_api_key_summary_none);
        }
        Preference modelRow = findPreference(KEY_MODEL);
        if (modelRow != null) {
            modelRow.setEnabled(hasServer);
            if (fetchingModels) modelRow.setSummary(R.string.tai_remote_model_loading);
            else if (!hasServer) modelRow.setSummary(R.string.tai_remote_model_needs_server);
            else modelRow.setSummary(model.isEmpty() ? getString(R.string.tai_remote_model_summary_none) : model);
        }
        SwitchPreferenceCompat images = findPreference(KEY_IMAGES);
        if (images != null) {
            images.setVisible(!model.isEmpty());
            boolean understands = settings.understandsImages();
            images.setChecked(understands);
            String state = getString(understands ? R.string.tai_remote_images_yes : R.string.tai_remote_images_no);
            if (probing) images.setSummary(R.string.tai_remote_images_checking);
            else if (settings.imagesOverridden()) images.setSummary(getString(R.string.tai_remote_images_by_you, state));
            else if (settings.probedImages() != null) images.setSummary(getString(R.string.tai_remote_images_checked, state));
            else images.setSummary(getString(R.string.tai_remote_images_unchecked, state));
        }
        Preference routing = findPreference(KEY_ROUTING);
        if (routing != null) {
            routing.setSummary(settings.prefersRemote() ? R.string.tai_remote_routing_prefer_remote
                : R.string.tai_remote_routing_local_first);
        }
        Preference test = findPreference(KEY_TEST);
        if (test != null) {
            test.setSummary(testSummary != null ? testSummary : getString(R.string.tai_remote_test_summary));
        }
        Preference remove = findPreference(KEY_REMOVE);
        if (remove != null) remove.setEnabled(!baseUrl.isEmpty() || !model.isEmpty() || settings.hasApiKey());
    }

    // ---------------------------------------------------------------- server address

    private void showBaseUrlDialog(@NonNull Context context) {
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        LinearLayout layout = dialogLayout(context);

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.tai_remote_base_url_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(settings.baseUrl());
        layout.addView(input);

        TextView note = hint(context, null);
        layout.addView(note);

        ChipGroup presets = new ChipGroup(context);
        presets.setPadding(0, dp(context, 8), 0, 0);
        addPreset(context, presets, R.string.tai_remote_preset_openai, PRESET_OPENAI, input);
        addPreset(context, presets, R.string.tai_remote_preset_openrouter, PRESET_OPENROUTER, input);
        addPreset(context, presets, R.string.tai_remote_preset_local, PRESET_LOCAL, input);
        layout.addView(presets);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                showUrlNote(context, note, s.toString(), false);
            }
        });
        showUrlNote(context, note, input.getText().toString(), false);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tai_remote_base_url_title)
            .setView(scroll(context, layout))
            .setPositiveButton(R.string.termux_ai_dialog_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        // Save stays open on a refused address, so the user sees why and can fix it.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = TaiRemoteClient.normalizeBaseUrl(input.getText().toString());
            if (!value.isEmpty() && !TaiRemoteClient.checkUrl(value).allowed()) {
                showUrlNote(context, note, value, true);
                return;
            }
            settings.setBaseUrl(value);
            testSummary = null;
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    private void addPreset(@NonNull Context context, @NonNull ChipGroup group, int labelRes, @NonNull String url,
                           @NonNull EditText input) {
        Chip chip = new Chip(context);
        chip.setText(labelRes);
        chip.setOnClickListener(v -> {
            input.setText(url);
            input.setSelection(input.getText().length());
        });
        group.addView(chip);
    }

    /** The line under the address: the unencrypted note, or why the address is refused. */
    private void showUrlNote(@NonNull Context context, @NonNull TextView note, @NonNull String url, boolean afterSave) {
        TaiRemoteClient.UrlVerdict verdict = TaiRemoteClient.checkUrl(url);
        int text;
        switch (verdict) {
            case OK_UNENCRYPTED:
                text = R.string.tai_remote_url_unencrypted_note;
                break;
            case BAD_SCHEME:
                text = R.string.tai_remote_url_bad_scheme;
                break;
            case PUBLIC_HTTP:
                text = R.string.tai_remote_url_public_http;
                break;
            case MALFORMED:
                // While typing, an unfinished address is not an error yet.
                text = afterSave || looksFinished(url) ? R.string.tai_remote_url_malformed : 0;
                break;
            default:
                text = 0;
        }
        note.setVisibility(text == 0 ? View.GONE : View.VISIBLE);
        if (text != 0) note.setText(text);
    }

    private static boolean looksFinished(@NonNull String url) {
        return url.contains("://") && url.indexOf("://") + 3 < url.length() && url.contains(".");
    }

    // ---------------------------------------------------------------- API key

    /** Modelled on {@link TaiHuggingFaceTokenDialog}: masked field, Save, Cancel. */
    private void showApiKeyDialog(@NonNull Context context) {
        LinearLayout layout = dialogLayout(context);
        TextView message = new TextView(context);
        message.setText(R.string.tai_remote_api_key_dialog_message);
        layout.addView(message);

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.tai_remote_api_key_title);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String current = new TaiRemoteSettings(context).apiKey();
        input.setText(current == null ? "" : current);
        input.setSelectAllOnFocus(true);
        layout.addView(input);
        layout.addView(hint(context, getString(R.string.tai_remote_consent)));

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tai_remote_api_key_title)
            .setView(scroll(context, layout))
            .setPositiveButton(R.string.termux_ai_dialog_save, (d, w) -> {
                String key = input.getText().toString().trim();
                boolean saved = new TaiRemoteSettings(context).setApiKey(key);
                testSummary = null;
                if (!saved) AppNotice.show(context, R.string.tai_remote_api_key_save_failed, true);
                else if (!key.isEmpty()) AppNotice.show(context, R.string.tai_remote_api_key_saved, false);
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ---------------------------------------------------------------- model

    /** Fetches {@code /models} off the main thread, then the picker or the free-text field. */
    private void chooseModel(@NonNull Context context) {
        if (fetchingModels) return;
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        if (!TaiRemoteClient.checkUrl(settings.baseUrl()).allowed()) return;
        fetchingModels = true;
        refresh();
        Context app = context.getApplicationContext();
        runInBackground(() -> {
            TaiRemoteSettings background = new TaiRemoteSettings(app);
            TaiRemoteClient.ModelList list = new TaiRemoteClient(app, background.baseUrl(), background.apiKey()).listModels();
            handler.post(() -> {
                fetchingModels = false;
                Context ui = getContext();
                if (!isAdded() || ui == null) return;
                refresh();
                if (!list.ids.isEmpty()) showModelPicker(ui, list.ids);
                else if (list.freeText) showModelNameDialog(ui);
                else {
                    AppNotice.show(ui, getString(R.string.tai_remote_model_list_failed,
                        list.error == null ? "" : list.error), true);
                }
            });
        });
    }

    /** A searchable single-choice list over the server's model ids. */
    private void showModelPicker(@NonNull Context context, @NonNull List<String> ids) {
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        String current = new TaiRemoteSettings(context).modelId();

        LinearLayout layout = dialogLayout(context);
        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.tai_remote_model_search_hint);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        layout.addView(search);

        ListView list = new ListView(context);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
            android.R.layout.simple_list_item_single_choice, new ArrayList<>(sorted));
        list.setAdapter(adapter);
        // The list scrolls itself; a fixed height keeps the dialog's buttons on screen.
        layout.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 360)));
        Runnable markCurrent = () -> {
            list.clearChoices();
            int position = adapter.getPosition(current);
            if (position >= 0) list.setItemChecked(position, true);
        };
        markCurrent.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                // ArrayAdapter's own filter only matches the start of each word; search the whole id.
                adapter.setNotifyOnChange(false);
                adapter.clear();
                adapter.addAll(TaiRemoteClient.matchingModelIds(sorted, s));
                adapter.notifyDataSetChanged();
                markCurrent.run();
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tai_remote_model_title)
            .setView(layout)
            .setNeutralButton(R.string.tai_remote_model_type_instead, (d, w) -> showModelNameDialog(context))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            String picked = adapter.getItem(position);
            dialog.dismiss();
            if (picked != null) pickModel(context, picked);
        });
        dialog.show();
    }

    /** The fallback when the server lists no models: type the id it expects. */
    private void showModelNameDialog(@NonNull Context context) {
        LinearLayout layout = dialogLayout(context);
        TextView message = new TextView(context);
        message.setText(R.string.tai_remote_model_free_text_message);
        layout.addView(message);
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.tai_remote_model_free_text_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(new TaiRemoteSettings(context).modelId());
        layout.addView(input);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tai_remote_model_free_text_title)
            .setView(scroll(context, layout))
            .setPositiveButton(R.string.termux_ai_dialog_save, (d, w) -> {
                String model = input.getText().toString().trim();
                if (!model.isEmpty()) pickModel(context, model);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Saves the model and, in the background, asks it about images. */
    private void pickModel(@NonNull Context context, @NonNull String model) {
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        boolean changed = !model.equals(settings.modelId());
        settings.setModelId(model);
        testSummary = null;
        if (!changed && settings.probedImages() != null) {
            refresh();
            return;
        }
        probing = true;
        refresh();
        Context app = context.getApplicationContext();
        runInBackground(() -> {
            TaiRemoteSettings background = new TaiRemoteSettings(app);
            TaiRemoteClient.ImageVerdict verdict = new TaiRemoteClient(app, background.baseUrl(), background.apiKey())
                .probeImages(model);
            // A later pick replaced this model while the probe ran: its answer no longer applies.
            if (model.equals(background.modelId())) {
                background.setProbedImages(verdict == TaiRemoteClient.ImageVerdict.IMAGES ? Boolean.TRUE
                    : verdict == TaiRemoteClient.ImageVerdict.TEXT_ONLY ? Boolean.FALSE : null);
            }
            handler.post(() -> {
                probing = false;
                if (isAdded()) refresh();
            });
        });
    }

    /** The switch is the user's override; matching the probe again clears it. */
    private void setImagesOverride(boolean understands) {
        TaiRemoteSettings settings = settings();
        if (settings == null) return;
        Boolean probed = settings.probedImages();
        settings.setImagesOverride(probed != null && probed == understands ? null : understands);
        handler.post(this::refresh);
    }

    // ---------------------------------------------------------------- routing, test, remove

    private void showRoutingDialog(@NonNull Context context) {
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        String[] values = {TaiRemoteSettings.ROUTING_PREFER_REMOTE, TaiRemoteSettings.ROUTING_LOCAL_FIRST};
        CharSequence[] labels = {getString(R.string.tai_remote_routing_prefer_remote),
            getString(R.string.tai_remote_routing_local_first)};
        int checked = settings.prefersRemote() ? 0 : 1;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tai_remote_routing_title)
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                settings.setRouting(values[which]);
                d.dismiss();
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void testConnection(@NonNull Context context) {
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        if (!settings.isConfigured()) {
            testSummary = getString(R.string.tai_remote_test_needs_setup);
            refresh();
            return;
        }
        testSummary = getString(R.string.tai_remote_test_running);
        refresh();
        Context app = context.getApplicationContext();
        runInBackground(() -> {
            TaiRemoteSettings background = new TaiRemoteSettings(app);
            TaiRemoteClient.TestResult result = new TaiRemoteClient(app, background.baseUrl(), background.apiKey())
                .testConnection(background.modelId());
            handler.post(() -> {
                if (!isAdded()) return;
                testSummary = result.ok ? getString(R.string.tai_remote_test_ok, (int) result.latencyMs)
                    : (result.error == null ? "" : result.error);
                refresh();
            });
        });
    }

    private void confirmRemove(@NonNull Context context) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tai_remote_remove_confirm_title)
            .setMessage(R.string.tai_remote_remove_confirm_message)
            .setPositiveButton(R.string.tai_remote_remove_action, (d, w) -> {
                new TaiRemoteSettings(context).clearAll();
                testSummary = null;
                AppNotice.show(context, R.string.tai_remote_removed, false);
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ---------------------------------------------------------------- helpers

    private void runInBackground(@NonNull Runnable task) {
        try {
            worker.execute(task);
        } catch (RuntimeException ignored) {
            // The screen is closing; the work is no longer wanted.
        }
    }

    /** "gpt-4o on api.openai.com" for the row on the On-device AI page; {@code null} when unset. */
    @Nullable
    static String rowSummary(@NonNull Context context) {
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        if (!settings.isConfigured()) return null;
        String host;
        try {
            host = new URI(settings.baseUrl()).getHost();
        } catch (Exception e) {
            host = settings.baseUrl();
        }
        return context.getString(R.string.tai_remote_row_summary_on, settings.modelId(), host);
    }

    @NonNull
    private static LinearLayout dialogLayout(@NonNull Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(context, 24);
        layout.setPadding(padH, dp(context, 8), padH, 0);
        return layout;
    }

    @NonNull
    private static ScrollView scroll(@NonNull Context context, @NonNull View content) {
        // A custom view is not scrolled by the dialog; wrap it so the buttons stay reachable.
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        return scroll;
    }

    @NonNull
    private static TextView hint(@NonNull Context context, @Nullable CharSequence text) {
        TextView hint = new TextView(context);
        if (text != null) hint.setText(text);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        TypedValue color = new TypedValue();
        if (context.getTheme().resolveAttribute(com.termux.shared.R.attr.termuxColorOnSurfaceVariant, color, true)) {
            hint.setTextColor(color.data);
        }
        hint.setPadding(0, dp(context, 10), 0, 0);
        return hint;
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
