package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerCategoryTileAdapterTest {

    @Test public void preservesFixedBucketAndFirstSevenOrderWithStableIds() {
        AppDrawerCategoryTileAdapter adapter = adapter();
        AppDrawerCategoryBucket social = bucket(AppDrawerCategory.SOCIAL, 9);
        AppDrawerCategoryBucket utilities = bucket(AppDrawerCategory.UTILITIES, 2);
        adapter.submit(Arrays.asList(social, utilities));
        assertEquals(2, adapter.getItemCount());
        assertEquals(AppDrawerCategory.SOCIAL.ordinal(), adapter.getItemId(0));
        assertEquals(AppDrawerCategory.UTILITIES.ordinal(), adapter.getItemId(1));
        assertEquals(7, adapter.bucketAt(0).previews().size());
        for (int i = 0; i < 7; i++)
            assertEquals("App " + i, adapter.bucketAt(0).previews().get(i).label);
    }

    @Test public void recycledHolderRetainsNoDrawableOrAppListener() {
        AppDrawerCategoryTileAdapter adapter = adapter();
        adapter.submit(Arrays.asList(bucket(AppDrawerCategory.SOCIAL, 7)));
        RecyclerView parent = new RecyclerView(RuntimeEnvironment.getApplication());
        AppDrawerCategoryTileAdapter.Holder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        assertNotNull(holder.tile.icons[0].getDrawable());
        adapter.onViewRecycled(holder);
        for (int i = 0; i < 7; i++) {
            assertNull(holder.tile.icons[i].getDrawable());
            assertFalse(holder.tile.icons[i].isClickable());
        }
    }

    @Test public void containerDisablesEagerCacheAndPrefetchAndCapturesSelectedSquare() {
        AppDrawerCategoryView view = categoryView(Arrays.asList(
            bucket(AppDrawerCategory.SOCIAL, 7), bucket(AppDrawerCategory.UTILITIES, 2)));
        RecyclerView overview = view.getOverview();
        Object recycler = org.robolectric.util.ReflectionHelpers.getField(overview, "mRecycler");
        assertEquals(0, (int) org.robolectric.util.ReflectionHelpers.getField(
            recycler, "mViewCacheMax"));
        GridLayoutManager manager = (GridLayoutManager) overview.getLayoutManager();
        assertNotNull(manager);
        assertFalse(manager.isItemPrefetchEnabled());
        assertTrue(overview.getChildCount() > 0);
        Frame bounds = view.getTileAdapter().selectedTileBounds(overview, "social", view);
        assertNotNull(bounds);
        assertEquals(view.getTileAdapter().getMetrics().tileSidePx, bounds.width(), 1f);
        assertEquals(view.getTileAdapter().getMetrics().tileSidePx, bounds.height(), 1f);
        assertTrue(bounds.left >= 0 && bounds.right <= view.getWidth());
    }

    private static AppDrawerCategoryTileAdapter adapter() {
        AppDrawerCategoryTileAdapter adapter = new AppDrawerCategoryTileAdapter(null);
        adapter.setMetrics(metrics());
        adapter.setExpansionListener((bucket, source) -> {});
        return adapter;
    }

    static AppDrawerCategoryView categoryView(List<AppDrawerCategoryBucket> buckets) {
        AppDrawerCategoryView view = new AppDrawerCategoryView(
            RuntimeEnvironment.getApplication(), null);
        view.setMetrics(metrics());
        view.submitBuckets(buckets);
        layout(view, 360, 640);
        return view;
    }

    static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    static AppDrawerCategoryGridMetrics metrics() {
        return AppDrawerCategoryGridMetrics.resolve(360, 640, 1, 16, 16, 24,
            8 * 1024 * 1024);
    }

    static AppDrawerCategoryBucket bucket(AppDrawerCategory category, int count) {
        java.util.ArrayList<LauncherAppEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) entries.add(new LauncherAppEntry(
            new AppRef("com.example." + category.slug + i, "Main"), "App " + i,
            new ColorDrawable(Color.RED)));
        return new AppDrawerCategoryBucket(category, entries);
    }
}
