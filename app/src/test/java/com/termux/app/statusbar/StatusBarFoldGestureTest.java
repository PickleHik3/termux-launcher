package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The fold, driven through the real bar from the real screen: the same drag across the bar opens
 * and folds it on every edge that grows one, from anywhere along it — the panel the open bar grew
 * into as readily as the row. A bottom bar is the case this covers first: its panel stands where a
 * downward finger naturally lands, and a veto over that panel was what left it open for good.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StatusBarFoldGestureTest {

    /** The bar's thickness open and folded, in the same proportion the style gives it. */
    private static final int OPEN_PX = 96;
    private static final int FOLDED_PX = 28;
    private static final int RUN_PX = 1080;
    /** Far enough across to clear any touch slop the platform reports. */
    private static final int TRAVEL_PX = 70;

    private final List<Boolean> mRequests = new ArrayList<>();
    private StatusBarSwipeLayout mHost;

    /** Stands the real bar on {@code edge} in the given form and lays it out there. */
    private void stand(Edge edge, boolean folded) {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        mHost = activity.findViewById(R.id.terminal_window_bar_host);
        assertNotNull(mHost);
        mHost.setVisibility(View.VISIBLE);
        StatusBarEdgeArrangement.apply(mHost, edge);
        View slot = mHost.findViewById(R.id.terminal_top_widget_area);
        // The panel exists only while the bar is open, exactly as the activity shows and hides it.
        if (slot != null) slot.setVisibility(folded ? View.GONE : View.VISIBLE);
        mHost.setEdge(edge);
        mHost.setExpansionAllowed(StatusBarGesturePolicy.expansionAllowed(edge));
        mHost.setCollapsed(folded);
        mRequests.clear();
        mHost.setListener(mRequests::add);
        int thickness = folded ? FOLDED_PX : OPEN_PX;
        boolean vertical = StatusBarGesturePolicy.isVertical(edge);
        int width = vertical ? OPEN_PX : RUN_PX;
        int height = vertical ? RUN_PX : thickness;
        mHost.measure(exact(width), exact(height));
        mHost.layout(0, 0, width, height);
    }

    /** The middle of the bar's panel — the band the open bar grew into. */
    private float[] panelPoint() {
        View slot = mHost.findViewById(R.id.terminal_top_widget_area);
        assertNotNull("the open bar has a panel", slot);
        assertTrue("the panel is laid out", slot.getHeight() > 0);
        return new float[]{slot.getLeft() + slot.getWidth() / 2f,
            slot.getTop() + slot.getHeight() / 2f};
    }

    /** The middle of the status row, over the window strip. */
    private float[] rowPoint() {
        View row = mHost.findViewById(R.id.terminal_status_row);
        assertNotNull(row);
        assertTrue("the row is laid out", row.getHeight() > 0);
        return new float[]{row.getLeft() + row.getWidth() / 2f,
            row.getTop() + row.getHeight() / 2f};
    }

    /** Drags {@code delta} across the bar from {@code point} and returns what the listener heard. */
    private List<Boolean> dragAcross(Edge edge, float[] point, float delta) {
        mRequests.clear();
        boolean vertical = StatusBarGesturePolicy.isVertical(edge);
        float endX = vertical ? point[0] + delta : point[0];
        float endY = vertical ? point[1] : point[1] + delta;
        mHost.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, point[0], point[1]));
        mHost.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, endX, endY));
        mHost.dispatchTouchEvent(event(MotionEvent.ACTION_UP, endX, endY));
        return mRequests;
    }

    /** Towards the bar's own edge: the fold. */
    private static float foldDelta(Edge edge) {
        return -TRAVEL_PX * StatusBarGesturePolicy.expandSign(edge);
    }

    /** Away from it: the opening. */
    private static float openDelta(Edge edge) {
        return TRAVEL_PX * StatusBarGesturePolicy.expandSign(edge);
    }

    @Test public void anOpenRowBarFoldsFromItsPanelAndFromItsRowOnBothEdges() {
        for (Edge edge : new Edge[]{Edge.TOP, Edge.BOTTOM}) {
            stand(edge, false);
            assertEquals(edge + ": the fold starts in the panel",
                java.util.Collections.singletonList(true),
                dragAcross(edge, panelPoint(), foldDelta(edge)));

            stand(edge, false);
            assertEquals(edge + ": and on the row, over the window strip",
                java.util.Collections.singletonList(true),
                dragAcross(edge, rowPoint(), foldDelta(edge)));

            // Pushed further open there is nothing to do, from either place.
            stand(edge, false);
            assertTrue(edge + ": an already open bar cannot open further",
                dragAcross(edge, panelPoint(), openDelta(edge)).isEmpty());
        }
    }

    @Test public void aFoldedRowBarOpensFromItsRowAndNotBackwards() {
        for (Edge edge : new Edge[]{Edge.TOP, Edge.BOTTOM}) {
            stand(edge, true);
            assertEquals(edge + ": the folded bar opens away from its edge",
                java.util.Collections.singletonList(false),
                dragAcross(edge, rowPoint(), openDelta(edge)));

            stand(edge, true);
            assertTrue(edge + ": and has nothing to fold",
                dragAcross(edge, rowPoint(), foldDelta(edge)).isEmpty());
        }
    }

    @Test public void aBarDownASideNeverChangesForm() {
        for (Edge edge : new Edge[]{Edge.LEFT, Edge.RIGHT}) {
            for (boolean folded : new boolean[]{true, false}) {
                stand(edge, folded);
                float[] near = {mHost.getWidth() / 2f, 200f};
                float[] far = {mHost.getWidth() / 2f, 900f};
                assertTrue(edge + " folded=" + folded + ": inward does nothing",
                    dragAcross(edge, near, openDelta(edge)).isEmpty());
                assertTrue(edge + " folded=" + folded + ": outward does nothing",
                    dragAcross(edge, far, foldDelta(edge)).isEmpty());
            }
        }
    }

    @Test public void aChildThatScrollsAcrossTheBarKeepsItsOwnDrag() {
        // The narrow rule that replaced the panel veto: only a child answering drags on the fold's
        // own axis takes the stream. A card that scrolls across the bar is one; the window strip,
        // which scrolls along it, is not — the case above already folds the bar from over it.
        stand(Edge.BOTTOM, false);
        ScrollView card = new ScrollView(mHost.getContext());
        View tall = new View(mHost.getContext());
        // A scroll container measures its content unbounded, so the content's own minimum is what
        // gives it something to scroll.
        tall.setMinimumHeight(OPEN_PX * 4);
        card.addView(tall, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mHost.addView(card, new FrameLayout.LayoutParams(RUN_PX, OPEN_PX / 2));
        card.measure(exact(RUN_PX), exact(OPEN_PX / 2));
        card.layout(0, 0, RUN_PX, OPEN_PX / 2);

        assertTrue("the card's own scroll is not the bar's fold",
            dragAcross(Edge.BOTTOM, new float[]{RUN_PX / 2f, OPEN_PX / 4f},
                foldDelta(Edge.BOTTOM)).isEmpty());
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 10L, action, x, y, 0);
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
