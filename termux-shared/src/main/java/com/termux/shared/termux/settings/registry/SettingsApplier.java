package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Carries out the refresh a {@link SettingDeclaration} names after a save: at once while the
 * launcher is on screen, otherwise when it next resumes. A declaration whose key a preference
 * listener already refreshes ({@link SettingRefresh#LISTENER}) gets nothing from here. Every door
 * that saves through the registry (launcherctl, and later Settings, the Assistant and restore)
 * hands its {@link SettingsRegistry.Change} to this one applier.
 */
public final class SettingsApplier {

    /** The launcher as the applier sees it; the app's activity behind it. */
    public interface Host {
        /** Whether the launcher is resumed now, so a refresh shows at once. */
        boolean isLauncherResumed();

        /** Carries out {@code refresh} on the launcher; may be called from any thread. */
        void run(@NonNull SettingRefresh refresh);
    }

    @NonNull private final Host mHost;
    /** Refreshes owed to the next resume, each once. */
    private final EnumSet<SettingRefresh> mPending = EnumSet.noneOf(SettingRefresh.class);

    public SettingsApplier(@NonNull Host host) {
        mHost = host;
    }

    /** Refreshes for {@code change}; nothing for a null (refused) save or one that moved nothing. */
    public void apply(@Nullable SettingsRegistry.Change change) {
        if (change == null || !change.changed()) return;
        SettingRefresh refresh = change.declaration.refresh;
        if (refresh == SettingRefresh.LISTENER) return;
        synchronized (this) {
            if (!mHost.isLauncherResumed()) {
                mPending.add(refresh);
                return;
            }
        }
        mHost.run(refresh);
    }

    /** The launcher resumed: runs what was owed while it was away. */
    public void onLauncherResumed() {
        List<SettingRefresh> owed;
        synchronized (this) {
            if (mPending.isEmpty()) return;
            owed = new ArrayList<>(mPending);
            mPending.clear();
        }
        for (SettingRefresh refresh : owed) mHost.run(refresh);
    }
}
