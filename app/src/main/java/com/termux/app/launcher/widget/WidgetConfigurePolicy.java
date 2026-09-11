package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** SDK-gated interpretation of an app-widget provider's initial configuration contract. */
public final class WidgetConfigurePolicy {
    public enum Decision { NONE, REQUIRED, UNAVAILABLE }

    private WidgetConfigurePolicy() {}

    @NonNull
    public static Decision decide(@Nullable ComponentName configure, int widgetFeatures,
                                  int sdkInt, boolean configureActivityAvailable) {
        if (configure == null) return Decision.NONE;
        if (!configureActivityAvailable) return Decision.UNAVAILABLE;
        if (sdkInt >= 28) {
            int optional = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL;
            int reconfigurable = AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE;
            if ((widgetFeatures & optional) != 0 && (widgetFeatures & reconfigurable) != 0) {
                return Decision.NONE;
            }
        }
        return Decision.REQUIRED;
    }

    /**
     * Whether a widget already on the page can be sent back to its own settings screen.
     *
     * <p>A provider opts in with {@code WIDGET_FEATURE_RECONFIGURABLE}: that flag is the only
     * statement that its configuration activity is safe to run a second time against an ID it
     * has already configured. Without it the activity is a first-run screen, and relaunching it
     * can leave the provider's own stored state for this widget inconsistent — so a provider that
     * merely has a {@code configure} activity is not offered the cog.
     */
    public static boolean reconfigurable(@Nullable ComponentName configure, int widgetFeatures,
                                         int sdkInt, boolean configureActivityAvailable) {
        if (configure == null || !configureActivityAvailable) return false;
        // widgetFeatures arrived in API 28; before it a provider had no way to say this.
        if (sdkInt < 28) return false;
        return (widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0;
    }
}
