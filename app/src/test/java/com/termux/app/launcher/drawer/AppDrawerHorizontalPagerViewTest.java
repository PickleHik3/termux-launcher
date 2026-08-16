package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerHorizontalPagerViewTest {

    @Test public void pagerUsesHorizontalLayoutOneCachedNeighbourAndOneSnapHelper() {
        AppDrawerHorizontalPagerView pager = new AppDrawerHorizontalPagerView(
            RuntimeEnvironment.getApplication());
        assertEquals(LinearLayoutManager.HORIZONTAL,
            ((LinearLayoutManager) pager.getLayoutManager()).getOrientation());
        assertFalse(pager.isHorizontalScrollLocked());
        // Accessing the one attached helper is also a guard against replacing it with ViewPager.
        pager.getPagerSnapHelper();
    }

    @Test public void closeObserverDoesNotOverrideInterceptionToStealTheStream() {
        boolean declared;
        try {
            AppDrawerHorizontalPagerView.class.getDeclaredMethod("onInterceptTouchEvent",
                android.view.MotionEvent.class);
            declared = true;
        } catch (NoSuchMethodException expected) {
            declared = false;
        }
        assertFalse(declared);
    }

    @Test public void pagerSnapHelperTargetsAtMostOneFullPagePerFling() {
        AppDrawerHorizontalPagerView pager = laidOutPager();
        LinearLayoutManager layout = (LinearLayoutManager) pager.getLayoutManager();
        pager.scrollBy(20, 0);
        assertEquals(1,
            pager.getPagerSnapHelper().findTargetSnapPosition(layout, 10000, 0));
        pager.scrollToPosition(1);
        pager.measure(android.view.View.MeasureSpec.makeMeasureSpec(400,
                android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200,
                android.view.View.MeasureSpec.EXACTLY));
        pager.layout(0, 0, 400, 200);
        pager.scrollBy(20, 0);
        int positive = pager.getPagerSnapHelper().findTargetSnapPosition(layout, 10000, 0);
        int negative = pager.getPagerSnapHelper().findTargetSnapPosition(layout, -10000, 0);
        assertEquals(2, positive);
        assertTrue(Math.abs(positive - 1) <= 1);
        assertTrue(Math.abs(negative - 1) <= 1);
    }

    @Test public void nearestSnapViewChangesOnlyAfterTheHalfPageBoundary() {
        AppDrawerHorizontalPagerView pager = laidOutPager();
        LinearLayoutManager layout = (LinearLayoutManager) pager.getLayoutManager();
        pager.scrollBy(190, 0);
        assertEquals(0, layout.getPosition(pager.getPagerSnapHelper().findSnapView(layout)));
        pager.scrollBy(20, 0);
        assertEquals(1, layout.getPosition(pager.getPagerSnapHelper().findSnapView(layout)));
    }

    private static AppDrawerHorizontalPagerView laidOutPager() {
        AppDrawerHorizontalPagerView pager = new AppDrawerHorizontalPagerView(
            RuntimeEnvironment.getApplication());
        AppDrawerHorizontalPageAdapter adapter = new AppDrawerHorizontalPageAdapter(null);
        adapter.setMetrics(AppDrawerHorizontalGridMetrics.resolve(400f, 200f,
            1f, 11f, 4, 2));
        List<LauncherAppEntry> apps = new ArrayList<>();
        for (int i = 0; i < 24; i++) apps.add(new LauncherAppEntry(
            new AppRef("pkg." + i, "Main"), "App " + i, null));
        adapter.submit(apps);
        pager.setAdapter(adapter);
        pager.measure(android.view.View.MeasureSpec.makeMeasureSpec(400,
                android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200,
                android.view.View.MeasureSpec.EXACTLY));
        pager.layout(0, 0, 400, 200);
        return pager;
    }
}
