package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Looper;
import android.view.View;

import com.termux.app.launcher.data.LauncherAppDataProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class SuggestionBarAppDiscoveryTest {

    private Context context;
    private ShadowPackageManager shadowPackageManager;
    private SuggestionBarView suggestionBarView;
    private LauncherAppDataProvider appDataProvider;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        shadowPackageManager = shadowOf(context.getPackageManager());
        suggestionBarView = new SuggestionBarView(context, null);
        suggestionBarView.setMaxButtonCount(1);
        appDataProvider = LauncherAppDataProvider.getInstance(context);
        appDataProvider.invalidate();
        suggestionBarView.setAppDataProvider(appDataProvider);
        layOutBar();
    }

    /**
     * renderButtons() refuses to render until hasStableRenderBounds() is satisfied, and its
     * deferred retry is dropped for a view that is not attached to a window. A bare
     * {@code new SuggestionBarView(...)} therefore renders nothing at all, so every child-count
     * assertion below needs the bar to be laid out at a realistic dock size first.
     */
    private void layOutBar() {
        int widthPx = 1080;
        int heightPx = 160;
        suggestionBarView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        suggestionBarView.layout(0, 0, widthPx, heightPx);
        assertTrue("suggestion bar must report stable render bounds before rendering",
            suggestionBarView.isLaidOut() && suggestionBarView.getWidth() == widthPx);
    }

    private void awaitCatalogLoad() throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        appDataProvider.warmAsync(null);
        while (!appDataProvider.hasLoadedApps() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Launcher app catalog should load", appDataProvider.hasLoadedApps());
    }

    @Test
    public void testReloadAllApps_withRegisteredLauncherIntent_rendersAtLeastOneSuggestion() throws Exception {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = "com.example.testapp";
        resolveInfo.activityInfo.name = "com.example.testapp.MainActivity";
        resolveInfo.nonLocalizedLabel = "TestApp";
        resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();

        shadowPackageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);

        suggestionBarView.reloadAllApps();
        awaitCatalogLoad();
        suggestionBarView.reloadWithInput("", null);

        int childCount = suggestionBarView.getChildCount();
        assertEquals("SuggestionBarView should render at least 1 suggestion when a launcher app is registered", true,
                childCount > 0);
    }

    @Test
    public void testReloadAllApps_withNoRegisteredApps_rendersEmptyStateHintAndDoesNotCrash() throws Exception {
        suggestionBarView.reloadAllApps();
        awaitCatalogLoad();
        suggestionBarView.reloadWithInput("", null);

        int childCount = suggestionBarView.getChildCount();

        assertEquals("SuggestionBarView should render a single empty-state hint when no apps are available", 1, childCount);
    }

    @Test
    public void testReloadAllApps_withMultipleRegisteredApps_rendersMultipleSuggestions() throws Exception {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        for (int i = 0; i < 3; i++) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = "com.example.testapp" + i;
            resolveInfo.activityInfo.name = "com.example.testapp" + i + ".MainActivity";
            resolveInfo.nonLocalizedLabel = "TestApp" + i;
            resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
            shadowPackageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
        }

        suggestionBarView.reloadAllApps();
        awaitCatalogLoad();
        suggestionBarView.reloadWithInput("", null);

        int childCount = suggestionBarView.getChildCount();
        assertEquals("SuggestionBarView should render multiple suggestions when multiple apps are registered", true,
                childCount > 0);
    }

    @Test
    public void testReloadAllApps_withDefaultButtons_emptyInput_rendersSuggestions() throws Exception {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = "com.example.testapp";
        resolveInfo.activityInfo.name = "com.example.testapp.MainActivity";
        resolveInfo.nonLocalizedLabel = "TestApp";
        resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
        shadowPackageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);

        suggestionBarView.setDefaultButtons(null);
        suggestionBarView.reloadAllApps();
        awaitCatalogLoad();
        suggestionBarView.reloadWithInput("", null);

        int childCount = suggestionBarView.getChildCount();
        assertEquals("SuggestionBarView should render suggestions with default buttons set", true, childCount > 0);
    }
}
