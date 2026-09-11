package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.termux.R;
import com.termux.app.launcher.widget.WidgetEditOverlayView;
import com.termux.app.launcher.widget.WidgetPaneView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Which touches the Widgets page keeps for itself. The band along its border drops the page's own
 * tab, exactly as the Display page's does — and everything inside that band stays the widgets'
 * and the edit overlay's, or a widget that reaches the frame's edge would stop answering.
 *
 * <p>Whether the tab is out is asked the only way a finger can ask it: by tapping where one of
 * its buttons would be and seeing whether the page ran it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPaneFrameTapTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;
    /** The same band the Display page's frame keeps. */
    private static final float BAND_DP = 12f;
    /** The tab: two 30dp buttons 8dp apart, 5dp of padding, 3dp in from the trailing edge. */
    private static final float TAB_WIDTH_DP = 78f;
    private static final float TAB_INSET_DP = 3f;
    /** Where the pair is split: the middle of the gap between the two buttons. */
    private static final float TAB_SPLIT_DP = 39f;

    /** What the page asked the launcher for, in order, over a 4 x 5 grid. */
    private static class Calls implements WidgetPaneFrame.Host {
        final List<String> log = new ArrayList<>();
        int columns = 4;
        int rows = 5;
        @Override public void openWidgetGridSettings() { log.add("settings"); }
        @Override public void editWidgets() { log.add("edit"); }
        @Override public int widgetGridColumns() { return columns; }
        @Override public int widgetGridRows() { return rows; }
        @Override public void setWidgetGrid(int newColumns, int newRows) {
            columns = newColumns;
            rows = newRows;
            log.add("grid " + newColumns + "x" + newRows);
        }
    }

    private static WidgetPaneFrame page(Activity activity) {
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneFrame page = (WidgetPaneFrame) LayoutInflater.from(activity)
            .inflate(R.layout.view_widget_pane, null);
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        return page;
    }

    private static float density(Activity activity) {
        return activity.getResources().getDisplayMetrics().density;
    }

    /** The left half of the tab: the settings cog. */
    private static float cogX(Activity activity) {
        float density = density(activity);
        return WIDTH - (TAB_INSET_DP + TAB_WIDTH_DP) * density + (TAB_SPLIT_DP - 8f) * density;
    }

    /** The right half of the tab: the edit pencil. */
    private static float pencilX(Activity activity) {
        float density = density(activity);
        return WIDTH - (TAB_INSET_DP + TAB_WIDTH_DP) * density + (TAB_SPLIT_DP + 8f) * density;
    }

    private static float tabCentreY(Activity activity) {
        return 16f * density(activity);
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private static void touch(WidgetPaneFrame page, int action, float x, float y) {
        MotionEvent event = event(action, x, y);
        page.dispatchTouchEvent(event);
        event.recycle();
    }

    /** A finger down and up in the same spot, with the tab's motion allowed to finish. */
    private static void tap(WidgetPaneFrame page, float x, float y) {
        touch(page, MotionEvent.ACTION_DOWN, x, y);
        touch(page, MotionEvent.ACTION_UP, x, y);
        // The tab's reveal and retract are 190 ms, and nothing on it answers mid-motion.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
    }

    /** A tap on the page's border band. */
    private static void tapBorder(WidgetPaneFrame page) {
        tap(page, 2f, HEIGHT / 2f);
    }

    private static boolean intercepts(WidgetPaneFrame page, float x, float y) {
        MotionEvent down = event(MotionEvent.ACTION_DOWN, x, y);
        boolean claimed = page.onInterceptTouchEvent(down);
        down.recycle();
        MotionEvent cancel = event(MotionEvent.ACTION_CANCEL, x, y);
        page.onTouchEvent(cancel);
        cancel.recycle();
        return claimed;
    }

    /**
     * The grid's outermost cells are only its own 6dp padding from the page's rim, so a top-row
     * widget's remove chip lives inside the page's 12dp border band. The page took that press as
     * a border tap and the widget could not be removed.
     */
    @Test
    public void aWidgetsOwnEditChipIsNotSwallowedByTheBorderBand() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        WidgetPaneView pane = (WidgetPaneView) page.grid();
        float density = density(activity);
        // The overlay is created lazily, so the page is laid out again with it in place.
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);

        int pad = Math.round(6f * density);
        overlay.show(new Rect(pad, pad, Math.round(200 * density), Math.round(120 * density)),
            true, true);
        page.applyWidgetEditing(true);

        // The top of the chip, where a thumb reaching for it lands: inside the band, and the
        // overlay's own hit test says it is the chip's.
        float chipX = pad + 15f * density;
        float chipY = 8f * density;
        assertTrue("precondition: the press is inside the page's border band",
            Math.min(chipX, chipY) <= BAND_DP * density);
        assertTrue("precondition: the overlay claims this point",
            overlay.wantsPoint(chipX - pane.getLeft(), chipY - pane.getTop()));
        assertFalse("the remove chip belongs to the widget", intercepts(page, chipX, chipY));
        assertTrue("the border away from the edit chrome is still the page's",
            intercepts(page, 2f, HEIGHT / 2f));
    }

    @Test
    public void theBorderBandBelongsToThePage_theRestBelongsToTheWidgets() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        float band = BAND_DP * density(activity);

        assertTrue("left edge", intercepts(page, 1f, HEIGHT / 2f));
        assertTrue("right edge", intercepts(page, WIDTH - 1f, HEIGHT / 2f));
        assertTrue("top edge", intercepts(page, WIDTH / 2f, 1f));
        assertTrue("bottom edge", intercepts(page, WIDTH / 2f, HEIGHT - 1f));
        assertTrue("the last pixel of the band", intercepts(page, band, HEIGHT / 2f));

        assertFalse("just inside the band", intercepts(page, band + 1f, HEIGHT / 2f));
        assertFalse("the middle of the grid", intercepts(page, WIDTH / 2f, HEIGHT / 2f));
    }

    @Test
    public void aBorderTapDropsTheTabAndAnotherPutsItAway() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);

        // Nothing is in the corner until the border is tapped.
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.emptyList(), calls.log);

        tapBorder(page);
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("settings"), calls.log);

        // Out, and away again: the corner goes quiet.
        tapBorder(page);
        tapBorder(page);
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("settings"), calls.log);
    }

    @Test
    public void theTabRunsTheSettingsCogAndTheEditPencil() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);

        tapBorder(page);
        tap(page, cogX(activity), tabCentreY(activity));
        tapBorder(page);
        tap(page, pencilX(activity), tabCentreY(activity));

        assertEquals(Arrays.asList("settings", "edit"), calls.log);
    }

    /**
     * On the phone the pencil put the pair away and the grid's size never came out: the tab was
     * still retracting when editing asked it to show, and it took itself for shown. This runs the
     * real order - editing begins inside the pencil's own tap.
     */
    @Test
    public void thePencilsOwnTapBringsTheGridSizeOut() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls() {
            @Override public void editWidgets() {
                super.editWidgets();
                page.applyWidgetEditing(true);
            }
        };
        page.setHost(calls);

        tapBorder(page);
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("edit"), calls.log);
        assertTrue("the grid tab is on its way out, not retracting with the pair",
            page.isControlsTabShown());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        assertTrue(page.isControlsTabShown());

        // And the pair is not what is on it: the cog's old spot runs nothing now.
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("edit"), calls.log);
    }

    @Test
    public void theEditingTabTakesTheCornerFromTheSettingsAndThePencil() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);

        // Editing brings the tab out on its own, and it is the grid's size that is in it now.
        page.applyWidgetEditing(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        tap(page, cogX(activity), tabCentreY(activity));
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals("neither the cog nor the pencil is on the editing tab",
            Collections.emptyList(), calls.log);

        // Leaving editing puts it away and gives the pair back.
        page.applyWidgetEditing(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals("the editing tab retracted", Collections.emptyList(), calls.log);

        tapBorder(page);
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("settings"), calls.log);
    }

    @Test
    public void aDragFromTheBorderIsNotABorderTap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        touch(page, MotionEvent.ACTION_DOWN, 2f, HEIGHT / 2f);
        touch(page, MotionEvent.ACTION_MOVE, 2f + slop * 4f, HEIGHT / 2f);
        touch(page, MotionEvent.ACTION_UP, 2f + slop * 4f, HEIGHT / 2f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);

        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals("a drag never brought the tab out", Collections.emptyList(), calls.log);
    }
}
