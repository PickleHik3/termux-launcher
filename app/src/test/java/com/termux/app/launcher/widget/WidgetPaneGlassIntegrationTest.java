package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.github.mmin18.widget.RealtimeBlurView;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import android.app.Activity;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPaneGlassIntegrationTest {
    @Test public void paneAddsNoBlurOrWallpaperCacheSurface() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        ViewGroup root = (ViewGroup) LayoutInflater.from(activity)
            .inflate(R.layout.activity_termux, null);
        WidgetPaneView pane = root.findViewById(R.id.widget_pane);
        assertEquals(0, count(pane, RealtimeBlurView.class));
        assertNotNull(root.findViewById(R.id.terminal_window_bar_blur));
        assertNotNull(root.findViewById(R.id.terminal_window_bar_wallpaper_backdrop));
    }
    private static int count(android.view.View view, Class<?> type) {
        int result = type.isInstance(view) ? 1 : 0;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result += count(((ViewGroup) view).getChildAt(i), type);
        return result;
    }
}
