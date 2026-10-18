package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Hosted Home floats: the Home float a Terminal widget is the frame of. It is a window (a shell
 * finds it, no adoption makes another), takes focus and the keyboard like a Home float, rises over
 * the keyboard with its widget, and is counted nowhere a Home float is: no chip, no pill, no
 * float layer, no status bar.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HostedFloatTest {

    private static final int WIDGET_ID = 7;
    private static final int SCREEN_WIDTH = 600;
    private static final int SCREEN_HEIGHT = 1000;
    /** The grid's top padding: a lifted widget is clipped above it. */
    private static final int GRID_TOP = 100;
    /** Where the widget's cell is laid out in the grid, and how big. */
    private static final int CELL_TOP = 500;
    private static final int CELL_HEIGHT = 300;

    private RecordingHost mHost;
    private TerminalPaneController mController;
    private HomeFloatLayer mLayer;
    private FrameLayout mGrid;
    private FrameLayout mCell;
    private Slot mSlot;

    @Before
    public void setUp() {
        android.provider.Settings.Global.putFloat(context().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        mHost = new RecordingHost();
        mController = new TerminalPaneController(mHost, new FrameLayout(context()),
            LayoutInflater.from(context()));
        // A Home page in a real window, so its views have places on screen: the grid with its
        // padding, one widget cell low on it, and the float layer over the whole page.
        android.app.Activity activity =
            Robolectric.buildActivity(android.app.Activity.class).setup().get();
        FrameLayout page = new FrameLayout(activity);
        activity.setContentView(page);
        mGrid = new FrameLayout(activity);
        mGrid.setPadding(0, GRID_TOP, 0, 0);
        mGrid.setClipToPadding(true);
        page.addView(mGrid, new FrameLayout.LayoutParams(SCREEN_WIDTH, SCREEN_HEIGHT));
        mCell = new FrameLayout(activity);
        FrameLayout.LayoutParams cellParams = new FrameLayout.LayoutParams(SCREEN_WIDTH, CELL_HEIGHT);
        cellParams.topMargin = CELL_TOP - GRID_TOP;
        mGrid.addView(mCell, cellParams);
        FrameLayout paneFrame = new FrameLayout(activity);
        mCell.addView(paneFrame, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mSlot = new Slot(paneFrame, mCell);
        mLayer = new HomeFloatLayer(activity);
        page.addView(mLayer, new FrameLayout.LayoutParams(SCREEN_WIDTH, SCREEN_HEIGHT));
        idle();
        mController.setHomeFloatLayer(mLayer);
        mController.setHomeFloatObstructions(onScreen(page) + 80, onScreen(page) + 880, -1);
    }

    @Test
    public void aWidgetsTerminalIsAWindowButNoHomeFloatAnythingCounts() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHostedFloat(shell, WIDGET_ID);

        assertTrue(window.isHostedFloat());
        assertTrue("a Home float, hosted by its widget", window.isHomeFloat());
        assertEquals(WIDGET_ID, window.hostedWidgetId());
        assertSame("a shell finds it as a window, so nothing adopts it again",
            window, mController.windowOf(shell));
        assertSame(window, mController.hostedFloatFor(WIDGET_ID));
        assertSame(window, mController.hostedFloatOf(shell));
        assertTrue("no chip, no pill, no status bar: the floats list leaves it out",
            mController.homeFloats().isEmpty());
        assertEquals(0, mController.homeFloatCount());
        assertNull("not a float of any page", mController.homeFloatOf(shell));
        assertEquals("never in the float layer", 0, mLayer.getChildCount());
        assertNull("not focused until its widget shows it", mController.focusedHomeFloat());
        assertSame("one float per widget", window, mController.openHostedFloat(terminal(), WIDGET_ID));
    }

    @Test
    public void itsPaneIsDrawnInItsWidgetAndLeavesWithIt() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHostedFloat(shell, WIDGET_ID);
        mController.attachHostedFloat(window, mSlot);

        assertTrue(mController.isHostedFloatInSlot(window));
        assertTrue("its terminal is in the widget", holdsShell(mSlot.paneFrame, shell));
        assertEquals(0, mLayer.getChildCount());

        mController.detachHostedFloat(window, mSlot);
        assertFalse(mController.isHostedFloatInSlot(window));
        assertEquals("the widget left the screen: nothing drawn for it", 0,
            mSlot.paneFrame.getChildCount());
        assertSame("the shell keeps running as the widget's", window, mController.windowOf(shell));

        mController.attachHostedFloat(window, mSlot);
        assertTrue("back with the widget, the same shell", holdsShell(mSlot.paneFrame, shell));
    }

    @Test
    public void aTapFocusesItAndATapBesideOrThePageTurningGivesFocusBack() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHostedFloat(shell, WIDGET_ID);
        mController.focusHomeFloat(window);
        assertNull("off screen it takes no focus", mController.focusedHomeFloat());

        mController.attachHostedFloat(window, mSlot);
        mController.focusSession(shell);
        assertSame(window, mController.focusedHomeFloat());
        assertSame("its pane is the one typing reaches", shell, mController.getActiveSession());

        // The float layer is over the grid and reports every press that misses its own floats:
        // one on the widget is a press on it.
        pressLayerAt(mCell, 20, 20);
        assertSame(window, mController.focusedHomeFloat());
        assertEquals(0, mHost.focusReleased);

        pressLayerAt(mGrid, 20, GRID_TOP + 20);
        assertNull("a press on Home beside it gives focus back", mController.focusedHomeFloat());
        assertEquals("the keyboard it raised goes with it", 1, mHost.focusReleased);

        mController.focusSession(shell);
        mController.detachHostedFloat(window, mSlot);
        assertNull("its page turned away: Home has the focus", mController.focusedHomeFloat());
        assertEquals(2, mHost.focusReleased);
    }

    @Test
    public void aFocusedWidgetsTerminalRisesOverTheKeyboardNoHigherThanTheGridAndSettlesBack() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHostedFloat(shell, WIDGET_ID);
        mController.attachHostedFloat(window, mSlot);
        idle();
        TerminalView view = mController.getViewForSession(shell);
        // The test's shell has no process: a held grid keeps the text size from sizing it, and the
        // emulator, its cursor on the last row, is the test's.
        mController.beginHostSurfaceResize();
        view.setTextSize(20);
        int rows = Math.max(2, (int) (view.getHeight() / view.getTerminalCellHeightPixels()));
        view.mEmulator = new TerminalEmulator(new SilentOutput(), false, 40, rows, 10,
            (int) view.getTerminalCellHeightPixels(), 100, null);
        byte[] toLastRow = ("\033[" + rows + ";1H").getBytes();
        view.mEmulator.append(toLastRow, toLastRow.length);
        mController.focusSession(shell);

        int page = onScreen(mGrid);
        // A keyboard whose top is well above the widget's bottom: the widget rises, but never
        // above the grid's top, where the grid would clip it.
        mController.setHomeFloatObstructions(page + 80, page + 880, page + 450);
        float lift = -mCell.getTranslationY();
        assertTrue("risen: " + lift, lift > 0f);
        assertTrue("no higher than the grid's top", CELL_TOP - lift >= GRID_TOP);

        mController.setHomeFloatObstructions(page + 80, page + 880, -1);
        assertEquals("the keyboard down: back where the grid put it", 0f,
            mCell.getTranslationY(), 0f);

        mController.setHomeFloatObstructions(page + 80, page + 880, page + 450);
        assertTrue(mCell.getTranslationY() < 0f);
        mController.releaseHomeFloatFocus();
        assertEquals("unfocused: back where the grid put it", 0f, mCell.getTranslationY(), 0f);
    }

    @Test
    public void itsShellEndingLeavesTheWidgetStandingAndSaysWhichWidget() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHostedFloat(shell, WIDGET_ID);
        mController.attachHostedFloat(window, mSlot);
        mController.focusSession(shell);

        assertEquals(TerminalPaneController.FINISHED_PANE, mController.onSessionFinished(shell));
        assertEquals(WIDGET_ID, mHost.endedWidget);
        assertNull(mController.windowOf(shell));
        assertNull(mController.hostedFloatFor(WIDGET_ID));
        assertNull(mController.focusedHomeFloat());
        assertEquals("nothing left in the widget", 0, mSlot.paneFrame.getChildCount());
        assertEquals("and no Home float came or went", 0, mHost.floatsChanged);
    }

    @Test
    public void removingItsWindowEndsItWithNoFloatsAnnounced() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHostedFloat(shell, WIDGET_ID);
        mController.attachHostedFloat(window, mSlot);

        assertEquals(java.util.Collections.singletonList(shell), mController.removeWindow(window));
        assertTrue(mController.hostedFloats().isEmpty());
        assertEquals(0, mSlot.paneFrame.getChildCount());
        assertEquals(0, mHost.floatsChanged);
        assertEquals("ended by its widget's removal, not on its own", Integer.MIN_VALUE,
            mHost.endedWidget);
    }

    @Test
    public void aWidgetsTerminalDocksNowhere() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHostedFloat(shell, WIDGET_ID);
        mController.attachHostedFloat(window, mSlot);
        mController.focusSession(shell);

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_NONE, mController.toggleFloatActivePane());
        assertTrue(window.isHostedFloat());
        assertNull(mController.activeWindow());
        assertTrue(mController.homeFloats().isEmpty());
    }

    @Test
    public void theLiftIsTheCursorRowsOvershootCappedAtTheCeiling() {
        assertEquals("keyboard down", 0, HomeFloatPolicy.hostedLiftPx(false, 900, 600, 500, 100));
        assertEquals("no keyboard top", 0, HomeFloatPolicy.hostedLiftPx(true, 900, -1, 500, 100));
        assertEquals("the row clears it already", 0,
            HomeFloatPolicy.hostedLiftPx(true, 580, 600, 500, 100));
        assertEquals("the row's overshoot", 120, HomeFloatPolicy.hostedLiftPx(true, 720, 600, 500, 100));
        assertEquals("capped at the ceiling", 400, HomeFloatPolicy.hostedLiftPx(true, 1100, 600, 500, 100));
        assertEquals("a widget at the ceiling stays", 0,
            HomeFloatPolicy.hostedLiftPx(true, 900, 600, 100, 100));
    }

    @Test
    public void theGroundIsThePalettesBackgroundAtTheCardsOpacity() {
        assertEquals(0xFF102030, HomeFloatPolicy.hostedGround(0xFF102030, 255));
        assertEquals(0x80102030, HomeFloatPolicy.hostedGround(0x00102030, 128));
        assertEquals("clamped", 0xFF102030, HomeFloatPolicy.hostedGround(0x40102030, 300));
        assertEquals(0x00102030, HomeFloatPolicy.hostedGround(0xFF102030, -5));
    }

    // --- helpers ---

    /**
     * A press goes down on the float layer over {@code target}, at a point inside it. The layer
     * holds no float, so it reports the press as beside them, by its raw point: the event is made
     * at the screen point, which is what a fresh event reports as raw.
     */
    private void pressLayerAt(@NonNull View target, int dx, int dy) {
        int[] at = new int[2];
        target.getLocationOnScreen(at);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
            at[0] + dx, at[1] + dy, 0);
        mLayer.dispatchTouchEvent(down);
        down.recycle();
    }

    private static int onScreen(@NonNull View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return at[1];
    }

    private static boolean holdsShell(@NonNull View view, @Nullable TerminalSession shell) {
        if (view instanceof TerminalView) return ((TerminalView) view).getCurrentSession() == shell;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            if (holdsShell(group.getChildAt(i), shell)) return true;
        return false;
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @NonNull
    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }

    /** A widget's slot: its pane frame, and the cell the grid lays it out in. */
    private static final class Slot implements HostedFloatSlot {
        final FrameLayout paneFrame;
        final View cell;

        Slot(@NonNull FrameLayout paneFrame, @NonNull View cell) {
            this.paneFrame = paneFrame;
            this.cell = cell;
        }

        @Override @NonNull public FrameLayout paneFrame() { return paneFrame; }
        @Override @NonNull public View liftTarget() { return cell; }
        @Override public float cornerRadiusPx() { return 24f; }
        @Override public int groundAlpha() { return 255; }
    }

    private static final class RecordingHost implements TerminalPaneController.Host {
        int focusReleased;
        int floatsChanged;
        int endedWidget = Integer.MIN_VALUE;

        @Override public TerminalSession createShell(String cwd) { return terminal(); }
        @Override public void configurePaneView(TerminalView view) {}
        @Override public void removeShell(TerminalSession session) {}
        @Override public void onActivePaneChanged() {}
        @Override public void onTreesChanged() {}
        @Override public String defaultCwd() { return "/"; }
        @Override public void onHomeFloatFocusReleased() { focusReleased++; }
        @Override public void onHomeFloatsChanged() { floatsChanged++; }
        @Override public void onHostedFloatEnded(int widgetId) { endedWidget = widgetId; }
    }

    private static final class SilentOutput extends com.termux.terminal.TerminalOutput {
        @Override public void write(byte[] data, int offset, int count) {}
        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }
}
