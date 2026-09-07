package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.terminal.TerminalWindowBar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The bar is moved, not rebuilt: one host and one set of views travel between the content column,
 * where a row lives, and the root container, where a column does.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarEdgeArrangementTest {

    private Activity mActivity;
    private StatusBarSwipeLayout mHost;
    private LinearLayout mColumn;
    private FrameLayout mContainer;
    private View mContentRoot;
    private View mRail;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mHost = new StatusBarSwipeLayout(mActivity, null);
        mHost.setId(R.id.terminal_window_bar_host);

        mColumn = new LinearLayout(mActivity);
        mColumn.setOrientation(LinearLayout.VERTICAL);
        mColumn.addView(mHost, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 96));
        View terminal = new View(mActivity);
        mColumn.addView(terminal, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mContainer = new FrameLayout(mActivity);
        mContentRoot = new View(mActivity);
        mContainer.addView(mContentRoot);
        mContainer.addView(mColumn);
        mRail = new View(mActivity);
        mContainer.addView(mRail);
        mActivity.setContentView(mContainer);
    }

    @Test public void aRowTakesTheTopOrTheBottomOfTheContentColumn() {
        StatusBarEdgeArrangement.moveHost(mHost, mColumn, mContainer, mContentRoot, Edge.TOP, 96);
        assertSame(mColumn, mHost.getParent());
        assertEquals(0, mColumn.indexOfChild(mHost));
        assertEquals(96, mHost.getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, mHost.getLayoutParams().width);

        StatusBarEdgeArrangement.moveHost(mHost, mColumn, mContainer, mContentRoot, Edge.BOTTOM, 96);
        assertSame(mColumn, mHost.getParent());
        assertEquals("last, so the terminal keeps the middle and the dock is right below",
            mColumn.getChildCount() - 1, mColumn.indexOfChild(mHost));
    }

    @Test public void aColumnStandsBesideTheContentRootInFrontOfTheRail() {
        StatusBarEdgeArrangement.moveHost(mHost, mColumn, mContainer, mContentRoot, Edge.RIGHT, 76);
        assertSame(mContainer, mHost.getParent());
        assertEquals(mContainer.indexOfChild(mContentRoot) + 1, mContainer.indexOfChild(mHost));
        assertEquals("in front of the rail, which draws over it where they share a column",
            true, mContainer.indexOfChild(mHost) < mContainer.indexOfChild(mRail));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mHost.getLayoutParams();
        assertEquals(76, params.width);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height);
        assertEquals(Gravity.END | Gravity.TOP, params.gravity);

        StatusBarEdgeArrangement.moveHost(mHost, mColumn, mContainer, mContentRoot, Edge.LEFT, 76);
        assertEquals(Gravity.START | Gravity.TOP,
            ((FrameLayout.LayoutParams) mHost.getLayoutParams()).gravity);

        // And back to a row, in the column it came from.
        StatusBarEdgeArrangement.moveHost(mHost, mColumn, mContainer, mContentRoot, Edge.TOP, 32);
        assertSame(mColumn, mHost.getParent());
        assertEquals(32, mHost.getLayoutParams().height);
    }

    @Test public void aColumnStandsTheSameContentOnItsSideAndTheRowStandsItBack() {
        LinearLayout row = new LinearLayout(mActivity);
        row.setId(R.id.terminal_status_row);
        row.setOrientation(LinearLayout.HORIZONTAL);
        mHost.addView(row, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 24));

        PlaceContentStrip strip = new PlaceContentStrip(mActivity, null);
        strip.setId(R.id.terminal_status_place_content);
        row.addView(strip, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TerminalWindowBar windows = new TerminalWindowBar(mActivity, null);
        windows.setId(R.id.terminal_window_bar);
        strip.addView(windows, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        StatusBarWindowColumn windowColumn = new StatusBarWindowColumn(mActivity, null);
        windowColumn.setId(R.id.terminal_status_window_column);
        windowColumn.setVisibility(View.GONE);
        strip.addView(windowColumn, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout stats = new LinearLayout(mActivity);
        stats.setId(R.id.terminal_status_stats_cluster);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        StatusBarWidgetView cpu = new StatusBarWidgetView(mActivity);
        stats.addView(cpu, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(stats, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        StatusBarEdgeArrangement.apply(mHost, Edge.LEFT);
        assertEquals(LinearLayout.VERTICAL, row.getOrientation());
        assertEquals(LinearLayout.VERTICAL, strip.getOrientation());
        assertEquals(LinearLayout.VERTICAL, stats.getOrientation());
        assertEquals(LinearLayout.VERTICAL, cpu.getOrientation());
        assertEquals("the row's pills make way for the chip column",
            View.GONE, windows.getVisibility());
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, stats.getLayoutParams().width);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, stats.getLayoutParams().height);

        StatusBarEdgeArrangement.apply(mHost, Edge.BOTTOM);
        assertEquals(LinearLayout.HORIZONTAL, row.getOrientation());
        assertEquals(LinearLayout.HORIZONTAL, strip.getOrientation());
        assertEquals(LinearLayout.HORIZONTAL, cpu.getOrientation());
        assertEquals("and the chip column makes way for the pills",
            View.GONE, windowColumn.getVisibility());
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, stats.getLayoutParams().width);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, stats.getLayoutParams().height);
    }


    @Test public void aBottomRowKeepsItsClockAtTheFootWhereTheRowIsNot() {
        // The row rides the bar's inner edge — the top of a bottom bar — so the widget slot has
        // to take the outer one, or the two share the same stretch and the clock wears the stats.
        FrameLayout slot = new FrameLayout(mActivity);
        slot.setId(R.id.terminal_top_widget_area);
        mHost.addView(slot, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 68, Gravity.TOP));

        StatusBarEdgeArrangement.apply(mHost, Edge.BOTTOM);
        assertEquals(Gravity.BOTTOM, ((FrameLayout.LayoutParams) slot.getLayoutParams()).gravity);
        assertEquals(View.VISIBLE, slot.getVisibility());

        StatusBarEdgeArrangement.apply(mHost, Edge.TOP);
        assertEquals(Gravity.TOP, ((FrameLayout.LayoutParams) slot.getLayoutParams()).gravity);
    }
}
