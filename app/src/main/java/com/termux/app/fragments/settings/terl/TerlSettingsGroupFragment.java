package com.termux.app.fragments.settings.terl;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fragments.settings.RegistryPreferenceDataStore;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.SettingsMaterialDialogs;
import com.termux.app.fragments.settings.StatusActionPreference;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.app.notice.AppNotice;
import com.termux.app.settings.UseAsAdapter;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

/**
 * One group of Terl's Settings: its rows built from the tree ({@link TerlSettingRows}), every
 * setting saved through the registry, and each setting row's menu (hold it: Copy as config,
 * Reset). One page per group, named by its own class so a saved Back stack reopens it.
 */
public abstract class TerlSettingsGroupFragment extends PreferenceFragmentCompat {

    private static final String KEY_HOME_OFF = "terl_home_off";

    /** The values the page was built with; a resume rebuilds it only when one moved. */
    @Nullable private String mBuiltWith;

    @NonNull
    protected abstract TerlSettingsTree.Group group();

    /** The class name of {@code group}'s page. */
    @NonNull
    public static String className(@NonNull TerlSettingsTree.Group group) {
        switch (group) {
            case HOME: return Home.class.getName();
            case WORKSPACES: return Workspaces.class.getName();
            case KEYS: return Keys.class.getName();
            case LINUX_APPS: return LinuxApps.class.getName();
            case NOTIFICATIONS: return Notifications.class.getName();
            case SYSTEM: return SystemGroup.class.getName();
            default: throw new IllegalArgumentException(group + " is v1's page");
        }
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        build();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TerlRowMenu.attach(this, this::build);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(group().titleRes);
        Context context = getContext();
        if (context != null && mBuiltWith != null && !mBuiltWith.equals(values(context))) build();
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (getContext() != null && SettingsMaterialDialogs.show(getContext(), preference)) return;
        super.onDisplayPreferenceDialog(preference);
    }

    /** Builds the page anew from the tree and the values saved now. */
    void build() {
        PreferenceManager manager = getPreferenceManager();
        Context context = manager.getContext();
        SettingsRegistry registry = TerlSettings.registry(context);
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        RegistryPreferenceDataStore store = new RegistryPreferenceDataStore(
            RegistryPreferenceDataStore.ignoringUnknownKeys(), registry, TerlSettings.applier(),
            TerlSettingRows.rowsMap(), message -> Toast.makeText(app, message, Toast.LENGTH_SHORT).show());
        manager.setPreferenceDataStore(store);
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        if (group() == TerlSettingsTree.Group.HOME && TerlSettings.homeIsOff(context)) {
            screen.addPreference(homeOffLine(context, registry));
        } else {
            TerlSettingRows.addRows(context, screen, group(), registry);
        }
        askForNotificationAccess(screen, context);
        store.bind(screen);
        setPreferenceScreen(screen);
        SettingsLayoutUtils.applyScreenLayout(this);
        mBuiltWith = values(context);
    }

    /** This group's values as one string, and whether Home is off. */
    @NonNull
    private String values(@NonNull Context context) {
        SettingsRegistry registry = TerlSettings.registry(context);
        StringBuilder values = new StringBuilder(String.valueOf(TerlSettings.homeIsOff(context)));
        for (TerlSettingsTree.Item item : TerlSettingsTree.items(group())) {
            if (item.kind == TerlSettingsTree.Item.Kind.SETTING) values.append('|').append(registry.get(item.id));
        }
        return values.toString();
    }

    /** Under Use Terl as Terminal, Home's rows give way to one line, with Change. */
    @NonNull
    private Preference homeOffLine(@NonNull Context context, @NonNull SettingsRegistry registry) {
        StatusActionPreference line = new StatusActionPreference(context, null);
        line.setKey(KEY_HOME_OFF);
        line.setPersistent(false);
        line.setState(context.getString(R.string.terl_settings_home_off),
            context.getString(R.string.terl_settings_home_off_change), StatusActionPreference.Tone.NEUTRAL);
        line.setOnPreferenceClickListener(row -> {
            SettingsRegistry.Change change = registry.set(UseAsAdapter.ID, TERMUX_APP.USE_AS_HOME).change();
            if (change != null) TerlSettings.applier().apply(change);
            build();
            return true;
        });
        return line;
    }

    /** Notification dots switched on without notification access ask for it, as v1's row does. */
    private void askForNotificationAccess(@NonNull PreferenceScreen screen, @NonNull Context context) {
        Preference dots = screen.findPreference(SettingDeclarations.NOTIFICATION_DOTS.id);
        if (dots == null) return;
        dots.setOnPreferenceChangeListener((row, value) -> {
            if (Boolean.TRUE.equals(value) && !LauncherNotificationAccess.isEnabled(context)) {
                new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.termux_app_launcher_notification_access_title)
                    .setMessage(R.string.termux_app_launcher_notification_access_message)
                    .setPositiveButton(R.string.termux_app_launcher_notification_access_enable,
                        (dialog, which) -> openNotificationAccess(context))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            }
            return true;
        });
    }

    private static void openNotificationAccess(@NonNull Context context) {
        for (android.content.Intent intent : new android.content.Intent[] {
                LauncherNotificationAccess.detailSettingsIntent(context),
                LauncherNotificationAccess.listSettingsIntent()}) {
            try {
                context.startActivity(intent);
                return;
            } catch (RuntimeException ignored) {
                // The next one, then the notice.
            }
        }
        AppNotice.show(context, R.string.termux_app_launcher_notification_access_unavailable);
    }

    @Keep public static final class Home extends TerlSettingsGroupFragment {
        @NonNull @Override protected TerlSettingsTree.Group group() { return TerlSettingsTree.Group.HOME; }
    }

    @Keep public static final class Workspaces extends TerlSettingsGroupFragment {
        @NonNull @Override protected TerlSettingsTree.Group group() { return TerlSettingsTree.Group.WORKSPACES; }
    }

    @Keep public static final class Keys extends TerlSettingsGroupFragment {
        @NonNull @Override protected TerlSettingsTree.Group group() { return TerlSettingsTree.Group.KEYS; }
    }

    @Keep public static final class LinuxApps extends TerlSettingsGroupFragment {
        @NonNull @Override protected TerlSettingsTree.Group group() { return TerlSettingsTree.Group.LINUX_APPS; }
    }

    @Keep public static final class Notifications extends TerlSettingsGroupFragment {
        @NonNull @Override protected TerlSettingsTree.Group group() { return TerlSettingsTree.Group.NOTIFICATIONS; }
    }

    @Keep public static final class SystemGroup extends TerlSettingsGroupFragment {
        @NonNull @Override protected TerlSettingsTree.Group group() { return TerlSettingsTree.Group.SYSTEM; }
    }
}
