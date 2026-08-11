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
}
