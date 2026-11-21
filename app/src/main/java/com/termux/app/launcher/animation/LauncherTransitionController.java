package com.termux.app.launcher.animation;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.SuggestionBarView;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Coordinates launcher app transition behavior and safety fallbacks.
 */
public final class LauncherTransitionController {

    private static final String LOG_TAG = "LauncherTransitionController";
    /** Set by the first controller of a process; safe mode lapses on each cold start. */
    private static boolean sProcessStarted;
    /**
     * How long a HOME resolution is trusted without an {@link #invalidateDefaultHome} call. The
     * preferred-home broadcast is not delivered on every Android build (a role change need not
     * send it), so the cache also expires on its own.
     */
    private static final long DEFAULT_HOME_TTL_MS = 60_000L;

    @NonNull
    private final Activity activity;
    @NonNull
    private final TermuxAppSharedPreferences preferences;
    private int transitionFailureCount = 0;
    /** Whether this app was the default home at {@link #defaultHomeCheckedAtMs}; main thread. */
    private boolean defaultHome;
    private long defaultHomeCheckedAtMs = -1L;

    public LauncherTransitionController(@NonNull Activity activity, @NonNull TermuxAppSharedPreferences preferences) {
        this.activity = activity;
        this.preferences = preferences;
        boolean first = !sProcessStarted;
        sProcessStarted = true;
        if (LauncherTransitionPolicy.shouldClearSafeModeOnStart(
            preferences.isAppLauncherAnimationSafeMode(), first)) {
            preferences.setAppLauncherAnimationSafeMode(false);
            Logger.logInfo(LOG_TAG, "Launcher animation safe mode cleared on cold start.");
        }
    }

    public boolean isLauncherAnimationEnabled() {
        return preferences.isAppLauncherAnimationsEnabled();
    }

    public boolean isSafeModeEnabled() {
        return preferences.isAppLauncherAnimationSafeMode();
    }

    public void onAnimationPreferenceUpdated() {
        transitionFailureCount = 0;
    }

    public boolean shouldUseAdvancedAnimations() {
        return isLauncherAnimationEnabled()
            && !isSafeModeEnabled()
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && isDefaultHome();
    }

    /**
     * Answers the system's gesture contract with the icon the leaving app shrinks into: its dock
     * icon, else its icon on the page in front (Home, or the drawer when it is open). With no
     * icon the contract is left unanswered, so the system plays its default animation when its
     * own wait ends; that is not an error and never counts toward safe mode.
     *
     * @param root the window's content, searched for Home and drawer icons
     */
    public void maybeHandleGestureContract(@Nullable Intent intent,
                                           @Nullable SuggestionBarView suggestionBarView,
                                           @Nullable View root, boolean drawerOpen) {
        if (!shouldUseAdvancedAnimations()) {
            return;
        }
        GestureNavContractCompat contract = GestureNavContractCompat.fromIntent(intent);
        if (contract == null) {
            return;
        }
        try {
            String pkg = contract.componentName.getPackageName();
            RectF dock = suggestionBarView == null ? null
                : validBounds(suggestionBarView.getLaunchIconBounds(contract.componentName));
            RectF home = null;
            RectF drawer = null;
            if (dock == null) {
                if (drawerOpen) drawer = validBounds(LaunchIconLocator.find(root, pkg, true));
                else home = validBounds(LaunchIconLocator.find(root, pkg, false));
            }
            LauncherTransitionPolicy.Source source = LauncherTransitionPolicy.pickSource(
                dock != null, home != null, drawer != null, drawerOpen);
            RectF bounds = source == LauncherTransitionPolicy.Source.DOCK ? dock
                : source == LauncherTransitionPolicy.Source.HOME ? home
                : source == LauncherTransitionPolicy.Source.DRAWER ? drawer : null;
            if (bounds == null) {
                Logger.logInfo(LOG_TAG, "Gesture handoff none pkg=" + pkg
                    + ": no icon, the system plays its default");
                return;
            }
            if (contract.sendEndPosition(bounds)) {
                Logger.logInfo(LOG_TAG, "Gesture handoff sent source="
                    + source.name().toLowerCase(java.util.Locale.ROOT) + " pkg=" + pkg
                    + " bounds=" + bounds.toShortString());
                transitionFailureCount = 0;
            } else {
                recordFailure("callback_failed");
            }
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Gesture handoff failed", e);
            recordFailure("exception");
        }
    }

    @Nullable
    private static RectF validBounds(@Nullable RectF bounds) {
        return bounds != null && bounds.width() > 0f && bounds.height() > 0f ? bounds : null;
    }

    private void recordFailure(@NonNull String reason) {
        transitionFailureCount++;
        Logger.logInfo(LOG_TAG, "Gesture handoff error reason=" + reason + " count=" + transitionFailureCount);
        if (LauncherTransitionPolicy.shouldLatchSafeMode(transitionFailureCount)
            && !preferences.isAppLauncherAnimationSafeMode()) {
            preferences.setAppLauncherAnimationSafeMode(true);
            Logger.logWarn(LOG_TAG, "Enabling launcher animation safe mode after repeated errors.");
        }
    }

    /**
     * Drops the cached HOME resolution, so the next launch asks the package manager again. Call
     * it when the preferred home activity changes.
     */
    public void invalidateDefaultHome() {
        defaultHomeCheckedAtMs = -1L;
    }

    /** {@link #resolveDefaultHome}, held between launches rather than resolved on every one. */
    private boolean isDefaultHome() {
        long now = SystemClock.elapsedRealtime();
        if (defaultHomeCheckedAtMs < 0 || now - defaultHomeCheckedAtMs >= DEFAULT_HOME_TTL_MS) {
            defaultHome = resolveDefaultHome();
            defaultHomeCheckedAtMs = now;
        }
        return defaultHome;
    }

    private boolean resolveDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = activity.getPackageManager();
        ResolveInfo resolveInfo = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return false;
        }
        String homePackage = resolveInfo.activityInfo.packageName;
        return !TextUtils.isEmpty(homePackage) && activity.getPackageName().equals(homePackage);
    }
}
