package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The float chips in the float layer: only a chip is held, so the page keeps the air between and
 * around them (its swipes and Home's focus release), a sideways swipe on a chip pages Home, and a
 * press that travels past the slop is no tap.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeFloatChipsViewTest {

    private HomeFloatLayer mLayer;
    private HomeFloatChipsView mChips;
    private final List<Integer> mTapped = new ArrayList<>();
    private int mPressesBeside;

    @Before
    public void setUp() {
        Context themed = new android.view.ContextThemeWrapper(
            RuntimeEnvironment.getApplication(), R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mLayer = new HomeFloatLayer(themed);
        mLayer.setListener(new HomeFloatLayer.Listener() {
            @Override public void onHomePageShown(int page) {}
            @Override public void onPressBesideFloats(float rawX, float rawY) { mPressesBeside++; }
        });
        mChips = new HomeFloatChipsView(themed);
        mChips.setListener(mTapped::add);
        mLayer.addView(mChips, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        mChips.setChips(Arrays.asList(
            HomeFloatChipsPolicy.Chip.of(" neovim", "neovim, minimised on page 1"),
            HomeFloatChipsPolicy.Chip.of(" home", "home, minimised on page 2")));
        mLayer.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        mLayer.layout(0, 0, 1000, 2000);
        mChips.setTranslationY(1500f);
    }

    @Test
    public void onlyAChipIsHeldAndTheAirBetweenIsThePages() {
        HomeFloatChipsPolicy.Layout row = mChips.layoutNow();
        assertEquals(2, row.shown);
        float y = rowY();
        assertTrue(mLayer.holdsPoint(chipX(0), y));
        assertTrue(mLayer.holdsPoint(chipX(1), y));
        float between = mChips.getLeft() + (row.right[0] + row.left[1]) / 2f;
        assertFalse("the gap between two chips", mLayer.holdsPoint(between, y));
        assertFalse("beside the row", mLayer.holdsPoint(mChips.getLeft() - 5f, y));
        assertFalse("above the row", mLayer.holdsPoint(chipX(0), y - mChips.getHeight()));
    }

    @Test
    public void aSidewaysSwipeOnAChipPagesHome() {
        assertTrue(mLayer.yieldsSidewaysSwipe(chipX(0), rowY()));
    }

    @Test
    public void aPressBetweenChipsIsAPressBesideTheFloats() {
        HomeFloatChipsPolicy.Layout row = mChips.layoutNow();
        float between = mChips.getLeft() + (row.right[0] + row.left[1]) / 2f;
        dispatch(mLayer, MotionEvent.ACTION_DOWN, between, rowY());
        assertEquals(1, mPressesBeside);
        dispatch(mLayer, MotionEvent.ACTION_DOWN, chipX(1), rowY());
        assertEquals("a press on a chip keeps the float's focus", 1, mPressesBeside);
    }

    @Test
    public void aTapRestoresButAPressThatTravelsPastTheSlopDoesNot() {
        float x = chipX(1) - mChips.getLeft();
        float y = mChips.getHeight() / 2f;
        touch(MotionEvent.ACTION_DOWN, x, y);
        touch(MotionEvent.ACTION_UP, x, y);
        assertEquals(Arrays.asList(1), mTapped);

        int slop = ViewConfiguration.get(mChips.getContext()).getScaledTouchSlop();
        touch(MotionEvent.ACTION_DOWN, x, y);
        touch(MotionEvent.ACTION_MOVE, x + slop + 2f, y);
        touch(MotionEvent.ACTION_MOVE, x, y);
        touch(MotionEvent.ACTION_UP, x, y);
        assertEquals("a swipe that came back is still no tap", Arrays.asList(1), mTapped);
    }

    @Test
    public void noChipsNoRow() {
        mChips.setChips(new ArrayList<>());
        assertEquals(View.GONE, mChips.getVisibility());
        assertFalse(mLayer.holdsPoint(500f, rowY()));
    }

    private float chipX(int index) {
        HomeFloatChipsPolicy.Layout row = mChips.layoutNow();
        return mChips.getLeft() + (row.left[index] + row.right[index]) / 2f;
    }

    private float rowY() {
        return mChips.getTop() + mChips.getTranslationY() + mChips.getHeight() / 2f;
    }

    private void touch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), action, x, y, 0);
        mChips.onTouchEvent(event);
        event.recycle();
    }

    private static void dispatch(@NonNull View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
}
