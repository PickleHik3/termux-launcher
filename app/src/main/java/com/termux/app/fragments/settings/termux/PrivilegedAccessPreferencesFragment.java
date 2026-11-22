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
import com.termux.privileged.PrivilegedChoice;
import com.termux.privileged.PrivilegedPolicyStore;
import com.termux.privileged.ShizukuBackend;
import com.termux.privileged.lane.PrivilegedLaneBinding;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

@Keep
public class PrivilegedAccessPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_STATUS = "privileged_backend_status";
    private static final String KEY_REQUEST_PERMISSION = "priv_request_shizuku_permission";
    private static final String KEY_BACKEND = PrivilegedPolicyStore.KEY_BACKEND;
    private static final String KEY_LANE_STATUS = "priv_lane_status";
    private static final String KEY_LANE = PrivilegedPolicyStore.KEY_LANE_ENABLED;

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
        configureBackendChoice(context);
        configureLaneToggle(context);
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

    private void configureBackendChoice(@NonNull Context context) {
        Preference preference = findPreference(KEY_BACKEND);
        if (preference == null)
            return;

        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            PrivilegedChoice choice = PrivilegedChoice.fromStored(String.valueOf(newValue));
            if (choice == null)
                return false;
            PrivilegedPolicyStore.setChoice(context, choice);
            // The choice gates the lane too; settle it again once the backend has been re-picked.
            PrivilegedLaneBinding.getInstance().applyPolicy();
            PrivilegedBackendManager.getInstance().reselectBackend().thenAccept(success -> {
                PrivilegedLaneBinding.getInstance().applyPolicy();
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(this::refreshStatusSummary);
                }
            });
            refreshStatusSummary();
            return true;
        });
    }

    private void configureLaneToggle(@NonNull Context context) {
        Preference preference = findPreference(KEY_LANE);
        if (preference == null)
            return;

        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            if (newValue instanceof Boolean) {
                PrivilegedPolicyStore.setLaneEnabled(context, (Boolean) newValue);
            }
            PrivilegedLaneBinding.getInstance().applyPolicy();
            // Binding is asynchronous; the row settles on the next resume or refresh.
            refreshLaneStatus();
            return true;
        });
    }

    private void refreshLaneStatus() {
        Preference lanePreference = findPreference(KEY_LANE_STATUS);
        if (!(lanePreference instanceof StatusCardPreference))
            return;

        PrivilegedLaneBinding binding = PrivilegedLaneBinding.getInstance();
        PrivilegedLaneBinding.State state = binding.state();
        String label;
        switch (state) {
            case RUNNING:
                label = getString(R.string.termux_privileged_lane_state_running, binding.serviceUid());
                break;
            case ROOT:
                label = getString(R.string.termux_privileged_lane_state_root);
                break;
            case BINDING:
                label = getString(R.string.termux_privileged_lane_state_binding);
                break;
            case NO_SHIZUKU:
                label = getString(R.string.termux_privileged_lane_state_no_shizuku);
                break;
            case NO_PERMISSION:
                label = getString(R.string.termux_privileged_lane_state_no_permission);
                break;
            case NO_ROOT:
                label = getString(R.string.termux_privileged_lane_state_no_root);
                break;
            case OFF:
            default:
                label = getString(R.string.termux_privileged_lane_state_off);
                break;
        }
        ((StatusCardPreference) lanePreference).setStatus(label, state == PrivilegedLaneBinding.State.RUNNING
            || state == PrivilegedLaneBinding.State.ROOT);
    }

    private void refreshStatusSummary() {
        Context context = getContext();
        if (context == null)
            return;
        refreshLaneStatus();

        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        PrivilegedChoice.Status status = manager.currentStatus();

        Preference statusPreference = findPreference(KEY_STATUS);
        if (statusPreference instanceof StatusCardPreference) {
            ((StatusCardPreference) statusPreference).setStatus(getString(statusLabel(status)),
                status == PrivilegedChoice.Status.USING_SHIZUKU || status == PrivilegedChoice.Status.USING_ROOT);
        }

        // Asking for Shizuku's permission only makes sense once Shizuku is up and has not granted it.
        Preference request = findPreference(KEY_REQUEST_PERMISSION);
        if (request != null) {
            boolean visible = status == PrivilegedChoice.Status.NEEDS_PERMISSION;
            if (request.isVisible() != visible)
                request.setVisible(visible);
        }
    }

    private static int statusLabel(@NonNull PrivilegedChoice.Status status) {
        switch (status) {
            case USING_SHIZUKU:
                return R.string.termux_privileged_status_using_shizuku;
            case USING_ROOT:
                return R.string.termux_privileged_status_using_root;
            case NEEDS_PERMISSION:
                return R.string.termux_privileged_status_needs_permission;
            case SHIZUKU_NOT_RUNNING:
                return R.string.termux_privileged_status_shizuku_not_running;
            case ROOT_DENIED:
                return R.string.termux_privileged_status_root_denied;
            case NOT_AVAILABLE:
                return R.string.termux_privileged_status_not_available;
            case OFF:
                return R.string.termux_privileged_status_off;
            case CHECKING:
            default:
                return R.string.termux_privileged_status_checking;
        }
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
            case PrivilegedPolicyStore.KEY_LANE_ENABLED:
                PrivilegedPolicyStore.setLaneEnabled(mContext, value);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.KEY_LAUNCHERCTL_ROOT_REBOOT: {
                TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext, false);
                if (preferences != null) preferences.setLauncherCtlRootRebootEnabled(value);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (key == null)
            return defValue;

        switch (key) {
            case PrivilegedPolicyStore.KEY_LANE_ENABLED:
                return PrivilegedPolicyStore.isLaneSwitchOn(mContext);
            case TermuxPreferenceConstants.TERMUX_APP.KEY_LAUNCHERCTL_ROOT_REBOOT: {
                TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext, false);
                return preferences != null && preferences.isLauncherCtlRootRebootEnabled();
            }
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        PrivilegedChoice choice = PrivilegedChoice.fromStored(value);
        if (PrivilegedPolicyStore.KEY_BACKEND.equals(key) && choice != null) {
            PrivilegedPolicyStore.setChoice(mContext, choice);
        }
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        if (PrivilegedPolicyStore.KEY_BACKEND.equals(key)) {
            return PrivilegedPolicyStore.getChoice(mContext).stored;
        }
        return defValue;
    }
}
