package com.termux.app.launcher.widget;

import android.app.Application;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetProviderCatalogLoaderTest {
    @Test public void groupsProfilesFiltersHomeSortsAndFallsBackAfterRuntimeFailure() {
        FakeBoundary boundary = new FakeBoundary();
        AppWidgetProviderInfo home = info("z.pkg", "Home", true);
        AppWidgetProviderInfo nonHome = info("x.pkg", "Keyguard", false);
        boundary.providers = Arrays.asList(home, nonHome); boundary.throwPreview = true;
        WidgetProviderCatalogLoader loader = loader(boundary);
        final List<WidgetAppGroup>[] result = new List[1];
        loader.load(metrics(400, 600), 0, (g, groups) -> result[0] = groups);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, result[0].size()); // same package, separate personal/work serials
        assertEquals(1, result[0].get(0).providers.size());
        assertNull(result[0].get(0).providers.get(0).preview);
        assertNotNull(result[0].get(0).providers.get(0).icon);
    }

    @Test public void staleGenerationIsSuppressedAndMetricsRefreshChangesSpanFit() {
        FakeBoundary boundary = new FakeBoundary(); boundary.providers = Collections.singletonList(
            info("pkg", "Wide", true)); boundary.providers.get(0).minWidth = 220;
        QueuedExecutor queue = new QueuedExecutor();
        WidgetProviderCatalogLoader loader = new WidgetProviderCatalogLoader(boundary, queue,
            new Handler(Looper.getMainLooper()), 2.625f);
        AtomicInteger callbacks = new AtomicInteger();
        loader.load(metrics(200, 600), 1, (g, groups) -> callbacks.incrementAndGet());
        loader.load(metrics(800, 600), 2, (g, groups) -> {
            callbacks.incrementAndGet(); assertTrue(groups.get(0).providers.get(0).fits);
        });
        queue.runAll(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, callbacks.get());
    }

    @Test public void providerPixelDimensionsAreNotScaledAgainAt420Dpi() {
        FakeBoundary boundary = new FakeBoundary();
        AppWidgetProviderInfo provider = info("pkg", "TwoByTwo", true);
        provider.minWidth = 200; provider.minHeight = 200;
        provider.minResizeWidth = 100; provider.minResizeHeight = 100;
        boundary.providers = Collections.singletonList(provider);
        final WidgetProviderItem[] result = new WidgetProviderItem[1];
        loader(boundary).load(metrics(400, 600), 0,
            (generation, groups) -> result[0] = groups.get(0).providers.get(0));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, result[0].columnSpan);
        assertEquals(2, result[0].rowSpan);
        assertEquals(1, result[0].minimumColumnSpan);
        assertEquals(1, result[0].minimumRowSpan);
        assertTrue(result[0].fits);
    }

    private static WidgetProviderCatalogLoader loader(FakeBoundary boundary) {
        return new WidgetProviderCatalogLoader(boundary, Runnable::run,
            new Handler(Looper.getMainLooper()), 2.625f);
    }
    private static WidgetGridMetrics metrics(int width, int height) {
        return new WidgetGridMetrics(new Rect(0, 0, width, height), 0, 0, 0,
            WidgetGridDefinition.DEFAULT, false);
    }
    private static AppWidgetProviderInfo info(String pkg, String cls, boolean home) {
        AppWidgetProviderInfo info = WidgetTestFixtures.info(false);
        info.provider = new android.content.ComponentName(pkg, cls);
        info.widgetCategory = home ? AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
            : AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
        info.minWidth = 40; info.minHeight = 40; info.minResizeWidth = 20; info.minResizeHeight = 20;
        return info;
    }
    private static final class FakeBoundary implements WidgetProviderCatalogLoader.Boundary {
        List<AppWidgetProviderInfo> providers = Collections.emptyList(); boolean throwPreview;
        int serial;
        @Override public List<UserHandle> profiles() { return Arrays.asList(Process.myUserHandle(), Process.myUserHandle()); }
        @Override public long serial(UserHandle profile) { return serial++ * 10L; }
        @Override public List<AppWidgetProviderInfo> providers(UserHandle profile) { return providers; }
        @Override public String providerLabel(AppWidgetProviderInfo info) { return info.provider.getClassName(); }
        @Override public String appLabel(AppWidgetProviderInfo info) { return info.provider.getPackageName(); }
        @Override public Drawable appIcon(AppWidgetProviderInfo info) { return new ColorDrawable(1); }
        @Override public Drawable providerIcon(AppWidgetProviderInfo info) { return new ColorDrawable(2); }
        @Override public Drawable preview(AppWidgetProviderInfo info) {
            if (throwPreview) throw new RuntimeException("broken preview"); return new ColorDrawable(3);
        }
        @Override public boolean enabled(AppWidgetProviderInfo info) { return true; }
    }
    private static final class QueuedExecutor implements Executor {
        final java.util.ArrayList<Runnable> work = new java.util.ArrayList<>();
        @Override public void execute(Runnable command) { work.add(command); }
        void runAll() { for (Runnable runnable : new java.util.ArrayList<>(work)) runnable.run(); }
    }
}
