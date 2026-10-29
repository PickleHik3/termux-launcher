package com.termux.app.setup;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.settings.UseAsSetting;
import com.termux.app.wall.UseAs;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The choices the wizard holds until Finish, and the snapshot taken when it opened that
 * "Keep my current setup" goes back to. Each wave adds its keys to both.
 */
final class SetupState {

    /** The values the wizard owns, as they stood. */
    static final class Snapshot {
        @NonNull final UseAs useAs;
        @NonNull final String startIn;

        Snapshot(@NonNull UseAs useAs, @NonNull String startIn) {
            this.useAs = useAs;
            this.startIn = startIn;
        }
    }

    @NonNull private final Context mContext;
    @NonNull private final Snapshot mSnapshot;
    @NonNull private UseAs mUseAs;

    SetupState(@NonNull Context context) {
        mContext = context;
        mSnapshot = new Snapshot(UseAsSetting.read(context), readStartIn(context));
        mUseAs = mSnapshot.useAs;
    }

    @NonNull
    UseAs useAs() {
        return mUseAs;
    }

    void setUseAs(@NonNull UseAs useAs) {
        mUseAs = useAs;
    }

    /** The Start in Finish will leave: the workspace for Terminal, the stored one for Home. */
    @NonNull
    String startIn() {
        return mUseAs == UseAs.TERMINAL ? TERMUX_APP.LAUNCHER_START_IN_WORKSPACE
            : mSnapshot.startIn;
    }

    /** Whether Finish moves Start in: only Terminal does. */
    boolean startInChanges() {
        return mUseAs == UseAs.TERMINAL;
    }

    @NonNull
    Snapshot snapshot() {
        return mSnapshot;
    }

    /** Writes every choice as Finish does. */
    void apply() {
        UseAsSetting.write(mContext, mUseAs);
        if (mUseAs == UseAs.TERMINAL) {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext);
            if (prefs != null)
                prefs.setLauncherStartIn(TERMUX_APP.LAUNCHER_START_IN_WORKSPACE);
        }
    }

    /** Puts back what the snapshot holds, writing only what moved. */
    void restore(@NonNull Snapshot snapshot) {
        if (UseAsSetting.read(mContext) != snapshot.useAs)
            UseAsSetting.write(mContext, snapshot.useAs);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext);
        if (prefs != null && !prefs.getLauncherStartIn().equals(snapshot.startIn))
            prefs.setLauncherStartIn(snapshot.startIn);
        mUseAs = snapshot.useAs;
    }

    @NonNull
    private static String readStartIn(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        return prefs == null ? TERMUX_APP.DEFAULT_LAUNCHER_START_IN : prefs.getLauncherStartIn();
    }
}
