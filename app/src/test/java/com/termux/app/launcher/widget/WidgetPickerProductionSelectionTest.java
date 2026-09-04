package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerProductionSelectionTest {
    @Test public void realPlusAdapterCardPolicyAndA1CallProduceDurablePlacement() {
        Fixture fixture = new Fixture(false);
        fixture.controller.openPicker();
        fixture.idleAndLayout();
        RecyclerView.ViewHolder card = fixture.pane.picker().list()
            .findViewHolderForAdapterPosition(1);
        assertNotNull("real provider card must be attached", card);
        assertTrue(card.itemView.performClick());
        assertEquals(1, fixture.platform.allocations);
        assertEquals(1, fixture.repository.records().size());
        LauncherWidgetRecord placed = fixture.repository.records().get(0);
        assertEquals(new WidgetCellRect(0, 0, 2, 2), placed.cell);
        assertTrue(placed.sizeOptions().containsKey(
            android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
    }

    static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        final LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        final WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;
        final AppWidgetProviderInfo info;
        boolean surfaceShowing = true;
        int restoreCount;
        Fixture(boolean fill) {
            activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            info = WidgetTestFixtures.info(false); info.widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
            info.minWidth = 400; info.minHeight = 220; info.minResizeWidth = 100; info.minResizeHeight = 80;
            platform.directBind = true; platform.directlyBoundInfo = info;
            widgets = new LauncherWidgetHostController(activity, repository, platform);
            if (fill) for (int row = 0, id = 1; row < 6; row++) for (int column = 0; column < 4; column++, id++) {
                repository.putRecord(new LauncherWidgetRecord(id,
                    new android.content.ComponentName("full", "P" + id), 0,
                    LauncherWidgetRecord.State.PROVIDER_MISSING,
                    new WidgetCellRect(column, row, column + 1, row + 1), new android.os.Bundle(), null));
            }
            pane = new WidgetPaneView(activity); activity.setContentView(pane);
            FakeBoundary boundary = new FakeBoundary(info);
            WidgetProviderCatalogLoader loader = new WidgetProviderCatalogLoader(boundary,
                Runnable::run, new Handler(Looper.getMainLooper()), activity.getResources(),
                1024 * 1024);
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return surfaceShowing; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() {
                    restoreCount++; surfaceShowing = true;
                }
            }, loader);
            controller.onWallPageShown(true); layout();
        }
        void idleAndLayout() { Shadows.shadowOf(Looper.getMainLooper()).idle(); layout();
            Shadows.shadowOf(Looper.getMainLooper()).idle(); }
        void layout() { pane.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)); pane.layout(0, 0, 1080, 900); }
    }

    private static final class FakeBoundary implements WidgetProviderCatalogLoader.Boundary {
        final AppWidgetProviderInfo info; FakeBoundary(AppWidgetProviderInfo info) { this.info = info; }
        @Override public List<UserHandle> profiles() { return Collections.singletonList(Process.myUserHandle()); }
        @Override public long serial(UserHandle profile) { return 0; }
        @Override public List<AppWidgetProviderInfo> providers(UserHandle profile) { return Collections.singletonList(info); }
        @Override public String providerLabel(AppWidgetProviderInfo info) { return "Agenda"; }
        @Override public String appLabel(AppWidgetProviderInfo info) { return "Calendar"; }
        @Override public Drawable appIcon(AppWidgetProviderInfo info) { return new ColorDrawable(1); }
        @Override public Drawable providerIcon(AppWidgetProviderInfo info) { return new ColorDrawable(2); }
        @Override public Drawable preview(AppWidgetProviderInfo info) { return null; }
        @Override public boolean enabled(AppWidgetProviderInfo info) { return true; }
    }
}
