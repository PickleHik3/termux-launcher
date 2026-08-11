package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerCategoryExpansionViewTest {

    @Test public void morphStartsAtSelectedSquareEndsAtBodyAndUsesStagedAlphas() {
        Fixture f = fixture();
        Frame start = f.view.getMorph().currentFrame();
        assertNotNull(start);
        assertEquals(f.metrics.tileSidePx, start.width(), 1f);
        assertEquals(1f, f.view.getOverview().getAlpha(), 0f);
        assertEquals(0f, f.view.getDetailHeader().getAlpha(), 0f);
        f.view.getMorph().setProgress(1f);
        Frame end = f.view.getMorph().currentFrame();
        assertNotNull(end);
        assertEquals(0f, end.left, 0f);
        assertEquals(0f, end.top, 0f);
        assertEquals(360f, end.right, 0f);
        assertEquals(640f, end.bottom, 0f);
        assertEquals(0f, f.view.getMorph().getAlpha(), 0f);
    }

    @Test public void forwardAndReverseNeverKeepOverviewAndDetailIconsLiveTogether() {
        Fixture f = fixture();
        for (int i = 0; i < 300 && f.view.expansionState()
            != AppDrawerCategoryExpansionModel.State.EXPANDED; i++) {
            f.view.advance(1f / 60f, false);
            assertFalse(hasOverviewDrawable(f.view) && f.view.getDetailAdapter().getItemCount() > 0);
        }
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED, f.view.expansionState());
        assertEquals(f.bucket.size(), f.view.getDetailAdapter().getItemCount());
        assertEquals(View.INVISIBLE, f.view.getOverview().getVisibility());

        assertTrue(f.view.collapse());
        assertEquals(View.VISIBLE, f.view.getOverview().getVisibility());
        for (int i = 0; i < 300 && f.view.expansionState()
            != AppDrawerCategoryExpansionModel.State.OVERVIEW; i++) {
            f.view.advance(1f / 60f, false);
            assertFalse(hasOverviewDrawable(f.view) && f.view.getDetailAdapter().getItemCount() > 0);
        }
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW, f.view.expansionState());
        assertEquals(0, f.view.getDetailAdapter().getItemCount());
    }

    @Test public void reducedMotionRunsTheSameReleaseBindAndFinalizers() {
        Fixture f = fixture();
        assertFalse(f.view.advance(1f / 60f, true));
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED, f.view.expansionState());
        assertEquals(f.bucket.size(), f.view.getDetailAdapter().getItemCount());
        assertFalse(hasOverviewDrawable(f.view));
        assertTrue(f.view.collapse());
        assertFalse(f.view.advance(1f / 60f, true));
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW, f.view.expansionState());
        assertEquals(0, f.view.getDetailAdapter().getItemCount());
    }

    @Test public void morphOwnsNoBitmapSnapshotField() {
        for (java.lang.reflect.Field field : AppDrawerCategoryMorphView.class.getDeclaredFields())
            assertFalse(android.graphics.Bitmap.class.isAssignableFrom(field.getType()));
    }

    private static boolean hasOverviewDrawable(AppDrawerCategoryView view) {
        for (int i = 0; i < view.getOverview().getChildCount(); i++) {
            View child = view.getOverview().getChildAt(i);
            if (!(child instanceof AppDrawerCategoryTileView)) continue;
            for (android.widget.ImageView icon : ((AppDrawerCategoryTileView) child).icons)
                if (icon.getDrawable() != null) return true;
        }
        return false;
    }

    private static Fixture fixture() {
        AppDrawerCategoryGridMetrics metrics = AppDrawerCategoryTileAdapterTest.metrics();
        AppDrawerCategoryView view = new AppDrawerCategoryView(
            RuntimeEnvironment.getApplication(), null);
        view.setMetrics(metrics);
        AppDrawerCategoryBucket bucket = AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, 12);
        view.submitBuckets(Collections.singletonList(bucket));
        AppDrawerCategoryTileAdapterTest.layout(view, 360, 640);
        AppDrawerCategoryTileView tile = (AppDrawerCategoryTileView)
            view.getOverview().getChildAt(0);
        view.onExpandRequested(bucket, tile);
        AppDrawerCategoryTileAdapterTest.layout(view, 360, 640);
        return new Fixture(view, metrics, bucket);
    }

    private static final class Fixture {
        final AppDrawerCategoryView view;
        final AppDrawerCategoryGridMetrics metrics;
        final AppDrawerCategoryBucket bucket;
        Fixture(AppDrawerCategoryView view, AppDrawerCategoryGridMetrics metrics,
            AppDrawerCategoryBucket bucket) {
            this.view = view;
            this.metrics = metrics;
            this.bucket = bucket;
        }
    }
}
