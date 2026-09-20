package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.TermuxActivity;
import com.termux.app.dock.TestDockLayouts;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

/**
 * The drawer's off-path warm-up: the grid is built, bound and laid out before the first pull
 * instead of inside the frame the finger starts it on.
 *
 * <p>What the JVM can show is the state the warm-up leaves behind — one content tree, an adapter
 * with items in it, and nothing made visible. The frame time it buys is a device measurement.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerWarmUpTest {

    private static final int WIDTH_PX = 1080;
    private static final int HEIGHT_PX = 2160;

    private TermuxAppSharedPreferences preferences;
    private FakeAppDrawerHost host;
    private AppDrawerController controller;
    private LauncherAppDataProvider provider;

    @Before public void setUp() throws Exception {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        View root = activity.findViewById(R.id.activity_termux_root_view);
        assertNotNull("the drawer host is a sibling of the content root, under this one", root);
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH_PX, HEIGHT_PX);

        Context context = activity.getApplicationContext();
        seedLauncherApps(context);
        provider = LauncherAppDataProvider.getInstance(context);
        provider.invalidate();
        awaitCatalogLoad();

        SharedPreferences raw = activity.getSharedPreferences("drawer-warm-up", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(activity, raw, null);
        preferences.setAppLauncherDrawerEnabled(true);

        host = new FakeAppDrawerHost(activity, preferences);
        host.viewRoot = root;
        host.dockLayout = TestDockLayouts.capsule(
            activity.getResources().getDisplayMetrics().density, 16);
        host.suggestionBar = new SuggestionBarView(activity, null);
        controller = new AppDrawerController(host);
    }

    @Test public void theWarmUpBuildsTheGridAndBindsTheCatalogueIntoIt() {
        assertTrue(controller.warmUp());

        AppDrawerContentView content = contentOf(controller);
        assertNotNull("the warm-up builds the content tree", content);
        assertNotNull(content.getGrid().getAdapter());
        assertTrue("the warmed grid is bound to the catalogue, not left empty",
            content.getGrid().getAdapter().getItemCount() > 0);
    }

    @Test public void theWarmUpShowsNothing() {
        assertTrue(controller.warmUp());

        AppDrawerContentView content = contentOf(controller);
        assertNotNull(content);
        assertEquals("the warmed grid stays hidden until an open shows it",
            View.INVISIBLE, content.getVisibility());
        View drawerHost = host.findView(R.id.app_drawer_host);
        assertNotNull(drawerHost);
        assertEquals("the drawer host is only raised by prepareOverlay",
            View.INVISIBLE, drawerHost.getVisibility());
        assertFalse("nothing warmed is engaged", controller.isEngaged());
        assertFalse(controller.isOpen());
    }

    @Test public void openingAfterTheWarmUpReusesTheContentItBuilt() {
        assertTrue(controller.warmUp());
        AppDrawerContentView warmed = contentOf(controller);

        // What beginDrag does first, and the only place a second content view could come from.
        assertTrue(ReflectionHelpers.callInstanceMethod(controller, "bindViews"));

        assertSame("the open reuses the warmed content", warmed, contentOf(controller));
        AppDrawerPlaneView plane = ReflectionHelpers.getField(controller, "mPlane");
        assertNotNull(plane);
        FrameLayout contentHost = plane.getContentHost();
        assertEquals("one content tree, not two", 1, contentHost.getChildCount());
    }

    @Test public void aPullBeforeTheWarmUpCancelsIt() {
        assertTrue(ReflectionHelpers.callInstanceMethod(controller, "bindViews"));
        AppDrawerContentView built = contentOf(controller);
        assertNotNull(built);

        assertFalse("a drawer already built is not warmed a second time", controller.warmUp());

        assertSame(built, contentOf(controller));
        AppDrawerPlaneView plane = ReflectionHelpers.getField(controller, "mPlane");
        assertNotNull(plane);
        assertEquals(1, plane.getContentHost().getChildCount());
    }

    @Test public void aDisabledDrawerIsNeverBuilt() {
        preferences.setAppLauncherDrawerEnabled(false);

        assertFalse(controller.warmUp());

        assertNull("a switched-off drawer inflates nothing", contentOf(controller));
        assertNull(ReflectionHelpers.getField(controller, "mPlane"));
    }

    @Test public void theWarmUpRunsOnlyOnce() {
        assertTrue(controller.warmUp());
        AppDrawerContentView warmed = contentOf(controller);

        assertFalse(controller.warmUp());

        assertSame(warmed, contentOf(controller));
    }

    @Nullable private static AppDrawerContentView contentOf(AppDrawerController controller) {
        return ReflectionHelpers.getField(controller, "mContent");
    }

    private static void seedLauncherApps(Context context) {
        ShadowPackageManager shadowPackageManager = shadowOf(context.getPackageManager());
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (int i = 0; i < 24; i++) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = "com.example.warm" + i;
            resolveInfo.activityInfo.name = "com.example.warm" + i + ".MainActivity";
            resolveInfo.nonLocalizedLabel = "Warm" + i;
            resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
            shadowPackageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
        }
    }

    private void awaitCatalogLoad() throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        provider.warmAsync(null);
        while (!provider.hasLoadedApps() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("the catalogue must be loaded before the drawer is warmed",
            provider.hasLoadedApps());
    }
}
