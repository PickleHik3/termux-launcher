package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/**
 * Under Terl v1's dock stands down (no size), so the drawer sheet grows from the control bar: the
 * bar's Drawer tool and Search's All apps row open it (terl-integration-2). The bar lends its rect
 * only; it is never faded or lifted as v1's dock is.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerControlBarSeedTest {

    private FrameLayout root;
    private View dock;
    private View bar;
    private AppDrawerController controller;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new FrameLayout(activity);
        activity.setContentView(root);
        dock = new View(activity);
        dock.setId(R.id.accessory_surface_host);
        root.addView(dock, new FrameLayout.LayoutParams(0, 0));
        bar = new View(activity);
        bar.setId(R.id.control_bar);
        root.addView(bar, new FrameLayout.LayoutParams(400, 100));
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        FakeAppDrawerHost host = new FakeAppDrawerHost(activity, null);
        host.viewRoot = root;
        controller = new AppDrawerController(host);
    }

    private View seed() {
        return ReflectionHelpers.callInstanceMethod(controller, "seedView",
            ReflectionHelpers.ClassParameter.from(AppDrawerPullGeometry.Seed.class,
                AppDrawerPullGeometry.Seed.DOCK));
    }

    @Test public void withV1sDockDownTheSheetGrowsFromTheControlBar() {
        assertSame(bar, seed());
    }

    @Test public void aDockThatStandsStillSeedsTheSheet() {
        dock.setLayoutParams(new FrameLayout.LayoutParams(400, 120));
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);

        assertSame(dock, seed());
    }

    @Test public void theBarIsNeverTakenForTheDockTheDrawerFades() {
        boolean isBar = ReflectionHelpers.callStaticMethod(AppDrawerController.class, "isControlBarSeed",
            ReflectionHelpers.ClassParameter.from(View.class, bar));
        boolean isDock = ReflectionHelpers.callStaticMethod(AppDrawerController.class, "isControlBarSeed",
            ReflectionHelpers.ClassParameter.from(View.class, dock));
        assertTrue(isBar);
        assertFalse(isDock);
    }
}
