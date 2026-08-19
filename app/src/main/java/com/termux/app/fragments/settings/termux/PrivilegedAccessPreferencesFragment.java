package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;

import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusCardPreference;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.PrivilegedPolicyStore;
import com.termux.privileged.ShizukuBackend;

@Keep
public class PrivilegedAccessPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_STATUS = "privileged_backend_status";
    private static final String KEY_REQUEST_PERMISSION = "priv_request_shizuku_permission";
    private static final String KEY_MASTER = PrivilegedPolicyStore.KEY_MASTER_ENABLED;
    private static final String KEY_PREFER_SHIZUKU = PrivilegedPolicyStore.KEY_PREFER_SHIZUKU;
    private static final String KEY_ALLOW_SHELL = PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(PrivilegedAccessPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_privileged_access_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);

        configurePermissionRequestAction(context);
        configureBackendToggleRefresh(context, KEY_MASTER);
        configureBackendToggleRefresh(context, KEY_PREFER_SHIZUKU);
        configureBackendToggleRefresh(context, KEY_ALLOW_SHELL);
        refreshStatusSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.termux_privileged_access_preferences_title);
        }
        Context context = getContext();
        if (context != null) {
            PrivilegedBackendManager.getInstance().initializeIfNeeded(context).thenAccept(success -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(this::refreshStatusSummary);
                }
            });
        }
        refreshStatusSummary();
    }

    private void configurePermissionRequestAction(@NonNull Context context) {
        Preference preference = findPreference(KEY_REQUEST_PERMISSION);
        if (preference == null)
            return;

        preference.setOnPreferenceClickListener(clicked -> {
            PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
            manager.initializeShizukuOnly(context).thenAccept(available -> {
                boolean requested = manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String message = requested ?
                            getString(R.string.termux_privileged_request_permission_requested) :
                            getString(R.string.termux_privileged_request_permission_unavailable);
                        AppNotice.show(context, message, false);
                        refreshStatusSummary();
                    });
                }
            });
            return true;
        });
    }

    private void configureBackendToggleRefresh(@NonNull Context context, @NonNull String key) {
        Preference preference = findPreference(key);
        if (preference == null)
            return;

        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            if (newValue instanceof Boolean) {
                writePrivilegedToggle(context, key, (Boolean) newValue);
            }
            PrivilegedBackendManager.getInstance().reselectBackend().thenAccept(success -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(this::refreshStatusSummary);
                }
            });
            return true;
        });
    }

    private void writePrivilegedToggle(@NonNull Context context, @NonNull String key, boolean value) {
        switch (key) {
            case PrivilegedPolicyStore.KEY_MASTER_ENABLED:
                PrivilegedPolicyStore.setMasterEnabled(context, value);
                break;
            case PrivilegedPolicyStore.KEY_PREFER_SHIZUKU:
                PrivilegedPolicyStore.setPreferShizuku(context, value);
                break;
            case PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK:
                PrivilegedPolicyStore.setShellFallbackEnabled(context, value);
                break;
            default:
                break;
        }
    }

    private void refreshStatusSummary() {
        Preference statusPreference = findPreference(KEY_STATUS);
        if (statusPreference == null)
            return;

        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        PrivilegedBackendManager.BackendState state = manager.getBackendState();
        boolean permission = manager.getBackend() != null && manager.getBackend().hasPermission();

        if (statusPreference instanceof StatusCardPreference) {
            boolean active = state == PrivilegedBackendManager.BackendState.READY
                || state == PrivilegedBackendManager.BackendState.FALLBACK_SHELL;
            ((StatusCardPreference) statusPreference).setStatus(
                manager.getBackendType() + " · " + state, active);
            StringBuilder body = new StringBuilder();
            appendStatusLine(body, "state", String.valueOf(state));
            appendStatusLine(body, "reason", String.valueOf(manager.getStatusReason()));
            appendStatusLine(body, "permission", String.valueOf(permission));
            appendStatusLine(body, "message", manager.getStatusMessage());
            statusPreference.setSummary(body.toString().trim());
        } else {
            statusPreference.setSummary(manager.getStatusDescription());
        }
    }

    private void appendStatusLine(@NonNull StringBuilder builder, @NonNull String key, String value) {
        builder.append(String.format(java.util.Locale.US, "%-11s", key)).append(value).append('\n');
    }
}

class PrivilegedAccessPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private static PrivilegedAccessPreferencesDataStore mInstance;

    private PrivilegedAccessPreferencesDataStore(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized PrivilegedAccessPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new PrivilegedAccessPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (key == null)
            return;

        switch (key) {
            case PrivilegedPolicyStore.KEY_MASTER_ENABLED:
                PrivilegedPolicyStore.setMasterEnabled(mContext, value);
                break;
            case PrivilegedPolicyStore.KEY_PREFER_SHIZUKU:
                PrivilegedPolicyStore.setPreferShizuku(mContext, value);
                break;
            case PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK:
                PrivilegedPolicyStore.setShellFallbackEnabled(mContext, value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (key == null)
            return defValue;

        switch (key) {
            case PrivilegedPolicyStore.KEY_MASTER_ENABLED:
                return PrivilegedPolicyStore.isMasterEnabled(mContext);
            case PrivilegedPolicyStore.KEY_PREFER_SHIZUKU:
                return PrivilegedPolicyStore.isPreferShizuku(mContext);
            case PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK:
                return PrivilegedPolicyStore.isShellFallbackEnabled(mContext);
            default:
                return defValue;
        }
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        return defValue;
    }
}
