package com.termux.app.settings;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.registry.SettingRefresh;

/**
 * What the Settings applier asks of the resumed launcher ({@link LauncherSettings.Screen}): each
 * refresh is posted onto the main thread, so one owed at resume runs after the resume, never
 * inside it, and then carried out through the {@link Host} the way the Settings row or the
 * Appearance editor carries it out.
 */
public final class SettingsRefreshScreen implements LauncherSettings.Screen {

    /** The launcher's screen as this class drives it; the activity is behind it. */
    public interface Host {
        /** Whether the screen is finishing or not ready, so a refresh has nothing to restyle. */
        boolean isGone();

        /** Runs {@code work} later on the main thread. */
        void post(@NonNull Runnable work);

        /** Reloads the window's styling, recreating the activity when {@code recreate}. */
        void restyle(boolean recreate);

        /** Re-stamps the panes and the keyboard at the Text size now saved. */
        void applyTextSize();
    }

    @NonNull private final Host mHost;

    public SettingsRefreshScreen(@NonNull Host host) {
        mHost = host;
    }

    @Override
    public void run(@NonNull SettingRefresh refresh) {
        mHost.post(() -> carryOut(refresh));
    }

    private void carryOut(@NonNull SettingRefresh refresh) {
        if (mHost.isGone()) return;
        switch (refresh) {
            case RESTYLE: mHost.restyle(false); break;
            case THEME: mHost.restyle(true); break;
            case TEXT_SIZE: mHost.applyTextSize(); break;
            default: break;
        }
    }
}
