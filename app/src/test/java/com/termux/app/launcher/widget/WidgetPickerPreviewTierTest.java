package com.termux.app.launcher.widget;

import android.app.Application;
import android.appwidget.AppWidgetProviderInfo;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.widget.RemoteViews;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The preview ladder: generated preview, declared preview layout, bitmap. Every rung is asked
 * through the boundary and the platform version is injected, so the ladder is testable on one
 * Robolectric SDK rather than three.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerPreviewTierTest {

    @Test public void generatedPreviewWinsOnThirtyFiveWhenTheProviderOffersOne() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.offersGenerated = true;
        WidgetPreviewArtwork artwork = resolve(boundary, 35);
        assertEquals(WidgetPreviewArtwork.TIER_GENERATED, artwork.tier);
        assertTrue(artwork.isLive());
        assertEquals(1, boundary.generatedCalls);
        assertEquals(0, boundary.layoutCalls);
        assertEquals(0, boundary.bitmapCalls);
    }

    /** The same provider on Android 12 has no such API: the ladder starts one rung down. */
    @Test public void generatedPreviewIsNotAskedForBelowThirtyFive() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.offersGenerated = true;
        WidgetPreviewArtwork artwork = resolve(boundary, Build.VERSION_CODES.S);
        assertEquals(WidgetPreviewArtwork.TIER_PREVIEW_LAYOUT, artwork.tier);
        assertEquals(0, boundary.generatedCalls);
    }

    @Test public void providerWithoutTheHomeScreenCategoryFallsToItsPreviewLayout() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.offersGenerated = false;
        WidgetPreviewArtwork artwork = resolve(boundary, 35);
        assertEquals(WidgetPreviewArtwork.TIER_PREVIEW_LAYOUT, artwork.tier);
        assertEquals(0, boundary.generatedCalls);
        assertEquals(1, boundary.layoutCalls);
    }

    @Test public void previewLayoutIsNotAskedForBelowThirtyOne() {
        FakeBoundary boundary = new FakeBoundary();
        WidgetPreviewArtwork artwork = resolve(boundary, Build.VERSION_CODES.P);
        assertEquals(WidgetPreviewArtwork.TIER_IMAGE, artwork.tier);
        assertNotNull(artwork.image);
        assertEquals(0, boundary.layoutCalls);
        assertEquals(1, boundary.bitmapCalls);
    }

    @Test public void aProviderDeclaringNoPreviewLayoutKeepsTheBitmap() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.layout = null;
        WidgetPreviewArtwork artwork = resolve(boundary, 35);
        assertEquals(WidgetPreviewArtwork.TIER_IMAGE, artwork.tier);
        assertNotNull(artwork.image);
    }

    /**
     * A platform missing the API throws a {@link LinkageError}, not an exception, and a hostile
     * provider can throw anything at all. Every rung falls through to the next instead of taking
     * the sheet down with it.
     */
    @Test public void everyRungFallsThroughOnLinkageErrorsAndOnRuntimeFailures() {
        FakeBoundary boundary = new FakeBoundary();
        boundary.offersGenerated = true;
        boundary.generatedFailure = new NoSuchMethodError("getWidgetPreview");
        boundary.layoutFailure = new NoClassDefFoundError("android/widget/RemoteViews");
        WidgetPreviewArtwork artwork = resolve(boundary, 35);
        assertEquals(WidgetPreviewArtwork.TIER_IMAGE, artwork.tier);
        assertNotNull(artwork.image);

        FakeBoundary hostile = new FakeBoundary();
        hostile.offersGenerated = true;
        hostile.categoryFailure = new NoSuchFieldError("generatedPreviewCategories");
        hostile.layoutFailure = new IllegalStateException("hostile provider");
        hostile.bitmapFailure = new RuntimeException("hostile provider");
        WidgetPreviewArtwork fallen = resolve(hostile, 35);
        assertEquals(WidgetPreviewArtwork.TIER_IMAGE, fallen.tier);
        assertNotNull(fallen.image); // the provider icon, the last thing left
    }

    /** A live preview the card cannot inflate costs that provider its rung for the session. */
    @Test public void aFailedRenderDemotesTheProviderToTheBitmapTier() {
        FakeBoundary boundary = new FakeBoundary();
        WidgetProviderCatalogLoader loader = loader(boundary, 35);
        WidgetProviderItem item = item(loader, boundary);
        final WidgetPreviewArtwork[] first = new WidgetPreviewArtwork[1];
        loader.loadPreview(item, (it, artwork) -> first[0] = artwork);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(WidgetPreviewArtwork.TIER_PREVIEW_LAYOUT, first[0].tier);

        loader.notePreviewRenderFailed(item);
        final WidgetPreviewArtwork[] second = new WidgetPreviewArtwork[1];
        loader.loadPreview(item, (it, artwork) -> second[0] = artwork);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(WidgetPreviewArtwork.TIER_IMAGE, second[0].tier);
        assertEquals(1, boundary.bitmapCalls);

        // And it stays demoted: the held bitmap answers without asking the provider again.
        loader.loadPreview(item, (it, artwork) -> { });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, boundary.bitmapCalls);
        assertEquals(1, boundary.layoutCalls);
    }

    /** A live preview is charged a flat cost, so a long session still evicts under the budget. */
    @Test public void livePreviewsAreChargedAgainstThePreviewBudget() {
        FakeBoundary boundary = new FakeBoundary();
        WidgetProviderCatalogLoader loader = loader(boundary, 35);
        WidgetProviderItem item = item(loader, boundary);
        loader.loadPreview(item, (it, artwork) -> { });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(WidgetPreviewArtwork.LIVE_NOMINAL_BYTES, loader.previewBytes());
        loader.releasePreviews();
        assertEquals(0, loader.previewBytes());
    }

    private static WidgetPreviewArtwork resolve(FakeBoundary boundary, int sdkInt) {
        WidgetProviderCatalogLoader loader = loader(boundary, sdkInt);
        WidgetProviderItem item = item(loader, boundary);
        final WidgetPreviewArtwork[] delivered = new WidgetPreviewArtwork[1];
        loader.loadPreview(item, (it, artwork) -> delivered[0] = artwork);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(delivered[0]);
        return delivered[0];
    }

    private static WidgetProviderCatalogLoader loader(FakeBoundary boundary, int sdkInt) {
        return new WidgetProviderCatalogLoader(boundary, Runnable::run,
            new Handler(Looper.getMainLooper()), resources(), 4 * 1024 * 1024, sdkInt);
    }

    private static WidgetProviderItem item(WidgetProviderCatalogLoader loader,
                                           FakeBoundary boundary) {
        final WidgetProviderItem[] item = new WidgetProviderItem[1];
        loader.load(new WidgetGridMetrics(new Rect(0, 0, 400, 600), 0, 0, 0,
            WidgetGridDefinition.DEFAULT, false), 0,
            (generation, groups) -> item[0] = groups.get(0).providers.get(0));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(item[0]);
        return item[0];
    }

    private static Resources resources() {
        return RuntimeEnvironment.getApplication().getResources();
    }

    private static final class FakeBoundary implements WidgetProviderCatalogLoader.Boundary {
        final AppWidgetProviderInfo provider;
        boolean offersGenerated;
        RemoteViews generated;
        RemoteViews layout;
        Throwable categoryFailure, generatedFailure, layoutFailure, bitmapFailure;
        int generatedCalls, layoutCalls, bitmapCalls;

        FakeBoundary() {
            String self = RuntimeEnvironment.getApplication().getPackageName();
            generated = new RemoteViews(self, com.termux.R.layout.launcher_widget_error_tile);
            layout = new RemoteViews(self, com.termux.R.layout.launcher_widget_error_tile);
            provider = WidgetTestFixtures.info(false);
            provider.provider = new android.content.ComponentName("pkg", "Clock");
            provider.widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
            provider.minWidth = 40; provider.minHeight = 40;
            provider.minResizeWidth = 20; provider.minResizeHeight = 20;
        }

        @Override public List<UserHandle> profiles() {
            return Collections.singletonList(Process.myUserHandle());
        }
        @Override public long serial(UserHandle profile) { return 0L; }
        @Override public List<AppWidgetProviderInfo> providers(UserHandle profile) {
            return Collections.singletonList(provider);
        }
        @Override public String providerLabel(AppWidgetProviderInfo info) { return "Clock"; }
        @Override public String appLabel(AppWidgetProviderInfo info) { return "pkg"; }
        @Override public Drawable appIcon(AppWidgetProviderInfo info) { return new ColorDrawable(1); }
        @Override public Drawable providerIcon(AppWidgetProviderInfo info) {
            return new ColorDrawable(2);
        }
        @Override public boolean offersGeneratedPreview(AppWidgetProviderInfo info) {
            throwIfSet(categoryFailure);
            return offersGenerated;
        }
        @Override public RemoteViews generatedPreview(AppWidgetProviderInfo info) {
            generatedCalls++; throwIfSet(generatedFailure); return generated;
        }
        @Override public RemoteViews previewLayout(AppWidgetProviderInfo info) {
            layoutCalls++; throwIfSet(layoutFailure); return layout;
        }
        @Override public Drawable preview(AppWidgetProviderInfo info) {
            bitmapCalls++; throwIfSet(bitmapFailure); return new ColorDrawable(3);
        }
        @Override public boolean enabled(AppWidgetProviderInfo info) { return true; }

        private static void throwIfSet(Throwable failure) {
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        }
    }
}
