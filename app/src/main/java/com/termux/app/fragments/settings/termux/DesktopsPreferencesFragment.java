package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.x11.DesktopCatalogue;
import com.termux.app.x11.DesktopInstallRun;
import com.termux.app.x11.DesktopsPage;
import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.ProotDistro;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.List;

/**
 * Settings › Linux apps › Desktops: every desktop the catalogue offers. An installed desktop's row
 * opens that desktop's own settings page ({@link X11AppPreferencesFragment}); one that is not
 * installed offers Install, which runs {@link DesktopInstallRun} with a small watch-only terminal
 * beneath the row. The run belongs to the process, not to this page: leaving Settings does not
 * stop it and the page rebuilds from the run's live state when it is shown again. Rows are built
 * from the files on disk and the run's phase each time ({@link DesktopsPage}).
 */
@Keep
public final class DesktopsPreferencesFragment extends MaterialPreferenceFragment {

    private static final String LOG_TAG = "DesktopsPreferencesFragment";
    private static final String KEY_PREFIX = "x11_desktop:";
    /** A row whose tap installs; the key marks it as an action, not a way into a page. */
    public static final String INSTALL_KEY_PREFIX = "x11_desktop_install:";
    private static final int TERMINAL_COLUMNS = 64;

    @Nullable private WatchOnlyTerminal terminal;
    private boolean building;

    private final DesktopInstallRun.Listener runListener = new DesktopInstallRun.Listener() {
        @Override
        public void onScreenUpdated() {
            WatchOnlyTerminal view = terminal;
            if (view != null) view.onScreenUpdated();
        }

        @Override
        public void onRunChanged() {
            if (!building) build();
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(context));
        build();
    }

    /** Desktops the app catalogue found in containers: they open their settings, nothing more. */
    private static void addContainerDesktops(@NonNull Context context,
                                             @NonNull PreferenceScreen screen) {
        java.util.List<DesktopsPage.ContainerRow> rows;
        try {
            rows = DesktopsPage.containerRows(LinuxAppCatalog.scan(LinuxAppCatalog.roots()));
        } catch (RuntimeException e) {
            // An unreadable container leaves the page without its desktops, not without the page.
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the containers' desktops failed", e);
            return;
        }
        for (DesktopsPage.ContainerRow row : rows) {
            Preference preference = new Preference(context);
            preference.setKey(KEY_PREFIX + row.appId);
            preference.setTitle(row.name);
            preference.setSummary(R.string.settings_x11_desktop_installed);
            preference.setFragment(X11AppPreferencesFragment.class.getName());
            preference.getExtras().putString(X11AppPreferencesFragment.ARG_ID, row.appId);
            preference.getExtras().putString(X11AppPreferencesFragment.ARG_NAME, row.name);
            preference.getExtras().putBoolean(X11AppPreferencesFragment.ARG_DESKTOP, true);
            screen.addPreference(preference);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_x11_desktops_title);
        if (context == null) return;
        DesktopInstallRun.get(context).attach(runListener);
        build();
    }

    @Override
    public void onPause() {
        Context context = getContext();
        if (context != null) DesktopInstallRun.get(context).detach(runListener);
        // The run keeps its session; only this page's view of it goes.
        WatchOnlyTerminal view = terminal;
        if (view != null) view.release();
        super.onPause();
    }

    private void build() {
        Context context = getContext();
        PreferenceScreen screen = getPreferenceScreen();
        if (context == null || screen == null) return;
        building = true;
        try {
            DesktopInstallRun run = DesktopInstallRun.get(context);
            List<DesktopsPage.Row> rows = rowsFor(run);
            if (dismissesFinishedRun(rows)) {
                run.dismiss();
                rows = rowsFor(run);
            }
            screen.removeAll();
            boolean terminalShown = false;
            for (DesktopsPage.Row row : rows) {
                screen.addPreference(preferenceOf(context, run, row));
                if (row.showsTerminal()) {
                    screen.addPreference(terminalPreferenceOf(context, run, row));
                    terminalShown = true;
                }
            }
            addContainerDesktops(context, screen);
            if (!terminalShown && terminal != null) terminal.watch(null, false, null);
            SettingsLayoutUtils.applyScreenLayout(this);
        } finally {
            building = false;
        }
    }

