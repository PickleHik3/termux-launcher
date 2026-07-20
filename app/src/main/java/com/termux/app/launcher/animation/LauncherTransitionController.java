package com.termux.app.launcher.animation;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;

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
    private static final int SAFE_MODE_FAILURE_THRESHOLD = 3;

    @NonNull
    private final Activity activity;
    @NonNull
    private final TermuxAppSharedPreferences preferences;
    private int transitionFailureCount = 0;

    public LauncherTransitionController(@NonNull Activity activity, @NonNull TermuxAppSharedPreferences preferences) {
        this.activity = activity;
        this.preferences = preferences;
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

    public void maybeHandleGestureContract(@Nullable Intent intent, @Nullable SuggestionBarView suggestionBarView) {
        if (!shouldUseAdvancedAnimations()) {
            return;
        }
        GestureNavContractCompat contract = GestureNavContractCompat.fromIntent(intent);
        if (contract == null) {
            return;
        }
        if (suggestionBarView == null) {
            Logger.logDebug(LOG_TAG, "Skipping gesture contract handoff: suggestion bar unavailable.");
            return;
        }

        RectF iconBounds = suggestionBarView.getLaunchIconBounds(contract.componentName);
        if (iconBounds == null || iconBounds.width() <= 0f || iconBounds.height() <= 0f) {
            recordFailure("icon_bounds_missing");
            return;
        }

        if (contract.sendEndPosition(iconBounds)) {
            transitionFailureCount = 0;
            if (preferences.isAppLauncherAnimationSafeMode()) {
                preferences.setAppLauncherAnimationSafeMode(false);
            }
        } else {
            recordFailure("callback_failed");
        }
    }

    private void recordFailure(@NonNull String reason) {
        transitionFailureCount++;
        Logger.logDebug(LOG_TAG, "Launcher transition fallback reason=" + reason + " count=" + transitionFailureCount);
        if (transitionFailureCount >= SAFE_MODE_FAILURE_THRESHOLD && !preferences.isAppLauncherAnimationSafeMode()) {
            preferences.setAppLauncherAnimationSafeMode(true);
            Logger.logWarn(LOG_TAG, "Enabling launcher animation safe mode after repeated failures.");
        }
    }

    private boolean isDefaultHome() {
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
