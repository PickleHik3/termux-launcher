package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerHorizontalPageAdapterTest {

    private RecyclerView parent;
    private AppDrawerHorizontalPageAdapter adapter;

    @Before public void setUp() {
        parent = new RecyclerView(RuntimeEnvironment.getApplication());
        parent.layout(0, 0, 400, 200);
        adapter = new AppDrawerHorizontalPageAdapter(null);
        adapter.setMetrics(AppDrawerHorizontalGridMetrics.resolve(400f, 200f,
            1f, 11f, 4, 2));
        adapter.submit(apps(10));
    }

    @Test public void holdersAreViewportWidthAndBindRowMajorPages() {
        AppDrawerHorizontalPageAdapter.PageHolder first = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(first, 0);
        assertEquals(400, first.itemView.getLayoutParams().width);
        assertEquals(8, first.cells.size());
        for (int i = 0; i < 8; i++)
            assertEquals("App " + i, first.cells.get(i).label.getText().toString());
    }

    @Test public void lastPageEmptiesAreClearedAndNonClickable() {
        AppDrawerHorizontalPageAdapter.PageHolder last = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(last, 1);
        assertEquals("App 8", last.cells.get(0).label.getText().toString());
        assertEquals("App 9", last.cells.get(1).label.getText().toString());
        for (int i = 2; i < last.cells.size(); i++) {
            assertEquals(View.INVISIBLE, last.cells.get(i).getVisibility());
            assertFalse(last.cells.get(i).isClickable());
            assertNull(last.cells.get(i).icon.getDrawable());
        }
    }

    @Test public void recyclingReleasesEveryCell() {
        AppDrawerHorizontalPageAdapter.PageHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        adapter.onViewRecycled(holder);
        for (AppDrawerAppCellView cell : holder.cells) {
            assertNull(cell.icon.getDrawable());
            assertEquals("", cell.label.getText().toString());
        }
    }

    @Test public void pageZeroIconLookupUsesTheBoundFirstCell() {
        AppDrawerHorizontalPagerView pager = new AppDrawerHorizontalPagerView(
            RuntimeEnvironment.getApplication());
        pager.setAdapter(adapter);
        pager.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        pager.layout(0, 0, 400, 200);
        android.widget.ImageView source =
            (android.widget.ImageView) adapter.pageZeroIcon(pager);
        assertSame(adapter.entries().get(0).icon, source.getDrawable());
    }

    private static List<LauncherAppEntry> apps(int count) {
        List<LauncherAppEntry> apps = new ArrayList<>();
        for (int i = 0; i < count; i++) apps.add(new LauncherAppEntry(
            new AppRef("pkg." + i, "Main"), "App " + i, new ColorDrawable(Color.RED)));
        return apps;
    }
}
