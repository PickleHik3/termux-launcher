package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerLiveApplyTest {
    @Test public void sameContentTreeAcceptsEveryMetricFamilyAndViewType() {
        AppDrawerContentView content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        AppDrawerContentView identity = content;
        content.setVerticalMetrics(AppDrawerGridMetrics.resolve(800, 2, 24, 6, 40));
        assertEquals(6, content.getGrid().getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager
            ? ((androidx.recyclerview.widget.GridLayoutManager) content.getGrid().getLayoutManager()).getSpanCount() : -1);
        content.setViewType(AppDrawerViewType.HORIZONTAL);
        content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(800, 900, 2, 24, 5, 4, 44));
        assertEquals(5, content.getHorizontalAdapter().getMetrics().columns);
        content.setViewType(AppDrawerViewType.CATEGORIES);
        content.setCategoryMetrics(AppDrawerCategoryGridMetrics.resolve(800, 900, 2, 30, 24,
            40, 8 * 1024 * 1024, 3, 48));
        assertEquals(AppDrawerViewType.CATEGORIES, content.getViewType());
        assertSame(identity, content);
    }
}
