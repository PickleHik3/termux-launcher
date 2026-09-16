package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
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
        View surface = activity.findViewById(R.id.terminal_surface_host);
        View bottom = activity.findViewById(R.id.place_edge_stack_bottom);
        assertEquals(3, column.getChildCount());
        assertSame(top, column.getChildAt(0));
        assertSame(surface, column.getChildAt(1));
        assertSame(bottom, column.getChildAt(2));
        // The terminal takes whatever the two stacks leave, which is what makes their thickness a
        // content inset without anyone having to pad for it.
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) surface.getLayoutParams();
        assertEquals(0, params.height);
        assertEquals(1f, params.weight, 0f);
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

    @Test
    public void theSideStacksStandBesideThePaddedContentRoot() {
        // Not inside it: a side stack occupies the display-cutout column the content root is
        // inset from, which is the whole reason it is a sibling rather than a child.
        TermuxActivity activity = inflate();
        ViewGroup container = activity.findViewById(R.id.terminal_root_container);
        View contentRoot = activity.findViewById(R.id.activity_termux_root_relative_layout);
        for (int id : new int[] {R.id.place_edge_stack_left, R.id.place_edge_stack_right}) {
            View stack = activity.findViewById(id);
            assertSame(container, stack.getParent());
            assertTrue("over the content root's ground",
                container.indexOfChild(stack) > container.indexOfChild(contentRoot));
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
        assertSame(activity.findViewById(R.id.place_edge_stack_top), host.getParent());
    }

    @Test
    public void aTopExtraKeysRowStandsInTheTopStack() {
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.EXTRA_KEYS, Edge.TOP));
        View host = activity.findViewById(R.id.place_extra_keys_host);
        assertSame(activity.findViewById(R.id.place_edge_stack_top), host.getParent());
    }

    @Test
    public void theBottomEdgeLeavesBothRowsToTheDock() {
        // The accessory stack is what keeps the bottom bars sitting above the in-app keyboard, so
        // a bottom slot must not pull either host into the bottom stack.
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.APPS, Edge.BOTTOM));
        EdgeStackView bottom = activity.findViewById(R.id.place_edge_stack_bottom);
        assertEquals(0, bottom.getChildCount());
    }

    @Test
    public void oneHostCrossesEveryEdgeRatherThanOnePerEdge() {
        TermuxActivity activity = inflate();
        View apps = activity.findViewById(R.id.place_apps_bar_host);
        View keys = activity.findViewById(R.id.place_extra_keys_host);
        for (Edge edge : new Edge[] {Edge.TOP, Edge.LEFT, Edge.RIGHT}) {
            activity.applyEdgeStacks(layoutWith(Element.APPS, edge));
            assertSame(edge + " apps", activity.findViewById(R.id.place_apps_bar_host), apps);
            assertSame(edge.toString(), stackOf(activity, edge), apps.getParent());
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
