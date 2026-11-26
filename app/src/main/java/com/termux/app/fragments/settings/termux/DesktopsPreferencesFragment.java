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
import com.termux.app.x11.DesktopsPage;
import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.ProotDistro;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * Settings › Linux apps › Desktops: every desktop the catalogue offers and whether it is
 * installed. An installed desktop's row opens that desktop's own settings page
 * ({@link X11AppPreferencesFragment}); the rest only say they are not installed. Rows are built
 * from the files on disk each time the page opens.
 */
@Keep
public final class DesktopsPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_PREFIX = "x11_desktop:";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);
        for (DesktopsPage.Row row : DesktopsPage.rows(
                file -> new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, file).exists(),
                file -> false)) {
            screen.addPreference(preferenceOf(context, row));
        }
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_x11_desktops_title);
    }

    @NonNull
    private static Preference preferenceOf(@NonNull Context context, @NonNull DesktopsPage.Row row) {
        Preference preference = new Preference(context);
        preference.setKey(KEY_PREFIX + row.desktopId);
        preference.setTitle(row.name);
        preference.setSummary(row.installed
            ? R.string.settings_x11_desktop_installed : R.string.settings_x11_desktop_not_installed);
        DesktopCatalogue.Desktop desktop = DesktopCatalogue.byId(row.desktopId);
        LinuxAppCatalog.LinuxApp session = row.opensSettings() && desktop != null
            ? sessionOf(desktop) : null;
        if (session == null) {
            preference.setSelectable(false);
        } else {
            preference.setFragment(X11AppPreferencesFragment.class.getName());
            preference.getExtras().putString(X11AppPreferencesFragment.ARG_ID, session.id);
            preference.getExtras().putString(X11AppPreferencesFragment.ARG_NAME, session.name);
            preference.getExtras().putBoolean(X11AppPreferencesFragment.ARG_DESKTOP, true);
            preference.getExtras().putBoolean(X11AppPreferencesFragment.ARG_WAYLAND_DESKTOP,
                desktop.kind == DesktopCatalogue.Kind.WAYLAND);
        }
        return preference;
    }

    /** The prefix's session entry for {@code desktop}: the key its settings are stored under. */
    @Nullable
    private static LinuxAppCatalog.LinuxApp sessionOf(@NonNull DesktopCatalogue.Desktop desktop) {
        try {
            for (LinuxAppCatalog.LinuxApp app
                    : LinuxAppCatalog.scan(LinuxAppCatalog.rootsOf(ProotDistro.Container.PREFIX))) {
                if (app.session && DesktopsPage.isSessionOf(desktop, app.desktopNames)) return app;
            }
        } catch (RuntimeException e) {
            // An unreadable desktop file leaves the row without a page, not the page without rows.
        }
        return null;
    }
}
