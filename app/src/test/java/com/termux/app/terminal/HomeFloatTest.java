package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
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

import com.termux.app.place.Element;
import com.termux.app.place.HomeChromePolicy;
import com.termux.app.place.KeyboardOverlayPolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Home floats (docs/GLOSSARY.md): windows of the session drawn over one Home page, pinned to it,
 * born full-size between the status bar and the dock, remembering their shape per orientation,
 * lifted over the keyboard, and the status bar on Home that comes with them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeFloatTest {

    private static final int LAYER_WIDTH = 600;
    private static final int LAYER_HEIGHT = 1000;
    /** The status bar's bottom and the dock's top, in the layer's (and the screen's) pixels. */
    private static final int ROOM_TOP = 80;
    private static final int DOCK_TOP = 880;

    private RecordingHost mHost;
    private TerminalPaneController mController;
    private HomeFloatLayer mLayer;

    @Before
    public void setUp() {
        android.provider.Settings.Global.putFloat(context().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        mHost = new RecordingHost();
        mController = new TerminalPaneController(mHost, new FrameLayout(context()),
            LayoutInflater.from(context()));
        mLayer = new HomeFloatLayer(context());
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        mController.setHomeFloatLayer(mLayer);
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
    }

    // --- Birth ---

    @Test
    public void aHomeFloatIsAWindowOutOfTheRowBornFullSizeAndFocused() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHomeFloat(shell, 0);

        assertTrue(window.isHomeFloat());
        assertEquals(0, window.homePage());
        assertSame("a shell, the drawer and the shortcuts find it as a window",
            window, mController.windowOf(shell));
        assertSame(window, mController.focusedHomeFloat());
        assertSame("the float's pane is the one typing reaches", shell, mController.getActiveSession());
        assertNull("it is never shown in the pane host", mController.activeWindow());

        View container = containerOf(window);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("born full width", LAYER_WIDTH, params.width);
        assertEquals("between the status bar and the dock", DOCK_TOP - ROOM_TOP, params.height);
        assertEquals(ROOM_TOP, params.topMargin);
        assertEquals(0, params.leftMargin);
        assertEquals(View.VISIBLE, container.getVisibility());
        assertEquals(1, mHost.floatsChanged);
    }

    @Test
    public void aHomeFloatsTitlebarOffersMinimise() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        FloatTitlebarView titlebar = titlebarOf(containerOf(window));
        assertNotNull(titlebar);
        assertNotNull(titlebar.buttonFor(FloatTitlebarPolicy.ACTION_MINIMISE));
        assertNotNull(titlebar.buttonFor(FloatTitlebarPolicy.ACTION_DOCK));
    }

    @Test
    public void manyFloatsAndATapBringsOneToTheFront() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        TerminalPaneController.Window second = mController.openHomeFloat(terminal(), 0);
        assertSame(second, mController.focusedHomeFloat());
        assertSame(containerOf(second), mLayer.getChildAt(mLayer.getChildCount() - 1));

        mController.focusHomeFloat(first);
        assertSame(first, mController.focusedHomeFloat());
        assertSame("raised over the others", containerOf(first),
            mLayer.getChildAt(mLayer.getChildCount() - 1));
        assertSame(first, mController.homeFloats().get(1));
    }

    // --- Pinned to its page ---

    @Test
    public void aFloatShowsOnlyOnItsOwnPage() {
        TerminalPaneController.Window onFirst = mController.openHomeFloat(terminal(), 0);
        TerminalPaneController.Window onSecond = mController.openHomeFloat(terminal(), 1);
        assertEquals(View.VISIBLE, containerOf(onFirst).getVisibility());
        assertEquals(View.GONE, containerOf(onSecond).getVisibility());

        mLayer.onHomePageShown(1);
        assertEquals(View.GONE, containerOf(onFirst).getVisibility());
        assertEquals(View.VISIBLE, containerOf(onSecond).getVisibility());
    }

    @Test
    public void turningThePageAwayFromTheFocusedFloatGivesFocusBackToHome() {
        mController.openHomeFloat(terminal(), 0);
        mLayer.onHomePageShown(1);
        assertNull(mController.focusedHomeFloat());
        assertEquals(1, mHost.focusReleased);
    }

    @Test
    public void thePageHoldsWhatItsFloatsCoverAndNothingElse() {
        mController.openHomeFloat(terminal(), 0);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        assertTrue(mLayer.holdsPoint(300, 500));
        assertFalse("above the room, under the status bar", mLayer.holdsPoint(300, ROOM_TOP / 2f));
        mLayer.onHomePageShown(1);
        assertFalse("another page's float holds nothing here", mLayer.holdsPoint(300, 500));
    }

    // --- Focus ---

    @Test
    public void aPressOnHomeBesideTheFloatsGivesFocusBackAndClosesTheKeyboard() {
        TerminalSession shell = terminal();
        mController.openHomeFloat(shell, 0);
        press(mLayer, 300, ROOM_TOP / 2f);
        assertNull(mController.focusedHomeFloat());
        assertEquals("the host closes the keyboard the float raised", 1, mHost.focusReleased);
        assertNull(mController.getActiveSession());
    }

    @Test
    public void closingTheFocusedFloatLeavesTheWorkspaceAlone() {
        TerminalSession rowShell = terminal();
        TerminalPaneController.Window row = mController.newWindow(rowShell);
        mController.showWindow(row);
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHomeFloat(shell, 0);

        assertEquals(TerminalPaneController.FINISHED_PANE, mController.onSessionFinished(shell));
        assertNull(mController.windowOf(shell));
        assertTrue(mController.homeFloats().isEmpty());
        assertEquals(0, mLayer.getChildCount());
        assertSame("the row's window is where it was", row, mController.activeWindow());
        assertSame(rowShell, mController.getActiveSession());
        assertEquals(1, mHost.focusReleased);
        assertFalse(window.isMinimised());
    }

    // --- Minimise ---

    @Test
    public void minimiseTucksTheFloatAwayAndRestoreBringsItBackFocused() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        mController.minimiseHomeFloat(window);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);

        assertTrue(window.isMinimised());
        assertNull("minimising the focused float gives focus back", mController.focusedHomeFloat());
        assertEquals(1, mHost.focusReleased);
        assertEquals(View.GONE, containerOf(window).getVisibility());
        assertEquals("still a float: the bar keeps its chip", 1, mController.homeFloatCount());

        mController.restoreHomeFloat(window);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
        assertFalse(window.isMinimised());
        assertEquals(View.VISIBLE, containerOf(window).getVisibility());
        assertEquals(1f, containerOf(window).getAlpha(), 0f);
        assertSame(window, mController.focusedHomeFloat());
    }

    @Test
    public void minimiseAllAndRestoreFloatsReachEveryPage() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        TerminalPaneController.Window second = mController.openHomeFloat(terminal(), 2);
        mController.minimiseAllHomeFloats();
        assertTrue(first.isMinimised());
        assertTrue(second.isMinimised());
        mController.restoreHomeFloats();
        assertFalse(first.isMinimised());
        assertFalse(second.isMinimised());
        assertEquals(View.GONE, containerOf(second).getVisibility());
    }

    // --- Dock and float ---

    @Test
    public void dockSendsTheFloatIntoTheRowAsATiledWindow() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHomeFloat(shell, 0);
        assertEquals(TerminalPaneController.FLOAT_TOGGLE_DOCKED,
            mController.toggleFloatActivePane());

        assertSame(window, mHost.docked);
        assertFalse(window.isHomeFloat());
        assertTrue(mController.homeFloats().isEmpty());
        assertEquals(0, mLayer.getChildCount());
        assertNull(mController.focusedHomeFloat());
        mController.showWindow(window);
        assertSame(shell, mController.getActiveSession());
        assertFalse(mController.isFloating(shell));
    }

    @Test
    public void aOnePaneWindowFloatsOntoHomeAndASplitOneKeepsItsFloatingPane() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.newWindow(shell);
        mController.showWindow(window);
        mHost.takesFloatedWindows = true;

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            mController.toggleFloatActivePane());
        assertSame(window, mHost.floatedOntoHome);
        assertTrue(mController.floatWindowOntoHome(window, 1));
        assertTrue(window.isHomeFloat());
        assertEquals(1, window.homePage());
        assertNull("the pane host let go of it", mController.activeWindow());
        assertSame(shell, mController.getActiveSession());
        assertFalse("a float floats no further", mController.floatWindowOntoHome(window, 0));
    }

    // --- Orientation memory ---

    @Test
    public void aFloatComesBackToTheShapeItHadInEachOrientation() {
        HomeFloatPolicy.ShapeMemory memory = new HomeFloatPolicy.ShapeMemory();
        RectF portrait = new RectF(.1f, .1f, .6f, .5f);
        RectF landscape = new RectF(.3f, .2f, .9f, .9f);
        assertNull("the first sight of an orientation keeps the float as it is",
            memory.turn(false, portrait, null));
        assertNull("a first visit to landscape keeps the fractions",
            memory.turn(true, portrait, null));
        HomeFloatPolicy.Shape back = memory.turn(false, landscape, null);
        assertNotNull(back);
        assertEquals(portrait, back.frac);
        HomeFloatPolicy.Shape again = memory.turn(true, portrait, null);
        assertNotNull(again);
        assertEquals(landscape, again.frac);
        assertNull("no turn, nothing handed back", memory.turn(true, landscape, null));
    }

    @Test
    public void maximiseIsRememberedPerOrientationToo() {
        HomeFloatPolicy.ShapeMemory memory = new HomeFloatPolicy.ShapeMemory();
        RectF small = new RectF(.2f, .2f, .5f, .5f);
        memory.turn(false, FloatTitlebarPolicy.fullRoom(), small);
        memory.turn(true, FloatTitlebarPolicy.fullRoom(), small);
        HomeFloatPolicy.Shape back = memory.turn(false, new RectF(.1f, .1f, .3f, .3f), null);
        assertNotNull(back);
        assertEquals(FloatTitlebarPolicy.fullRoom(), back.frac);
        assertEquals(small, back.restoreFrac);
    }

    // --- Room, lift and the bar ---

    @Test
    public void theRoomIsBetweenTheStatusBarAndTheDockAndNeverInverted() {
        assertEquals(new Rect(0, 80, 600, 880), HomeFloatPolicy.room(600, 1000, 80, 120));
        assertEquals(new Rect(0, 0, 600, 1000), HomeFloatPolicy.room(600, 1000, -20, -5));
        Rect squeezed = HomeFloatPolicy.room(600, 100, 90, 90);
        assertEquals(90, squeezed.top);
        assertEquals(91, squeezed.bottom);
    }

    @Test
    public void theFocusedFloatRisesSoItsCursorRowClearsTheKeyboard() {
        assertEquals(0, KeyboardOverlayPolicy.floatLiftPx(false, 900, 600));
        assertEquals(0, KeyboardOverlayPolicy.floatLiftPx(true, 500, 600));
        assertEquals(300, KeyboardOverlayPolicy.floatLiftPx(true, 900, 600));
    }

    @Test
    public void theKeyboardLiftsOnlyTheFocusedFloatAndNeverResizesIt() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        int height = container.getLayoutParams().height;
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        assertEquals("never resized", height, container.getLayoutParams().height);
        assertTrue("lifted no lower than where it was", container.getTranslationY() <= 0f);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        assertEquals("put back when the keyboard closes", 0f, container.getTranslationY(), 0f);
    }

    @Test
    public void aFullSizeFloatsTitlebarStaysInTheRoomWhenItsCursorRowIsUnderTheKeyboard() {
        // The emulator check: a full screen of shell output put the cursor on the last row, the
        // keyboard came up, and the lift raised the whole float by the cursor row's overshoot,
        // titlebar and all, under the status bar, where none of its buttons could be reached.
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mController.openHomeFloat(shell, 0);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        TerminalView view = mController.getViewForSession(shell);
        assertNotNull(view);
        // The test's shell has no process and its view no client: a held grid keeps the new text
        // size from sizing the shell, and the emulator is the test's.
        mController.beginHostSurfaceResize();
        view.setTextSize(20);
        int rows = Math.max(2, (int) (view.getHeight() / view.getTerminalCellHeightPixels()));
        view.mEmulator = new TerminalEmulator(new SilentOutput(), false, 40, rows, 10,
            (int) view.getTerminalCellHeightPixels(), 100, null);
        byte[] toLastRow = ("\033[" + rows + ";1H").getBytes();
        view.mEmulator.append(toLastRow, toLastRow.length);
        assertEquals(rows - 1, view.mEmulator.getCursorRow());

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 500);
        assertTrue("the titlebar's top stays at or below the room's top",
            container.getTop() + container.getTranslationY() >= ROOM_TOP);
        FloatTitlebarView titlebar = titlebarOf(container);
        assertNotNull(titlebar);
        assertEquals("the titlebar is the float's top strip", 0, titlebar.getTop());
    }

    @Test
    public void aFloatLowInTheRoomRisesOnlyAsFarAsTheRoomsTop() {
        assertEquals(0, HomeFloatPolicy.liftPx(0, 300, 80));
        assertEquals("the keyboard's ask, while there is room above", 120,
            HomeFloatPolicy.liftPx(120, 300, 80));
        assertEquals("capped at the room's top", 220, HomeFloatPolicy.liftPx(500, 300, 80));
        assertEquals("a float at the room's top stays put", 0, HomeFloatPolicy.liftPx(95, 80, 80));
        assertEquals("never lowered", 0, HomeFloatPolicy.liftPx(95, 40, 80));
    }

    @Test
    public void aFloatOpenedBeforeTheBarKeepsItsTitlebarInTheRoomWhenTheBarTakesItsRoom() {
        // The layer in a real window, so it has a place on screen: the bar arriving moves it down.
        android.app.Activity activity =
            Robolectric.buildActivity(android.app.Activity.class).setup().get();
        FrameLayout column = new FrameLayout(activity);
        activity.setContentView(column);
        column.addView(mLayer, new FrameLayout.LayoutParams(LAYER_WIDTH, LAYER_HEIGHT));
        idle();
        int[] at = new int[2];
        column.getLocationOnScreen(at);
        final int columnTop = at[1];
        final int dockTop = columnTop + DOCK_TOP;

        // No bar on Home yet: the room starts at the layer's top.
        mController.setHomeFloatObstructions(columnTop, dockTop, -1);
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        View container = containerOf(window);
        idle();
        assertTitlebarAtLeast(container, columnTop);

        // The first float brings the bar onto Home: it takes its height at the top of the content
        // column, so the layer moves down and gets shorter. The layout lands before the
        // obstructions are synced again.
        final int bar = 84;
        FrameLayout.LayoutParams moved =
            new FrameLayout.LayoutParams(LAYER_WIDTH, LAYER_HEIGHT - bar);
        moved.topMargin = bar;
        mLayer.setLayoutParams(moved);
        idle();
        assertTitlebarAtLeast(container, columnTop + bar);
        mController.setHomeFloatObstructions(columnTop + bar, dockTop, -1);
        idle();
        assertTitlebarAtLeast(container, columnTop + bar);
        assertEquals("still full-size: the bar's bottom to the dock's top",
            DOCK_TOP - bar, container.getHeight());

        // The keyboard comes up: the float may not rise past the bar either.
        mController.setHomeFloatObstructions(columnTop + bar, dockTop, columnTop + 400);
        assertTitlebarAtLeast(container, columnTop + bar);

        // The bar leaves with the keyboard down: the float grows back to the column's top.
        mController.setHomeFloatObstructions(columnTop, dockTop, -1);
        mLayer.setLayoutParams(new FrameLayout.LayoutParams(LAYER_WIDTH, LAYER_HEIGHT));
        idle();
        assertTitlebarAtLeast(container, columnTop);
        assertEquals(DOCK_TOP, container.getHeight());
    }

    /** The float's strip, its top edge, starts at or below {@code roomTopOnScreen}. */
    private static void assertTitlebarAtLeast(@NonNull View container, int roomTopOnScreen) {
        int[] at = new int[2];
        container.getLocationOnScreen(at);
        FloatTitlebarView titlebar = titlebarOf(container);
        assertNotNull(titlebar);
        assertEquals("the titlebar is the float's top strip", 0, titlebar.getTop());
        assertTrue("strip top " + at[1] + " at or under the room's top " + roomTopOnScreen,
            at[1] >= roomTopOnScreen);
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void homeHasTheStatusBarExactlyWhileFloatsExist() {
        PaneWallPage home = PaneWallPage.WIDGETS;
        assertFalse(HomeChromePolicy.statusShownAtRest(home, false));
        assertTrue(HomeChromePolicy.statusShownAtRest(home, true));
        assertTrue(HomeChromePolicy.statusShownAtRest(PaneWallPage.TERMINAL, false));
        assertTrue("compact on Home", HomeChromePolicy.statusForcedCompact(home));
        assertFalse(HomeChromePolicy.statusForcedCompact(PaneWallPage.TERMINAL));

        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.hiddenFrom(PlaceLayout.Edge.TOP, 0));
        PlaceLayout homeLayout = new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
        assertSame(homeLayout,
            HomeChromePolicy.withFloatStatusBar(homeLayout, PlaceLayout.Edge.TOP, false));
        PlaceLayout withBar =
            HomeChromePolicy.withFloatStatusBar(homeLayout, PlaceLayout.Edge.TOP, true);
        assertFalse(withBar.slot(Element.STATUS).hidden);
        assertEquals(PlaceLayout.Edge.TOP, withBar.slot(Element.STATUS).edge);
    }

    @Test
    public void onHomeTheBarShowsTheFloatsChipsExactlyWhileFloatsExist() {
        // The bar came onto Home with the first float but its chips stayed hidden, so a minimised
        // float had nothing to be tapped back from.
        assertTrue(HomeChromePolicy.windowChipsShown(PaneWallPage.WIDGETS, true));
        assertFalse("no float, no chips on Home",
            HomeChromePolicy.windowChipsShown(PaneWallPage.WIDGETS, false));
        assertTrue(HomeChromePolicy.windowChipsShown(PaneWallPage.TERMINAL, false));
        assertTrue(HomeChromePolicy.windowChipsShown(PaneWallPage.DISPLAY, false));
        for (boolean floats : new boolean[] {false, true}) {
            assertEquals("the chips show wherever the bar rests on Home",
                HomeChromePolicy.statusShownAtRest(PaneWallPage.WIDGETS, floats),
                HomeChromePolicy.windowChipsShown(PaneWallPage.WIDGETS, floats));
        }
    }

    // --- The float's shell gets its size ---

    @Test
    public void aFloatOpenedWhileTheGridIsHeldIsSizedWhenTheHoldEnds() {
        // The status bar coming onto Home and the keyboard rising hold the grid while the float
        // is laid out for the first time; a size discarded at the end of the hold leaves the
        // shell without an emulator, and the pane draws solid black.
        mController.beginHostSurfaceResize();
        TerminalSession shell = terminal();
        mController.openHomeFloat(shell, 0);
        TerminalView view = mController.getViewForSession(shell);
        assertNotNull(view);
        int[] sized = {0};
        view.setSizeUpdateObserverForTests(v -> sized[0]++);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        assertTrue("laid out", view.getWidth() > 0 && view.getHeight() > 0);
        assertEquals("held while the grid is held", 0, sized[0]);

        mController.finishHostSurfaceResizeKeepingBottom();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("the float's shell is sized once the hold ends", sized[0] > 0);
    }

    @Test
    public void aFloatLaidOutDuringAHoldTakenAfterItOpenedIsSizedWhenTheHoldEnds() {
        TerminalSession shell = terminal();
        mController.openHomeFloat(shell, 0);
        TerminalView view = mController.getViewForSession(shell);
        assertNotNull(view);
        int[] sized = {0};
        view.setSizeUpdateObserverForTests(v -> sized[0]++);
        // The keyboard rises right after the float opens, before the float's first layout.
        mController.beginHostSurfaceResize();
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        assertEquals(0, sized[0]);

        mController.finishHostSurfaceResizeKeepingBottom();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("the float's shell is sized once the hold ends", sized[0] > 0);
    }

    @Test
    public void aFloatsChipCarriesItsPage() {
        TerminalWindowBar.WindowItem item = new TerminalWindowBar.WindowItem("~", "home")
            .withHomeFloat(1, "Home float, page 2");
        assertEquals("~ · 2", item.label);
        assertEquals("home, Home float, page 2", item.spokenLabel);
    }

    // --- Helpers ---

    private static final class RecordingHost implements TerminalPaneController.Host {
        int floatsChanged;
        int focusReleased;
        @Nullable TerminalPaneController.Window docked;
        @Nullable TerminalPaneController.Window floatedOntoHome;
        boolean takesFloatedWindows;

        @Override public TerminalSession createShell(String cwd) { return terminal(); }
        @Override public void configurePaneView(TerminalView view) {}
        @Override public void removeShell(TerminalSession session) {}
        @Override public void onActivePaneChanged() {}
        @Override public void onTreesChanged() {}
        @Override public String defaultCwd() { return "/"; }
        @Override public void onHomeFloatsChanged() { floatsChanged++; }
        @Override public void onHomeFloatFocusReleased() { focusReleased++; }
        @Override public void onHomeFloatDocked(@NonNull TerminalPaneController.Window window) {
            docked = window;
        }
        @Override public boolean onFloatWindowOntoHome(
                @NonNull TerminalPaneController.Window window) {
            floatedOntoHome = window;
            return takesFloatedWindows;
        }
    }

    private static final class SilentOutput extends com.termux.terminal.TerminalOutput {
        @Override public void write(byte[] data, int offset, int count) {}
        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }

    @NonNull
    private View containerOf(@NonNull TerminalPaneController.Window window) {
        TerminalSession shell = mController.windowActiveSession(window);
        for (int i = 0; i < mLayer.getChildCount(); i++) {
            View child = mLayer.getChildAt(i);
            if (holdsShell(child, shell)) return child;
        }
        throw new AssertionError("no container for the float");
    }

    private static boolean holdsShell(@NonNull View view, @Nullable TerminalSession shell) {
        if (view instanceof TerminalView) return ((TerminalView) view).getCurrentSession() == shell;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            if (holdsShell(group.getChildAt(i), shell)) return true;
        return false;
    }

    @Nullable
    private static FloatTitlebarView titlebarOf(@NonNull View container) {
        ViewGroup group = (ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++)
            if (group.getChildAt(i) instanceof FloatTitlebarView)
                return (FloatTitlebarView) group.getChildAt(i);
        return null;
    }

    private static void press(@NonNull View view, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        view.dispatchTouchEvent(down);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0);
        view.dispatchTouchEvent(up);
        up.recycle();
    }

    private static void layout(@NonNull View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    @NonNull
    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @NonNull
    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }
}
