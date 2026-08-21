package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WidgetConfigurePolicyTest {
    private static final ComponentName CONFIGURE = new ComponentName("pkg", "Configure");

    @Test public void componentFlagsSdkAndAvailabilityAreExact() {
        assertEquals(WidgetConfigurePolicy.Decision.NONE,
            WidgetConfigurePolicy.decide(null, 0, 35, false));
        assertEquals(WidgetConfigurePolicy.Decision.REQUIRED,
            WidgetConfigurePolicy.decide(CONFIGURE, 0, 35, true));
        assertEquals(WidgetConfigurePolicy.Decision.UNAVAILABLE,
            WidgetConfigurePolicy.decide(CONFIGURE, 0, 35, false));
        int optional = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
            | AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE;
        assertEquals(WidgetConfigurePolicy.Decision.NONE,
            WidgetConfigurePolicy.decide(CONFIGURE, optional, 35, true));
        assertEquals(WidgetConfigurePolicy.Decision.REQUIRED,
            WidgetConfigurePolicy.decide(CONFIGURE, optional, 27, true));
        assertEquals(WidgetConfigurePolicy.Decision.REQUIRED,
            WidgetConfigurePolicy.decide(CONFIGURE, 1 << 20, 35, true));
    }
}
