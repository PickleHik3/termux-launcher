package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.termux.app.launcher.icon.DockIconCache;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Wiring only: the byte budgeting, eviction and normalization live in
 * {@link com.termux.app.launcher.icon.DockIconCacheTest}. What matters here is that the dock owns
 * one {@link DockIconCache}, hands the drawer the same instances, and drops it when its artwork
 * assumptions change.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarIconCacheTest {

    private SuggestionBarView suggestionBarView;
    private DockIconCache iconCache;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        suggestionBarView = new SuggestionBarView(context, null);
        iconCache = ReflectionHelpers.getField(suggestionBarView, "iconCache");
    }

    private static LauncherAppEntry entry(String id) {
        return new LauncherAppEntry(new AppRef("com.example." + id, "Main"), id,
            new ColorDrawable(0xFF00FF00));
    }

    @Test
    public void theDrawerAndTheDockShareOneRenderedIcon() {
        LauncherAppEntry app = entry("a");

        Drawable rendered = suggestionBarView.getRenderedIcon(app, 48);

        assertSame(rendered, suggestionBarView.getRenderedIcon(app, 48));
        assertTrue(iconCache.sizeBytes() > 0);
    }

    @Test
    public void theExposedBudget_isTheLiveCacheBudget() {
        assertEquals(iconCache.budgetBytes(), suggestionBarView.getRenderedIconCacheBudgetBytes());
        assertTrue(iconCache.budgetBytes() >= 8 * 1024 * 1024);
        assertTrue(iconCache.budgetBytes() <= 32 * 1024 * 1024);
    }

    @Test
    public void clearAppCache_evictsRenderedIconBitmaps() {
        suggestionBarView.getRenderedIcon(entry("a"), 48);
        assertTrue(iconCache.sizeBytes() > 0);

        suggestionBarView.clearAppCache();

        assertEquals(0, iconCache.sizeBytes());
    }

    @Test
    public void persistedPinnedIconMutation_evictsRenderedIconBitmaps() {
        suggestionBarView.getRenderedIcon(entry("a"), 48);
        assertTrue(iconCache.sizeBytes() > 0);

        ReflectionHelpers.callInstanceMethod(suggestionBarView, "persistPinsAndReload");

        assertEquals(0, iconCache.sizeBytes());
    }
}
