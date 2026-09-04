package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusCardPreference;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelImporter;
import com.termux.ai.TaiModelProfile;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.launcherctl.LauncherCtlApiServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class TaiPreferencesFragment extends MaterialPreferenceFragment {
    private static final String MODEL_ROW_PREFIX = "tai_model_row_";
    private static final String IMPORT_BACKEND_LITERT = TaiModelSpec.BACKEND_LITERT_LM;
    private static final String IMPORT_BACKEND_MNN = TaiModelSpec.BACKEND_MNN_LLM;

    private static final class OverrideSpec {
        final String key;
        final int titleRes;
        final int entriesRes;
        final int valuesRes;
        final String defaultValue;

        OverrideSpec(String key, int titleRes, int entriesRes, int valuesRes, String defaultValue) {
            this.key = key;
            this.titleRes = titleRes;
            this.entriesRes = entriesRes;
            this.valuesRes = valuesRes;
            this.defaultValue = defaultValue;
        }
    }

    private static final class ImportDraft {
        String backend = IMPORT_BACKEND_LITERT;
        String hfUrl = "";
        String hfToken = "";
        String modelId = "";
        Uri documentUri;
        TaiModelImporter.DocumentMetadata documentMetadata;
        // Modalities the user ticked at import time; survives the file-picker round-trip.
        final java.util.LinkedHashSet<String> capabilities = new java.util.LinkedHashSet<>();
        // Ordered like Gallery model configs: the first compatible accelerator is the default.
        final java.util.LinkedHashSet<String> compatibleAccelerators = new java.util.LinkedHashSet<>();
        String defaultAccelerator = "cpu";
        boolean acceleratorSelectionExplicit;

        ImportDraft() {
            compatibleAccelerators.add("cpu");
        }
    }

    private static final OverrideSpec[] OVERRIDE_SPECS = {
        new OverrideSpec("tai_max_tokens", R.string.termux_ai_max_tokens_title,
            R.array.termux_ai_max_tokens_entries, R.array.termux_ai_max_tokens_values, "auto"),
        new OverrideSpec("tai_top_k", R.string.termux_ai_top_k_title,
            R.array.termux_ai_top_k_entries, R.array.termux_ai_top_k_values, "auto"),
        new OverrideSpec("tai_top_p", R.string.termux_ai_top_p_title,
            R.array.termux_ai_top_p_entries, R.array.termux_ai_top_p_values, "auto"),
        new OverrideSpec("tai_temperature", R.string.termux_ai_temperature_title,
            R.array.termux_ai_temperature_entries, R.array.termux_ai_temperature_values, "auto"),
        new OverrideSpec("tai_accelerator", R.string.termux_ai_accelerator_title,
            R.array.termux_ai_accelerator_entries, R.array.termux_ai_accelerator_values, "auto"),
        new OverrideSpec("tai_thinking", R.string.termux_ai_thinking_title,
            R.array.termux_ai_auto_boolean_entries, R.array.termux_ai_auto_boolean_values, "auto"),
        new OverrideSpec("tai_speculative_decoding", R.string.termux_ai_speculative_decoding_title,
            R.array.termux_ai_auto_boolean_entries, R.array.termux_ai_auto_boolean_values, "auto"),
        new OverrideSpec("tai_idle_unload_minutes", R.string.termux_ai_idle_unload_title,
            R.array.termux_ai_idle_unload_entries, R.array.termux_ai_idle_unload_values, "10"),
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        this::onModelDocumentSelected);
    private ImportDraft pendingImportDraft;
    @Nullable private volatile JSONObject lastRuntimeStatus;
    private final ExecutorService runtimeActionExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "tai-settings-runtime");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable refreshRuntimeRunnable = new Runnable() {
        @Override
        public void run() {
            final Runnable self = this;
            final Context context = getContext();
            if (context == null || runtimeActionExecutor.isShutdown())
                return;
            // The runtime status is a blocking IPC; fetch it off the main thread, then apply the UI
            // update and decide whether to keep polling back on the main thread.
            runtimeActionExecutor.execute(() -> {
                final JSONObject status = fetchRuntimeStatusQuietly(context);
                handler.post(() -> {
                    if (!isAdded()) return;
                    Context ctx = getContext();
                    if (ctx == null) return;
                    applyTaiPage(ctx, status);
                    if (shouldContinueRefreshing(ctx, status)) {
                        handler.postDelayed(self, 2000L);
                    }
                });
            });
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_ai_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configureRuntimeControls(context);
        configureOverrides(context);
        configureEndpointPreferences(context);
        configureModelManager(context);
        configureHuggingFaceToken();
        configureAdvancedSection(context);
        configureLanToggle(context);
        configureAuthToggle(context);
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        Context context = getContext();
        String key = preference.getKey();
        if (context != null && key != null && preference instanceof EditTextPreference) {
            EditTextPreference editText = (EditTextPreference) preference;
            switch (key) {
                case TaiSettings.KEY_SYSTEM_PROMPT_GENERAL:
                    showGeneralPromptDialog(context, editText);
                    return;
                case "tai_mnn_custom_download":
                    showMnnCustomDownloadDialog(context, editText);
                    return;
                default:
                    break;
            }
        }
        super.onDisplayPreferenceDialog(preference);
    }

    private void showGeneralPromptDialog(Context context, EditTextPreference preference) {
        EditText input = buildDialogEditText(context, preference.getText(), InputType.TYPE_CLASS_TEXT, true);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_general_prompt_title)
            .setView(dialogScroll(context, wrapDialogView(context, null, input)))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) ->
                preference.setText(input.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.termux_ai_preferences_title);
            getActivity().getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        Context context = getContext();
        if (context != null) {
            refreshTaiPage(context);
            handler.postDelayed(refreshRuntimeRunnable, 2000L);
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshRuntimeRunnable);
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        runtimeActionExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setStaticSummary(String key, int summaryResId) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(summaryResId);
        }
    }

    private void refreshTaiPage(Context context) {
        if (runtimeActionExecutor.isShutdown())
            return;
        // The runtime status is a blocking IPC (it can wait on the TAI runtime process). Fetch it on
        // a background thread so the UI thread never stalls — that hang was ANR-ing the settings page.
        runtimeActionExecutor.execute(() -> {
            final JSONObject status = fetchRuntimeStatusQuietly(context);
            handler.post(() -> {
                if (!isAdded()) return;
                Context ctx = getContext();
                if (ctx == null) return;
                applyTaiPage(ctx, status);
            });
        });
    }

    /** Apply a (possibly null) pre-fetched runtime status plus the non-blocking page bits, on the UI thread. */
    private void applyTaiPage(Context context, @Nullable JSONObject runtimeStatus) {
        lastRuntimeStatus = runtimeStatus;
        updateRuntimeStatus(context, runtimeStatus);
        refreshOverrides();
        refreshEndpointPreferences(context);
        populateModelRows(context);
        refreshLanToggle(context);
    }

    /** Blocking runtime-status fetch; returns null instead of throwing/blocking the caller's UI. */
    @Nullable
    private JSONObject fetchRuntimeStatusQuietly(Context context) {
        try {
            return TaiManager.getInstance(context).runtimeStatus();
        } catch (Exception e) {
            return null;
        }
    }

    private void configureRuntimeControls(Context context) {
        TaiRuntimeActionsPreference actions = findPreference("tai_runtime_actions");
        if (actions == null) return;
        actions.setOnActionClickListener(new TaiRuntimeActionsPreference.OnActionClickListener() {
            @Override
            public void onStop() {
                cancelGeneration(context);
            }

            @Override
            public void onUnload() {
                unloadRuntime(context);
            }

            @Override
            public void onLogs() {
                showRuntimeLogs(context);
            }
        });
    }

    private void configureOverrides(Context context) {
        TaiOverridesPreference overrides = findPreference("tai_runtime_overrides");
        if (overrides == null) return;
        overrides.setOnOverrideClickListener(index -> showOverrideDialog(context, index));
        refreshOverrides();
    }

    private void refreshOverrides() {
        TaiOverridesPreference overrides = findPreference("tai_runtime_overrides");
        if (overrides == null) return;
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        List<TaiOverridesPreference.Item> items = new ArrayList<>();
        for (OverrideSpec spec : OVERRIDE_SPECS) {
            String value = preferences.getString(spec.key, spec.defaultValue);
            items.add(new TaiOverridesPreference.Item(getString(spec.titleRes), overrideValueLabel(spec.key, value)));
        }
        overrides.setItems(items);
    }

    private String overrideValueLabel(String key, String value) {
        if (value == null || value.isEmpty()) return "auto";
        if ("tai_accelerator".equals(key) || TaiSettings.FIELD_ACCELERATOR.equals(key)) {
            return "auto".equals(value) ? "profile" : value;
        }
        if ("tai_idle_unload_minutes".equals(key)) {
            return "0".equals(value) ? "off" : value + " min";
        }
        if ("tai_thinking".equals(key) || "tai_speculative_decoding".equals(key)
            || TaiSettings.FIELD_ENABLE_THINKING.equals(key)
            || TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(key)) {
            if ("true".equals(value)) return "on";
            if ("false".equals(value)) return "off";
            return "auto";
        }
        return value;
    }

    private void showOverrideDialog(Context context, int index) {
        if (index < 0 || index >= OVERRIDE_SPECS.length) return;
        OverrideSpec spec = OVERRIDE_SPECS[index];
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        String[] entries = getResources().getStringArray(spec.entriesRes);
        String[] values = getResources().getStringArray(spec.valuesRes);
        String current = preferences.getString(spec.key, spec.defaultValue);
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(spec.titleRes)
            .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                preferences.edit().putString(spec.key, values[which]).apply();
                dialog.dismiss();
                refreshOverrides();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void configureEndpointPreferences(Context context) {
        // Port/token editing, randomize and recreate all live inside the OpenAI endpoint dialog now.
        Preference endpointCopy = findPreference("tai_endpoint_copy");
        if (endpointCopy != null) {
            endpointCopy.setOnPreferenceClickListener(preference -> {
                showEndpointAccessDialog(context);
                return true;
            });
        }
        refreshEndpointPreferences(context);
    }

    private void configureAdvancedSection(Context context) {
        Preference parameters = findPreference("tai_parameters_defaults");
        if (parameters != null) {
            parameters.setOnPreferenceClickListener(preference -> {
                openParameterScreen(null);
                return true;
            });
        }
    }

    /**
     * Single cohesive endpoint dialog: OpenAI base URL and bearer token, each with its own copy
     * affordance (the token also reveals/hides in place), plus the on-disk file locations. Replaces
     * the previous nested reveal dialog and the scattered copy-from-row/copy-from-port-dialog paths.
     */
    private void showEndpointAccessDialog(Context context) {
        JSONObject endpoint;
        try {
            endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_endpoint_update_failed, true);
            return;
        }
        String baseUrl = endpoint.optString("baseUrl", "");
        String endpointFile = endpoint.optString("endpointFile", "~/.launcherctl/endpoint.json");
        String tokenFile = endpoint.optString("tokenFile", "~/.launcherctl/token");
        String initialToken = endpoint.optString("token", "");
        if (initialToken.isEmpty()) initialToken = new TaiSettings(context).getOrCreateApiToken();
        // Mutable holders so the Randomize/Recreate actions can update the values shown in place.
        final String[] url = { endpoint.optString("openAiBaseUrl", baseUrl.isEmpty() ? "" : baseUrl + "/v1") };
        final String[] token = { initialToken };
        final boolean[] revealed = {false};

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);

        TextView urlView = endpointValueView(context, url[0]);
        layout.addView(endpointRow(context, getString(R.string.termux_ai_endpoint_field_base_url), urlView,
            endpointButton(context, R.string.termux_ai_dialog_copy,
                () -> copyToClipboard(context, url[0], R.string.termux_ai_base_url_copied))));

        TextView tokenView = endpointValueView(context, TaiSettings.redactToken(token[0]));
        Button revealButton = endpointButton(context, R.string.termux_ai_dialog_reveal, null);
        revealButton.setOnClickListener(v -> {
            revealed[0] = !revealed[0];
            tokenView.setText(revealed[0] ? token[0] : TaiSettings.redactToken(token[0]));
            revealButton.setText(revealed[0] ? R.string.termux_ai_dialog_hide : R.string.termux_ai_dialog_reveal);
        });
        Button tokenCopy = endpointButton(context, R.string.termux_ai_dialog_copy,
            () -> copyToClipboard(context, token[0], R.string.termux_ai_api_token_copied));
        layout.addView(endpointRow(context, getString(R.string.termux_ai_endpoint_field_token), tokenView,
            revealButton, tokenCopy));

        Button randomizePort = endpointButton(context, R.string.termux_ai_endpoint_randomize_port, () -> {
            try {
                LauncherCtlApiServer.getInstance().randomizeApiPortFromSettings(context);
                JSONObject ep = LauncherCtlApiServer.getInstance().endpointSettings(context);
                String b = ep.optString("baseUrl", "");
                url[0] = ep.optString("openAiBaseUrl", b.isEmpty() ? "" : b + "/v1");
                urlView.setText(url[0]);
                refreshEndpointPreferences(context);
                AppNotice.show(context, R.string.termux_ai_api_port_randomized, false);
            } catch (JSONException e) {
                AppNotice.show(context, R.string.termux_ai_endpoint_update_failed, true);
            }
        });
        Button recreateToken = endpointButton(context, R.string.termux_ai_endpoint_recreate_token, () -> {
            try {
                JSONObject ep = LauncherCtlApiServer.getInstance().rotateAuthTokenFromSettings(context)
                    .optJSONObject("endpoint");
                String fresh = ep == null ? "" : ep.optString("token", "");
                token[0] = fresh.isEmpty() ? new TaiSettings(context).getOrCreateApiToken() : fresh;
                tokenView.setText(revealed[0] ? token[0] : TaiSettings.redactToken(token[0]));
                refreshEndpointPreferences(context);
                AppNotice.show(context, R.string.termux_ai_api_token_rotated, false);
            } catch (JSONException e) {
                AppNotice.show(context, R.string.termux_ai_endpoint_update_failed, true);
            }
        });
        layout.addView(endpointRow(context, getString(R.string.termux_ai_endpoint_manage_label), null,
            randomizePort, recreateToken));

        TextView files = new TextView(context);
        files.setText(getString(R.string.termux_ai_endpoint_files_footnote, endpointFile, tokenFile));
        files.setTextIsSelectable(true);
        files.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        files.setPadding(0, Math.round(10 * density), 0, Math.round(4 * density));
        layout.addView(files);

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_endpoint_access_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private TextView endpointValueView(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextIsSelectable(true);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        return view;
    }

    private Button endpointButton(Context context, int textRes, @Nullable Runnable action) {
        Button button = new Button(context, null, android.R.attr.borderlessButtonStyle);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        int padH = Math.round(8 * context.getResources().getDisplayMetrics().density);
        button.setPadding(padH, 0, padH, 0);
        button.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorPrimary));
        if (action != null) button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout endpointRow(Context context, String label, @Nullable TextView valueView, Button... buttons) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, Math.round(8 * density), 0, 0);
        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        labelView.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        column.addView(labelView);
        if (valueView != null) column.addView(valueView);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        for (Button button : buttons) actions.addView(button);
        column.addView(actions);
        return column;
    }

    private int resolveAttrColor(int attr) {
        TypedValue value = new TypedValue();
        if (getContext() != null && getContext().getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return 0xFF000000;
    }

    private void copyToClipboard(Context context, String text, int toastResId) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Termux Launcher", text));
        AppNotice.show(context, toastResId, false);
    }

    private EditText buildDialogEditText(Context context, String value, int inputType, boolean multiline) {
        EditText input = new EditText(context);
        input.setInputType(inputType | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setMinLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        if (value != null) {
            input.setText(value);
            input.setSelection(value.length());
        }
        return input;
    }

    /**
     * Wraps dialog content so the button bar stays reachable. AlertDialog scrolls its message text
     * but never a custom view, so a tall layout — the import flow stacks seventeen rows — pushes
     * the buttons off the bottom of the screen. Content that already fits is unaffected.
     */
    private ScrollView dialogScroll(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout wrapDialogView(Context context, CharSequence header, View input) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);
        if (header != null) {
            TextView headerView = new TextView(context);
            headerView.setText(header);
            headerView.setTextIsSelectable(true);
            headerView.setTypeface(Typeface.MONOSPACE);
            headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            headerView.setPadding(0, 0, 0, Math.round(12 * density));
            layout.addView(headerView);
        }
        layout.addView(input);
        return layout;
    }

    private TextView buildTokenHintView(Context context, int textRes) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView hint = new TextView(context);
        hint.setText(textRes);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        hint.setPadding(0, Math.round(10 * density), 0, 0);
        return hint;
    }

    private void refreshEndpointPreferences(Context context) {
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            EditTextPreference port = findPreference(TaiSettings.KEY_API_PORT);
            if (port != null) {
                port.setText(String.valueOf(endpoint.optInt("configuredPort", TaiSettings.DEFAULT_API_PORT)));
                port.setSummary(context.getString(R.string.termux_ai_api_port_summary,
                    endpoint.optString("openAiBaseUrl", "")));
            }
            EditTextPreference token = findPreference(TaiSettings.KEY_API_TOKEN);
            if (token != null) {
                String endpointToken = endpoint.optString("token", new TaiSettings(context).getOrCreateApiToken());
                token.setText(endpointToken);
                token.setSummary(TaiSettings.redactToken(endpointToken));
            }
            Preference endpointCopy = findPreference("tai_endpoint_copy");
            if (endpointCopy != null) {
                endpointCopy.setSummary(getString(R.string.termux_ai_endpoint_notice_summary_dynamic,
                    endpoint.optString("openAiBaseUrl", ""),
                    endpoint.optString("tokenFile", "~/.launcherctl/token")));
            }
        } catch (JSONException e) {
            // Endpoint settings unavailable; leave existing summaries in place.
        }
    }


    private void configureLanToggle(Context context) {
        SwitchPreferenceCompat lanToggle = findPreference("tai_lan_enabled");
        if (lanToggle == null) return;
        lanToggle.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (Boolean) newValue;
            if (enabled) {
                showLanWarningDialog(context, lanToggle);
                return false;
            }
            new TaiSettings(context).setApiBindMode(TaiSettings.BIND_MODE_LOCALHOST);
            try {
                LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
                refreshEndpointPreferences(context);
            } catch (JSONException e) {
                AppNotice.show(context, R.string.termux_ai_endpoint_update_failed, true);
            }
            return true;
        });
    }

    private void refreshLanToggle(Context context) {
        SwitchPreferenceCompat lanToggle = findPreference("tai_lan_enabled");
        if (lanToggle == null) return;
        String bindMode = new TaiSettings(context).getApiBindMode();
        lanToggle.setChecked(TaiSettings.BIND_MODE_LAN.equals(bindMode));
    }

    private void configureAuthToggle(Context context) {
        SwitchPreferenceCompat authToggle = findPreference(TaiSettings.KEY_API_AUTH_REQUIRED);
        if (authToggle == null) return;
        authToggle.setChecked(new TaiSettings(context).isApiAuthRequired());
        authToggle.setOnPreferenceChangeListener((preference, newValue) -> {
            new TaiSettings(context).setApiAuthRequired((Boolean) newValue);
            try {
                LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
                refreshEndpointPreferences(context);
            } catch (JSONException e) {
                AppNotice.show(context, R.string.termux_ai_endpoint_update_failed, true);
            }
            return true;
        });
    }

    private void showLanWarningDialog(Context context, SwitchPreferenceCompat lanToggle) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_lan_warning_title)
            .setMessage(R.string.termux_ai_lan_warning_message)
            .setPositiveButton(R.string.termux_ai_dialog_enable, (dialog, which) -> {
                lanToggle.setChecked(true);
                new TaiSettings(context).setApiBindMode(TaiSettings.BIND_MODE_LAN);
                try {
                    LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
                    refreshEndpointPreferences(context);
                } catch (JSONException e) {
                    AppNotice.show(context, R.string.termux_ai_endpoint_update_failed, true);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showMnnCustomDownloadDialog(Context context, EditTextPreference preference) {
        EditText input = buildDialogEditText(context, preference.getText(),
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false);

        TextView warning = new TextView(context);
        warning.setText(R.string.termux_ai_mnn_custom_download_warning);
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        int padH = Math.round(24 * context.getResources().getDisplayMetrics().density);
        warning.setPadding(0, Math.round(12 * context.getResources().getDisplayMetrics().density), 0, 0);

        LinearLayout layout = wrapDialogView(context, null, input);
        layout.addView(warning);

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_mnn_custom_download_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!url.startsWith("https://")) {
                    AppNotice.show(context, R.string.termux_ai_mnn_custom_download_invalid_url, true);
                    return;
                }
                startMnnCustomDownload(context, url);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void startMnnCustomDownload(Context context, String url) {
        String modelId = deriveModelIdFromUrl(url);
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                JSONObject request = new JSONObject();
                request.put("modelId", modelId);
                request.put("url", url);
                request.put("acceptedTerms", true);
                JSONArray capabilities = new JSONArray();
                capabilities.put(TaiModelSpec.CAPABILITY_TEXT_CHAT);
                request.put("capabilities", capabilities);
                result = TaiManager.getInstance(context.getApplicationContext()).downloadModel(request.toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    AppNotice.show(currentContext, R.string.termux_ai_model_download_started, false);
                    handler.removeCallbacks(refreshRuntimeRunnable);
                    handler.postDelayed(refreshRuntimeRunnable, 1000L);
                } else {
                    String message = finalResult == null
                        ? currentContext.getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", currentContext.getString(R.string.termux_ai_model_action_failed));
                    AppNotice.show(currentContext, message, true);
                }
                refreshTaiPage(currentContext);
            });
        });
    }

    private String deriveModelIdFromUrl(String url) {
        try {
            // For a .../resolve/main/<file> URL the file name (config.json) is useless as an id;
            // use the repo name instead. For a bare repo URL the last segment is the repo name.
            int resolve = url == null ? -1 : url.indexOf("/resolve/");
            String basis = resolve > 0 ? url.substring(0, resolve) : url;
            String lastSegment = Uri.parse(basis).getLastPathSegment();
            if (lastSegment != null && !lastSegment.isEmpty()) {
                String sanitized = TaiModelImporter.sanitizeModelId(
                    TaiModelImporter.stripModelExtension(lastSegment));
                if (!sanitized.isEmpty()) return sanitized;
            }
        } catch (Exception ignored) {
        }
        return "custom-mnn-model";
    }

    private String buildModelRowSummary(String baseSummary, String backend) {
        String backendLabel;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) {
            backendLabel = getString(R.string.termux_ai_backend_label_mnn);
        } else {
            backendLabel = getString(R.string.termux_ai_backend_label_litert);
        }
        return baseSummary + " · " + backendLabel;
    }

    private void updateRuntimeStatus(Context context, @Nullable JSONObject runtimeStatus) {
        Preference status = findPreference("tai_runtime_status");
        TaiRuntimeActionsPreference actions = findPreference("tai_runtime_actions");
        if (status == null && actions == null) return;
        try {
            if (runtimeStatus == null) throw new JSONException("runtime status unavailable");
            JSONObject runtime = runtimeStatus.getJSONObject("runtime");
            boolean activeGeneration = runtime.optBoolean("activeGeneration", false);
            boolean loaded = runtime.optBoolean("loaded", false);
            String state = runtime.optString("state", "unloaded");
            boolean loading = "loading".equals(state);
            boolean stopping = "stopping".equals(state);
            if (status != null) {
                if (status instanceof StatusCardPreference) {
                    ((StatusCardPreference) status).setStatus(
                        "RUNTIME · " + state.toUpperCase(Locale.US), loaded || activeGeneration);
                }
                status.setSummary(buildRuntimeCardBody(context, runtime, runtimeStatus));
            }
            if (actions != null) {
                actions.setActionStates(
                    activeGeneration || loading,
                    (loaded || loading) && !activeGeneration && !stopping,
                    true);
            }
        } catch (JSONException e) {
            if (status != null) status.setSummary(R.string.termux_ai_runtime_status_summary);
        }
    }

    private String buildRuntimeCardBody(Context context, JSONObject runtime, JSONObject runtimeStatus) {
        StringBuilder body = new StringBuilder();
        JSONObject device = runtimeStatus.optJSONObject("device");
        if (device != null) {
            StringBuilder deviceLine = new StringBuilder(device.optString("model", "unknown"));
            if (!device.isNull("memoryGiB")) {
                deviceLine.append(" · ").append(String.format(Locale.US, "%.1f GiB", device.optDouble("memoryGiB")));
            }
            appendKv(body, "device", deviceLine.toString());
            appendKv(body, "accel", join(device.optJSONArray("phase1Accelerators")));
        }
        // Engine availability (folded in from the former standalone "Device & engine info" row).
        TaiDeviceCapabilities caps = TaiDeviceCapabilities.detect(context);
        boolean liteRtOk = caps.liteRtLmAbiSupported && caps.liteRtLmNativeLibrariesAvailable;
        StringBuilder engine = new StringBuilder("litert-lm ")
            .append(liteRtOk ? "ok" : "unavailable")
            .append(" · mnn-llm ")
            .append(caps.mnnSupported ? "ok" : "unavailable");
        if (!caps.mnnSupported && caps.mnnUnsupportedReason != null) {
            engine.append(" (").append(caps.mnnUnsupportedReason).append(')');
        }
        appendKv(body, "engine", engine.toString());
        appendKv(body, "model", nullable(runtime, "loadedModelId", "none"));
        appendKv(body, "backend", runtime.optString("backend", "none"));
        String fallback = nullable(runtime, "backendFallbackReason", "");
        if (!fallback.isEmpty()) appendKv(body, "fallback", fallback);
        if (runtime.optBoolean("activeGeneration", false)) appendKv(body, "generate", "active");
        String runtimeProcess = runtimeStatus.optString("runtimeProcess", "");
        if (!runtimeProcess.isEmpty()) appendKv(body, "process", runtimeProcess);
        long keepWarmRemaining = runtime.optLong("keepWarmRemainingMs", 0L);
        if (keepWarmRemaining > 0L) appendKv(body, "warm", formatDuration(keepWarmRemaining));
        long idleRemaining = runtime.optLong("idleUnloadRemainingMs", 0L);
        if (idleRemaining > 0L) appendKv(body, "idle", formatDuration(idleRemaining));
        String statusMessage = runtime.optString("status", "");
        if (!statusMessage.isEmpty()) appendKv(body, "status", statusMessage);
        JSONObject profile = runtimeStatus.optJSONObject("modelProfile");
        if (profile != null) {
            StringBuilder compat = new StringBuilder(join(profile.optJSONArray("compatibleAccelerators")));
            if (!profile.isNull("minDeviceMemoryInGb")) {
                compat.append(" · min ").append(profile.optInt("minDeviceMemoryInGb")).append(" GiB");
            }
            appendKv(body, "compat", compat.toString());
        }
        JSONArray warnings = runtimeStatus.optJSONArray("compatibilityWarnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                String warning = warnings.optString(i, "");
                if (!warning.isEmpty()) appendKv(body, "warning", warning);
            }
        }
        JSONObject crash = runtimeStatus.optJSONObject("lastRuntimeCrash");
        if (crash != null) {
            String model = crash.optString("modelId", "");
            String accelerator = crash.optString("accelerator", "");
            appendKv(body, "last", "AI runtime crashed while loading " + (model.isEmpty() ? "a model" : model)
                + (accelerator.isEmpty() ? "" : " on " + accelerator));
            appendKv(body, "fallback", crash.optString("suggestedFallback", "Try CPU or a smaller model."));
        }
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            String baseUrl = endpoint.optString("openAiBaseUrl", "");
            String token = endpoint.optString("token", "");
            if (!baseUrl.isEmpty()) appendKv(body, "endpoint", baseUrl);
            if (!token.isEmpty()) appendKv(body, "token", TaiSettings.redactToken(token));
        } catch (JSONException ignored) {
        }
        return body.toString().trim();
    }

    private void appendKv(StringBuilder builder, String key, String value) {
        builder.append(String.format(Locale.US, "%-9s", key)).append(value).append('\n');
    }

    private void configureModelManager(Context context) {
        Preference browseCatalog = findPreference("tai_models_browse_catalog");
        if (browseCatalog != null) {
            browseCatalog.setSummary(R.string.termux_ai_models_browse_catalog_summary);
        }

        Preference importModel = findPreference("tai_model_import");
        if (importModel != null) {
            importModel.setSummary(R.string.termux_ai_model_import_summary);
            importModel.setOnPreferenceClickListener(preference -> {
                showImportFlowDialog(context, new ImportDraft());
                return true;
            });
        }

        Preference refresh = findPreference("tai_models_refresh");
        if (refresh != null) {
            refresh.setOnPreferenceClickListener(preference -> {
                refreshTaiPage(context);
                return true;
            });
        }
        refreshTaiPage(context);
    }

    private void cancelGeneration(Context context) {
        Context appContext = context.getApplicationContext();
        runRuntimeAction(
            () -> TaiManager.getInstance(appContext).cancelRuntime(),
            R.string.termux_ai_runtime_cancel_requested);
    }

    private void unloadRuntime(Context context) {
        Context appContext = context.getApplicationContext();
        runRuntimeAction(
            () -> TaiManager.getInstance(appContext).unloadModel(),
            R.string.termux_ai_runtime_unloaded);
    }

    private void runRuntimeAction(RuntimeAction action, int successResId) {
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                result = action.run();
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                if (finalResult == null) {
                    AppNotice.show(currentContext, R.string.termux_ai_runtime_action_failed, true);
                } else {
                    toastRuntimeResult(currentContext, finalResult, successResId);
                }
                refreshTaiPage(currentContext);
            });
        });
    }

    private interface RuntimeAction {
        JSONObject run() throws JSONException;
    }

    private void toastRuntimeResult(Context context, JSONObject result, int successResId) {
        if (result.optBoolean("loadCancellationRequested", false)) {
            AppNotice.show(context,
                result.optString("message", context.getString(R.string.termux_ai_runtime_cancel_requested)), true);
        } else if (result.optBoolean("ok", false)) {
            AppNotice.show(context, successResId, false);
        } else {
            AppNotice.show(context, result.optString("message", context.getString(R.string.termux_ai_runtime_action_failed)), true);
        }
    }

    private void configureHuggingFaceToken() {
        Preference token = findPreference(TaiSettings.KEY_HUGGINGFACE_TOKEN);
        if (token == null) return;
        updateHuggingFaceTokenSummary(token);
        token.setOnPreferenceClickListener(preference -> {
            Context context = getContext();
            if (context == null) return true;
            showHuggingFaceTokenDialog(context, token);
            return true;
        });
    }

    private void updateHuggingFaceTokenSummary(@NonNull Preference preference) {
        String value = preference.getContext()
            .getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TaiSettings.KEY_HUGGINGFACE_TOKEN, "");
        preference.setSummary(value == null || value.trim().isEmpty()
            ? getString(R.string.termux_ai_huggingface_token_summary)
            : getString(R.string.termux_ai_huggingface_token_set_summary));
    }

    private void showHuggingFaceTokenDialog(Context context, Preference preference) {
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, 0, padding, 0);

        TextView message = new TextView(context);
        message.setText(R.string.termux_ai_huggingface_token_dialog_message);
        layout.addView(message);

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.termux_ai_huggingface_token_title);
        input.setText(context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TaiSettings.KEY_HUGGINGFACE_TOKEN, ""));
        input.setSelectAllOnFocus(true);
        layout.addView(input);

        layout.addView(buildTokenHintView(context, R.string.termux_ai_huggingface_token_permissions_hint));
        layout.addView(buildTokenHintView(context, R.string.termux_ai_huggingface_token_gated_hint));

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_huggingface_token_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(TaiSettings.KEY_HUGGINGFACE_TOKEN, input.getText().toString().trim())
                    .apply();
                updateHuggingFaceTokenSummary(preference);
                AppNotice.show(context, R.string.termux_ai_huggingface_token_saved, false);
            })
            .setNeutralButton(R.string.termux_ai_huggingface_token_get_action,
                (dialog, which) -> openUrl(context, "https://huggingface.co/settings/tokens"))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void populateModelRows(Context context) {
        PreferenceCategory category = findPreference("tai_models_category");
        if (category == null) return;

        for (int i = category.getPreferenceCount() - 1; i >= 0; i--) {
            Preference preference = category.getPreference(i);
            if (preference != null && preference.getKey() != null && preference.getKey().startsWith(MODEL_ROW_PREFIX)) {
                category.removePreference(preference);
            }
        }

        TaiModelStore store = new TaiModelStore(context);
        store.pruneMissingUserModels();
        Map<String, TaiModelSpec> installedModels = store.getInstalledUserModels();
        Preference empty = findPreference("tai_models_empty");
        if (empty != null) empty.setVisible(installedModels.isEmpty());
        String activeModelId = new TaiSettings(context).getDefaultAssistantModel();
        String loadedId = loadedModelId();

        for (TaiModelSpec model : installedModels.values()) {
            TaiModelPreference row = new TaiModelPreference(context);
            row.setKey(MODEL_ROW_PREFIX + model.id);
            row.setTitle(model.displayName);
            row.setSummary(buildModelRowSummary(model.roleHint, model.backend));
            row.setMetaLine(buildInstalledMetaLine(context, model));
            boolean modelLoaded = model.id.equals(loadedId);
            String pill;
            if (modelLoaded) {
                pill = getString(R.string.termux_ai_model_state_loaded);
            } else if (model.id.equals(activeModelId)) {
                pill = getString(R.string.termux_ai_model_pill_active);
            } else {
                pill = getString(R.string.termux_ai_model_pill_installed);
            }
            row.setPill(pill, model.id.equals(activeModelId));
            row.setBackendTone(TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)
                ? TaiModelPreference.BackendTone.MNN : TaiModelPreference.BackendTone.LITERT);
            configureProgress(row, null);
            row.setPersistent(false);
            row.setOnPreferenceClickListener(preference -> {
                showInstalledModelActions(context, model);
                return true;
            });
            category.addPreference(row);
        }
    }

    private CharSequence buildInstalledMetaLine(Context context, TaiModelSpec model) {
        String accel = null;
        Integer minMemGb = model.recommendedRamGb > 0 ? model.recommendedRamGb : null;
        try {
            TaiModelProfile profile = TaiModelProfile.forModel(model);
            accel = joinAccelerators(profile.compatibleAccelerators);
            if (profile.minDeviceMemoryInGb != null) minMemGb = profile.minDeviceMemoryInGb;
        } catch (Exception ignored) {
        }
        CharSequence meta = buildMetaLine(context, model.sizeBytes, accel, minMemGb);
        // Multimodal LiteRT models are exposed over the API as modality-scoped ids
        // (<id>, <id>-vision, <id>-audio); surface which modes the shell can select.
        boolean image = model.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)
            || model.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        boolean audio = model.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT)
            || model.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend) && (image || audio)
            && meta instanceof SpannableStringBuilder) {
            StringBuilder modes = new StringBuilder("chat");
            if (image) modes.append(" · vision");
            if (audio) modes.append(" · audio");
            SpannableStringBuilder builder = (SpannableStringBuilder) meta;
            int keyColor = resolveAttrColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
            int valueColor = resolveAttrColor(context, com.termux.shared.R.attr.termuxColorOnSurface);
            builder.append("   ");
            appendMeta(builder, "modes ", modes.toString(), keyColor, valueColor);
        }
        return meta;
    }

    private CharSequence buildMetaLine(Context context, long sizeBytes, String accel, Integer minMemGb) {
        int keyColor = resolveAttrColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
        int valueColor = resolveAttrColor(context, com.termux.shared.R.attr.termuxColorOnSurface);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        appendMeta(builder, "size ", formatBytes(sizeBytes), keyColor, valueColor);
        if (accel != null && !accel.isEmpty()) {
            builder.append("   ");
            appendMeta(builder, "accel ", accel, keyColor, valueColor);
        }
        if (minMemGb != null) {
            builder.append("   ");
            appendMeta(builder, "min mem ", minMemGb + " GiB", keyColor, valueColor);
        }
        return builder;
    }

    private void appendMeta(SpannableStringBuilder builder, String key, String value, int keyColor, int valueColor) {
        int start = builder.length();
        builder.append(key);
        builder.setSpan(new ForegroundColorSpan(keyColor), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        start = builder.length();
        builder.append(value);
        builder.setSpan(new ForegroundColorSpan(valueColor), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String joinAccelerators(List<String> accelerators) {
        if (accelerators == null || accelerators.isEmpty()) return null;
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < accelerators.size(); i++) {
            if (i > 0) joined.append(" · ");
            joined.append(String.valueOf(accelerators.get(i)).toLowerCase(Locale.US));
        }
        return joined.toString();
    }

    private int resolveAttrColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        if (context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, value, true)) {
            return value.data;
        }
        return 0;
    }

    private void configureProgress(TaiModelPreference row, JSONObject download) {
        if (download == null) {
            row.setDownloadProgress(false, false, 0);
            return;
        }
        String status = download.optString("status", "");
        boolean active = TaiModelStore.STATE_QUEUED.equals(status)
            || TaiModelStore.STATE_DOWNLOADING.equals(status)
            || TaiModelStore.STATE_VERIFYING.equals(status);
        long bytesRead = download.optLong("bytesRead", 0L);
        long totalBytes = download.optLong("totalBytes", 0L);
        row.setDownloadProgress(active, totalBytes <= 0L, totalBytes > 0L ? (int) (bytesRead * 10000L / totalBytes) : 0);
    }

    private String buildModelSummary(TaiModelCatalog.CatalogEntry entry, TaiModelSpec installed, JSONObject download) {
        StringBuilder summary = new StringBuilder();
        summary.append(entry.roleHint).append(" - ").append(formatBytes(entry.sizeBytes));
        summary.append("\nBackend: ").append(entry.backend).append(" - ").append(entry.format);
        if (entry.quantization != null) summary.append(" - ").append(entry.quantization);
        if (entry.recommendedRamGb > 0) summary.append("\nRecommended memory: ").append(entry.recommendedRamGb).append(" GiB");
        TaiModelSpec catalogModel = new TaiModelRegistry().getModel(entry.modelId);
        if (catalogModel != null && TaiModelSpec.BACKEND_LITERT_LM.equals(entry.backend)) {
            TaiModelProfile profile = TaiModelProfile.forModel(catalogModel);
            summary.append("\nAccelerators: ").append(profile.compatibleAccelerators.toString());
            if (profile.minDeviceMemoryInGb != null) {
                summary.append(" - minimum memory ").append(profile.minDeviceMemoryInGb).append(" GiB");
            }
        }
        if (entry.gated) {
            summary.append(" - gated");
        }
        if (installed != null && installed.localPath != null) {
            summary.append("\nInstalled: ").append(formatBytes(installed.sizeBytes));
            summary.append("\n").append(installed.localPath);
        } else if (download != null) {
            summary.append("\nDownload: ").append(download.optString("status", "unknown"));
            long bytesRead = download.optLong("bytesRead", 0L);
            long totalBytes = download.optLong("totalBytes", 0L);
            if (totalBytes > 0) {
                summary.append(" ").append(formatPercent(bytesRead, totalBytes));
            }
            String error = download.optString("error", "");
            if (!error.isEmpty()) {
                summary.append("\n").append(error);
            }
        } else {
            summary.append("\nNot installed");
        }
        return summary.toString();
    }

    private String join(JSONArray values) {
        if (values == null || values.length() == 0) return "none";
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            if (i > 0) joined.append(", ");
            joined.append(values.optString(i, ""));
        }
        return joined.toString();
    }

    private String buildInstalledModelSummary(TaiModelSpec model) {
        StringBuilder summary = new StringBuilder();
        summary.append(model.roleHint).append(" - ").append(model.source);
        summary.append("\nBackend: ").append(model.backend).append(" - ").append(model.format);
        if (model.quantization != null) summary.append(" - ").append(model.quantization);
        if (model.recommendedRamGb > 0) summary.append("\nRecommended memory: ").append(model.recommendedRamGb).append(" GiB");
        if (model.localPath != null) {
            summary.append("\nInstalled: ").append(formatBytes(model.sizeBytes));
            summary.append("\n").append(model.localPath);
        }
        summary.append("\nEndpoint capabilities: ").append(model.endpointCapabilities.toString());
        if (!model.sourceCapabilities.equals(model.endpointCapabilities)) {
            summary.append("\nSource capabilities: ").append(model.sourceCapabilities.toString());
        }
        summary.append("\nEndpoint context: ").append(model.endpointContextWindow);
        if (model.sourceContextWindow != model.endpointContextWindow) {
            summary.append(" · source ").append(model.sourceContextWindow);
        }
        try {
            TaiModelProfile profile = TaiModelProfile.forModel(model);
            summary.append("\nAccelerators: ").append(profile.compatibleAccelerators.toString());
            if (profile.minDeviceMemoryInGb != null) {
                summary.append(" - minimum memory ").append(profile.minDeviceMemoryInGb).append(" GiB");
            }
            summary.append("\nDefaults: ").append(model.defaultMaxOutputTokens).append(" max output tokens, temperature ")
                .append(profile.defaultTemperature);
        } catch (Exception ignored) {
        }
        return summary.toString();
    }

    private void showModelActions(Context context, TaiModelCatalog.CatalogEntry entry, TaiModelSpec installed, JSONObject download) {
        if (download != null && (TaiModelStore.STATE_QUEUED.equals(download.optString("status"))
            || TaiModelStore.STATE_DOWNLOADING.equals(download.optString("status"))
            || TaiModelStore.STATE_VERIFYING.equals(download.optString("status")))) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(entry.displayName)
                .setMessage(buildModelSummary(entry, installed, download))
                .setPositiveButton(R.string.termux_ai_model_download_cancel, (dialog, which) -> cancelModelDownload(context, entry.modelId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
        if (installed != null) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(entry.displayName)
                .setMessage(buildModelSummary(entry, installed, download))
                .setPositiveButton(R.string.termux_ai_model_load_action, (dialog, which) -> loadModel(context, entry.modelId))
                .setNeutralButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, entry.modelId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        if (entry.gated) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.termux_ai_model_gated_title)
                .setMessage(context.getString(R.string.termux_ai_model_gated_message, entry.displayName))
                .setPositiveButton(R.string.termux_ai_model_download_start, (dialog, which) -> startCatalogDownload(context, entry))
                .setNeutralButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.termux_ai_model_download_title, entry.displayName))
            .setMessage(context.getString(R.string.termux_ai_model_download_message,
                entry.displayName, formatBytes(entry.sizeBytes), entry.license))
            .setPositiveButton(R.string.termux_ai_model_download_start, (dialog, which) -> startCatalogDownload(context, entry))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Nullable
    private String loadedModelId() {
        JSONObject status = lastRuntimeStatus;
        if (status == null) return null;
        try {
            JSONObject runtime = status.getJSONObject("runtime");
            if (runtime.optBoolean("loaded", false) || "loading".equals(runtime.optString("state", ""))) {
                String id = runtime.optString("loadedModelId", "");
                return id.isEmpty() ? null : id;
            }
        } catch (JSONException ignored) {
        }
        return null;
    }

    private boolean isModelLoaded(@Nullable String modelId) {
        if (modelId == null) return false;
        return modelId.equals(loadedModelId());
    }

    private void showInstalledModelActions(Context context, TaiModelSpec model) {
        boolean active = model.id.equals(new TaiSettings(context).getDefaultAssistantModel());
        boolean loaded = isModelLoaded(model.id);
        String stateLine = loaded
            ? getString(R.string.termux_ai_model_state_loaded)
            : active
                ? getString(R.string.termux_ai_model_pill_active)
                : getString(R.string.termux_ai_model_pill_installed);
        CharSequence[] actions = new CharSequence[] {
            getString(R.string.termux_ai_model_load_action),
            active ? getString(R.string.termux_ai_model_active_action) : getString(R.string.termux_ai_model_set_active_action),
            getString(R.string.termux_ai_model_tune_action),
            loaded ? getString(R.string.termux_ai_model_delete_action_loaded) : getString(R.string.termux_ai_model_delete_action)
        };
        new MaterialAlertDialogBuilder(context)
            .setTitle(model.displayName)
            .setMessage(buildInstalledModelSummary(model) + "\n" + getString(R.string.termux_ai_model_state_label, stateLine))
            .setItems(actions, (dialog, which) -> {
                if (which == 0) loadModel(context, model.id);
                else if (which == 1 && !active) setActiveModel(context, model.id);
                else if (which == 2) openParameterScreen(model);
                else if (which == 3) {
                    if (loaded) {
                        AppNotice.show(context, R.string.termux_ai_model_delete_loaded_warning, true);
                    } else {
                        confirmDeleteModel(context, model);
                    }
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openParameterScreen(@Nullable TaiModelSpec model) {
        TaiParameterPreferencesFragment fragment = new TaiParameterPreferencesFragment();
        if (model != null) fragment.setArguments(TaiParameterPreferencesFragment.argumentsForModel(model));
        getParentFragmentManager().beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit();
    }

    private void setActiveModel(Context context, String modelId) {
        TaiModelSpec model = new TaiModelStore(context).getInstalledUserModels().get(modelId);
        TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(context);
        if (model != null && TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend) && !capabilities.mnnSupported) {
            String reason = capabilities.mnnUnsupportedReason;
            AppNotice.show(context, reason == null ? context.getString(R.string.termux_ai_mnn_runtime_pending) : reason, true);
            return;
        }
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        preferences.edit().putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, modelId).apply();
        AppNotice.show(context, R.string.termux_ai_model_active_saved, false);
        refreshTaiPage(context);
    }

    private void confirmDeleteModel(Context context, TaiModelSpec model) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.termux_ai_model_delete_title, model.displayName))
            .setMessage(R.string.termux_ai_model_delete_message)
            .setPositiveButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, model.id))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showRuntimeLogs(Context context) {
        if (runtimeActionExecutor.isShutdown())
            return;
        // runtimeStatus() blocks on the runtime IPC — fetch off the main thread, show the dialog on it.
        runtimeActionExecutor.execute(() -> {
            String status;
            try {
                status = redactRuntimeDebugJson(context, TaiManager.getInstance(context).runtimeStatus().toString(2));
            } catch (Exception e) {
                status = null;
            }
            final String body = status;
            handler.post(() -> {
                if (!isAdded() || getContext() == null) return;
                Context ctx = getContext();
                if (body == null) {
                    AppNotice.show(ctx, R.string.termux_ai_runtime_action_failed, true);
                    return;
                }
                new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.termux_ai_runtime_logs_title)
                    .setMessage(body)
                    .setPositiveButton(R.string.termux_ai_dialog_copy, (dialog, which) ->
                        copyToClipboard(ctx, body, R.string.termux_ai_runtime_logs_copied))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        });
    }

    private String redactRuntimeDebugJson(Context context, String body) {
        String redacted = body == null ? "" : body;
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            String token = endpoint.optString("token", "");
            if (!token.isEmpty()) redacted = redacted.replace(token, TaiSettings.redactToken(token));
        } catch (JSONException ignored) {
        }
        String hfToken = new TaiSettings(context).getHuggingFaceToken();
        if (!hfToken.trim().isEmpty()) redacted = redacted.replace(hfToken, TaiSettings.redactToken(hfToken));
        return redacted;
    }

    private void startCatalogDownload(Context context, TaiModelCatalog.CatalogEntry entry) {
        try {
            JSONObject result = TaiManager.getInstance(context).downloadCatalogModel(entry.modelId);
            if (result.optBoolean("ok", false)) {
                AppNotice.show(context, R.string.termux_ai_model_download_started, false);
                handler.removeCallbacks(refreshRuntimeRunnable);
                handler.postDelayed(refreshRuntimeRunnable, 1000L);
            } else {
                AppNotice.show(context, result.optString("message", context.getString(R.string.termux_ai_model_action_failed)), true);
            }
            refreshTaiPage(context);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
    }

    private void cancelModelDownload(Context context, String modelId) {
        try {
            JSONObject result = TaiManager.getInstance(context).cancelDownload(
                new JSONObject().put("modelId", modelId).toString());
            AppNotice.show(context, result.optBoolean("ok", false)
                ? R.string.termux_ai_model_download_cancelled
                : R.string.termux_ai_model_action_failed, false);
            refreshTaiPage(context);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
    }

    private void onModelDocumentSelected(Uri uri) {
        if (uri == null) return;
        Context context = getContext();
        if (context == null) return;
        ImportDraft draft = pendingImportDraft == null ? new ImportDraft() : pendingImportDraft;
        TaiModelImporter.DocumentMetadata metadata =
            TaiManager.getInstance(context).modelDocumentMetadata(uri);
        TaiModelImporter.ValidationResult validation =
            TaiModelImporter.validateImportFileNameForBackend(draft.backend, metadata.displayName);
        if (!validation.supported) {
            AppNotice.show(context, validation.message, true);
            return;
        }
        draft.documentUri = uri;
        draft.documentMetadata = metadata;
        if (draft.modelId == null || draft.modelId.trim().isEmpty()) {
            draft.modelId = TaiModelImporter.sanitizeModelId(
                TaiModelImporter.stripModelExtension(metadata.displayName));
        }
        fillGuessedImportCapabilities(draft.capabilities, metadata.displayName);
        if (!draft.acceleratorSelectionExplicit) {
            fillGuessedImportAccelerators(draft, metadata.displayName);
        }
        pendingImportDraft = draft;
        showImportFlowDialog(context, draft);
    }

    private void showImportFlowDialog(Context context, ImportDraft draft) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);

        // The dialog's own message, carried inside the scrolled view rather than passed to
        // setMessage(). AlertDialogLayout only gives one panel the height left over from the
        // buttons, and a dialog holding both a message and a custom view has two, so it falls back
        // to plain LinearLayout measurement — which let this flow's seventeen rows push Import and
        // Cancel off the bottom of the screen. One panel, and the buttons stay put.
        TextView dialogMessage = new TextView(context);
        dialogMessage.setText(R.string.termux_ai_model_import_dialog_message);
        dialogMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dialogMessage.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        dialogMessage.setPadding(0, 0, 0, Math.round(
            12 * context.getResources().getDisplayMetrics().density));
        layout.addView(dialogMessage);

        // Backend is auto-detected: a Hugging Face URL resolves to LiteRT or MNN from the repo's
        // files; a local file is always a LiteRT package. No manual toggle.
        layout.addView(importDialogLabel(context, R.string.termux_ai_import_source_label, 0));
        EditText hfUrlInput = new EditText(context);
        hfUrlInput.setSingleLine(true);
        hfUrlInput.setHint(R.string.termux_ai_model_import_hf_url_field_hint);
        hfUrlInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        hfUrlInput.setText(draft.hfUrl == null ? "" : draft.hfUrl);
        layout.addView(hfUrlInput);

        if (draft.capabilities.isEmpty()) {
            fillGuessedImportCapabilities(draft.capabilities,
                draft.hfUrl == null || draft.hfUrl.trim().isEmpty()
                    ? (draft.documentMetadata == null ? "" : draft.documentMetadata.displayName)
                    : draft.hfUrl);
        }

        TextView modalityLabel = new TextView(context);
        modalityLabel.setText(R.string.termux_ai_model_import_modalities_label);
        modalityLabel.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorPrimary));
        modalityLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        modalityLabel.setPadding(0, padding / 2, 0, 0);
        layout.addView(modalityLabel);

        CheckBox chat = new CheckBox(context);
        chat.setText(R.string.termux_ai_import_cap_chat);
        chat.setChecked(draft.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        CheckBox embeddings = new CheckBox(context);
        embeddings.setText(R.string.termux_ai_import_cap_embeddings);
        embeddings.setChecked(draft.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        CheckBox image = new CheckBox(context);
        image.setText(R.string.termux_ai_import_cap_image);
        image.setChecked(draft.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        CheckBox audio = new CheckBox(context);
        audio.setText(R.string.termux_ai_import_cap_audio);
        audio.setChecked(draft.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        CheckBox tools = new CheckBox(context);
        tools.setText(R.string.termux_ai_import_cap_tools);
        tools.setChecked(draft.capabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
        CheckBox code = new CheckBox(context);
        code.setText(R.string.termux_ai_import_cap_code);
        code.setChecked(draft.capabilities.contains(TaiModelSpec.CAPABILITY_CODE));
        CheckBox reasoning = new CheckBox(context);
        reasoning.setText(R.string.termux_ai_import_cap_reasoning);
        reasoning.setChecked(draft.capabilities.contains("reasoning"));
        CheckBox multilingual = new CheckBox(context);
        multilingual.setText(R.string.termux_ai_import_cap_multilingual);
        multilingual.setChecked(draft.capabilities.contains("multilingual"));
        layout.addView(chat);
        layout.addView(embeddings);
        layout.addView(image);
        layout.addView(audio);
        layout.addView(tools);
        layout.addView(code);
        layout.addView(reasoning);
        layout.addView(multilingual);

        layout.addView(importDialogLabel(context, R.string.termux_ai_import_accelerators_label, 8));
        CheckBox gpuCompatible = new CheckBox(context);
        gpuCompatible.setText(R.string.termux_ai_import_gpu_compatible);
        gpuCompatible.setChecked(draft.compatibleAccelerators.contains("gpu"));
        layout.addView(gpuCompatible);
        Spinner defaultAccelerator = new Spinner(context);
        android.widget.ArrayAdapter<CharSequence> acceleratorAdapter = new android.widget.ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item,
            new CharSequence[]{getString(R.string.termux_ai_import_default_cpu),
                getString(R.string.termux_ai_import_default_gpu)});
        acceleratorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        defaultAccelerator.setAdapter(acceleratorAdapter);
        defaultAccelerator.setSelection("gpu".equals(draft.defaultAccelerator) ? 1 : 0);
        layout.addView(defaultAccelerator);
        gpuCompatible.setOnTouchListener((view, event) -> {
            draft.acceleratorSelectionExplicit = true;
            return false;
        });
        defaultAccelerator.setOnTouchListener((view, event) -> {
            draft.acceleratorSelectionExplicit = true;
            return false;
        });
        gpuCompatible.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && defaultAccelerator.getSelectedItemPosition() == 1) {
                defaultAccelerator.setSelection(0);
            }
        });
        defaultAccelerator.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                if (position == 1) gpuCompatible.setChecked(true);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        boolean rawTflite = draft.documentMetadata != null
            && draft.documentMetadata.displayName.toLowerCase(Locale.ROOT).endsWith(".tflite");
        if (rawTflite) {
            chat.setChecked(false);
            chat.setEnabled(false);
            embeddings.setChecked(true);
            image.setChecked(false); image.setEnabled(false);
            audio.setChecked(false); audio.setEnabled(false);
            tools.setChecked(false); tools.setEnabled(false);
            code.setChecked(false); code.setEnabled(false);
            reasoning.setChecked(false); reasoning.setEnabled(false);
            multilingual.setChecked(false); multilingual.setEnabled(false);
        }

        hfUrlInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                LinkedHashSet<String> guessed = new LinkedHashSet<>();
                fillGuessedImportCapabilities(guessed, s == null ? "" : s.toString());
                chat.setChecked(guessed.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
                embeddings.setChecked(guessed.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
                image.setChecked(guessed.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
                audio.setChecked(guessed.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
                tools.setChecked(guessed.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
                code.setChecked(guessed.contains(TaiModelSpec.CAPABILITY_CODE));
                reasoning.setChecked(guessed.contains("reasoning"));
                multilingual.setChecked(guessed.contains("multilingual"));
                if (!draft.acceleratorSelectionExplicit) {
                    fillGuessedImportAccelerators(draft, s == null ? "" : s.toString());
                    gpuCompatible.setChecked(draft.compatibleAccelerators.contains("gpu"));
                    defaultAccelerator.setSelection("gpu".equals(draft.defaultAccelerator) ? 1 : 0);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        EditText modelIdInput = new EditText(context);
        modelIdInput.setSingleLine(true);
        modelIdInput.setHint(R.string.termux_ai_model_import_id_hint);
        modelIdInput.setInputType(InputType.TYPE_CLASS_TEXT);
        modelIdInput.setText(draft.modelId == null ? "" : draft.modelId);
        modelIdInput.setSelectAllOnFocus(true);
        layout.addView(modelIdInput);

        // Tap-to-set Hugging Face token (needed for gated/private repos).
        TextView tokenLine = new TextView(context);
        tokenLine.setPadding(0, padding / 2, 0, padding / 2);
        tokenLine.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorPrimary));
        Runnable refreshTokenLine = () -> tokenLine.setText(
            new TaiSettings(context).getHuggingFaceToken().trim().isEmpty()
                ? context.getString(R.string.termux_ai_model_import_token_unset)
                : context.getString(R.string.termux_ai_model_import_token_set));
        refreshTokenLine.run();
        tokenLine.setOnClickListener(v -> promptHuggingFaceToken(context, refreshTokenLine));
        layout.addView(tokenLine);

        TextView selectedFile = new TextView(context);
        selectedFile.setText(importSelectionText(context, draft));
        selectedFile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        selectedFile.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        selectedFile.setPadding(0, padding / 2, 0, 0);
        layout.addView(selectedFile);

        Runnable captureModalities = () -> {
            draft.capabilities.clear();
            if (chat.isChecked()) draft.capabilities.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
            if (embeddings.isChecked()) draft.capabilities.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
            if (image.isChecked()) draft.capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
            if (audio.isChecked()) draft.capabilities.add(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
            if (tools.isChecked()) draft.capabilities.add(TaiModelSpec.CAPABILITY_TOOL_USE);
            if (code.isChecked()) draft.capabilities.add(TaiModelSpec.CAPABILITY_CODE);
            if (reasoning.isChecked()) draft.capabilities.add("reasoning");
            if (multilingual.isChecked()) draft.capabilities.add("multilingual");
            if (isQwenThinkingImport(draft)) {
                draft.capabilities.add("reasoning");
                draft.capabilities.add(TaiModelSpec.CAPABILITY_LLM_THINKING);
            }
            draft.compatibleAccelerators.clear();
            draft.defaultAccelerator = defaultAccelerator.getSelectedItemPosition() == 1 ? "gpu" : "cpu";
            draft.compatibleAccelerators.add(draft.defaultAccelerator);
            if (gpuCompatible.isChecked()) draft.compatibleAccelerators.add("gpu");
            draft.compatibleAccelerators.add("cpu");
        };

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_model_import_dialog_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(R.string.termux_ai_model_import_verify_action, null)
            .setNeutralButton(R.string.termux_ai_model_import_choose_file, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show();
        Button positive = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        positive.setOnClickListener(view -> {
            draft.backend = IMPORT_BACKEND_LITERT; // local files are LiteRT; URLs auto-detect downstream
            String hfUrl = hfUrlInput.getText().toString().trim();
            draft.hfUrl = hfUrl;
            draft.hfToken = hfUrl.isEmpty() ? "" : new TaiSettings(context).getHuggingFaceToken();
            draft.modelId = modelIdInput.getText().toString().trim();
            captureModalities.run();
            if (draft.capabilities.isEmpty()) {
                AppNotice.show(context, R.string.termux_ai_import_no_capability, true);
                return;
            }
            if (startImportDraft(context, draft)) dialog.dismiss();
        });
        Button neutral = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
        neutral.setOnClickListener(view -> {
            draft.backend = IMPORT_BACKEND_LITERT;
            draft.hfUrl = "";
            draft.hfToken = "";
            draft.modelId = modelIdInput.getText().toString().trim();
            captureModalities.run();
            if (draft.capabilities.isEmpty()) {
                AppNotice.show(context, R.string.termux_ai_import_no_capability, true);
                return;
            }
            pendingImportDraft = draft;
            modelPicker.launch(new String[]{"application/octet-stream", "application/json", "*/*"});
            dialog.dismiss();
        });
    }

    private TextView importDialogLabel(Context context, int textRes, int topPaddingDp) {
        TextView label = new TextView(context);
        label.setText(textRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorPrimary));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        int top = Math.round(topPaddingDp * context.getResources().getDisplayMetrics().density);
        label.setPadding(0, top, 0, 0);
        return label;
    }

    private void fillGuessedImportCapabilities(@NonNull LinkedHashSet<String> capabilities, @Nullable String source) {
        capabilities.clear();
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        boolean embedding = value.contains("embeddinggemma-300m")
            || value.contains("qwen3-embedding-0.6b-mnn")
            || value.contains("qwen3-embedding-4b-mnn")
            || value.contains("qwen3-embedding-8b-mnn")
            || value.contains("bge-")
            || value.contains("e5-")
            || value.contains("gte-")
            || value.contains("jina-embedding")
            || value.contains("embed");
        if (embedding) capabilities.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
        else capabilities.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        boolean gemma4 = value.contains("gemma-4-e2b") || value.contains("gemma-4-e4b");
        if (value.contains("-vl") || value.contains("_vl") || value.contains("vision")
            || value.contains("image") || value.contains("multimodal") || gemma4) {
            capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        }
        if (value.contains("audio") || value.contains("speech") || gemma4) {
            capabilities.add(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        }
        if (value.contains("functiongemma") || value.contains("function-calling")
            || value.contains("tool-use") || value.contains("tool_use")) {
            capabilities.add(TaiModelSpec.CAPABILITY_TOOL_USE);
        }
        if (value.contains("coder") || value.contains("code-")) capabilities.add(TaiModelSpec.CAPABILITY_CODE);
        if (value.contains("deepseek-r1") || value.contains("reasoning")) capabilities.add("reasoning");
        if (value.contains("qwen3") && value.contains("thinking")) {
            capabilities.add("reasoning");
            capabilities.add(TaiModelSpec.CAPABILITY_LLM_THINKING);
        }
        if (value.contains("qwen") || value.contains("multilingual")) capabilities.add("multilingual");
    }

    private void fillGuessedImportAccelerators(@NonNull ImportDraft draft, @Nullable String source) {
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        boolean mnn = value.contains("-mnn") || value.contains("_mnn") || value.endsWith("config.json");
        boolean knownGpu = !mnn && ((value.contains("qwen3") && value.contains("thinking"))
            || value.contains("gemma-4-e2b") || value.contains("gemma-4-e4b")
            || value.contains("deepseek-r1-distill-qwen-1.5b")
            || value.contains("qwen2.5-1.5b-instruct"));
        draft.compatibleAccelerators.clear();
        draft.defaultAccelerator = knownGpu ? "gpu" : "cpu";
        draft.compatibleAccelerators.add(draft.defaultAccelerator);
        if (knownGpu) draft.compatibleAccelerators.add("cpu");
    }

    private boolean isQwenThinkingImport(@NonNull ImportDraft draft) {
        String identity = (draft.modelId + " " + draft.hfUrl + " "
            + (draft.documentMetadata == null ? "" : draft.documentMetadata.displayName))
            .toLowerCase(Locale.ROOT);
        return identity.contains("qwen3") && identity.contains("thinking");
    }

    @NonNull
    private TaiModelProfile importRuntimeProfile(@NonNull ImportDraft draft) {
        ArrayList<String> accelerators = new ArrayList<>(draft.compatibleAccelerators);
        TaiModelProfile defaults = new TaiModelProfile(java.util.Collections.singletonList("cpu"),
            1024, 64, 0.95d, 1.0d, null, "edge-gallery-import-default");
        if (isQwenThinkingImport(draft)) {
            defaults = new TaiModelProfile(java.util.Arrays.asList("gpu", "cpu"), 2048, 64,
                0.95d, 1.0d, 3, TaiModelProfile.SOURCE_LITERT_COMMUNITY,
                TaiModelProfile.THINKING_ALWAYS, "<think>", "</think>");
        }
        return new TaiModelProfile(accelerators, defaults.defaultMaxTokens, defaults.defaultTopK,
            defaults.defaultTopP, defaults.defaultTemperature, defaults.minDeviceMemoryInGb,
            "import-dialog-selection", defaults.thinkingMode, defaults.thinkingChannelStart,
            defaults.thinkingChannelEnd);
    }

    private CharSequence importSelectionText(Context context, ImportDraft draft) {
        if (draft.documentMetadata == null) {
            return context.getString(R.string.termux_ai_model_import_no_file_selected);
        }
        TaiModelImporter.DocumentMetadata metadata = draft.documentMetadata;
        return context.getString(R.string.termux_ai_model_import_selected,
            metadata.displayName, metadata.sizeBytes > 0L ? formatBytes(metadata.sizeBytes) : "unknown size");
    }

    /** Prompt for and persist a Hugging Face token (for gated/private repos); runs onSaved after. */
    private void promptHuggingFaceToken(Context context, @Nullable Runnable onSaved) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.termux_ai_huggingface_token_title);
        input.setText(new TaiSettings(context).getHuggingFaceToken());
        input.setSelectAllOnFocus(true);
        LinearLayout layout = wrapDialogView(context,
            context.getString(R.string.termux_ai_huggingface_token_dialog_message), input);
        layout.addView(buildTokenHintView(context, R.string.termux_ai_huggingface_token_permissions_hint));
        layout.addView(buildTokenHintView(context, R.string.termux_ai_huggingface_token_gated_hint));
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_huggingface_token_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) -> {
                new TaiSettings(context).setHuggingFaceToken(input.getText().toString().trim());
                if (onSaved != null) onSaved.run();
            })
            .setNeutralButton(R.string.termux_ai_huggingface_token_get_action,
                (dialog, which) -> openUrl(context, "https://huggingface.co/settings/tokens"))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private boolean startImportDraft(Context context, ImportDraft draft) {
        boolean hasUrl = draft.hfUrl != null && !draft.hfUrl.trim().isEmpty();
        boolean hasFile = draft.documentUri != null && draft.documentMetadata != null;
        if (hasUrl == hasFile) {
            AppNotice.show(context, R.string.termux_ai_model_import_choose_one_source, true);
            return false;
        }
        if (hasUrl) {
            TaiModelImporter.ValidationResult validation =
                TaiModelImporter.validateHuggingFaceImportUrl(draft.hfUrl);
            if (!validation.supported) {
                AppNotice.show(context, validation.message, true);
                return false;
            }
            startHuggingFaceImport(context, draft);
            return true;
        }
        // Local file: only LiteRT packages can be imported from storage (MNN comes from a URL).
        TaiModelImporter.ValidationResult validation = TaiModelImporter.validateImportFileNameForBackend(
            IMPORT_BACKEND_LITERT, draft.documentMetadata.displayName);
        if (!validation.supported) {
            AppNotice.show(context, validation.message, true);
            return false;
        }
        importModelDocument(context, draft.documentUri, draft.modelId, IMPORT_BACKEND_LITERT,
            draft.capabilities, importRuntimeProfile(draft));
        pendingImportDraft = null;
        return true;
    }

    private void startHuggingFaceImport(Context context, ImportDraft draft) {
        String modelId = TaiModelImporter.sanitizeModelId(draft.modelId == null || draft.modelId.trim().isEmpty()
            ? deriveModelIdFromUrl(draft.hfUrl) : draft.modelId);
        if (modelId.isEmpty()) {
            AppNotice.show(context, R.string.termux_ai_model_import_invalid_model_id, true);
            return;
        }
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                JSONObject request = new JSONObject();
                request.put("modelId", modelId);
                request.put("displayName", modelId);
                request.put("url", draft.hfUrl);
                request.put("acceptedTerms", true);
                // Backend/format are auto-detected by downloadModel from the resolved file.
                JSONArray capabilities = new JSONArray();
                for (String capability : draft.capabilities) capabilities.put(capability);
                request.put("capabilities", capabilities);
                request.put("runtimeProfile", importRuntimeProfile(draft).toJson());
                if (draft.hfToken != null && !draft.hfToken.trim().isEmpty()) {
                    request.put("huggingFaceToken", draft.hfToken.trim());
                }
                result = TaiManager.getInstance(context.getApplicationContext()).downloadModel(request.toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    AppNotice.show(currentContext, R.string.termux_ai_model_download_started, false);
                    handler.removeCallbacks(refreshRuntimeRunnable);
                    handler.postDelayed(refreshRuntimeRunnable, 1000L);
                } else if (finalResult != null && "gated_model_requires_auth".equals(finalResult.optString("error"))) {
                    // Gated/private repo: prompt for a token, then retry the same import.
                    new MaterialAlertDialogBuilder(currentContext)
                        .setTitle(R.string.termux_ai_huggingface_token_title)
                        .setMessage(finalResult.optString("message", currentContext.getString(R.string.termux_ai_model_import_gated_message)))
                        .setPositiveButton(R.string.termux_ai_huggingface_token_title,
                            (d, w) -> promptHuggingFaceToken(currentContext, () -> {
                                draft.hfToken = new TaiSettings(currentContext).getHuggingFaceToken();
                                startHuggingFaceImport(currentContext, draft);
                            }))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                } else {
                    String message = finalResult == null
                        ? currentContext.getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", currentContext.getString(R.string.termux_ai_model_action_failed));
                    AppNotice.show(currentContext, message, true);
                }
                pendingImportDraft = null;
                refreshTaiPage(currentContext);
            });
        });
    }

    private void importModelDocument(Context context, Uri uri, String modelId, String backend,
                                     java.util.Set<String> capabilities,
                                     TaiModelProfile runtimeProfile) {
        Context appContext = context.getApplicationContext();
        Preference importPreference = findPreference("tai_model_import");
        if (importPreference != null) {
            importPreference.setEnabled(false);
            importPreference.setSummary(R.string.termux_ai_model_import_copying);
        }
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                result = new TaiModelImporter(appContext, new TaiModelStore(appContext))
                    .importDocument(uri, modelId, backend, capabilities, runtimeProfile);
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                Preference currentImportPreference = findPreference("tai_model_import");
                if (currentImportPreference != null) {
                    currentImportPreference.setEnabled(true);
                    currentImportPreference.setSummary(R.string.termux_ai_model_import_summary);
                }
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    AppNotice.show(currentContext, R.string.termux_ai_model_imported, false);
                } else {
                    String message = finalResult == null
                        ? currentContext.getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", currentContext.getString(R.string.termux_ai_model_action_failed));
                    AppNotice.show(currentContext, message, true);
                }
                refreshTaiPage(currentContext);
            });
        });
    }

    private void loadModel(Context context, String modelId) {
        try {
            JSONObject request = new JSONObject();
            request.put("model", modelId);
            JSONObject result = TaiManager.getInstance(context).loadModel(request.toString());
            toastRuntimeResult(context, result, R.string.termux_ai_model_loaded);
            refreshTaiPage(context);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_runtime_action_failed, true);
        }
    }

    private void deleteModel(Context context, String modelId) {
        try {
            JSONObject request = new JSONObject();
            request.put("modelId", modelId);
            request.put("confirm", true);
            JSONObject result = TaiManager.getInstance(context).deleteModel(request.toString());
            AppNotice.show(context,
                result.optBoolean("deleted", false) ? R.string.termux_ai_model_deleted : R.string.termux_ai_model_delete_missing, false);
            refreshTaiPage(context);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
    }

    private JSONObject findDownload(JSONArray downloads, String modelId) {
        if (downloads == null) return null;
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && modelId.equals(item.optString("modelId", ""))) {
                return item;
            }
        }
        return null;
    }

    private boolean hasActiveDownloads(Context context) {
        JSONArray downloads = new TaiModelStore(context).getDownloads();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item == null) continue;
            String status = item.optString("status", "");
            if (TaiModelStore.STATE_QUEUED.equals(status)
                || TaiModelStore.STATE_DOWNLOADING.equals(status)
                || TaiModelStore.STATE_VERIFYING.equals(status)) return true;
        }
        return false;
    }

    private boolean shouldContinueRefreshing(Context context, @Nullable JSONObject runtimeStatus) {
        if (hasActiveDownloads(context)) return true;
        try {
            if (runtimeStatus == null) return false;
            JSONObject runtime = runtimeStatus.getJSONObject("runtime");
            return runtime.optBoolean("activeGeneration", false)
                || runtime.optBoolean("loaded", false)
                || runtime.optLong("keepWarmRemainingMs", 0L) > 0L
                || runtime.optLong("idleUnloadRemainingMs", 0L) > 0L;
        } catch (JSONException e) {
            return false;
        }
    }

    private String formatPercent(long value, long total) {
        if (total <= 0) return "";
        double percent = (double) value * 100.0 / (double) total;
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "unknown size";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes > 0L) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    private String nullable(JSONObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) return fallback;
        return object.optString(key, fallback);
    }

    private void openUrl(Context context, String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            AppNotice.show(context, url, true);
        }
    }
}
