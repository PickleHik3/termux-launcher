package com.termux.app.terminal;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A long press lifts a window's chip, a drag carries it along the bar, the release drops it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWindowBarReorderTest {

    private TerminalWindowBar mBar;
    private final List<int[]> mMoves = new ArrayList<>();
    private final AtomicInteger mSelected = new AtomicInteger(-1);
    private long mDownTime;

    @Before
    public void setUp() {
        // Attached, as on the phone: a chip's click and the long press both run on posts.
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        mBar = new TerminalWindowBar(activity, null);
        int rowHeight = Math.round(24f * activity.getResources().getDisplayMetrics().density);
        FrameLayout root = new FrameLayout(activity);
        root.addView(mBar, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
        activity.setContentView(root);
        mBar.setOnWindowSelectedListener(mSelected::set);
        mBar.setOnWindowChipMovedListener((from, to) -> mMoves.add(new int[]{from, to}));
        mBar.setWindows(Arrays.asList(
            new TerminalWindowBar.WindowItem("one", "one"),
            new TerminalWindowBar.WindowItem("two", "two"),
            new TerminalWindowBar.WindowItem("three", "three"),
            new TerminalWindowBar.WindowItem("float", "float")), 0);
        // The first three are the Workspace's windows; the fourth stands for a Home float.
        mBar.setMovableChipCount(3);
        ShadowLooper.idleMainLooper();
        assertTrue(chip(2).getRight() > 0);
    }

    @Test
    public void aLongPressAndADragPastOneChipMovesIt() {
        float start = centre(0);
        float past = centre(1) + dp(12);
        down(start);
        holdForLongPress();
        assertEquals(0, mBar.liftedChipIndex());
        move(start + (past - start) / 2);
        move(past);
        up(past);
        settle();

        assertEquals(1, mMoves.size());
        assertEquals(0, mMoves.get(0)[0]);
        assertEquals(1, mMoves.get(0)[1]);
        assertEquals(-1, mBar.liftedChipIndex());
        // A drop is not a tap: the selection did not move.
        assertEquals(-1, mSelected.get());
    }

    @Test
    public void aDragPastBothNeighboursLandsLast() {
        float start = centre(0);
        down(start);
        holdForLongPress();
        move(centre(1) + dp(12));
        move(centre(2) + dp(12));
        up(centre(2) + dp(12));
        settle();

        assertEquals(1, mMoves.size());
        assertEquals(2, mMoves.get(0)[1]);
    }

    @Test
    public void aDragThatComesBackReportsNothing() {
        float start = centre(0);
        down(start);
        holdForLongPress();
        move(centre(1) + dp(12));
        move(start);
        up(start);
        settle();

        assertTrue(mMoves.isEmpty());
        assertEquals(-1, mSelected.get());
        assertEquals(0f, chip(1).getTranslationX(), 0f);
        assertEquals(1f, chip(0).getScaleX(), 0f);
    }

    @Test
    public void aMostlyVerticalMoveReturnsTheChip() {
        float start = centre(0);
        down(start);
        holdForLongPress();
        move(start + dp(4), dp(80));
        // Back along the row past a neighbour: too late, the chip already went back.
        move(centre(1) + dp(12));
        up(centre(1) + dp(12));
        settle();

        assertTrue(mMoves.isEmpty());
    }

    @Test
    public void aTapStillTaps() {
        down(centre(1));
        up(centre(1));
        ShadowLooper.idleMainLooper();

        assertEquals(1, mSelected.get());
        assertTrue(mMoves.isEmpty());
    }

    @Test
    public void aHomeFloatsChipNeverLifts() {
        float start = centre(3);
        down(start);
        holdForLongPress();
        assertEquals(-1, mBar.liftedChipIndex());
        up(start);
        ShadowLooper.idleMainLooper();
        assertTrue(mMoves.isEmpty());
    }

    @Test
    public void aBarOfOneWindowLiftsNothing() {
        mBar.setWindows(Arrays.asList(new TerminalWindowBar.WindowItem("one", "one")), 0);
        mBar.setMovableChipCount(1);
        ShadowLooper.idleMainLooper();
        down(centre(0));
        holdForLongPress();
        assertEquals(-1, mBar.liftedChipIndex());
        up(centre(0));
    }

    private View chip(int index) {
        return ((LinearLayout) mBar.getChildAt(0)).getChildAt(index);
    }

    /** A chip's centre in the bar's own coordinates. */
    private float centre(int index) {
        View chip = chip(index);
        View strip = mBar.getChildAt(0);
        return strip.getLeft() + (chip.getLeft() + chip.getRight()) / 2f - mBar.getScrollX();
    }

    private float dp(float value) {
        return value * mBar.getResources().getDisplayMetrics().density;
    }

    private void holdForLongPress() {
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L,
            TimeUnit.MILLISECONDS);
    }

    private void settle() {
        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS);
    }

    private void down(float x) {
        mDownTime = SystemClock.uptimeMillis();
        send(MotionEvent.ACTION_DOWN, x, 0f);
    }

    private void move(float x) {
        move(x, 0f);
    }

    private void move(float x, float dy) {
        send(MotionEvent.ACTION_MOVE, x, dy);
        ShadowLooper.idleMainLooper(16L, TimeUnit.MILLISECONDS);
    }

    private void up(float x) {
        send(MotionEvent.ACTION_UP, x, 0f);
    }

    private void send(int action, float x, float dy) {
        float y = mBar.getHeight() / 2f + dy;
        MotionEvent event = MotionEvent.obtain(mDownTime, SystemClock.uptimeMillis(), action, x,
            y, 0);
        mBar.dispatchTouchEvent(event);
        event.recycle();
    }
}
