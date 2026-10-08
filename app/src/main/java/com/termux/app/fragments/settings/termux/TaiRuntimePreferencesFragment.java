package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiRemoteClient;
import com.termux.ai.TaiRemoteSettings;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusCardPreference;
import com.termux.app.notice.AppNotice;
import com.termux.launcherctl.LauncherCtlApiServer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * On-device AI → Runtime: the detailed runtime card and the actions on it. Stop and Unload are
 * enabled only when they apply, and their summaries say why when they do not. The card shows the
 * redacted API token and the logs dialog the full status, so the page is kept out of screenshots.
 */
@Keep
public class TaiRuntimePreferencesFragment extends MaterialPreferenceFragment {
    private static final String KEY_STATUS = "tai_runtime_status";
    private static final String KEY_STOP = "tai_runtime_stop";
    private static final String KEY_UNLOAD = "tai_runtime_unload";
    private static final String KEY_LOGS = "tai_runtime_logs";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TaiRuntimePoll runtimePoll = new TaiRuntimePoll(this::isAdded);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.tai_runtime_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        onClick(KEY_STOP, () -> runRuntimeAction(
            () -> TaiManager.getInstance(context.getApplicationContext()).cancelRuntime(),
            R.string.termux_ai_runtime_cancel_requested));
        onClick(KEY_UNLOAD, () -> runRuntimeAction(
            () -> TaiManager.getInstance(context.getApplicationContext()).unloadModel(),
            R.string.termux_ai_runtime_unloaded));
        onClick(KEY_LOGS, () -> showRuntimeLogs(context));
    }

    private void onClick(String key, Runnable action) {
        Preference preference = findPreference(key);
        if (preference == null) return;
        preference.setOnPreferenceClickListener(p -> {
            action.run();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.termux_ai_runtime_screen_title);
            getActivity().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        Context context = getContext();
        if (context != null) runtimePoll.start(context, this::applyStatus);
    }

    @Override
    public void onPause() {
        runtimePoll.stop();
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        runtimePoll.shutdown();
        super.onDestroy();
    }

    private void applyStatus(@Nullable JSONObject status) {
        Context context = getContext();
        if (context == null) return;
        Preference card = findPreference(KEY_STATUS);
        if (card instanceof StatusCardPreference) {
            TaiRuntimeStatusText.Headline headline = TaiRuntimeStatusText.headline(status);
            ((StatusCardPreference) card).setStatus(getString(headline.label), headline.active);
            String body = TaiRuntimeStatusText.full(context, status);
            if (body.isEmpty()) card.setSummary(R.string.termux_ai_runtime_status_summary);
            else card.setSummary(body);
        }
        boolean stop = TaiRuntimeStatusText.stopEnabled(status);
        setAction(KEY_STOP, stop, stop ? R.string.termux_ai_runtime_row_stop_summary
            : R.string.termux_ai_runtime_row_stop_disabled);
        boolean unload = TaiRuntimeStatusText.unloadEnabled(status);
        setAction(KEY_UNLOAD, unload, unload ? R.string.termux_ai_runtime_row_unload_summary
            : R.string.termux_ai_runtime_row_unload_disabled);
    }

    private void setAction(String key, boolean enabled, int summaryRes) {
        Preference preference = findPreference(key);
        if (preference == null) return;
        preference.setEnabled(enabled);
        preference.setSummary(summaryRes);
    }

    private interface RuntimeAction {
        JSONObject run() throws JSONException;
    }

    private void runRuntimeAction(RuntimeAction action, int successResId) {
        runtimePoll.run(() -> {
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
                runtimePoll.refreshNow();
            });
        });
    }

    private static void toastRuntimeResult(Context context, JSONObject result, int successResId) {
        if (result.optBoolean("loadCancellationRequested", false)) {
            AppNotice.show(context,
                result.optString("message", context.getString(R.string.termux_ai_runtime_cancel_requested)), true);
        } else if (result.optBoolean("ok", false)) {
            AppNotice.show(context, successResId, false);
        } else {
            AppNotice.show(context, result.optString("message", context.getString(R.string.termux_ai_runtime_action_failed)), true);
        }
    }

    private void showRuntimeLogs(Context context) {
        // runtimeStatus() blocks on the runtime IPC — fetch off the main thread, show the dialog on it.
        runtimePoll.run(() -> {
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
                        copyToClipboard(ctx, body))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        });
    }

    private static void copyToClipboard(@NonNull Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Termux Launcher", text));
        AppNotice.show(context, R.string.termux_ai_runtime_logs_copied, false);
    }

    private static String redactRuntimeDebugJson(Context context, String body) {
        String redacted = body == null ? "" : body;
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            String token = endpoint.optString("token", "");
            if (!token.isEmpty()) redacted = redacted.replace(token, TaiSettings.redactToken(token));
        } catch (JSONException ignored) {
        }
        String hfToken = new TaiSettings(context).getHuggingFaceToken();
        if (!hfToken.trim().isEmpty()) redacted = redacted.replace(hfToken, TaiSettings.redactToken(hfToken));
        try {
            redacted = TaiRemoteClient.redact(redacted, new TaiRemoteSettings(context).apiKey());
        } catch (RuntimeException ignored) {
            // The keystore is unavailable; the key cannot be in the text either way.
        }
        return redacted;
    }
}