    @NonNull
    private static List<DesktopsPage.Row> rowsFor(@NonNull DesktopInstallRun run) {
        return DesktopsPage.rows(
            file -> new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, file).exists(),
            file -> false, run.desktopId(), run.phase(), run.kind());
    }

    private static boolean dismissesFinishedRun(@NonNull List<DesktopsPage.Row> rows) {
        for (DesktopsPage.Row row : rows) if (row.dismissesRun()) return true;
        return false;
    }

    @NonNull
    private Preference terminalPreferenceOf(@NonNull Context context, @NonNull DesktopInstallRun run,
                                            @NonNull DesktopsPage.Row row) {
        if (terminal == null) terminal = new WatchOnlyTerminal(context, TERMINAL_COLUMNS);
        // The run owns its session, so this page only ever loses the view.
        boolean removal = row.status == DesktopsPage.Status.REMOVING
            || row.status == DesktopsPage.Status.REMOVE_FAILED;
        terminal.watch(run.session(), false, getString(removal
            ? R.string.settings_x11_desktop_remove_output
            : R.string.settings_x11_desktop_install_output, row.name));
        DesktopInstallTerminalPreference preference = new DesktopInstallTerminalPreference(context);
        preference.setKey(KEY_PREFIX + row.desktopId + ":terminal");
        preference.setTerminal(terminal);
        return preference;
    }

    @NonNull
    private static Preference preferenceOf(@NonNull Context context, @NonNull DesktopInstallRun run,
                                           @NonNull DesktopsPage.Row row) {
        Preference preference = new Preference(context);
        preference.setKey(KEY_PREFIX + row.desktopId);
        preference.setTitle(row.name);
        DesktopCatalogue.Desktop desktop = DesktopCatalogue.byId(row.desktopId);
        switch (row.status) {
            case INSTALLED:
                preference.setSummary(R.string.settings_x11_desktop_installed);
                bindSettings(preference, desktop);
                break;
            case INSTALLING:
                preference.setSummary(R.string.settings_x11_desktop_installing);
                preference.setSelectable(false);
                break;
            case REMOVING:
                preference.setSummary(R.string.settings_x11_desktop_removing);
                preference.setSelectable(false);
                break;
            case REMOVE_FAILED:
                preference.setSummary(R.string.settings_x11_desktop_remove_failed);
                bindSettings(preference, desktop);
                break;
            case UNAVAILABLE:
                preference.setSummary(R.string.settings_x11_desktop_install);
                preference.setSelectable(false);
                preference.setEnabled(false);
                break;
            case FAILED:
            case NOT_INSTALLED:
            default:
                preference.setKey(INSTALL_KEY_PREFIX + row.desktopId);
                preference.setSummary(row.status == DesktopsPage.Status.FAILED
                    ? R.string.settings_x11_desktop_try_again : R.string.settings_x11_desktop_install);
                if (desktop == null || !row.canInstall()) {
                    preference.setSelectable(false);
                } else {
                    preference.setOnPreferenceClickListener(p -> {
                        run.start(desktop);
                        return true;
                    });
                }
                break;
        }
        return preference;
    }

    private static void bindSettings(@NonNull Preference preference, @Nullable DesktopCatalogue.Desktop desktop) {
        LinuxAppCatalog.LinuxApp session = desktop != null ? sessionOf(desktop) : null;
        if (session == null) {
            preference.setSelectable(false);
            return;
        }
        preference.setFragment(X11AppPreferencesFragment.class.getName());
        preference.getExtras().putString(X11AppPreferencesFragment.ARG_ID, session.id);
        preference.getExtras().putString(X11AppPreferencesFragment.ARG_NAME, session.name);
        preference.getExtras().putBoolean(X11AppPreferencesFragment.ARG_DESKTOP, true);
        preference.getExtras().putBoolean(X11AppPreferencesFragment.ARG_WAYLAND_DESKTOP,
            desktop.kind == DesktopCatalogue.Kind.WAYLAND);
    }

    /** The prefix's session entry for {@code desktop}: the key its settings are stored under. */
    @Nullable
    private static LinuxAppCatalog.LinuxApp sessionOf(@NonNull DesktopCatalogue.Desktop desktop) {
        try {
            for (LinuxAppCatalog.LinuxApp app
                    : LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(ProotDistro.Container.PREFIX))) {
                if (app.session && DesktopsPage.isSessionOf(desktop, app)) return app;
            }
        } catch (RuntimeException e) {
            // An unreadable desktop file leaves the row without a page, not the page without rows.
        }
        return null;
    }
}
