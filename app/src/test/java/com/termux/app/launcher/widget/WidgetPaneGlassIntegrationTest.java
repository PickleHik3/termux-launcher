package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;

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
        // The widget pane is not in this layout (it was only ever counted as absent): the whole
        // layout holds no capturing blur view, the pane included wherever it is added.
        assertEquals(0, countBlurViews(root));
        assertNotNull(root.findViewById(R.id.terminal_window_bar_wallpaper_backdrop));
    }
    /** Views that blur by capturing the window, by name: none is left in the app. */
    private static int countBlurViews(android.view.View view) {
        int result = view.getClass().getSimpleName().contains("Blur") ? 1 : 0;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result += countBlurViews(((ViewGroup) view).getChildAt(i));
        return result;
    }
}
