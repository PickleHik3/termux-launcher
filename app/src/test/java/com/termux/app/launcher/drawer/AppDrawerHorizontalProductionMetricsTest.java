package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.GridLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w360dp-h804dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerHorizontalProductionMetricsTest {

    private static final int WIDTH_PX = 1080;
    private static final int HEIGHT_PX = 2412;

    @Test public void autoRowsFillCurrentPagerHeightWithoutEmittingAnOverflowRow() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        SharedPreferences raw = activity.getSharedPreferences("horizontal-production-metrics",
            Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(activity, raw, null);
        preferences.setAppLauncherDrawerViewType("horizontal");

        AppDrawerController controller =
            new AppDrawerController(new FakeAppDrawerHost(activity, preferences));
        AppDrawerContentView content = new AppDrawerContentView(activity);
        AppDrawerPlaneView plane = new AppDrawerPlaneView(activity);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpenRect",
            new Frame(0f, 0f, WIDTH_PX, HEIGHT_PX));
        ReflectionHelpers.callInstanceMethod(controller, "prepareContent",
            ReflectionHelpers.ClassParameter.from(AppDrawerPlaneView.class, plane));

        AppDrawerHorizontalGridMetrics metrics = content.getHorizontalAdapter().getMetrics();
        assertNotNull(metrics);
        float consumed = metrics.rows * metrics.rowHeightPx;
        float remainder = metrics.usablePageHeightPx - consumed;
        assertTrue("AUTO remained capped at " + metrics.rows + " rows", metrics.rows > 6);
        assertTrue("page rows overflow usable height", consumed <= metrics.usablePageHeightPx + 0.01f);
        assertTrue("AUTO left at least one whole row unused", remainder < metrics.rowHeightPx);

        controller.getSearchController().setCatalogue(apps(metrics.itemsPerPage + 1));
        content.measure(View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH_PX, HEIGHT_PX);
        RecyclerView pager = content.getHorizontalPager();
        RecyclerView.ViewHolder holder = pager.findViewHolderForAdapterPosition(0);
        assertTrue(holder instanceof AppDrawerHorizontalPageAdapter.PageHolder);
        GridLayout page = ((AppDrawerHorizontalPageAdapter.PageHolder) holder).page;
        assertEquals(metrics.rows, page.getRowCount());
        assertTrue(page.getRowCount() * metrics.rowHeightPx
            <= content.horizontalPagerUsableHeight(HEIGHT_PX) + 0.01f);
    }

    private static List<LauncherAppEntry> apps(int count) {
        List<LauncherAppEntry> apps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            apps.add(new LauncherAppEntry(new AppRef("phone.app" + i, "Main"),
                "App " + i, null));
        }
        return apps;
    }
}
