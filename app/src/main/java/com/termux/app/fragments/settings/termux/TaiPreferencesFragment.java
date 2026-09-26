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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusCardPreference;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.launcherctl.LauncherCtlApiServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class TaiPreferencesFragment extends MaterialPreferenceFragment implements TaiDownloadHub.Listener {

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
    private final ExecutorService runtimeActionExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "tai-settings-runtime");
        thread.setDaemon(true);
        return thread;
    });
    /** Model count for the Model centre row; re-read only when a download changes status. */
    private int installedCount = -1;
    @NonNull private String downloadStatuses = "";
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
        configureModelCentreRow();
        configureHuggingFaceToken();
        configureAdvancedSection(context);
        configureLanToggle(context);
        configureAuthToggle(context);
        // A deep link from another page (the keyboard's voice rows) lands on one category.
        Bundle arguments = getArguments();
        String scrollTo = arguments == null ? null
            : arguments.getString(com.termux.app.activities.SettingsActivity.EXTRA_SCROLL_TO_KEY);
        if (scrollTo != null) scrollToPreference(scrollTo);
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
    public void onStart() {
        super.onStart();
        Context context = getContext();
        // The Model centre row's summary and progress line follow the hub's pushes; no polling.
        if (context != null) TaiDownloadHub.get(context).addListener(this);
    }

    @Override
    public void onStop() {
        Context context = getContext();
        if (context != null) TaiDownloadHub.get(context).removeListener(this);
        super.onStop();
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
        updateRuntimeStatus(context, runtimeStatus);
        refreshOverrides();
        refreshEndpointPreferences(context);
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
     * but never a custom view, so a tall layout (the token dialog on a small phone) pushes the
     * buttons off the bottom of the screen. Content that already fits is unaffected.
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

    private void configureModelCentreRow() {
        Preference centre = findPreference("tai_model_centre");
        if (centre == null) return;
        centre.setOnPreferenceClickListener(preference -> {
            TaiModelCentreFragment.open(getActivity(), TaiModelCentreFragment.SEGMENT_INSTALLED);
            return true;
        });
    }

    /** Keeps the Model centre row's "N installed · M downloading" and its progress line current. */
    @Override
    public void onDownloadsChanged(@NonNull List<TaiDownloadHub.Snapshot> downloads) {
        Context context = getContext();
        TaiModelCentreRowPreference row = findPreference("tai_model_centre");
        if (context == null || row == null) return;
        StringBuilder statuses = new StringBuilder();
        List<TaiModelCentreRows.Input> inputs = new ArrayList<>(downloads.size());
        for (TaiDownloadHub.Snapshot item : downloads) {
            inputs.add(TaiModelCentreRows.Input.of(item));
            statuses.append(item.id).append('=').append(item.status).append(';');
        }
        // A finished download changes the installed count; a progress tick does not, so the
        // store is read only when some status moved.
        if (installedCount < 0 || !statuses.toString().equals(downloadStatuses)) {
            installedCount = new TaiModelStore(context).getInstalledUserModels().size();
            downloadStatuses = statuses.toString();
        }
        TaiModelCentreRows.Summary summary = TaiModelCentreRows.summarize(inputs);
        String text;
        if (summary.downloading > 0) text = getString(R.string.tai_model_centre_row_summary_busy, installedCount, summary.downloading);
        else if (summary.paused > 0) text = getString(R.string.tai_model_centre_row_summary_paused, installedCount, summary.paused);
        else text = getString(R.string.tai_model_centre_row_summary, installedCount);
        if (!TextUtils.equals(text, row.getSummary())) row.setSummary(text);
        row.setProgress(summary.downloading > 0, summary.progress);
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

    private String join(JSONArray values) {
        if (values == null || values.length() == 0) return "none";
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            if (i > 0) joined.append(", ");
            joined.append(values.optString(i, ""));
        }
        return joined.toString();
    }

    private void openParameterScreen(@Nullable TaiModelSpec model) {
        TaiParameterPreferencesFragment fragment = new TaiParameterPreferencesFragment();
        if (model != null) fragment.setArguments(TaiParameterPreferencesFragment.argumentsForModel(model));
        getParentFragmentManager().beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit();
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

    private boolean shouldContinueRefreshing(Context context, @Nullable JSONObject runtimeStatus) {
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
