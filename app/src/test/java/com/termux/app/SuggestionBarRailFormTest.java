package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.dock.DockLayoutPolicy;
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

    private SuggestionBarView render(boolean vertical, int count, int width, int height) {
        SuggestionBarView bar = new SuggestionBarView(context, null);
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
        SuggestionBarView rail = new SuggestionBarView(context, null);
        rail.setVerticalForm(true);
        measureAndLayout(rail, railBarWidthPx(), RAIL_HEIGHT);
        assertTrue("a rail's own band is bounds enough", stableRenderBounds(rail));

        SuggestionBarView row = new SuggestionBarView(context, null);
        measureAndLayout(row, railBarWidthPx(), ROW_HEIGHT);
        assertFalse("lying down that is still a squeezed row", stableRenderBounds(row));

        SuggestionBarView stub = new SuggestionBarView(context, null);
        stub.setVerticalForm(true);
        measureAndLayout(stub, 1, 1);
        assertFalse("a column with no bounds at all is still refused", stableRenderBounds(stub));
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
    public void theRailIsOnePage() {
        SuggestionBarView rail = new SuggestionBarView(context, null);
        rail.setVerticalForm(true);
        rail.setMaxButtonCount(4);
        ReflectionHelpers.setField(rail, "pinnedItems", pinned(11));

        assertEquals("the column holds everything pinned", 11,
            (int) ReflectionHelpers.callInstanceMethod(rail, "computePinnedItemsPerPage"));
        assertEquals("so there is nothing left to page to", 1,
            (int) ReflectionHelpers.callInstanceMethod(rail, "getPinnedPagesCount"));
    }

    @Test
    public void reloadingWhileStandingUpKeepsTheColumn() {
        // `termux-reload-settings` comes back through reloadWithInput. It used to defer, leaving
        // the rail blank until something else rebuilt it.
        SuggestionBarView bar = new SuggestionBarView(context, null);
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
