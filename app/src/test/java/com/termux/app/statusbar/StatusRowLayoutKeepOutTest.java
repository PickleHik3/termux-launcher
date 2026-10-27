package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The status row applying its keep-out (#163): a flexible strip and a fixed reading in a
 * 1080 × 2400 window whose camera hole sits at 500..580 × 0..90 and whose corners are 100px.
 * The row works out where it stands in its own layout pass and lays its children out around the
 * hole, without asking its parent for anything.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusRowLayoutKeepOutTest {

    private static final int W = 1080;
    private static final int H = 2400;
    private static final int MARGIN = 11;

    private FrameLayout mWindow;
    private StatusRowLayout mRow;
    private View mStrip;
    private View mReading;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        mWindow = new FrameLayout(activity);
        mRow = new StatusRowLayout(activity, null);
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        mStrip = new View(activity);
        mRow.addView(mStrip, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        mReading = new View(activity);
        LinearLayout.LayoutParams reading = new LinearLayout.LayoutParams(200,
            ViewGroup.LayoutParams.MATCH_PARENT);
        reading.rightMargin = 30;
        mRow.addView(mReading, reading);
    }

    private void standAt(int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 66);
        params.topMargin = top;
        if (mRow.getParent() == null) mWindow.addView(mRow, params);
        else mRow.setLayoutParams(params);
        mWindow.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        mWindow.layout(0, 0, W, H);
    }

    private static StatusRowKeepOut.Screen screen(StatusRowKeepOut.Mode mode) {
        return new StatusRowKeepOut.Screen(true, 500, 0, 580, 90, 80, 100f, 100f, 100f, 100f,
            mode, 0, 0, MARGIN);
    }

    @Test public void withoutAScreenTheRowIsAPlainLinearLayout() {
        standAt(10);
        assertEquals(0, mStrip.getLeft());
        assertEquals(mStrip.getRight(), mReading.getLeft());
        assertEquals(W - 30, mReading.getRight());
        assertTrue(mRow.keepOut().isNone());
    }

    @Test public void inTheCameraBandTheChipsStopAtTheHoleAndTheEndsClearTheCorners() {
        mRow.setScreenKeepOut(screen(StatusRowKeepOut.Mode.AUTO));
        standAt(10);
        assertEquals(new StatusRowKeepOut.Answer(57, 57, 500 - MARGIN, 580 + MARGIN),
            mRow.keepOut());
        assertEquals(57, mStrip.getLeft());
        assertEquals(500 - MARGIN, mStrip.getRight());
        assertEquals(500 - MARGIN - 57, mStrip.getMeasuredWidth());
        assertEquals(W - 57 - 30, mReading.getRight());
        assertTrue(mReading.getLeft() >= 580 + MARGIN);
        // The row's own size never moves, so its parent has nothing to redo.
        assertEquals(W, mRow.getWidth());
    }

    @Test public void belowTheBandTheRowLaysOutAsItAlwaysDid() {
        mRow.setScreenKeepOut(screen(StatusRowKeepOut.Mode.AUTO));
        standAt(10);
        standAt(200);
        assertTrue(mRow.keepOut().isNone());
        assertEquals(0, mStrip.getLeft());
        assertEquals(mStrip.getRight(), mReading.getLeft());
        assertEquals(W - 30, mReading.getRight());
    }

    @Test public void offKeepsTheCornersButDropsTheGap() {
        mRow.setScreenKeepOut(screen(StatusRowKeepOut.Mode.OFF));
        standAt(10);
        assertEquals(57, mStrip.getLeft());
        assertEquals(mStrip.getRight(), mReading.getLeft());
        assertEquals(W - 57 - 30, mReading.getRight());
    }

    /**
     * Full screen on the developer's phone (pong, 1080 × 2412, corners 115px, camera box
     * 454..540 × 0..126, 4dp at 2.625 = 11px air): the system bar hides and the pane holding the
     * row moves up from under it to the screen's top. The row's own frame inside the pane does not
     * change, so nothing lays the row out again; it must still find the corners it now runs into.
     */
    @Test public void aRowWhosePaneMovesUpUnderTheCornersStepsItsEndsIn() {
        int w = 1080;
        int h = 2412;
        FrameLayout pane = new FrameLayout(mWindow.getContext());
        FrameLayout.LayoutParams paneParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 90);
        paneParams.topMargin = 126;
        mWindow.addView(pane, paneParams);
        pane.addView(mRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 90));
        mRow.setScreenKeepOut(new StatusRowKeepOut.Screen(true, 454, 0, 540, 126, 126,
            115f, 115f, 115f, 115f, StatusRowKeepOut.Mode.AUTO, 0, 0, 11));
        layoutWindow(w, h);
        assertTrue(mRow.keepOut().isNone());

        // The bars hide: only the pane's place in the window changes.
        paneParams.topMargin = 0;
        pane.setLayoutParams(paneParams);
        layoutWindow(w, h);
        mRow.onWindowLaidOut();
        layoutWindow(w, h);

        assertEquals(new StatusRowKeepOut.Answer(115, 115, 454 - 11, 540 + 11), mRow.keepOut());
        assertEquals(115, mStrip.getLeft());
        assertEquals(w - 115 - 30, mReading.getRight());
    }

    private void layoutWindow(int w, int h) {
        mWindow.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        mWindow.layout(0, 0, w, h);
    }
}
