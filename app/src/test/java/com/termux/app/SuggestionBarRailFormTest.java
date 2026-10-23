package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.launcher.drawer.DockRailScrollView;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rail is the pinned-apps row standing up: the same view, the same entries, turned on its
 * side. What that has to produce is a column of icons at a fixed pitch — the one
 * {@code DockLayoutPolicy} gives the rail — rather than a row of slots sharing a width.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarRailFormTest {

    private static final int RAIL_WIDTH = 160;
    private static final int RAIL_HEIGHT = 1200;
    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
    }

    private static List<LauncherAppEntry> entries(int count) {
        List<LauncherAppEntry> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(new LauncherAppEntry(new AppRef("com.example.app" + i, "Main"), "App " + i, null));
        return out;
    }

    /** A bar handed its sizes, as the dock hands them: a bar handed none draws no icons (#135). */
    private SuggestionBarView sized() {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        bar.setDockIconSizePx(Math.round(24f * bar.getResources().getDisplayMetrics().density));
        bar.setDockRailSizePx(DockLayoutPolicy.railIconSizePx(density()),
            DockLayoutPolicy.railSlotLengthPx(density()));
        return bar;
    }

    private SuggestionBarView render(boolean vertical, int count, int width, int height) {
        SuggestionBarView bar = sized();
        bar.setVerticalForm(vertical);
        bar.setMaxButtonCount(count);
        measureAndLayout(bar, width, height);
        ReflectionHelpers.callInstanceMethod(bar, "renderButtons",
            ClassParameter.from(List.class, entries(count)),
            ClassParameter.from(boolean.class, false));
        measureAndLayout(bar, width, height);
        return bar;
    }

    private static void measureAndLayout(SuggestionBarView bar, int width, int height) {
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, width, height);
    }

    private float density() {
        return context.getResources().getDisplayMetrics().density;
    }

    /**
     * The width a rail is actually given: {@code syncPinnedAppsHost} hands the host the rail's
     * band and pads it by the rail's own margin on each side, which leaves one icon.
     */
    private int railBarWidthPx() {
        int band = Math.round(density() * (DockLayoutPolicy.DOCK_RAIL_ICON_SIZE_DP
            + 2 * DockLayoutPolicy.DOCK_RAIL_EDGE_MARGIN_DP));
        return band - 2 * Math.round(density() * DockLayoutPolicy.DOCK_RAIL_EDGE_MARGIN_DP);
    }

    private static boolean stableRenderBounds(SuggestionBarView bar) {
        return ReflectionHelpers.callInstanceMethod(bar, "hasStableRenderBounds");
    }

    private static List<PinnedItem> pinned(int count) {
        List<PinnedItem> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        return out;
    }

    /** Gives the bar a dock's worth of pinned apps that resolve without a device catalogue. */
    private void seedPinned(SuggestionBarView bar, int count) {
        List<LauncherAppEntry> resolved = entries(count);
        Map<String, LauncherAppEntry> cache = ReflectionHelpers.getField(bar, "resolvedRefCache");
        for (LauncherAppEntry entry : resolved) cache.put(entry.appRef.stableId(), entry);
        List<PinnedItem> items = new ArrayList<>();
        for (LauncherAppEntry entry : resolved) items.add(new PinnedAppItem(entry.appRef));
        ReflectionHelpers.setField(bar, "pinnedItems", items);
        ReflectionHelpers.setField(bar, "allApps", new ArrayList<>(resolved));
    }

    private void assertIsAColumn(SuggestionBarView rail, int count) {
        assertEquals("every pinned icon is in the column", count, rail.getChildCount());
        int pitch = DockLayoutPolicy.railSlotLengthPx(density());
        for (int i = 0; i < rail.getChildCount(); i++) {
            View slot = rail.getChildAt(i);
            assertEquals("slot " + i + " top", rail.getChildAt(0).getTop() + i * pitch, slot.getTop());
            assertEquals("slot " + i + " left", rail.getChildAt(0).getLeft(), slot.getLeft());
        }
    }

    @Test
    public void theRailIsOneColumnOfSlots() {
        SuggestionBarView rail = render(true, 4, RAIL_WIDTH, RAIL_HEIGHT);
        assertEquals(1, rail.getColumnCount());
        assertEquals(4, rail.getRowCount());
        assertEquals(4, rail.getChildCount());
    }

    @Test
    public void railIconsStandAtTheRailsOwnPitchDownTheColumn() {
        SuggestionBarView rail = render(true, 4, RAIL_WIDTH, RAIL_HEIGHT);
        float density = context.getResources().getDisplayMetrics().density;
        int pitch = DockLayoutPolicy.railSlotLengthPx(density);
        assertTrue("a slot has to be worth measuring", pitch > 0);
        int firstTop = rail.getChildAt(0).getTop();
        for (int i = 0; i < rail.getChildCount(); i++) {
            View slot = rail.getChildAt(i);
            assertEquals("slot " + i + " top", firstTop + i * pitch, slot.getTop());
            assertEquals("slot " + i + " height", pitch, slot.getHeight());
            // One column: every icon starts at the same offset across the rail.
            assertEquals("slot " + i + " left", rail.getChildAt(0).getLeft(), slot.getLeft());
        }
    }

    @Test
    public void theRowStillLiesDownAndSharesItsWidth() {
        SuggestionBarView row = render(false, 4, ROW_WIDTH, ROW_HEIGHT);
        assertEquals(4, row.getColumnCount());
        assertEquals(1, row.getRowCount());
        int firstTop = row.getChildAt(0).getTop();
        for (int i = 1; i < row.getChildCount(); i++) {
            assertEquals("slot " + i + " top", firstTop, row.getChildAt(i).getTop());
            assertTrue("slot " + i + " is further along the row",
                row.getChildAt(i).getLeft() > row.getChildAt(i - 1).getLeft());
        }
    }

    @Test
    public void theFormIsTheSameViewTurned() {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        bar.setVerticalForm(true);
        assertTrue(bar.isVerticalForm());
        bar.setVerticalForm(false);
        assertEquals(false, bar.isVerticalForm());
    }

    // ------------------------------------------------ the band a rail is really given (defect 1)

    @Test
    public void aRailAsNarrowAsTheBandItIsGivenStillRenders() {
        // One icon wide is every rail there is on a phone. Judged by the lying-down row's width
        // floor it was never "stable", so each render deferred and the last row of the dock
        // stayed on screen squeezed into the column.
        SuggestionBarView rail = render(true, 5, railBarWidthPx(), RAIL_HEIGHT);
        assertIsAColumn(rail, 5);
    }

    @Test
    public void theRenderGateFollowsTheAxis() {
        SuggestionBarView rail = sized();
        rail.setVerticalForm(true);
        measureAndLayout(rail, railBarWidthPx(), RAIL_HEIGHT);
        assertTrue("a rail's own band is bounds enough", stableRenderBounds(rail));

        SuggestionBarView row = new SuggestionBarView(context, null);
        measureAndLayout(row, railBarWidthPx(), ROW_HEIGHT);
        assertFalse("lying down that is still a squeezed row", stableRenderBounds(row));

        SuggestionBarView stub = sized();
        stub.setVerticalForm(true);
        measureAndLayout(stub, 1, 1);
        assertFalse("a column with no bounds at all is still refused", stableRenderBounds(stub));

        SuggestionBarView unsized = new SuggestionBarView(context, null);
        unsized.setVerticalForm(true);
        measureAndLayout(unsized, railBarWidthPx(), RAIL_HEIGHT);
        assertFalse("a rail handed no size draws no icons, as a row", stableRenderBounds(unsized));
        assertEquals(0, (int) ReflectionHelpers.callInstanceMethod(unsized, "iconSizePx"));
    }

    @Test
    public void theDocksRowHeightNeverReachesTheRail() {
        // The dock hands its row height to whatever bar it can find, every styling reload
        // included. Standing up, the bar is sized by the rail's metrics and nothing else.
        SuggestionBarView rail = render(true, 4, railBarWidthPx(), RAIL_HEIGHT);
        rail.setDockRowHeightHintPx(Math.round(density() * 96f));
        measureAndLayout(rail, railBarWidthPx(), RAIL_HEIGHT);

        assertTrue(stableRenderBounds(rail));
        assertEquals(DockLayoutPolicy.railIconSizePx(density()),
            (int) ReflectionHelpers.callInstanceMethod(rail, "iconSizePx"));
        assertIsAColumn(rail, 4);
    }

    @Test
    public void theRailIsOneColumnOfEveryPinWhateverItsLength() {
        // #146: paged by its own length, a rail inside its scroll never learnt that its band had
        // shrunk, and ran its last slot on under the dock. Its host scrolls the one column now.
        SuggestionBarView rail = sized();
        rail.setVerticalForm(true);
        rail.setMaxButtonCount(4);
        ReflectionHelpers.setField(rail, "pinnedItems", pinned(11));
        int column = 5 * DockLayoutPolicy.railSlotLengthPx(density());
        measureAndLayout(rail, railBarWidthPx(), column);

        assertEquals("every pin in the one column", 11,
            (int) ReflectionHelpers.callInstanceMethod(rail, "computePinnedItemsPerPage"));
        assertEquals("one page", 1,
            (int) ReflectionHelpers.callInstanceMethod(rail, "getPinnedPagesCount"));
        assertFalse(rail.hasPinnedOverflowPages());
    }

    /**
     * A rail of {@code pins} pins with {@code extra} more apps installed, the most-used page as
     * {@code mostUsed} says, standing in its scroll at a band of three slots and rendered.
     */
    private DockRailScrollView railInScroll(int pins, int extra, boolean mostUsed) {
        SuggestionBarView rail = sized();
        List<LauncherAppEntry> apps = entries(pins + extra);
        Map<String, LauncherAppEntry> cache = ReflectionHelpers.getField(rail, "resolvedRefCache");
        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            cache.put(apps.get(i).appRef.stableId(), apps.get(i));
            if (i < pins) items.add(new PinnedAppItem(apps.get(i).appRef));
        }
        ReflectionHelpers.setField(rail, "pinnedItems", items);
        ReflectionHelpers.setField(rail, "allApps", new ArrayList<>(apps));
        rail.setMaxButtonCount(extra);
        rail.setMostUsedPageEnabled(mostUsed);
        rail.setVerticalForm(true);

        int slot = DockLayoutPolicy.railSlotLengthPx(density());
        DockRailScrollView scroll = new DockRailScrollView(context);
        scroll.setFillViewport(true);
        scroll.setRailSlotLengthPx(slot);
        scroll.addView(rail, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        measureAndLayout(scroll, railBarWidthPx(), 3 * slot);
        rail.reloadWithInput("", null);
        measureAndLayout(scroll, railBarWidthPx(), 3 * slot);
        return scroll;
    }

    /** How far a ScrollView lets its column travel: the column less the scroll's own inside. */
    private static int scrollRangePx(DockRailScrollView scroll) {
        int inside = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        return Math.max(0, scroll.getChildAt(0).getHeight() - inside);
    }

    private static void measureAndLayout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    @Test
    public void theRailStandsTheMostUsedAppsAfterItsLastPinWhereTheScrollReachesThem() {
        // The developer's call, 2026-10-23: the rail keeps the most-used page, at the end of its
        // one column, since a page turn there would be the scroll's own axis.
        DockRailScrollView scroll = railInScroll(7, 3, true);
        SuggestionBarView rail = (SuggestionBarView) scroll.getChildAt(0);
        int slot = DockLayoutPolicy.railSlotLengthPx(density());

        assertEquals("still one page", 1,
            (int) ReflectionHelpers.callInstanceMethod(rail, "getPinnedPagesCount"));
        assertFalse("which no page turn could reach", rail.hasPinnedOverflowPages());
        assertEquals("no trailing page to tick", -1, rail.getPinnedDynamicPageIndex());
        assertIsAColumn(rail, 10);
        assertEquals("ten slots: the pins, then the most-used apps",
            DockLayoutPolicy.railContentLengthPx(7, 3, slot), rail.getMeasuredHeight());
        List<LauncherAppEntry> mostUsed =
            ReflectionHelpers.callInstanceMethod(rail, "railMostUsedEntries");
        assertEquals(3, mostUsed.size());
        for (LauncherAppEntry entry : mostUsed) {
            assertTrue("none of them a pin: " + entry.label,
                Integer.parseInt(entry.label.substring("App ".length())) >= 7);
        }

        // The scroll stands three whole slots, and its end shows the last of them.
        assertEquals(3 * slot, scroll.getMeasuredHeight());
        assertEquals("the scroll travels the seven it does not show", 7 * slot,
            scrollRangePx(scroll));
        assertEquals("so its end shows the last most-used app",
            scrollRangePx(scroll) + scroll.getHeight(), rail.getChildAt(9).getBottom());
    }

    @Test
    public void withTheMostUsedPageOffTheRailIsItsPins() {
        DockRailScrollView scroll = railInScroll(7, 3, false);
        SuggestionBarView rail = (SuggestionBarView) scroll.getChildAt(0);
        int slot = DockLayoutPolicy.railSlotLengthPx(density());

        assertIsAColumn(rail, 7);
        assertEquals(DockLayoutPolicy.railContentLengthPx(7, 0, slot), rail.getMeasuredHeight());
        assertEquals("the scroll ends at the last pin", 4 * slot, scrollRangePx(scroll));
    }

    @Test
    public void lyingDownTheMostUsedAppsAreStillAPageOfTheirOwn() {
        SuggestionBarView row = sized();
        seedPinned(row, 4);
        List<LauncherAppEntry> apps = entries(7);
        ReflectionHelpers.setField(row, "allApps", new ArrayList<>(apps));
        row.setMaxButtonCount(4);
        row.setMostUsedPageEnabled(true);
        measureAndLayout(row, ROW_WIDTH, ROW_HEIGHT);
        row.reloadWithInput("", null);
        measureAndLayout(row, ROW_WIDTH, ROW_HEIGHT);

        assertEquals("the pins' page, then the most-used page", 2,
            (int) ReflectionHelpers.callInstanceMethod(row, "getPinnedPagesCount"));
        assertEquals(1, row.getPinnedDynamicPageIndex());
        assertEquals("the page on show is the pins alone", 4, row.getChildCount());
        assertTrue(((List<?>) ReflectionHelpers.callInstanceMethod(row, "railMostUsedEntries"))
            .isEmpty());
    }

    @Test
    public void reloadingWhileStandingUpKeepsTheColumn() {
        // `termux-reload-settings` comes back through reloadWithInput. It used to defer, leaving
        // the rail blank until something else rebuilt it.
        SuggestionBarView bar = sized();
        seedPinned(bar, 5);
        bar.setMaxButtonCount(5);
        measureAndLayout(bar, ROW_WIDTH, ROW_HEIGHT);
        bar.reloadWithInput("", null);
        measureAndLayout(bar, ROW_WIDTH, ROW_HEIGHT);
        assertEquals("the dock's row first", 5, bar.getChildCount());

        bar.setVerticalForm(true);
        measureAndLayout(bar, railBarWidthPx(), RAIL_HEIGHT);
        bar.reloadWithInput("", null);
        measureAndLayout(bar, railBarWidthPx(), RAIL_HEIGHT);

        assertIsAColumn(bar, 5);
    }

    @Test
    public void aRailWithNothingPinnedClaimsNothing() {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        ViewGroup.LayoutParams unused = bar.getLayoutParams();
        assertEquals(null, unused);
        assertEquals(false, bar.hasPinnedItems());
    }
}
