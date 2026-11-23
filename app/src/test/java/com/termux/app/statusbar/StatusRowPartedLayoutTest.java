package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The status row parted at the camera, built the way activity_termux.xml builds it (the strip's
 * slot, then terminal_status_widgets with its 10dp end margin and the end padding the controller
 * gives it, holding the readings cluster), at the widths tl16 measured: a 1080px window, corners
 * 47px, a camera hole at 506..574 with 11px air (gap 495..585), CPU 284, dot 13, RAM 128, dot 13,
 * Battery 138, dot 13, Weather 108, each with its margins.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusRowPartedLayoutTest {

    private static final int W = 1080;
    private static final int H = 2400;
    private static final int GAP_START = 495;
    private static final int GAP_END = 585;
    /** terminal_status_widgets' 10dp end margin at tl16's density. */
    private static final int GROUP_MARGIN_END = 26;
    /** Docked's content edge inset (3dp and half its corner) at tl16. */
    private static final int DOCKED_PADDING_END = 29;
    private static final int FLOATING_PADDING_END = 21;

    private Activity mActivity;
    private FrameLayout mWindow;
    private StatusRowLayout mRow;
    private View mSlot;
    private LinearLayout mGroup;
    private StatusReadingsCluster mCluster;
    private View mCpu;
    private View mRam;
    private View mBattery;
    private View mWeather;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mWindow = new FrameLayout(mActivity);
    }

    private void build(int paddingEnd, boolean rtl) {
        mRow = new StatusRowLayout(mActivity, null);
        mRow.setId(R.id.terminal_status_row);
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        if (rtl) mRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        FrameLayout sides = new FrameLayout(mActivity);
        sides.setId(R.id.terminal_status_sides);
        LinearLayout place = new LinearLayout(mActivity);
        place.setId(R.id.terminal_status_place_content);
        place.setPaddingRelative(37, 0, 0, 0);
        place.addView(new View(mActivity), new LinearLayout.LayoutParams(300,
            ViewGroup.LayoutParams.MATCH_PARENT));
        sides.addView(place, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mRow.addView(sides, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        mSlot = sides;

        mGroup = new LinearLayout(mActivity);
        mGroup.setId(R.id.terminal_status_widgets);
        mGroup.setOrientation(LinearLayout.HORIZONTAL);
        mGroup.setPaddingRelative(0, 0, paddingEnd, 0);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        groupParams.setMarginEnd(GROUP_MARGIN_END);
        mRow.addView(mGroup, groupParams);

        mCluster = new StatusReadingsCluster(mActivity, null);
        mCluster.setId(R.id.terminal_status_stats_cluster);
        mCluster.setOrientation(LinearLayout.HORIZONTAL);
        mGroup.addView(mCluster, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dot(View.GONE);
        mCpu = reading(276);
        dot(View.VISIBLE);
        mRam = reading(120);
        dot(View.VISIBLE);
        mBattery = reading(130);
        dot(View.VISIBLE);
        mWeather = reading(100);
    }

    private void dot(int visibility) {
        View dot = new MaterialDotSeparatorView(mActivity, null);
        dot.setVisibility(visibility);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(9, 9);
        params.setMarginStart(2);
        params.setMarginEnd(2);
        mCluster.addView(dot, params);
    }

    private View reading(int width) {
        View view = new View(mActivity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,
            ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMarginStart(8);
        mCluster.addView(view, params);
        return view;
    }

    private void stand(int left, int top, int right, int height,
                       StatusRowKeepOut.Mode mode) {
        mRow.setScreenKeepOut(new StatusRowKeepOut.Screen(true, 506, 34, 574, 102, 136,
            47f, 47f, 48f, 48f, mode, 0, 0, 100, 11, 60));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.leftMargin = left;
        params.rightMargin = W - right;
        params.topMargin = top;
        mWindow.addView(mRow, params);
        for (int pass = 0; pass < 2; pass++) {
            mWindow.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
            mWindow.layout(0, 0, W, H);
        }
    }

    /** x of a view's left edge in the row. */
    private int leftInRow(View view) {
        int x = 0;
        for (View v = view; v != mRow; v = (View) v.getParent()) x += v.getLeft();
        return x;
    }

    private int rightInRow(View view) {
        return leftInRow(view) + view.getWidth();
    }

    private boolean stands(View reading) {
        return reading.getWidth() > 0 && !mCluster.isLeftOut(reading)
            && rightInRow(reading) <= rightInRow(mCluster);
    }

    /**
     * Docked in Full screen on tl16: the row runs the whole width at y 10..73, the corner steps
     * its end in 19px. That inset is already covered by the readings' own end margin and padding
     * (55px), so stacking it on them took 19px from the readings and left RAM out by 4px.
     */
    @Test public void dockedKeepsEveryReadingThatFitsAfterTheCamera() {
        build(DOCKED_PADDING_END, false);
        stand(0, 10, W, 63, StatusRowKeepOut.Mode.AUTO);
        assertEquals(GAP_START, mRow.keepOut().gapStartPx);
        assertEquals(GAP_END, mRow.keepOut().gapEndPx);
        assertTrue(rightInRow(mSlot) <= GAP_START);
        assertTrue(leftInRow(mGroup) >= GAP_END);
        assertTrue(stands(mCpu));
        assertTrue(stands(mRam));
        assertFalse(stands(mBattery));
        assertFalse(stands(mWeather));
        assertEquals(3, mCluster.kept());
        // The readings end where they do with Full screen off: their own end margin and padding.
        assertEquals(W - GROUP_MARGIN_END - DOCKED_PADDING_END, rightInRow(mRam));
    }

    /** Floating on tl16: the card's row at 21..1059, already clear of the corner. */
    @Test public void floatingKeepsCpuAndRam() {
        build(FLOATING_PADDING_END, false);
        stand(21, 31, W - 21, 58, StatusRowKeepOut.Mode.AUTO);
        int gapStart = GAP_START - 21;
        int gapEnd = GAP_END - 21;
        assertTrue(rightInRow(mSlot) <= gapStart);
        assertTrue(leftInRow(mGroup) >= gapEnd);
        assertTrue(stands(mCpu));
        assertTrue(stands(mRam));
        assertFalse(stands(mBattery));
        assertFalse(stands(mWeather));
    }

    /** Off: no gap, so no parting; every reading stands, as before. */
    @Test public void offKeepsEveryReading() {
        build(DOCKED_PADDING_END, false);
        stand(0, 10, W, 63, StatusRowKeepOut.Mode.OFF);
        assertEquals(StatusReadingsCluster.ALL, mCluster.kept());
        assertTrue(stands(mCpu));
        assertTrue(stands(mRam));
        assertTrue(stands(mBattery));
        assertTrue(stands(mWeather));
        // The corner inset still stacks on the readings' end margin and padding there.
        assertEquals(W - 19 - GROUP_MARGIN_END - DOCKED_PADDING_END, rightInRow(mWeather));
    }

    /** Right to left: the chips stand right of the camera, the readings left of it. */
    @Test public void rightToLeftMirrorsTheParting() {
        build(DOCKED_PADDING_END, true);
        stand(0, 10, W, 63, StatusRowKeepOut.Mode.AUTO);
        assertTrue(leftInRow(mSlot) >= GAP_END);
        assertTrue(rightInRow(mGroup) <= GAP_START);
        assertTrue(mCpu.getWidth() > 0 && !mCluster.isLeftOut(mCpu));
        assertTrue(mRam.getWidth() > 0 && !mCluster.isLeftOut(mRam));
        assertTrue(mCluster.isLeftOut(mBattery));
        assertTrue(mCluster.isLeftOut(mWeather));
        assertEquals(GROUP_MARGIN_END + DOCKED_PADDING_END, leftInRow(mCluster));
    }
}
