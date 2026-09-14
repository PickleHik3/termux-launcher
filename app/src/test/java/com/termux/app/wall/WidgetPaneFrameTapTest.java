package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.termux.R;
import com.termux.app.chrome.CornerZones;
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
 * Which touches the Widgets page keeps for itself. Its four corners drop the page's own tab,
 * exactly as the Display page's do — and everything between them stays the widgets' and the edit
 * overlay's, so a widget that reaches the frame's edge answers to its last pixel.
 *
 * <p>Whether the tab is out is asked the only way a finger can ask it: by tapping where one of
 * its buttons would be and seeing whether the page ran it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPaneFrameTapTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;
    /** The square each corner keeps. */
    private static final float CORNER_DP = CornerZones.SIZE_DP;
    /** The tab: three 30dp buttons 8dp apart, 5dp of padding, 3dp in from the trailing edge. */
    private static final float TAB_WIDTH_DP = 116f;
    private static final float TAB_INSET_DP = 3f;
    /** The middle of the gap between Settings and Edit. */
    private static final float TAB_SPLIT_DP = 39f;

    /** What the page asked the launcher for, in order, over a 4 x 5 grid. */
    private static class Calls implements WidgetPaneFrame.Host {
        final List<String> log = new ArrayList<>();
        int columns = 4;
        int rows = 5;
        @Override public void openWidgetGridSettings() { log.add("settings"); }
        @Override public void editWidgets() { log.add("edit"); }
        @Override public void showHelpOverlay() { log.add("help"); }
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

    /** The first button: the settings cog. */
    private static float cogX(Activity activity) {
        float density = density(activity);
        return WIDTH - (TAB_INSET_DP + TAB_WIDTH_DP) * density + (TAB_SPLIT_DP - 8f) * density;
    }

    /** The second button: the edit pencil. */
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

    /** A tap on the page's top-trailing corner, which is where the tab has always come out. */
    private static void tapCorner(WidgetPaneFrame page) {
        tap(page, WIDTH - 2f, 2f);
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
     * widget's remove chip lives inside the page's own corner square. The page took that press as
     * a corner tap and the widget could not be removed.
     */
    @Test
    public void aWidgetsOwnEditChipIsNotSwallowedByTheCorner() {
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
        assertEquals("precondition: the press is inside the page's own corner",
            CornerZones.TOP_LEFT,
            CornerZones.cornerAt(chipX, chipY, WIDTH, HEIGHT, CORNER_DP * density));
        assertTrue("precondition: the overlay claims this point",
            overlay.wantsPoint(chipX - pane.getLeft(), chipY - pane.getTop()));
        assertFalse("the remove chip belongs to the widget", intercepts(page, chipX, chipY));
        assertTrue("a corner the edit chrome does not want is still the page's",
            intercepts(page, WIDTH - 2f, HEIGHT - 2f));
    }

    @Test
    public void theCornersBelongToThePage_theEdgesBelongToTheWidgets() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        float corner = CORNER_DP * density(activity);

        assertTrue("top-leading", intercepts(page, 1f, 1f));
        assertTrue("top-trailing", intercepts(page, WIDTH - 1f, 1f));
        assertTrue("bottom-trailing", intercepts(page, WIDTH - 1f, HEIGHT - 1f));
        assertTrue("bottom-leading", intercepts(page, 1f, HEIGHT - 1f));
        assertTrue("the last pixel of the square", intercepts(page, corner, corner));

        assertFalse("one pixel past it", intercepts(page, corner + 1f, corner + 1f));
        assertFalse("the middle of the leading edge", intercepts(page, 1f, HEIGHT / 2f));
        assertFalse("the middle of the top edge", intercepts(page, WIDTH / 2f, 1f));
        assertFalse("the middle of the bottom edge", intercepts(page, WIDTH / 2f, HEIGHT - 1f));
        assertFalse("the middle of the grid", intercepts(page, WIDTH / 2f, HEIGHT / 2f));
    }

    /** The pure rule the frame asks on every touch down. */
    @Test
    public void theEditChromeTakesACornerBackFromThePage() {
        assertEquals(CornerZones.TOP_LEFT,
            WidgetPaneFrame.claimedCorner(4f, 4f, WIDTH, HEIGHT, 1f, false));
        assertEquals("a widget's own chip in the same corner",
            CornerZones.NONE, WidgetPaneFrame.claimedCorner(4f, 4f, WIDTH, HEIGHT, 1f, true));
        assertEquals("the edge between the corners",
            CornerZones.NONE, WidgetPaneFrame.claimedCorner(4f, HEIGHT / 2f, WIDTH, HEIGHT, 1f,
                false));
    }

    @Test
    public void theTabComesOutOfTheCornerThatWasTapped() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        page.setHost(new Calls());
        RectF tab = new RectF();

        tap(page, 2f, HEIGHT - 2f);
        page.controlsTab().tabBounds(tab);
        assertTrue("out of the leading side", tab.left < WIDTH / 2f);
        assertEquals("resting on the bottom edge", HEIGHT, tab.bottom, 1f);

        // Another corner moves it rather than putting it away.
        tap(page, WIDTH - 2f, 2f);
        assertTrue("still out", page.isControlsTabShown());
        page.controlsTab().tabBounds(tab);
        assertTrue("out of the trailing side", tab.right > WIDTH / 2f);
        assertEquals("hanging from the top edge", 0f, tab.top, 1f);

        // The corner it is out of is the one that puts it away.
        tap(page, WIDTH - 2f, 2f);
        assertFalse(page.isControlsTabShown());
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

        tapCorner(page);
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("settings"), calls.log);

        // Out, and away again: the corner goes quiet.
        tapCorner(page);
        tapCorner(page);
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("settings"), calls.log);
    }

    @Test
    public void theTabRunsTheSettingsCogAndTheEditPencil() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);

        tapCorner(page);
        tap(page, cogX(activity), tabCentreY(activity));
        tapCorner(page);
        tap(page, pencilX(activity), tabCentreY(activity));

        assertEquals(Arrays.asList("settings", "edit"), calls.log);
    }

    @Test
    public void helpStaysOnBothTheRestingAndEditingTabs() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        tapCorner(page);
        tapHelp(page, activity);
        assertEquals(Collections.singletonList("help"), calls.log);
        assertFalse(page.isControlsTabShown());

        page.applyWidgetEditing(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        tapHelp(page, activity);
        assertEquals(Arrays.asList("help", "help"), calls.log);
        assertFalse(page.isControlsTabShown());
    }

    private static void tapHelp(WidgetPaneFrame page, Activity activity) {
        // Help is the trailing 30dp button on either tab, measured from its actual bounds.
        RectF bounds = new RectF();
        page.controlsTab().tabBounds(bounds);
        tap(page, bounds.right - 20f * density(activity), bounds.centerY());
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

        tapCorner(page);
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

        tapCorner(page);
        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("settings"), calls.log);
    }

    @Test
    public void aDragFromACornerIsNotACornerTap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        touch(page, MotionEvent.ACTION_DOWN, WIDTH - 2f, 2f);
        touch(page, MotionEvent.ACTION_MOVE, WIDTH - 2f - slop * 4f, 2f);
        touch(page, MotionEvent.ACTION_UP, WIDTH - 2f - slop * 4f, 2f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);

        tap(page, cogX(activity), tabCentreY(activity));
        assertEquals("a drag never brought the tab out", Collections.emptyList(), calls.log);
    }
}
