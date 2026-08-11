package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.launcher.data.LauncherUsageStatsStore;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerContentCategoriesTest {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 640;
    private AppDrawerContentView content;
    private AppDrawerSearchController search;

    @Before public void setUp() {
        LauncherUsageStatsStore.getInstance(RuntimeEnvironment.getApplication()).clear();
        search = new AppDrawerSearchController();
        search.setHost(new AppDrawerSearchController.Host() {
            @Override public boolean isSearchActive() { return true; }
            @Override public void onSearchCommitRequested() {}
            @Override public void onSearchDismissRequested() {}
        });
        content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        content.setViewType(AppDrawerViewType.CATEGORIES);
        content.setCategoryMetrics(AppDrawerCategoryGridMetrics.resolve(WIDTH,
            content.horizontalPagerUsableHeight(HEIGHT), 1, 16, 16, 24, 8 * 1024 * 1024));
        content.setInteractive(true);
        content.bind(null, search);
        search.setCatalogue(apps());
        layout();
    }

    @Test public void categoryOverviewUsesFullWidthAndRemovesEveryOtherSurface() {
        assertEquals(View.VISIBLE, content.getCategoryView().getVisibility());
        assertEquals(View.GONE, content.getGrid().getVisibility());
        assertEquals(View.GONE, content.getHorizontalPager().getVisibility());
        assertEquals(View.GONE, content.getPageIndicator().getVisibility());
        assertEquals(View.GONE, content.getRopeColumn().getVisibility());
        assertFalse(content.isColumnActive());
        assertEquals(WIDTH, content.getCategoryView().getWidth());
        assertTrue(content.getCategoryView().getTileAdapter().getItemCount() > 0);
    }

    @Test public void focuslessQueryCancelsDetailUsesFlatFullWidthGridAndClearsToOverview() {
        expandFirst();
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED,
            content.getCategoryView().expansionState());
        assertTrue(search.handleCodePoint('a', false));
        layout();
        assertTrue(content.hasQuery());
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            content.getCategoryView().expansionState());
        assertEquals(View.GONE, content.getCategoryView().getVisibility());
        assertEquals(View.VISIBLE, content.getGrid().getVisibility());
        assertEquals(WIDTH, content.getGrid().getWidth());
        assertEquals(View.GONE, content.getRopeColumn().getVisibility());

        assertTrue(content.clearQueryIfPresent());
        layout();
        assertEquals(View.VISIBLE, content.getCategoryView().getVisibility());
        assertEquals(View.GONE, content.getGrid().getVisibility());
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            content.getCategoryView().expansionState());
    }

    @Test public void clearingSearchRecyclesRenderedGridCellsBeforeCategoryPreviewsBind() {
        LauncherAppEntry entry = new LauncherAppEntry(new AppRef("com.example.rendered", "Main"),
            "Alpha", new ColorDrawable(Color.RED), false, ApplicationInfo.CATEGORY_SOCIAL, 0);
        search.setCatalogue(Collections.singletonList(entry));
        assertTrue(search.handleCodePoint('a', false));
        layout();
        RecyclerView grid = content.getGrid();
        assertTrue(grid.getChildCount() > 0);
        AppDrawerAppsAdapter.Cell searchHolder = (AppDrawerAppsAdapter.Cell)
            grid.getChildViewHolder(grid.getChildAt(0));
        assertNotNull(searchHolder.icon.getDrawable());

        assertTrue(content.clearQueryIfPresent());
        assertNull(searchHolder.icon.getDrawable());
        layout();
        AppDrawerCategoryTileView tile = (AppDrawerCategoryTileView)
            content.getCategoryView().getOverview().getChildAt(0);
        assertNotNull(tile.icons[0].getDrawable());
    }

    @Test public void categoriesObserveSharedUsageLaunchesAndClearsAfterInitialClassification() {
        LauncherAppEntry entry = apps().get(0);
        search.setCatalogue(Collections.singletonList(entry));
        assertNull(bucket(AppDrawerCategory.SUGGESTIONS));

        LauncherUsageStatsStore.getInstance(RuntimeEnvironment.getApplication())
            .recordLaunch(entry.appRef.stableId());
        search.setCatalogue(Collections.singletonList(entry));
        assertEquals(entry, bucket(AppDrawerCategory.SUGGESTIONS).entries().get(0));

        LauncherUsageStatsStore.getInstance(RuntimeEnvironment.getApplication()).clear();
        search.setCatalogue(Collections.singletonList(entry));
        assertNull(bucket(AppDrawerCategory.SUGGESTIONS));
    }

    @Test public void backHierarchyClearsQueryThenCollapsesThenOffersDrawerClose() {
        search.handleCodePoint('a', false);
        assertTrue(content.handleBackInDrawer());
        assertFalse(content.hasQuery());
        expandFirst();
        assertTrue(content.handleBackInDrawer());
        content.advanceDrawerFx(1, 1f / 60f, true);
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            content.getCategoryView().expansionState());
        assertFalse(content.handleBackInDrawer());
    }

    @Test public void packageRefreshRetainsSelectedCategoryAndAbortsWhenItBecomesEmpty() {
        expandFirst();
        String selected = content.getCategoryView().getTileAdapter().bucketAt(0).category.slug;
        search.setCatalogue(apps());
        layout();
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED,
            content.getCategoryView().expansionState());
        assertEquals(selected, content.getCategoryView().getTileAdapter().bucketAt(0).category.slug);

        // The first fixed bucket for this fixture is Social. Removing its only entry omits the tile
        // and aborts the selected detail atomically.
        List<LauncherAppEntry> withoutSocial = new ArrayList<>(apps());
        withoutSocial.remove(0);
        search.setCatalogue(withoutSocial);
        layout();
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            content.getCategoryView().expansionState());
    }

    @Test public void zeroCatalogueShowsNonFocusableEmptyStateAndLeavesBodyAsCloseChrome() {
        search.setCatalogue(Collections.emptyList());
        layout();
        assertEquals(View.VISIBLE, content.getCategoryView().getEmptyState().getVisibility());
        assertFalse(content.getCategoryView().getEmptyState().isFocusable());
        float x = content.getCategoryView().getLeft() + WIDTH / 2f;
        float y = content.getCategoryView().getTop() + content.getCategoryView().getHeight() / 2f;
        assertFalse(content.ownsPoint(x, y));
    }

    private void expandFirst() {
        AppDrawerCategoryView categories = content.getCategoryView();
        AppDrawerCategoryBucket bucket = categories.getTileAdapter().bucketAt(0);
        AppDrawerCategoryTileView tile = (AppDrawerCategoryTileView)
            categories.getOverview().getChildAt(0);
        categories.onExpandRequested(bucket, tile);
        layout();
        content.advanceDrawerFx(1, 1f / 60f, true);
        layout();
    }

    private void layout() {
        content.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
    }

    private AppDrawerCategoryBucket bucket(AppDrawerCategory category) {
        for (AppDrawerCategoryBucket bucket : content.getCategoryView().getTileAdapter().buckets())
            if (bucket.category == category) return bucket;
        return null;
    }

    static List<LauncherAppEntry> apps() {
        return Arrays.asList(
            app("com.example.social", "Alpha", ApplicationInfo.CATEGORY_SOCIAL),
            app("com.example.work", "Beta", ApplicationInfo.CATEGORY_PRODUCTIVITY),
            app("com.example.tool", "Gamma", ApplicationInfo.CATEGORY_ACCESSIBILITY),
            app("com.example.game", "Delta", ApplicationInfo.CATEGORY_GAME),
            app("com.example.photo", "Epsilon", ApplicationInfo.CATEGORY_IMAGE),
            app("com.example.maps", "Zeta", ApplicationInfo.CATEGORY_MAPS),
            app("com.example.news", "Eta", ApplicationInfo.CATEGORY_NEWS),
            app("com.example.unknown", "Theta", ApplicationInfo.CATEGORY_UNDEFINED));
    }

    static LauncherAppEntry app(String pkg, String label, int category) {
        return new LauncherAppEntry(new AppRef(pkg, "Main"), label, null, false, category, 0);
    }
}
