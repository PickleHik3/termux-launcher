package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;
import com.termux.launcherctl.LauncherCtlApiServer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * On-device AI → Local API: the OpenAI-compatible endpoint other apps and scripts call, who may
 * call it and from where. The switches persist in {@link TaiSettings#PREFS_NAME}, the file the
 * runtime reads. The endpoint dialog can reveal the token, so the page is kept out of screenshots.
 */
@Keep
public class TaiApiPreferencesFragment extends MaterialPreferenceFragment {
    private static final String KEY_ENDPOINT = "tai_endpoint_copy";
    private static final String KEY_LAN = "tai_lan_enabled";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        getPreferenceManager().setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.tai_api_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference endpoint = findPreference(KEY_ENDPOINT);
        if (endpoint != null) {
            endpoint.setOnPreferenceClickListener(preference -> {
                showEndpointAccessDialog(context);
                return true;
            });
        }
        configureAuthToggle(context);
        configureLanToggle(context);
        refreshEndpointPreferences(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.termux_ai_local_api_title);
            getActivity().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        Context context = getContext();
        if (context != null) {
            refreshEndpointPreferences(context);
            refreshLanToggle(context);
        }
    }

    @Override
    public void onPause() {
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onPause();
    }

    private void refreshEndpointPreferences(Context context) {
        Preference endpointCopy = findPreference(KEY_ENDPOINT);
        if (endpointCopy == null) return;
        String baseUrl = "";
        try {
            baseUrl = LauncherCtlApiServer.getInstance().endpointSettings(context).optString("openAiBaseUrl", "");
        } catch (JSONException e) {
            // Endpoint settings unavailable; fall back to the static notice below.
        }
        if (baseUrl.isEmpty()) endpointCopy.setSummary(R.string.termux_ai_endpoint_notice_summary);
        else endpointCopy.setSummary(baseUrl);
    }

    private void configureAuthToggle(Context context) {
        SwitchPreferenceCompat authToggle = findPreference(TaiSettings.KEY_API_AUTH_REQUIRED);
        if (authToggle == null) return;
        authToggle.setChecked(new TaiSettings(context).isApiAuthRequired());
        authToggle.setOnPreferenceChangeListener((preference, newValue) -> {
            new TaiSettings(context).setApiAuthRequired((Boolean) newValue);
            applyEndpointSettings(context);
            return true;
        });
    }

    private void configureLanToggle(Context context) {
        SwitchPreferenceCompat lanToggle = findPreference(KEY_LAN);
        if (lanToggle == null) return;
        lanToggle.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (Boolean) newValue;
            if (enabled) {
                showLanWarningDialog(context, lanToggle);
                return false;
            }
            new TaiSettings(context).setApiBindMode(TaiSettings.BIND_MODE_LOCALHOST);
            applyEndpointSettings(context);
            return true;
        });
        refreshLanToggle(context);
    }

    private void refreshLanToggle(Context context) {
        SwitchPreferenceCompat lanToggle = findPreference(KEY_LAN);
        if (lanToggle == null) return;
        lanToggle.setChecked(TaiSettings.BIND_MODE_LAN.equals(new TaiSettings(context).getApiBindMode()));
    }

    private void showLanWarningDialog(Context context, SwitchPreferenceCompat lanToggle) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_lan_warning_title)
            .setMessage(R.string.termux_ai_lan_warning_message)
            .setPositiveButton(R.string.termux_ai_dialog_enable, (dialog, which) -> {
                lanToggle.setChecked(true);
                new TaiSettings(context).setApiBindMode(TaiSettings.BIND_MODE_LAN);
                applyEndpointSettings(context);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void applyEndpointSettings(Context context) {
        try {
            LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
            refreshEndpointPreferences(context);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_endpoint_update_failed, true);
        }
    }

    /**
     * Single cohesive endpoint dialog: OpenAI base URL and bearer token, each with its own copy
     * affordance (the token also reveals/hides in place), the port and token regenerate actions,
     * and the on-disk file locations.
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
        MaterialButton revealButton = endpointButton(context, R.string.termux_ai_dialog_reveal, null);
        revealButton.setOnClickListener(v -> {
            revealed[0] = !revealed[0];
            tokenView.setText(revealed[0] ? token[0] : TaiSettings.redactToken(token[0]));
            revealButton.setText(revealed[0] ? R.string.termux_ai_dialog_hide : R.string.termux_ai_dialog_reveal);
        });
        MaterialButton tokenCopy = endpointButton(context, R.string.termux_ai_dialog_copy,
            () -> copyToClipboard(context, token[0], R.string.termux_ai_api_token_copied));
        layout.addView(endpointRow(context, getString(R.string.termux_ai_endpoint_field_token), tokenView,
            revealButton, tokenCopy));

        MaterialButton randomizePort = endpointButton(context, R.string.termux_ai_endpoint_randomize_port, () -> {
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
        MaterialButton recreateToken = endpointButton(context, R.string.termux_ai_endpoint_recreate_token, () -> {
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
        files.setTextAppearance(textAppearance(context, com.google.android.material.R.attr.textAppearanceBodySmall));
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
        // The endpoint and token are exact values, so they stay monospace.
        view.setTextAppearance(textAppearance(context, com.google.android.material.R.attr.textAppearanceBodyMedium));
        view.setTypeface(Typeface.MONOSPACE);
        return view;
    }

    private int textAppearance(Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId;
    }

    private MaterialButton endpointButton(Context context, int textRes, @Nullable Runnable action) {
        MaterialButton button = new MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(textRes);
        if (action != null) button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout endpointRow(Context context, String label, @Nullable TextView valueView, MaterialButton... buttons) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, Math.round(8 * density), 0, 0);
        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(textAppearance(context, com.google.android.material.R.attr.textAppearanceLabelMedium));
        labelView.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        column.addView(labelView);
        if (valueView != null) column.addView(valueView);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        for (MaterialButton button : buttons) actions.addView(button);
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
}
