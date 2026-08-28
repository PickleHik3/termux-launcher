package com.termux.app.launcher.widget;

import android.app.Application;
import android.content.res.Resources;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
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
import org.robolectric.RuntimeEnvironment;
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
        assertNotNull(result[0].get(0).providers.get(0).icon);
        // The broken preview surfaces at lazy resolution and must not break the row; the miss is
        // remembered so the next bind does not ask the provider again.
        WidgetProviderItem broken = result[0].get(0).providers.get(0);
        final Drawable[] delivered = {new ColorDrawable(9)};
        loader.loadPreview(broken, (item, preview) -> delivered[0] = preview);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(delivered[0]);
        loader.loadPreview(broken, (item, preview) -> {});
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, boundary.previewCalls);
    }

    @Test public void cachedCatalogSkipsRebuildUntilKeyChangesOrInvalidated() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.providers = Collections.singletonList(info("pkg", "Clock", true));
        QueuedExecutor queue = new QueuedExecutor();
        WidgetProviderCatalogLoader loader = new WidgetProviderCatalogLoader(boundary, queue,
            new Handler(Looper.getMainLooper()), resources(), 1024);
        final List<WidgetAppGroup>[] first = new List[1];
        final List<WidgetAppGroup>[] second = new List[1];
        loader.load(metrics(400, 600), 1, (g, groups) -> first[0] = groups);
        queue.runAll(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        loader.load(metrics(400, 600), 1, (g, groups) -> second[0] = groups);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, queue.work.size()); // reopen served from cache, no re-query
        assertSame(first[0], second[0]);
        loader.load(metrics(800, 600), 1, (g, groups) -> {});
        assertEquals(2, queue.work.size()); // metrics change misses
        loader.load(metrics(400, 600), 2, (g, groups) -> {});
        assertEquals(3, queue.work.size()); // revision change misses
        queue.runAll(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        loader.invalidate();
        loader.load(metrics(400, 600), 2, (g, groups) -> {});
        assertEquals(4, queue.work.size()); // package change misses
    }

    @Test public void previewsResolveLazilyExactlyOncePerItem() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.providers = Collections.singletonList(info("pkg", "Clock", true));
        WidgetProviderCatalogLoader loader = loader(boundary);
        final WidgetProviderItem[] item = new WidgetProviderItem[1];
        loader.load(metrics(400, 600), 0,
            (g, groups) -> item[0] = groups.get(0).providers.get(0));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, boundary.previewCalls); // build never touches previews
        AtomicInteger callbacks = new AtomicInteger();
        final Drawable[] delivered = new Drawable[2];
        loader.loadPreview(item[0], (it, preview) -> {
            callbacks.incrementAndGet(); delivered[0] = preview;
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(delivered[0]);
        loader.loadPreview(item[0], (it, preview) -> {
            callbacks.incrementAndGet(); delivered[1] = preview;
        });
        assertEquals(2, callbacks.get()); // held previews answer synchronously
        assertSame(delivered[0], delivered[1]);
        assertEquals(1, boundary.previewCalls);
    }

    /**
     * The store, not the row, owns the pixels: previews are shrunk to the card's slot on the way
     * in, the total never exceeds the budget however many rows bind, and closing the picker
     * gives all of it back.
     */
    @Test public void previewsAreShrunkAndHeldUnderTheBudgetUntilReleased() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.providers = Arrays.asList(info("pkg", "A", true), info("pkg", "B", true),
            info("pkg", "C", true), info("pkg", "D", true));
        boundary.previewBitmapPx = 400;
        Resources resources = resources();
        int extent = Math.round(WidgetPickerAdapter.PREVIEW_DP
            * resources.getDisplayMetrics().density);
        int budget = extent * extent * 4 * 3; // three previews' worth at the retained size
        WidgetProviderCatalogLoader loader = new WidgetProviderCatalogLoader(boundary,
            Runnable::run, new Handler(Looper.getMainLooper()), resources, budget);
        assertEquals(extent, loader.previewExtentPx());
        final List<WidgetAppGroup>[] result = new List[1];
        loader.load(metrics(400, 600), 0, (g, groups) -> result[0] = groups);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        int bound = 0;
        for (WidgetAppGroup group : result[0]) {
            for (WidgetProviderItem item : group.providers) {
                loader.loadPreview(item, (it, preview) -> {
                    assertNotNull(preview);
                    assertTrue(preview.getIntrinsicWidth() <= extent);
                    assertTrue(preview.getIntrinsicHeight() <= extent);
                });
                bound++;
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(8, bound); // four providers in each of two profiles
        assertEquals(8, boundary.previewCalls);
        assertTrue(loader.previewBytes() > 0);
        assertTrue(loader.previewBytes() <= budget);
        assertEquals(budget, loader.previewBudgetBytes());
        loader.releasePreviews();
        assertEquals(0, loader.previewBytes());
    }

    @Test public void previewBudgetFollowsTheHeapBetweenItsClamps() {
        assertEquals(2 * 1024 * 1024, WidgetProviderCatalogLoader.previewBudgetBytes(0));
        assertEquals(4 * 1024 * 1024, WidgetProviderCatalogLoader.previewBudgetBytes(128));
        assertEquals(8 * 1024 * 1024, WidgetProviderCatalogLoader.previewBudgetBytes(512));
    }

    @Test public void staleGenerationIsSuppressedAndMetricsRefreshChangesSpanFit() {
        FakeBoundary boundary = new FakeBoundary(); boundary.providers = Collections.singletonList(
            info("pkg", "Wide", true)); boundary.providers.get(0).minWidth = 220;
        QueuedExecutor queue = new QueuedExecutor();
        WidgetProviderCatalogLoader loader = new WidgetProviderCatalogLoader(boundary, queue,
            new Handler(Looper.getMainLooper()), resources(), 1024);
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
            new Handler(Looper.getMainLooper()), resources(), 1024);
    }
    private static Resources resources() {
        return RuntimeEnvironment.getApplication().getResources();
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
        int serial; int previewCalls; int previewBitmapPx;
        @Override public List<UserHandle> profiles() { return Arrays.asList(Process.myUserHandle(), Process.myUserHandle()); }
        @Override public long serial(UserHandle profile) { return serial++ * 10L; }
        @Override public List<AppWidgetProviderInfo> providers(UserHandle profile) { return providers; }
        @Override public String providerLabel(AppWidgetProviderInfo info) { return info.provider.getClassName(); }
        @Override public String appLabel(AppWidgetProviderInfo info) { return info.provider.getPackageName(); }
        @Override public Drawable appIcon(AppWidgetProviderInfo info) { return new ColorDrawable(1); }
        @Override public Drawable providerIcon(AppWidgetProviderInfo info) { return new ColorDrawable(2); }
        @Override public Drawable preview(AppWidgetProviderInfo info) {
            previewCalls++;
            if (throwPreview) throw new RuntimeException("broken preview");
            if (previewBitmapPx > 0) return new BitmapDrawable(resources(), Bitmap.createBitmap(
                previewBitmapPx, previewBitmapPx, Bitmap.Config.ARGB_8888));
            return new ColorDrawable(3);
        }
        @Override public boolean enabled(AppWidgetProviderInfo info) { return true; }
    }
    private static final class QueuedExecutor implements Executor {
        final java.util.ArrayList<Runnable> work = new java.util.ArrayList<>();
        @Override public void execute(Runnable command) { work.add(command); }
        void runAll() { for (Runnable runnable : new java.util.ArrayList<>(work)) runnable.run(); }
    }
}
