package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import android.view.View;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerLiveApplyViewTypeRegressionTest {
    @Test public void verticalHorizontalAndCategoriesKeepTheirOriginalSurfaces() {
        AppDrawerContentView content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        content.setViewType(AppDrawerViewType.VERTICAL);
        assertEquals(View.VISIBLE, content.getGrid().getVisibility());
        content.setViewType(AppDrawerViewType.HORIZONTAL);
        java.util.List<LauncherAppEntry> apps = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) apps.add(new LauncherAppEntry(
            new AppRef("app" + i, "Main"), "App " + i, null));
        content.getHorizontalAdapter().submit(apps);
        content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(
            720, 1000, 2, 24, 4, 2));
        assertEquals(View.VISIBLE, content.getHorizontalPager().getVisibility());
        assertEquals(View.VISIBLE, content.getPageIndicator().getVisibility());
        content.setViewType(AppDrawerViewType.CATEGORIES);
        assertEquals(View.VISIBLE, content.getCategoryView().getVisibility());
        assertEquals(View.GONE, content.getHorizontalPager().getVisibility());
    }
}
