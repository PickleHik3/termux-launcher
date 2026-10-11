package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.terminal.TerminalWindowBar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The status row as activity_termux.xml builds it: the place strip (badge, window chips), the
 * CPU/RAM/weather cluster and the end widgets, on a 411dp phone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h900dp-xxhdpi")
public class StatusRowLayoutTest {

    private Activity mActivity;
    private StatusRowLayout mRow;
    private PlaceContentStrip mStrip;
    private TerminalWindowBar mBar;
    private LinearLayout mCluster;
    private StatusBarWidgetView mCpu;
    private MaterialDotSeparatorView mCpuRamDot;
    private StatusBarWidgetView mRam;
    private LinearLayout mEndWidgets;
    private MaterialDotSeparatorView mAiDot;
    private StatusBarWidgetView mAi;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mRow = new StatusRowLayout(mActivity, null);
        mRow.setId(R.id.terminal_status_row);
        mRow.setOrientation(LinearLayout.HORIZONTAL);

        mStrip = new PlaceContentStrip(mActivity, null);
        mStrip.setId(R.id.terminal_status_place_content);
        mStrip.setPaddingRelative(dp(10), 0, 0, 0);
        mRow.addView(mStrip, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        View badge = new View(mActivity);
        mStrip.addView(badge, new LinearLayout.LayoutParams(dp(20),
            ViewGroup.LayoutParams.MATCH_PARENT));
        mBar = new TerminalWindowBar(mActivity, null);
        mBar.setId(R.id.terminal_window_bar);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        barParams.setMarginStart(dp(6));
        mStrip.addView(mBar, barParams);

        mCluster = new LinearLayout(mActivity);
        mCluster.setId(R.id.terminal_status_stats_cluster);
        mRow.addView(mCluster, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mCpu = reading(mCluster, 0);
        mCpu.setValue("12%");
        mCpuRamDot = dot(mCluster);
        mRam = reading(mCluster, dp(3));
        mRam.setValue("48%");

        mEndWidgets = new LinearLayout(mActivity);
        mEndWidgets.setId(R.id.terminal_status_widgets);
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        endParams.setMarginEnd(dp(10));
        mRow.addView(mEndWidgets, endParams);
        mAiDot = dot(mEndWidgets);
        mAi = reading(mEndWidgets, dp(3));
        mAi.setValue("9:41");

        mActivity.setContentView(mRow, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
    }

    @Test public void theChipsRunUpToTheFirstReadingLessOneGap() {
        windows(2);
        layOut();
        assertEquals(dp(StatusRowFitPolicy.CHIPS_READINGS_GAP_DP), firstInk(mCpu) - chipsEnd());
    }

    @Test public void aReadingWhoseOwnInsetIsWiderThanTheGapKeepsItAndTheChipsStopAtItsEdge() {
        // CPU off: RAM leads, with its 3dp margin and 5dp padding before its ink.
        mCpu.setVisibility(View.GONE);
        mCpuRamDot.setVisibility(View.GONE);
        windows(2);
        layOut();
        assertEquals(0, mBar.getPaddingEnd());
        assertEquals(dp(3) + dp(5), firstInk(mRam) - chipsEnd());
    }

    @Test public void withNoReadingsTheChipsRunToTheRowsOwnEnd() {
        mCpu.setVisibility(View.GONE);
        mCpuRamDot.setVisibility(View.GONE);
        mRam.setVisibility(View.GONE);
        mAiDot.setVisibility(View.GONE);
        mAi.setVisibility(View.GONE);
        windows(2);
        layOut();
        assertEquals(0, mBar.getPaddingEnd());
    }

    @Test public void aColumnLeavesTheChipsAlone() {
        windows(2);
        layOut();
        int before = mBar.getPaddingEnd();
        mRow.setOrientation(LinearLayout.VERTICAL);
        mCpu.setVisibility(View.GONE);
        layOut();
        assertEquals(before, mBar.getPaddingEnd());
    }

    @Test public void aRowWithRoomKeepsItsDotsAndItsSpacing() {
        windows(2);
        layOut();
        assertFalse(mRow.isReadingsTight());
        assertEquals(dp(3), mCpuRamDot.getMeasuredWidth());
        assertEquals(dp(5), mCpu.getPaddingLeft());
        assertEquals(dp(5), mRam.getPaddingLeft());
    }

    @Test public void chipsOutOfRoomStandTheReadingsTightAndTakeWhatThatFrees() {
        windows(2);
        layOut();
        int spaciousBar = mBar.getWidth();
        int spaciousReadings = mCluster.getWidth() + mEndWidgets.getWidth();

        windows(30);
        layOut();

        assertTrue(mRow.isReadingsTight());
        assertEquals("no dots", 0, mCpuRamDot.getMeasuredWidth());
        assertEquals(0, mAiDot.getMeasuredWidth());
        assertEquals("less room around each reading", dp(2), mRam.getPaddingLeft());
        int freed = spaciousReadings - (mCluster.getWidth() + mEndWidgets.getWidth());
        assertEquals("two dots and three readings' worth of room",
            2 * dp(3) + 3 * 2 * (dp(5) - dp(2)), freed);
        // The chips get it all, less what the bar's end padding now holds of the gap.
        assertEquals(spaciousBar + freed, mBar.getWidth());
        assertEquals("still one gap before the first reading",
            dp(StatusRowFitPolicy.CHIPS_READINGS_GAP_DP), firstInk(mCpu) - chipsEnd());
    }

    @Test public void theReadingsStandBackOnceTheChipsFitAgain() {
        windows(30);
        layOut();
        assertTrue(mRow.isReadingsTight());

        windows(2);
        layOut();

        assertFalse(mRow.isReadingsTight());
        assertEquals(dp(3), mCpuRamDot.getMeasuredWidth());
        assertEquals(dp(5), mCpu.getPaddingLeft());
        assertEquals(dp(StatusRowFitPolicy.CHIPS_READINGS_GAP_DP), firstInk(mCpu) - chipsEnd());
    }

    @Test public void aColumnStandsTheReadingsAsAlways() {
        windows(30);
        layOut();
        assertTrue(mRow.isReadingsTight());

        mRow.setOrientation(LinearLayout.VERTICAL);
        layOut();

        assertFalse(mRow.isReadingsTight());
        assertEquals(dp(3), mCpuRamDot.getMeasuredWidth());
    }

    @Test public void aDecisionMeasuredTwiceIsTheSameDecision() {
        windows(30);
        layOut();
        layOut();
        assertTrue(mRow.isReadingsTight());
        windows(2);
        layOut();
        layOut();
        assertFalse(mRow.isReadingsTight());
    }

    // ---- helpers ----

    private StatusBarWidgetView reading(LinearLayout parent, int marginStart) {
        StatusBarWidgetView widget = new StatusBarWidgetView(mActivity);
        widget.setIconGlyph("");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMarginStart(marginStart);
        parent.addView(widget, params);
        return widget;
    }

    private MaterialDotSeparatorView dot(LinearLayout parent) {
        MaterialDotSeparatorView dot = new MaterialDotSeparatorView(mActivity, null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(3), dp(3));
        params.setMarginStart(dp(1));
        params.setMarginEnd(dp(1));
        parent.addView(dot, params);
        return dot;
    }

    void windows(int count) {
        List<TerminalWindowBar.WindowItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new TerminalWindowBar.WindowItem("window " + i, "window " + i));
        }
        mBar.setWindows(items, 0);
    }

    void layOut() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        mRow.measure(View.MeasureSpec.makeMeasureSpec(dp(411), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(24), View.MeasureSpec.EXACTLY));
        mRow.layout(0, 0, dp(411), dp(24));
    }

    /** Where the chips' viewport ends, in the row's coordinates. */
    int chipsEnd() {
        return mStrip.getLeft() + mBar.getRight() - mBar.getPaddingRight();
    }

    /** Where a reading's icon starts, in the row's coordinates. */
    int firstInk(StatusBarWidgetView reading) {
        View parent = (View) reading.getParent();
        return parent.getLeft() + reading.getLeft() + reading.getPaddingLeft();
    }

    int dp(float value) {
        return Math.round(value * mActivity.getResources().getDisplayMetrics().density);
    }
}
