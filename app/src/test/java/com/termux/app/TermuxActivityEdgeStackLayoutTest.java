package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.place.EdgeStackView;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.Slot;

import java.util.EnumMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The screen the arrangement walk writes into: four stacks, one per edge, with the terminal left as
 * the residual centre between them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityEdgeStackLayoutTest {

    private TermuxActivity inflate() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }

    @Test
    public void everyEdgeHasAStackAndKnowsWhichOneItIs() {
        TermuxActivity activity = inflate();
        int[] ids = {R.id.place_edge_stack_top, R.id.place_edge_stack_bottom,
            R.id.place_edge_stack_left, R.id.place_edge_stack_right};
        Edge[] edges = {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT};
        for (int i = 0; i < ids.length; i++) {
            EdgeStackView stack = activity.findViewById(ids[i]);
            assertNotNull(edges[i].toString(), stack);
            assertEquals(edges[i], stack.getEdge());
            assertEquals(edges[i] + " axis", edges[i].isOnSide()
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL, stack.getOrientation());
        }
    }

    @Test
    public void theTerminalIsTheResidualBetweenTheTopAndBottomStacks() {
        TermuxActivity activity = inflate();
        LinearLayout column = activity.findViewById(R.id.terminal_content_column);
        View top = activity.findViewById(R.id.place_edge_stack_top);
        View band = activity.findViewById(R.id.terminal_canvas_band);
        View bottom = activity.findViewById(R.id.place_edge_stack_bottom);
        assertEquals(3, column.getChildCount());
        assertSame(top, column.getChildAt(0));
        assertSame(band, column.getChildAt(1));
        assertSame(bottom, column.getChildAt(2));
        // The canvas band takes whatever the two stacks leave, which is what makes their thickness
        // a content inset without anyone having to pad for it.
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) band.getLayoutParams();
        assertEquals(0, params.height);
        assertEquals(1f, params.weight, 0f);
    }

    @Test
    public void theSideStacksFlankTheCanvasAndNothingElse() {
        // The defect: standing outside the content column, a rail pushed the status bar's chips,
        // the dock's rows and the in-app keyboard sideways with it. Inside the band it flanks the
        // terminal alone, which is how the miniature has always drawn it.
        TermuxActivity activity = inflate();
        LinearLayout band = activity.findViewById(R.id.terminal_canvas_band);
        View left = activity.findViewById(R.id.place_edge_stack_left);
        View surface = activity.findViewById(R.id.terminal_surface_host);
        View right = activity.findViewById(R.id.place_edge_stack_right);
        assertEquals(LinearLayout.HORIZONTAL, band.getOrientation());
        assertEquals(3, band.getChildCount());
        assertSame(left, band.getChildAt(0));
        assertSame(surface, band.getChildAt(1));
        assertSame(right, band.getChildAt(2));
        // The canvas is the weighted residual of the band, so its inset from the band's own edges
        // IS EdgeStackPolicy.contentInsets' left and right — the two stacks' thickness — and the
        // cutout the padded content root keeps outside all of it is the rest of that answer.
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) surface.getLayoutParams();
        assertEquals(0, params.width);
        assertEquals(1f, params.weight, 0f);
    }

    @Test
    public void everyRowKeepsTheWholeWidthWhateverStandsOnASide() {
        TermuxActivity activity = inflate();
        View column = activity.findViewById(R.id.terminal_content_column);
        View root = activity.findViewById(R.id.activity_termux_root_relative_layout);
        // The two bands a side rail used to narrow: the top edge's stack, and the accessory stack
        // that carries the dock's rows and the in-app keyboard.
        View top = activity.findViewById(R.id.place_edge_stack_top);
        assertSame(column, top.getParent());
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, top.getLayoutParams().width);
        View accessory = activity.findViewById(R.id.accessory_stack_container);
        assertSame(root, accessory.getParent());
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, accessory.getLayoutParams().width);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, column.getLayoutParams().width);
    }

    @Test
    public void everyMovableBarStartsInAStack() {
        TermuxActivity activity = inflate();
        int[] hostIds = {R.id.terminal_window_bar_host, R.id.place_az_bar_host,
            R.id.place_extra_keys_host, R.id.place_apps_bar_host};
        for (int hostId : hostIds) {
            View host = activity.findViewById(hostId);
            assertNotNull(host);
            assertTrue(host + " is a stack's child",
                host.getParent() instanceof EdgeStackView);
        }
    }

    // ------------------------------------------------------------------ the walk

    /** Every element on the bottom edge but the one named, which is put on {@code edge}. */
    private static PlaceLayout layoutWith(Element moved, Edge edge) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            slots.put(element, Slot.on(element == moved ? edge : Edge.BOTTOM, element));
        }
        slots.put(Element.STATUS, Slot.on(moved == Element.STATUS ? edge : Edge.TOP, Element.STATUS));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    @Test
    public void aTopAppsRowStandsInTheTopStack() {
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.APPS, Edge.TOP));
        View host = activity.findViewById(R.id.place_apps_bar_host);
        // Through the plank it stands on, which is the top stack's child.
        assertSame(activity.findViewById(R.id.place_off_dock_plank_bars), host.getParent());
        assertTrue(standsIn(activity, Edge.TOP, host));
    }

    /** Whether the bar ends up inside that edge's stack, plank or no plank between them. */
    private static boolean standsIn(TermuxActivity activity, Edge edge, View bar) {
        View stack = stackOf(activity, edge);
        for (ViewParent parent = bar.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == stack) return true;
        }
        return false;
    }

    @Test
    public void aTopExtraKeysRowStandsInTheTopStack() {
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.EXTRA_KEYS, Edge.TOP));
        View host = activity.findViewById(R.id.place_extra_keys_host);
        assertSame(activity.findViewById(R.id.place_edge_stack_top), host.getParent());
    }

    @Test
    public void theBottomEdgeLeavesTheDocksOwnRowsToTheAccessoryStack() {
        // The accessory stack is what keeps the bottom bars sitting above the in-app keyboard, so
        // a bottom slot stands the rows in the ordered stack down there, not in the one above the
        // dock — which is the status bar's, and only the status bar's.
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.APPS, Edge.BOTTOM));
        EdgeStackView bottom = activity.findViewById(R.id.place_edge_stack_bottom);
        assertEquals(0, bottom.getChildCount());
        EdgeStackView rows = activity.findViewById(R.id.accessory_row_stack);
        assertEquals(3, rows.getChildCount());
        assertSame(activity.findViewById(R.id.apps_bar_row_host), rows.getChildAt(0));
    }

    @Test
    public void oneHostCrossesEveryEdgeRatherThanOnePerEdge() {
        TermuxActivity activity = inflate();
        View apps = activity.findViewById(R.id.place_apps_bar_host);
        View keys = activity.findViewById(R.id.place_extra_keys_host);
        for (Edge edge : new Edge[] {Edge.TOP, Edge.LEFT, Edge.RIGHT}) {
            activity.applyEdgeStacks(layoutWith(Element.APPS, edge));
            assertSame(edge + " apps", activity.findViewById(R.id.place_apps_bar_host), apps);
            assertTrue(edge.toString(), standsIn(activity, edge, apps));
            activity.applyEdgeStacks(layoutWith(Element.EXTRA_KEYS, edge));
            assertSame(edge + " keys", activity.findViewById(R.id.place_extra_keys_host), keys);
            assertSame(edge.toString(), stackOf(activity, edge), keys.getParent());
        }
    }

    private static View stackOf(TermuxActivity activity, Edge edge) {
        switch (edge) {
            case TOP: return activity.findViewById(R.id.place_edge_stack_top);
            case BOTTOM: return activity.findViewById(R.id.place_edge_stack_bottom);
            case LEFT: return activity.findViewById(R.id.place_edge_stack_left);
            default: return activity.findViewById(R.id.place_edge_stack_right);
        }
    }

    // ------------------------------------------------------------------ the shared plank

    /** Every element on the bottom edge, with the two named put on {@code edge}. */
    private static PlaceLayout layoutWith(Element first, Element second, Edge edge) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            boolean moved = element == first || element == second;
            slots.put(element, Slot.on(moved ? edge : Edge.BOTTOM, element));
        }
        if (first != Element.STATUS && second != Element.STATUS)
            slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    @Test
    public void aTopRowAndTheIndexRidingItShareOnePlank() {
        TermuxActivity activity = inflate();
        PlaceLayout layout = layoutWith(Element.APPS, Element.AZ, Edge.TOP);
        activity.applyEdgeStacks(layout);

        ViewGroup plankBars = activity.findViewById(R.id.place_off_dock_plank_bars);
        View plank = activity.findViewById(R.id.place_off_dock_plank_host);
        View apps = activity.findViewById(R.id.place_apps_bar_host);
        View index = activity.findViewById(R.id.place_az_bar_host);
        assertSame("the row is on the plank", plankBars, apps.getParent());
        assertSame("and so is the index riding it", plankBars, index.getParent());
        assertSame("the plank is what stands in the top stack",
            activity.findViewById(R.id.place_edge_stack_top), plank.getParent());
        // The index is the outer band of the two along the top, the way it is the band under the
        // row along the bottom.
        assertEquals(0, plankBars.indexOfChild(index));
        assertEquals(1, plankBars.indexOfChild(apps));

        // One sheet of glass, under both bars.
        assertNotNull(activity.findViewById(R.id.place_off_dock_plank_glass));
        assertNotNull(activity.findViewById(R.id.place_off_dock_plank_blur));
        assertNotNull(activity.findViewById(R.id.place_off_dock_plank_surface));
    }

    @Test
    public void thePlankIsGlazedAtTheDocksOwnCornerRadius() {
        TermuxActivity activity = inflate();
        PlaceLayout layout = layoutWith(Element.APPS, Element.AZ, Edge.TOP);
        activity.applyEdgeStacks(layout);
        assertTrue(activity.syncOffDockPlank(layout));

        View plank = activity.findViewById(R.id.place_off_dock_plank_host);
        assertEquals(View.VISIBLE, plank.getVisibility());
        View glass = activity.findViewById(R.id.place_off_dock_plank_glass);
        assertTrue(glass.getOutlineProvider()
            instanceof com.termux.app.statusbar.StatusBarSurfaceOutlineProvider);
        com.termux.app.statusbar.StatusBarSurfaceOutlineProvider outline =
            (com.termux.app.statusbar.StatusBarSurfaceOutlineProvider) glass.getOutlineProvider();

        int glassHeightPx = activity.offDockPlankGlassHeightPx();
        assertTrue("both bands are on the sheet", glassHeightPx > 0);
        assertEquals("the Appearance editor's dock radius, clamped to the whole plank",
            activity.resolveDockCapsuleCornerRadiusPx(glassHeightPx), outline.radiusPx(), 0.01f);
        // The defect: clamped to the index's own 19dp band the plank could never be rounder than
        // half of it, whatever the slider said.
        assertTrue("rounder than a half-capsule of one bar",
            outline.radiusPx() > activity.resolveDockCapsuleCornerRadiusPx(
                Math.round(activity.getResources().getDisplayMetrics().density * 19f)));
    }

    @Test
    public void anIndexOnAnotherEdgeKeepsItsOwnCapsuleAndTheRowKeepsThePlank() {
        TermuxActivity activity = inflate();
        PlaceLayout layout = layoutWith(Element.APPS, Edge.TOP)
            .withSlot(Element.AZ, Slot.on(Edge.LEFT, Element.AZ));
        activity.applyEdgeStacks(layout);

        View apps = activity.findViewById(R.id.place_apps_bar_host);
        View index = activity.findViewById(R.id.place_az_bar_host);
        assertSame("the row is alone on the plank",
            activity.findViewById(R.id.place_off_dock_plank_bars), apps.getParent());
        assertSame("the index stands in the left stack",
            activity.findViewById(R.id.place_edge_stack_left), index.getParent());
        assertEquals(View.VISIBLE,
            activity.findViewById(R.id.place_az_bar_host_glass).getVisibility());
    }

    @Test
    public void theDefaultArrangementNeverBuildsAPlank() {
        // Everything along the bottom is the dock's own, so the plank stays away and the top stack
        // holds nothing but the status bar — the shipped screen, untouched.
        TermuxActivity activity = inflate();
        PlaceLayout layout = layoutWith(Element.APPS, Edge.BOTTOM);
        activity.applyEdgeStacks(layout);
        activity.syncOffDockPlank(layout);

        View plank = activity.findViewById(R.id.place_off_dock_plank_host);
        assertEquals(View.GONE, plank.getVisibility());
        assertEquals("nothing was ever put on it", 0,
            ((ViewGroup) activity.findViewById(R.id.place_off_dock_plank_bars)).getChildCount());
        EdgeStackView top = activity.findViewById(R.id.place_edge_stack_top);
        assertSame(activity.findViewById(R.id.terminal_window_bar_host), top.getChildAt(0));
        assertEquals(0,
            ((EdgeStackView) activity.findViewById(R.id.place_edge_stack_bottom)).getChildCount());
    }

    @Test
    public void oneKeyViewIsLentBetweenThePagerAndThePortableHost() {
        // The pager's first page and the bar standing on another edge are the same instance, so a
        // latched modifier and the colours the Appearance editor picked survive the move.
        TermuxActivity activity = inflate();
        ExtraKeysView keys = activity.lendExtraKeysPage(0);
        assertNotNull(keys);
        ViewGroup host = activity.findViewById(R.id.place_extra_keys_host);
        host.addView(keys);
        assertSame(host, keys.getParent());

        ExtraKeysView again = activity.lendExtraKeysPage(0);
        assertSame("one view per page, lent rather than rebuilt", keys, again);
        assertNull("lending takes it out of whatever was holding it", again.getParent());
    }
}
