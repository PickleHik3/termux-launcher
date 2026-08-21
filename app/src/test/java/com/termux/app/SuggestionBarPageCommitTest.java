package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.animation.ValueAnimator;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A qualified page swipe is a decision, and the slide is only how it is shown. Interrupting the
 * slide — a new touch on the row, a reset from the host — must therefore land on the page the
 * swipe asked for, not back on the page it came from. Interrupting without idling first is what
 * makes these deterministic: the settle is still on its first frame, exactly where a second
 * finger or a host reset lands in practice.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class SuggestionBarPageCommitTest {

    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;
    private static final float TRAVEL = 300f;

    private Context context;
    private SuggestionBarView row;

    @Before
    public void setUp() {
        // Robolectric's default scale of zero would finish every settle inside the frame that
        // starts it, and an animation that cannot be interrupted cannot show this defect.
        setDurationScale(1f);
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        context.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
        row = new SuggestionBarView(context, null);
        row.addView(new View(context), new ViewGroup.LayoutParams(ROW_WIDTH / 2, ROW_HEIGHT));
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, ROW_WIDTH, ROW_HEIGHT);

        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            items.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        }
        ReflectionHelpers.setField(row, "pinnedItems", new ArrayList<>(items));
        ReflectionHelpers.setField(row, "maxButtonCount", 3);
        ReflectionHelpers.setField(row, "pinnedItemsPerPage", 3);
    }

    @After
    public void tearDown() {
        setDurationScale(0f);
    }

    @Test
    public void anUninterruptedSwipeLandsOnTheNextPage() {
        swipeLeft();
        idle();

        assertEquals(1, page());
    }

    @Test
    public void aTouchThatInterruptsTheSettleStillLandsOnTheSwipedPage() {
        swipeLeft();
        // Mid-settle, without idling: the slide is running and the page is not committed yet.
        assertNotNull(ReflectionHelpers.getField(row, "swipePreviewReboundAnimator"));
        assertEquals(0, page());

        dispatch(MotionEvent.ACTION_DOWN, 100f, 80f);

        assertEquals(1, page());
    }

    @Test
    public void aHostResetThatInterruptsTheSettleStillLandsOnTheSwipedPage() {
        swipeLeft();
        assertEquals(0, page());

        row.resetTransientVisualState();

        assertEquals(1, page());
    }

    private void swipeLeft() {
        dispatch(MotionEvent.ACTION_DOWN, 400f, 80f);
        dispatch(MotionEvent.ACTION_MOVE, 400f - TRAVEL, 80f);
        dispatch(MotionEvent.ACTION_UP, 400f - TRAVEL, 80f);
    }

    private int page() {
        return ReflectionHelpers.getField(row, "pinnedPageIndex");
    }

    private void dispatch(int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        row.dispatchTouchEvent(event);
        event.recycle();
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
    }

    private static void setDurationScale(float scale) {
        ReflectionHelpers.setStaticField(ValueAnimator.class, "sDurationScale", scale);
    }
}
