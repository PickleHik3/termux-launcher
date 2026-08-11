package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerControllerCategoriesTest {
    private TermuxActivity activity;
    private TermuxAppSharedPreferences preferences;
    private AppDrawerController controller;
    private AppDrawerContentView content;
    private AppDrawerPlaneView plane;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        SharedPreferences raw = activity.getSharedPreferences("b5-controller", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(activity, raw, null);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);
        controller = activity.getAppDrawerController();
        content = new AppDrawerContentView(activity);
        plane = new AppDrawerPlaneView(activity);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpenRect", new Frame(0f, 0f, 720f, 1280f));
        ReflectionHelpers.setField(controller, "mOpenRadiusPx", 33f);
    }

    @Test public void prepareUsesFullWidthRadiusSharedBudgetAndNoGridPreferences() {
        preferences.setAppLauncherDrawerViewType("categories");
        preferences.setAppLauncherDrawerGridColumnsVertical(6);
        preferences.setAppLauncherDrawerGridColumnsHorizontal(6);
        ReflectionHelpers.setField(controller, "mLayoutConfig",
            AppDrawerLayoutConfig.from(preferences));
        invokePrepare();
        AppDrawerCategoryGridMetrics metrics = content.getCategoryView().getMetrics();
        assertEquals(AppDrawerViewType.CATEGORIES, content.getViewType());
        assertEquals(3, metrics.columns);
        assertEquals(33f, metrics.radiusPx, 0f);
        assertEquals(AppDrawerGridMetrics.resolveColumns(720f),
            ((AppDrawerAppsAdapter) content.getGrid().getAdapter()).getMetrics().columns);
        assertTrue(metrics.chargedPreviewBytes() <= 6L * 1024L * 1024L * 60L / 100L);
        assertEquals(720f, metrics.sidePaddingPx * 2f
            + metrics.spanWidthPx * metrics.columns
            + metrics.itemGapPx * (metrics.columns - 1), 0.01f);
    }

    @Test public void categoryExpansionMovesOnlyThroughContentsExistingFxReturn() {
        content.setViewType(AppDrawerViewType.CATEGORIES);
        content.setCategoryMetrics(AppDrawerCategoryTileAdapterTest.metrics());
        AppDrawerCategoryBucket bucket = AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, 8);
        content.getCategoryView().submitBuckets(java.util.Collections.singletonList(bucket));
        AppDrawerCategoryTileAdapterTest.layout(content.getCategoryView(), 360, 640);
        content.getCategoryView().onExpandRequested(bucket, (AppDrawerCategoryTileView)
            content.getCategoryView().getOverview().getChildAt(0));
        assertTrue(content.advanceDrawerFx(1f, 1f / 60f, false));
        content.advanceDrawerFx(1f, 1f / 60f, true);
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED,
            content.getCategoryView().expansionState());
    }

    @Test public void immediatePreferenceReloadKeepsOnePlaneAndContentAndResetsCategory() {
        content.setViewType(AppDrawerViewType.CATEGORIES);
        content.setCategoryMetrics(AppDrawerCategoryTileAdapterTest.metrics());
        AppDrawerCategoryBucket bucket = AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, 1);
        content.getCategoryView().submitBuckets(java.util.Collections.singletonList(bucket));
        AppDrawerCategoryTileAdapterTest.layout(content.getCategoryView(), 360, 640);
        content.getCategoryView().onExpandRequested(bucket, (AppDrawerCategoryTileView)
            content.getCategoryView().getOverview().getChildAt(0));
        content.advanceDrawerFx(1, 1f / 60f, true);

        preferences.setAppLauncherDrawerViewType("categories");
        ReflectionHelpers.setField(controller, "mLayoutConfig",
            AppDrawerLayoutConfig.from(preferences));

        ReflectionHelpers.setField(controller, "mPlane", plane);
        ReflectionHelpers.setField(controller, "mEngaged", true);
        ReflectionHelpers.setField(controller, "mOpen", true);
        Spring progress = ReflectionHelpers.getField(controller, "mProgress");
        progress.reset(1f);
        preferences.setAppLauncherDrawerViewType("vertical");
        controller.onPreferencesReloaded();

        assertFalse(controller.isEngaged());
        assertFalse(controller.isOpen());
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            content.getCategoryView().expansionState());
        assertEquals(AppDrawerViewType.VERTICAL, content.getViewType());
        assertSame(plane, ReflectionHelpers.getField(controller, "mPlane"));
        assertSame(content, ReflectionHelpers.getField(controller, "mContent"));
    }

    private void invokePrepare() {
        ReflectionHelpers.callInstanceMethod(controller, "prepareContent",
            ReflectionHelpers.ClassParameter.from(AppDrawerPlaneView.class, plane));
    }

}
