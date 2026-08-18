package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerCategoryDetailAdapterTest {

    @Test public void bindsRowMajorSharedGeometryArtworkLabelAndStableAnchor() {
        AppDrawerCategoryGridMetrics metrics = AppDrawerCategoryTileAdapterTest.metrics();
        AppDrawerCategoryBucket bucket = AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, 8);
        AppDrawerCategoryDetailAdapter adapter = new AppDrawerCategoryDetailAdapter(null);
        adapter.setMetrics(metrics);
        adapter.submit(bucket.entries());
        RecyclerView parent = new RecyclerView(RuntimeEnvironment.getApplication());
        AppDrawerCategoryDetailAdapter.Holder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 3);
        assertEquals("App 3", holder.cell.label.getText().toString());
        assertSame(bucket.entries().get(3).icon, holder.cell.icon.getDrawable());
        assertEquals(metrics.largeIconPx, holder.cell.icon.getLayoutParams().width);
        assertEquals(3, adapter.positionOfStableId(
            bucket.entries().get(3).appRef.stableId()));
        assertEquals(-1, adapter.positionOfStableId("missing"));
    }

    @Test public void installsExactCommonTintLongPressAndOneWayClickGate() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SuggestionBarView dock = new SuggestionBarView(activity, null);
        AppDrawerCategoryDetailAdapter adapter = new AppDrawerCategoryDetailAdapter(dock);
        adapter.setMetrics(AppDrawerCategoryTileAdapterTest.metrics());
        List<LauncherAppEntry> entries = AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, 1).entries();
        int[] checks = {0};
        adapter.setClickGate(() -> { checks[0]++; return true; });
        adapter.submit(entries);
        AppDrawerCategoryDetailAdapter.Holder holder = adapter.onCreateViewHolder(
            new RecyclerView(activity), 0);
        adapter.onBindViewHolder(holder, 0);
        assertTrue(holder.cell.hasOnClickListeners());
        assertTrue(holder.cell.isLongClickable());
        assertTrue(holder.cell.performClick());
        assertEquals(1, checks[0]);
    }

    @Test public void recycleClearsDrawableListenersAndDescriptions() {
        AppDrawerCategoryDetailAdapter adapter = new AppDrawerCategoryDetailAdapter(null);
        adapter.setMetrics(AppDrawerCategoryTileAdapterTest.metrics());
        adapter.submit(AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, 1).entries());
        AppDrawerCategoryDetailAdapter.Holder holder = adapter.onCreateViewHolder(
            new RecyclerView(RuntimeEnvironment.getApplication()), 0);
        adapter.onBindViewHolder(holder, 0);
        adapter.onViewRecycled(holder);
        assertNull(holder.cell.icon.getDrawable());
        assertEquals("", holder.cell.label.getText().toString());
        assertFalse(holder.cell.isLongClickable());
        assertNull(holder.cell.getContentDescription());
    }
}
