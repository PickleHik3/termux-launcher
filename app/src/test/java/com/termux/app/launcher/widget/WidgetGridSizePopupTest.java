package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid-size wheels as a finger meets them: one number per step of drag, applied to the grid
 * straight away, and never past what the settings sliders allow.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetGridSizePopupTest {

    /** Every size the wheels reported, in order. */
    private static final class Sizes implements WidgetGridSizePopup.Listener {
        final List<String> log = new ArrayList<>();
        @Override public void onGridSizeChanged(int columns, int rows) {
            log.add(columns + "x" + rows);
        }
    }

    private static Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        return activity;
    }

    private static View page(Activity activity) {
        FrameLayout page = new FrameLayout(activity);
        activity.setContentView(page);
        page.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 600, 800);
        return page;
    }

    /** The wheels in the panel, columns first. */
    private static List<GridSizeWheelView> wheels(View content) {
        List<GridSizeWheelView> found = new ArrayList<>();
        collect(content, found);
        return found;
    }

    private static void collect(View view, List<GridSizeWheelView> out) {
        if (view instanceof GridSizeWheelView) out.add((GridSizeWheelView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
    }

    /** Drag a wheel by {@code dyPx} — negative is up — and leave the finger down. */
    private static void drag(GridSizeWheelView wheel, float dyPx) {
        send(wheel, MotionEvent.ACTION_DOWN, 0f);
        send(wheel, MotionEvent.ACTION_MOVE, dyPx);
        send(wheel, MotionEvent.ACTION_UP, dyPx);
    }

    private static void send(GridSizeWheelView wheel, int action, float dyPx) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, 10f, 40f + dyPx, 0);
        wheel.onTouchEvent(event);
        event.recycle();
    }

    private static WidgetGridSizePopup shown(Activity activity, View page, Sizes sizes,
                                             int columns, int rows) {
        return WidgetGridSizePopup.show(page, new RectF(500f, 0f, 590f, 32f), columns, rows, sizes);
    }

    @Test
    public void thePanelHoldsTwoWheelsReadingTheGridItOpenedOn() {
        Activity activity = activity();
        View page = page(activity);
        Sizes sizes = new Sizes();
        WidgetGridSizePopup popup = shown(activity, page, sizes, 4, 5);
        assertTrue(popup.isShowing());

        List<GridSizeWheelView> wheels = wheels(popup.content());
        assertEquals(2, wheels.size());
        assertEquals("columns", 4, wheels.get(0).value());
        assertEquals("rows", 5, wheels.get(1).value());
        assertNotNull(wheels.get(0).getContentDescription());
        assertEquals("Columns 4", wheels.get(0).getContentDescription().toString());
        assertEquals("Rows 5", wheels.get(1).getContentDescription().toString());
        assertEquals("opening changes nothing", 0, sizes.log.size());
    }

    @Test
    public void oneStepOfDragIsOneColumn_appliedAsTheFingerMoves() {
        Activity activity = activity();
        View page = page(activity);
        Sizes sizes = new Sizes();
        WidgetGridSizePopup popup = shown(activity, page, sizes, 4, 5);
        List<GridSizeWheelView> wheels = wheels(popup.content());
        float step = GridSizeWheelPolicy.STEP_DP
            * activity.getResources().getDisplayMetrics().density;

        drag(wheels.get(0), -step);
        assertEquals(5, wheels.get(0).value());
        assertEquals(java.util.Collections.singletonList("5x5"), sizes.log);
    }

    @Test
    public void aDragShorterThanAStepChangesNothing() {
        Activity activity = activity();
        View page = page(activity);
        Sizes sizes = new Sizes();
        WidgetGridSizePopup popup = shown(activity, page, sizes, 4, 5);
        List<GridSizeWheelView> wheels = wheels(popup.content());
        float step = GridSizeWheelPolicy.STEP_DP
            * activity.getResources().getDisplayMetrics().density;

        drag(wheels.get(0), -(step - 1f));
        assertEquals(4, wheels.get(0).value());
        assertEquals(0, sizes.log.size());
    }

    @Test
    public void theWheelsStopAtTheirLimits() {
        Activity activity = activity();
        View page = page(activity);
        Sizes sizes = new Sizes();
        WidgetGridSizePopup popup = shown(activity, page, sizes, 4, 5);
        List<GridSizeWheelView> wheels = wheels(popup.content());
        float step = GridSizeWheelPolicy.STEP_DP
            * activity.getResources().getDisplayMetrics().density;

        drag(wheels.get(0), -step * 40f);
        assertEquals("eight columns and no more", 8, wheels.get(0).value());
        drag(wheels.get(0), step * 40f);
        assertEquals("two columns and no fewer", 2, wheels.get(0).value());

        drag(wheels.get(1), -step * 40f);
        assertEquals("twelve rows and no more", 12, wheels.get(1).value());
        drag(wheels.get(1), step * 40f);
        assertEquals("two rows and no fewer", 2, wheels.get(1).value());

        assertEquals("the last word is the grid the wheels now read",
            "2x2", sizes.log.get(sizes.log.size() - 1));
    }

    @Test
    public void thePanelClosesWhenItIsPutAway() {
        Activity activity = activity();
        View page = page(activity);
        WidgetGridSizePopup popup = shown(activity, page, new Sizes(), 4, 5);
        popup.dismiss();
        assertTrue("dismissing twice is harmless", !popup.isShowing());
        popup.dismiss();
    }
}
