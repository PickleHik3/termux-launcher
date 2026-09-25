package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.chrome.CornerZones;
import com.termux.app.launcher.widget.WidgetEditOverlayView;
import com.termux.app.launcher.widget.WidgetGridView;
import com.termux.app.launcher.widget.WidgetPaneView;
import com.termux.view.HoldTiming;

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
 * Which touches the Widgets page keeps for itself. Its four corners are <em>held</em> to drop the
 * page's own tab, exactly as a pane's are — so a tap, a drag and a widget's own controls all reach
 * the grid, and only a finger that rests in a corner square belongs to the page.
 *
 * <p>Whether the tab is out is asked the only way a finger can ask it: by tapping where one of
 * its buttons would be and seeing whether the page ran it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPaneFrameTapTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;
    /** The square each corner keeps, the same one a terminal pane holds. */
    private static final float CORNER_DP = CornerZones.PANE_SIZE_DP;
    /** The resting tab: six 30dp buttons 8dp apart, 5dp of padding, flush with the trailing edge. */
    private static final float TAB_WIDTH_DP = 230f;
    private static final float TAB_INSET_DP = 0f;
    /** One button and the gap after it. */
    private static final float TAB_STEP_DP = 38f;

    /** What the page asked the launcher for, in order, over a 4 x 5 grid. */
    private static class Calls implements WidgetPaneFrame.Host {
        final List<String> log = new ArrayList<>();
        int columns = 4;
        int rows = 5;
        @Override public void editWidgets() { log.add("edit"); }
        @Override public void showHelpOverlay() { log.add("help"); }
        @Override public void openSurfaceEditor() { log.add("appearance"); }
        @Override public void openLayoutEditor() { log.add("layout"); }
        @Override public void openWallpaperPicker() { log.add("wallpaper"); }
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

    /**
     * The same page, in a window. The grid's own long press rides on {@link View#postDelayed},
     * which a detached view only queues, so the race this file is about can only be driven on an
     * attached page.
     */
    private static WidgetPaneFrame attachedPage(Activity activity) {
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneFrame page = (WidgetPaneFrame) LayoutInflater.from(activity)
            .inflate(R.layout.view_widget_pane, null);
        activity.setContentView(page, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        return page;
    }

    private static float density(Activity activity) {
        return activity.getResources().getDisplayMetrics().density;
    }

    /** The middle of the nth button of the resting tab, counting from its leading edge. */
    private static float buttonX(Activity activity, int index) {
        float density = density(activity);
        return WIDTH - (TAB_INSET_DP + TAB_WIDTH_DP) * density
            + (5f + 15f + index * TAB_STEP_DP) * density;
    }

    /** The first button: the edit pencil. */
    private static float pencilX(Activity activity) {
        return buttonX(activity, 0);
    }

    /** The second: the plus that adds a page. */
    private static float plusX(Activity activity) {
        return buttonX(activity, 1);
    }

    /** The third button: the sliders that open Appearance. */
    private static float slidersX(Activity activity) {
        return buttonX(activity, 2);
    }

    /** The fourth button: the grid that opens Layout, one button and gap further along. */
    private static float layoutX(Activity activity) {
        return buttonX(activity, 3);
    }

    /** The fifth button: the wallpaper glyph, one button and gap past Layout. */
    private static float wallpaperX(Activity activity) {
        return buttonX(activity, 4);
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

    /** Let the tab's 190 ms reveal or retract land, since nothing on it answers mid-motion. */
    private static void settle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
    }

    /** A finger down, still until the corner claims it, then up: the gesture the corners answer. */
    private static void hold(WidgetPaneFrame page, float x, float y) {
        touch(page, MotionEvent.ACTION_DOWN, x, y);
        Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(HoldTiming.holdTimeoutMs() + 50L, TimeUnit.MILLISECONDS);
        touch(page, MotionEvent.ACTION_UP, x, y);
        settle();
    }

    /** A hold on the page's top-trailing corner, which is where the tab has always come out. */
    private static void holdCorner(WidgetPaneFrame page) {
        hold(page, WIDTH - 2f, 2f);
    }

    /** A child that keeps every gesture it is offered and remembers what it was sent. */
    private static class Recorder extends View {
        final List<Integer> actions = new ArrayList<>();

        Recorder(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            actions.add(event.getActionMasked());
            return true;
        }
    }

    /** A page whose whole area is a child taking touches, so the grid's side of this is visible. */
    private static Recorder recordingChild(WidgetPaneFrame page) {
        Recorder recorder = new Recorder(page.getContext());
        page.addView(recorder, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        return recorder;
    }

    /** A finger down and up in the same spot, with the tab's motion allowed to finish. */
    private static void tap(WidgetPaneFrame page, float x, float y) {
        touch(page, MotionEvent.ACTION_DOWN, x, y);
        touch(page, MotionEvent.ACTION_UP, x, y);
        settle();
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
        settle();

        // The top of the chip, where a thumb reaching for it lands: inside the band, and the
        // overlay's own hit test says it is the chip's.
        float chipX = pad + 15f * density;
        float chipY = 8f * density;
        assertEquals("precondition: the press is inside the page's own corner",
            CornerZones.TOP_LEFT,
            CornerZones.cornerAt(chipX, chipY, WIDTH, HEIGHT, CORNER_DP * density));
        assertTrue("precondition: the overlay claims this point",
            overlay.wantsPoint(chipX - pane.getLeft(), chipY - pane.getTop()));

        int restingCorner = page.controlsTab().corner();
        hold(page, chipX, chipY);
        assertEquals("the remove chip belongs to the widget, however long it is held",
            restingCorner, page.controlsTab().corner());

        hold(page, WIDTH - 2f, HEIGHT - 2f);
        assertEquals("a corner the edit chrome does not want is still the page's",
            CornerZones.BOTTOM_RIGHT, page.controlsTab().corner());
    }

    /** A hold opens the tab at each of the four corners, and nowhere between them. */
    @Test
    public void theCornersBelongToThePage_theEdgesBelongToTheWidgets() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        float corner = CORNER_DP * density(activity);

        assertEquals("top-leading", CornerZones.TOP_LEFT, cornerHeldAt(activity, 1f, 1f));
        assertEquals("top-trailing", CornerZones.TOP_RIGHT,
            cornerHeldAt(activity, WIDTH - 1f, 1f));
        assertEquals("bottom-trailing", CornerZones.BOTTOM_RIGHT,
            cornerHeldAt(activity, WIDTH - 1f, HEIGHT - 1f));
        assertEquals("bottom-leading", CornerZones.BOTTOM_LEFT,
            cornerHeldAt(activity, 1f, HEIGHT - 1f));
        assertEquals("the last pixel of the square", CornerZones.TOP_LEFT,
            cornerHeldAt(activity, corner, corner));

        assertEquals("one pixel past it", CornerZones.NONE,
            cornerHeldAt(activity, corner + 1f, corner + 1f));
        assertEquals("the middle of the leading edge", CornerZones.NONE,
            cornerHeldAt(activity, 1f, HEIGHT / 2f));
        assertEquals("the middle of the top edge", CornerZones.NONE,
            cornerHeldAt(activity, WIDTH / 2f, 1f));
        assertEquals("the middle of the bottom edge", CornerZones.NONE,
            cornerHeldAt(activity, WIDTH / 2f, HEIGHT - 1f));
        assertEquals("the middle of the grid", CornerZones.NONE,
            cornerHeldAt(activity, WIDTH / 2f, HEIGHT / 2f));
    }

    /** The corner a hold at this point brings the tab out of, or NONE when it brings out none. */
    private static int cornerHeldAt(Activity activity, float x, float y) {
        WidgetPaneFrame page = page(activity);
        page.setHost(new Calls());
        hold(page, x, y);
        return page.isControlsTabShown() ? page.controlsTab().corner() : CornerZones.NONE;
    }

    /** A tap in a corner square is the grid's: it reaches the child whole and opens nothing. */
    @Test
    public void aTapInACornerReachesTheWidgetsAndOpensNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        page.setHost(new Calls());
        Recorder child = recordingChild(page);

        tap(page, WIDTH - 2f, 2f);

        assertEquals("the child saw the whole tap",
            Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), child.actions);
        assertFalse("no tab came out", page.isControlsTabShown());
    }

    /** Held past the timer, the corner takes the gesture and the child is told to forget it. */
    @Test
    public void aHoldTakesTheGestureFromTheWidgetsAndOpensTheTab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        page.setHost(new Calls());
        Recorder child = recordingChild(page);

        hold(page, WIDTH - 2f, 2f);

        assertTrue("the tab came out of the held corner", page.isControlsTabShown());
        assertEquals(CornerZones.TOP_RIGHT, page.controlsTab().corner());
        assertEquals("the child saw a cancel, never the lift",
            Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), child.actions);
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
    public void theTabComesOutOfTheCornerThatWasHeld() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        page.setHost(new Calls());
        RectF tab = new RectF();

        hold(page, 2f, HEIGHT - 2f);
        page.controlsTab().tabBounds(tab);
        assertTrue("out of the leading side", tab.left < WIDTH / 2f);
        assertEquals("resting on the bottom edge", HEIGHT, tab.bottom, 1f);

        // Another corner moves it rather than putting it away.
        hold(page, WIDTH - 2f, 2f);
        assertTrue("still out", page.isControlsTabShown());
        page.controlsTab().tabBounds(tab);
        assertTrue("out of the trailing side", tab.right > WIDTH / 2f);
        assertEquals("hanging from the top edge", 0f, tab.top, 1f);

        // The corner it is out of is the one that puts it away.
        hold(page, WIDTH - 2f, 2f);
        assertFalse(page.isControlsTabShown());
    }

    @Test
    public void aBorderTapDropsTheTabAndAnotherPutsItAway() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);

        // Nothing is in the corner until the border is tapped.
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.emptyList(), calls.log);

        holdCorner(page);
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("edit"), calls.log);

        // Out, and away again: the corner goes quiet.
        holdCorner(page);
        holdCorner(page);
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("edit"), calls.log);
    }

    @Test
    public void theTabRunsTheEditPencilAndTheAppearanceSliders() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);

        holdCorner(page);
        tap(page, pencilX(activity), tabCentreY(activity));
        holdCorner(page);
        tap(page, slidersX(activity), tabCentreY(activity));

        assertEquals(Arrays.asList("edit", "appearance"), calls.log);
    }

    /**
     * The editing tab's two ways out: the tick keeps what editing did, the cross puts it back.
     * Neither is the launcher's business - the grid's own coordinator answers both - so the page's
     * host hears nothing.
     */
    @Test
    public void theEditingTabCarriesATickAndACross() {
        assertEquals(Collections.singletonList("keep"), tapEditExit(true));
        assertEquals(Collections.singletonList("discard"), tapEditExit(false));
    }

    private static List<String> tapEditExit(boolean keep) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        List<String> exits = new ArrayList<>();
        WidgetPaneView pane = page.findViewById(R.id.widget_pane);
        pane.setListener(new WidgetPaneView.Listener() {
            @Override public void onPageChangeRequested(int page) { }
            @Override public void onWidgetEditCommit() { exits.add("keep"); }
            @Override public void onWidgetEditDiscard() { exits.add("discard"); }
        }, item -> { });

        page.applyWidgetEditing(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        RectF button = new RectF();
        assertTrue("the tick and the cross are on the editing tab",
            page.editExitButtonBounds(keep, button));
        tap(page, button.centerX(), button.centerY());

        assertEquals("the page's host hears nothing about either",
            Collections.emptyList(), calls.log);
        return exits;
    }

    @Test
    public void helpStaysOnBothTheRestingAndEditingTabs() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        holdCorner(page);
        tapHelp(page, activity);
        assertEquals(Collections.singletonList("help"), calls.log);
        assertFalse(page.isControlsTabShown());

        page.applyWidgetEditing(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        tapHelp(page, activity);
        assertEquals(Arrays.asList("help", "help"), calls.log);
        assertFalse(page.isControlsTabShown());
    }

    /**
     * The page's three editor doors, side by side: the sliders open Appearance, the grid beside
     * them opens Layout, and the wallpaper glyph past that opens the wallpaper picker — each one
     * puts the tab away behind it.
     */
    @Test
    public void theSlidersOpenAppearanceAndTheGridOpensLayout() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        holdCorner(page);
        RectF bounds = new RectF();
        page.controlsTab().tabBounds(bounds);
        tap(page, slidersX(activity), bounds.centerY());
        assertEquals(Collections.singletonList("appearance"), calls.log);
        assertFalse(page.isControlsTabShown());

        holdCorner(page);
        tap(page, layoutX(activity), bounds.centerY());
        assertEquals(Arrays.asList("appearance", "layout"), calls.log);
        assertFalse(page.isControlsTabShown());

        holdCorner(page);
        tap(page, wallpaperX(activity), bounds.centerY());
        assertEquals(Arrays.asList("appearance", "layout", "wallpaper"), calls.log);
        assertFalse(page.isControlsTabShown());
    }

    /**
     * The + between the pencil and the sliders: another widgets page. Like the tick and the cross
     * it is the grid's own coordinator that answers it, so the page's host hears nothing.
     */
    @Test
    public void theTabCarriesAPlusThatAddsAPage() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        List<String> pages = new ArrayList<>();
        WidgetPaneView pane = page.findViewById(R.id.widget_pane);
        pane.setListener(new WidgetPaneView.Listener() {
            @Override public void onPageChangeRequested(int index) { }
            @Override public void onWidgetAddPage() { pages.add("add page"); }
        }, item -> { });

        holdCorner(page);
        RectF button = new RectF();
        assertTrue("the plus is on the resting tab", page.addPageButtonBounds(button));
        assertEquals("where the second button sits", plusX(activity), button.centerX(), 2f);
        tap(page, button.centerX(), button.centerY());

        assertEquals(Collections.singletonList("add page"), pages);
        assertEquals("the page's host hears nothing about it", Collections.emptyList(), calls.log);
        assertFalse("and the tab goes away behind it", page.isControlsTabShown());
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

        holdCorner(page);
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("edit"), calls.log);
        assertTrue("the grid tab is on its way out, not retracting with the pair",
            page.isControlsTabShown());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        assertTrue(page.isControlsTabShown());

        // And the pencil's own spot is not what is on it: it runs nothing now, the grid's size does.
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("edit"), calls.log);
    }

    @Test
    public void theEditingTabTakesTheCornerFromThePencilAndTheSliders() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);

        // Editing brings the tab out on its own, and it is the grid's size that is in it now.
        page.applyWidgetEditing(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        tap(page, pencilX(activity), tabCentreY(activity));
        tap(page, slidersX(activity), tabCentreY(activity));
        assertEquals("neither the pencil nor the sliders is on the editing tab",
            Collections.emptyList(), calls.log);

        // Leaving editing puts it away and gives the resting buttons back.
        page.applyWidgetEditing(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals("the editing tab retracted", Collections.emptyList(), calls.log);

        holdCorner(page);
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.singletonList("edit"), calls.log);
    }

    /** Out of the square before the timer: the gesture stays the grid's and nothing opens. */
    @Test
    public void aDragOutOfTheSquareBeforeTheTimerOpensNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        Calls calls = new Calls();
        page.setHost(calls);
        Recorder child = recordingChild(page);
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        touch(page, MotionEvent.ACTION_DOWN, WIDTH - 2f, 2f);
        touch(page, MotionEvent.ACTION_MOVE, WIDTH - 2f - slop * 4f, 2f);
        Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(HoldTiming.holdTimeoutMs() + 50L, TimeUnit.MILLISECONDS);
        touch(page, MotionEvent.ACTION_UP, WIDTH - 2f - slop * 4f, 2f);
        settle();

        assertFalse("a drag never brought the tab out", page.isControlsTabShown());
        assertFalse("and the grid kept the whole gesture",
            child.actions.contains(MotionEvent.ACTION_CANCEL));
        tap(page, pencilX(activity), tabCentreY(activity));
        assertEquals(Collections.emptyList(), calls.log);
    }

    /**
     * The bug this arbitration closes, on the page the phone showed it on: a hold in a corner
     * brought the corner tab out <em>and</em> the grid's own add/edit menu, which landed on top of
     * the tab's buttons. The corner's timer runs at three quarters of the system long press and
     * the grid's at the whole of it, so both fired on one still finger.
     */
    @Test
    public void aHoldInACornerNeverOpensTheGridsMenu() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = attachedPage(activity);
        page.setHost(new Calls());
        List<String> menu = watchGridLongPresses(page);

        holdPastTheGridsOwnTimer(page, WIDTH - 2f, 2f);

        assertTrue("the corner tab came out", page.isControlsTabShown());
        assertEquals("and the grid's menu never did", Collections.emptyList(), menu);
    }

    /** The other half of the same rule: everything between the corners is still the grid's. */
    @Test
    public void aHoldInTheMiddleOpensTheGridsMenuAndNoTab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = attachedPage(activity);
        page.setHost(new Calls());
        List<String> menu = watchGridLongPresses(page);

        holdPastTheGridsOwnTimer(page, WIDTH / 2f, HEIGHT / 2f);

        assertEquals("the grid kept its own long press",
            Collections.singletonList("empty"), menu);
        assertFalse("and no tab came out", page.isControlsTabShown());
    }

    /**
     * A finger that leaves the square before the hold fires is the grid's again — including its
     * long press, which the corner only ever borrowed.
     */
    @Test
    public void leavingTheCornerBeforeTheHoldHandsTheGridItsLongPressBack() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = attachedPage(activity);
        page.setHost(new Calls());
        List<String> menu = watchGridLongPresses(page);
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        // Out of the square, then still in the middle for a second, whole gesture: the corner is
        // out of the running and the next press is the grid's from its landing.
        touch(page, MotionEvent.ACTION_DOWN, WIDTH - 2f, 2f);
        touch(page, MotionEvent.ACTION_MOVE, WIDTH / 2f, HEIGHT / 2f);
        idlePastBothTimers();
        touch(page, MotionEvent.ACTION_UP, WIDTH / 2f, HEIGHT / 2f);
        settle();
        assertFalse("no tab came out of a drag", page.isControlsTabShown());

        holdPastTheGridsOwnTimer(page, WIDTH / 2f, HEIGHT / 2f);
        assertEquals("the grid's long press works again",
            Collections.singletonList("empty"), menu);
    }

    /** What the grid was asked for, in order, over the gestures a test drives. */
    private static List<String> watchGridLongPresses(WidgetPaneFrame page) {
        List<String> log = new ArrayList<>();
        WidgetGridView grid = ((WidgetPaneView) page.grid()).grid();
        grid.setListener(new WidgetGridView.Listener() {
            @Override public void onWidgetLongPressed(int appWidgetId, float rawX, float rawY) {
                log.add("widget");
            }
            @Override public void onEmptySpaceLongPressed(float rawX, float rawY) {
                log.add("empty");
            }
        });
        return log;
    }

    /** Past the corner's timer and the grid's alike, so a race would show as both answering. */
    private static void idlePastBothTimers() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            Math.max(HoldTiming.holdTimeoutMs(), ViewConfiguration.getLongPressTimeout()) + 50L,
            TimeUnit.MILLISECONDS);
    }

    private static void holdPastTheGridsOwnTimer(WidgetPaneFrame page, float x, float y) {
        touch(page, MotionEvent.ACTION_DOWN, x, y);
        idlePastBothTimers();
        touch(page, MotionEvent.ACTION_UP, x, y);
        settle();
    }

    /** A second finger before the timer gives the gesture up: two fingers are never a hold. */
    @Test
    public void aSecondFingerBeforeTheTimerOpensNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPaneFrame page = page(activity);
        page.setHost(new Calls());

        touch(page, MotionEvent.ACTION_DOWN, WIDTH - 2f, 2f);
        touch(page, MotionEvent.ACTION_POINTER_DOWN, WIDTH / 2f, HEIGHT / 2f);
        Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(HoldTiming.holdTimeoutMs() + 50L, TimeUnit.MILLISECONDS);
        touch(page, MotionEvent.ACTION_UP, WIDTH - 2f, 2f);
        settle();

        assertFalse(page.isControlsTabShown());
    }
}
