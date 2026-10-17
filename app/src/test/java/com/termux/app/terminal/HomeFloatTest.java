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
        assertNull("Dock is in the handle's menu", titlebar.buttonFor(FloatTitlebarPolicy.ACTION_DOCK));
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

    @Test
    public void aSecondFloatOnAPageCascadesSoTheFirstTitlebarStaysInSight() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        TerminalPaneController.Window second = mController.openHomeFloat(terminal(), 0);
        FrameLayout.LayoutParams under = (FrameLayout.LayoutParams) containerOf(first).getLayoutParams();
        FrameLayout.LayoutParams over = (FrameLayout.LayoutParams) containerOf(second).getLayoutParams();
        float density = context().getResources().getDisplayMetrics().density;
        int band = Math.round(density * (FloatTitlebarPolicy.HEIGHT_DP + FloatTitlebarPolicy.GAP_DP));
        int step = Math.round(density * HomeFloatPolicy.CASCADE_STEP_RIGHT_DP);
        assertEquals("one titlebar band down", under.topMargin + band, over.topMargin, 1);
        assertEquals("a small step right", under.leftMargin + step, over.leftMargin, 1);
        assertEquals("ends at the room's bottom", DOCK_TOP, over.topMargin + over.height, 1);
        assertEquals("ends at the room's right", LAYER_WIDTH, over.leftMargin + over.width, 1);
    }

    @Test
    public void aFloatOnAnotherPageOrOverAMinimisedOneIsBornFullSize() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        TerminalPaneController.Window elsewhere = mController.openHomeFloat(terminal(), 1);
        mController.minimiseHomeFloat(first);
        TerminalPaneController.Window again = mController.openHomeFloat(terminal(), 0);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) containerOf(again).getLayoutParams();
        assertEquals(ROOM_TOP, params.topMargin);
        assertEquals(0, params.leftMargin);
        assertNotNull(elsewhere);
    }

    @Test
    public void cascadeKeepsTheSizeAndClampsToTheRoom() {
        assertEquals("nothing under it: full size", HomeFloatPolicy.birthFrac(),
            HomeFloatPolicy.cascadeFrac(null, 600, 800, 16, 35, 120, 90));
        assertEquals("a room not measured yet: full size", HomeFloatPolicy.birthFrac(),
            HomeFloatPolicy.cascadeFrac(new RectF(0f, 0f, .5f, .5f), 0, 0, 16, 35, 120, 90));

        RectF small = HomeFloatPolicy.cascadeFrac(new RectF(.1f, .1f, .5f, .5f), 600, 800,
            16, 35, 120, 90);
        assertEquals((60 + 16) / 600f, small.left, 1e-4f);
        assertEquals((80 + 35) / 800f, small.top, 1e-4f);
        assertEquals("the size is kept", .4f, small.width(), 1e-4f);
        assertEquals(.4f, small.height(), 1e-4f);

        RectF full = HomeFloatPolicy.cascadeFrac(HomeFloatPolicy.birthFrac(), 600, 800,
            16, 35, 120, 90);
        assertEquals(new RectF(16 / 600f, 35 / 800f, 1f, 1f), full);

        RectF corner = HomeFloatPolicy.cascadeFrac(new RectF(.9f, .95f, 1f, 1f), 600, 800,
            16, 35, 120, 90);
        assertEquals("slides back to keep its least size", 1f, corner.right, 1e-4f);
        assertEquals(1f, corner.bottom, 1e-4f);
        assertEquals(120 / 600f, corner.width(), 1e-4f);
        assertEquals(90 / 800f, corner.height(), 1e-4f);
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
        assertEquals(new Rect(0, 80, 600, 880), HomeFloatPolicy.room(600, 1000, 80, 120, 0));
        assertEquals(new Rect(0, 0, 600, 1000), HomeFloatPolicy.room(600, 1000, -20, -5, -4));
        Rect squeezed = HomeFloatPolicy.room(600, 100, 90, 90, 0);
        assertEquals(90, squeezed.top);
        assertEquals(91, squeezed.bottom);
    }

    /** Home's Margin is the floats' air: inside the layer's sides, under the bar, over the dock. */
    @Test
    public void homesMarginInsetsTheRoomOnAllFourSides() {
        assertEquals(new Rect(24, 104, 576, 856), HomeFloatPolicy.room(600, 1000, 80, 120, 24));
        assertEquals("the 48 dp maximum at 3 px/dp", new Rect(144, 224, 456, 736),
            HomeFloatPolicy.room(600, 1000, 80, 120, 144));
        Rect squeezed = HomeFloatPolicy.room(600, 100, 90, 90, 24);
        assertEquals("a room with no height to spare keeps its pixel", 90, squeezed.top);
        assertEquals(91, squeezed.bottom);
        Rect narrow = HomeFloatPolicy.room(30, 1000, 80, 120, 24);
        assertEquals("the Margin gives way evenly, never inverting", 14, narrow.left);
        assertEquals(16, narrow.right);
        assertEquals("an unmeasured layer stays unmeasured", 0,
            HomeFloatPolicy.room(0, 1000, 80, 120, 24).width());
    }

    @Test
    public void aFloatBornAfterAMarginFillsTheSmallerRoom() {
        int margin = 24;
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1, margin);
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) containerOf(window).getLayoutParams();
        assertEquals(margin, params.leftMargin);
        assertEquals(ROOM_TOP + margin, params.topMargin);
        assertEquals(LAYER_WIDTH - 2 * margin, params.width);
        assertEquals(DOCK_TOP - ROOM_TOP - 2 * margin, params.height);
        assertEquals("still full-size: the whole room", HomeFloatPolicy.birthFrac(),
            window.active.floatFrac);
    }

    @Test
    public void aMarginChangeReLaysARememberedFloatAtItsOwnFraction() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        RectF small = new RectF(.1f, .5f, .8f, .9f);
        placeSmaller(window, small);
        int margin = 36;
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1, margin);
        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) containerOf(window).getLayoutParams();
        int roomWidth = LAYER_WIDTH - 2 * margin;
        int roomHeight = DOCK_TOP - ROOM_TOP - 2 * margin;
        assertEquals(margin + Math.round(.1f * roomWidth), params.leftMargin);
        assertEquals(ROOM_TOP + margin + Math.round(.5f * roomHeight), params.topMargin);
        assertEquals(Math.round(.7f * roomWidth), params.width);
        assertEquals(Math.round(.4f * roomHeight), params.height);
        assertNear("the fraction is the float's own and stays", small, window.active.floatFrac);
        // The three-argument pass the keyboard makes keeps the Margin it has.
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        assertEquals(margin + Math.round(.1f * roomWidth),
            ((FrameLayout.LayoutParams) containerOf(window).getLayoutParams()).leftMargin);
    }

    @Test
    public void theFocusedFloatRisesSoItsCursorRowClearsTheKeyboard() {
        assertEquals(0, KeyboardOverlayPolicy.floatLiftPx(false, 900, 600));
        assertEquals(0, KeyboardOverlayPolicy.floatLiftPx(true, 500, 600));
        assertEquals(300, KeyboardOverlayPolicy.floatLiftPx(true, 900, 600));
    }

    @Test
    public void theKeyboardLiftsASmallerFloatAndNeverResizesIt() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        placeSmaller(window, new RectF(.1f, .5f, .8f, .9f));
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        int height = params.height;
        int top = params.topMargin;
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        assertEquals("never resized", height, container.getLayoutParams().height);
        assertEquals("never moved: lifted", top,
            ((FrameLayout.LayoutParams) container.getLayoutParams()).topMargin);
        assertTrue("lifted no lower than where it was", container.getTranslationY() <= 0f);
        assertTrue("never past the room's top",
            top + container.getTranslationY() >= ROOM_TOP);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        assertEquals("put back when the keyboard closes", 0f, container.getTranslationY(), 0f);
    }

    @Test
    public void aCascadedFloatKeepsItsTitlebarBelowTheFirstOnesWhenTheKeyboardIsUp() {
        // The emulator check: the cascaded float rose to the room's top with the keyboard up and
        // hid the first float's titlebar again.
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        TerminalPaneController.Window second = mController.openHomeFloat(terminal(), 0);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        View container = containerOf(second);
        int cascadedTop = ((FrameLayout.LayoutParams) container.getLayoutParams()).topMargin;
        assertTrue("born below the first", cascadedTop > ROOM_TOP);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("its titlebar stays where it was", cascadedTop, params.topMargin);
        assertEquals("its bottom at the keyboard's top", 400, params.topMargin + params.height, 1);
        assertEquals("shrunk, not lifted", 0f, container.getTranslationY(), 0f);
        assertEquals("the first sits flat at its size", ROOM_TOP,
            ((FrameLayout.LayoutParams) containerOf(first).getLayoutParams()).topMargin);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals(cascadedTop, params.topMargin);
        assertEquals("grown back when the keyboard closes", DOCK_TOP,
            params.topMargin + params.height, 1);
    }

    @Test
    public void aFullSizeFloatShrinksAboveTheKeyboardAndGrowsBack() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        RectF born = new RectF(window.active.floatFrac);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("its titlebar at the room's top", ROOM_TOP, params.topMargin);
        assertEquals("its bottom at the keyboard's top", 400 - ROOM_TOP, params.height);
        assertEquals(LAYER_WIDTH, params.width);
        assertEquals("shrunk, not lifted", 0f, container.getTranslationY(), 0f);
        assertEquals("the keyboard is no resize: the float still fills its room",
            born, window.active.floatFrac);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("grown back when the keyboard closes", DOCK_TOP - ROOM_TOP, params.height);
        assertEquals(ROOM_TOP, params.topMargin);
    }

    @Test
    public void aMaximisedFloatShrinksForTheKeyboardAndStillRestoresItsShape() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        RectF small = new RectF(.1f, .5f, .8f, .8f);
        placeSmaller(window, small);
        mController.toggleFloatMaximised(window.active);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        assertEquals(400 - ROOM_TOP, container.getLayoutParams().height);
        assertTrue("still maximised", FloatTitlebarPolicy.isMaximised(window.active.floatRestoreFrac));
        assertNear("the restore shape is kept", small, window.active.floatRestoreFrac);

        // Restore with the keyboard up: the float is the shape it was, lifted rather than shrunk.
        mController.toggleFloatMaximised(window.active);
        assertNear("restored", small, window.active.floatFrac);
        assertEquals(Math.round(small.height() * (DOCK_TOP - ROOM_TOP)),
            container.getLayoutParams().height);
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        assertEquals(Math.round(small.height() * (DOCK_TOP - ROOM_TOP)),
            container.getLayoutParams().height);
    }

    @Test
    public void onlyTheFocusedFloatShrinksForTheKeyboard() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        TerminalPaneController.Window second = mController.openHomeFloat(terminal(), 0);
        // Born cascaded off the first; maximised, it fills the room as the first does.
        titlebarOf(containerOf(second)).buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE).performClick();
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        assertEquals(400 - ROOM_TOP, containerOf(second).getLayoutParams().height);
        assertEquals("an unfocused float sits at its size", DOCK_TOP - ROOM_TOP,
            containerOf(first).getLayoutParams().height);

        mController.focusHomeFloat(first);
        assertEquals(400 - ROOM_TOP, containerOf(first).getLayoutParams().height);
        assertEquals(DOCK_TOP - ROOM_TOP, containerOf(second).getLayoutParams().height);
    }

    @Test
    public void aFloatReachesTheRoomsBottomOnlyWhileItEndsThere() {
        assertTrue(HomeFloatPolicy.reachesBottom(FloatTitlebarPolicy.fullRoom()));
        assertTrue(HomeFloatPolicy.reachesBottom(HomeFloatPolicy.birthFrac()));
        assertTrue("rounding is not a placement",
            HomeFloatPolicy.reachesBottom(new RectF(0f, 0f, 1.00001f, 0.99999f)));
        assertTrue("a cascaded float", HomeFloatPolicy.reachesBottom(new RectF(.03f, .05f, 1f, 1f)));
        assertTrue(HomeFloatPolicy.reachesBottom(new RectF(.05f, 0f, 1.05f, 1f)));
        assertFalse(HomeFloatPolicy.reachesBottom(new RectF(0f, 0f, 1f, .9f)));
        assertFalse(HomeFloatPolicy.reachesBottom(null));
    }

    @Test
    public void theKeyboardRoomShrinksOnlyAFloatThatReachesTheRoomsBottom() {
        Rect room = new Rect(0, 80, 600, 880);
        RectF full = FloatTitlebarPolicy.fullRoom();
        assertEquals(new Rect(0, 80, 600, 400),
            HomeFloatPolicy.keyboardRoom(room, full, true, 400, 90));
        assertEquals("a smaller float keeps the room and is lifted", room,
            HomeFloatPolicy.keyboardRoom(room, new RectF(.1f, .5f, .8f, .9f), true, 400, 90));
        assertEquals("keyboard down", room, HomeFloatPolicy.keyboardRoom(room, full, false, 400, 90));
        assertEquals("a keyboard below the room changes nothing", room,
            HomeFloatPolicy.keyboardRoom(room, full, true, 900, 90));
        assertEquals("never inverted", new Rect(0, 80, 600, 81),
            HomeFloatPolicy.keyboardRoom(room, full, true, 20, 0));
    }

    @Test
    public void aCascadedFloatLaidOutInItsKeyboardRoomKeepsItsTopAndEndsAtTheKeyboard() {
        Rect room = new Rect(0, 80, 600, 880);
        // Cascaded 40px down and 16px right off a full-size float.
        RectF frac = new RectF(16f / 600f, 40f / 800f, 1f, 1f);
        Rect laidOut = HomeFloatPolicy.keyboardRoom(room, frac, true, 400, 90);
        int top = laidOut.top + Math.round(frac.top * laidOut.height());
        int bottom = top + Math.round(frac.height() * laidOut.height());
        assertEquals("its titlebar stays below the first one's", 120, top);
        assertEquals("its bottom meets the keyboard", 400, bottom, 1);
        // A drag starts from the shape it shows, in the whole room's terms.
        RectF shown = HomeFloatPolicy.reframe(frac, laidOut, room);
        assertEquals(40f / 800f, shown.top, 1f / 800f);
        assertEquals(320f / 800f, shown.bottom, 1f / 800f);
        assertEquals(frac.left, shown.left, 1e-6f);
        assertEquals(1f, shown.right, 1e-6f);
    }

    @Test
    public void aFloatShrunkForTheKeyboardKeepsItsOwnTop() {
        // The emulator's numbers: a room from 220 to 2100, the keyboard's top at 1400, a float
        // cascaded 92px down off a full-size one, so its titlebar sits at 312.
        Rect room = new Rect(0, 220, 1080, 2100);
        assertEquals("the titlebar stays put, the bottom meets the keyboard",
            new Rect(42, 312, 1080, 1400),
            HomeFloatPolicy.keyboardShrunk(new Rect(42, 312, 1080, 2100), room, 1400, 300));
        assertEquals("a float at the room's top ends at the keyboard, as today",
            new Rect(0, 220, 1080, 1400),
            HomeFloatPolicy.keyboardShrunk(new Rect(room), room, 1400, 300));
        assertEquals("the minimum height holds",
            new Rect(42, 1200, 1080, 1500),
            HomeFloatPolicy.keyboardShrunk(new Rect(42, 1200, 1080, 2100), room, 1400, 300));
        assertEquals("never grown past the height it had",
            new Rect(42, 1300, 1080, 1500),
            HomeFloatPolicy.keyboardShrunk(new Rect(42, 1300, 1080, 1500), room, 1400, 300));
        assertEquals("keyboard down", new Rect(42, 312, 1080, 2100),
            HomeFloatPolicy.keyboardShrunk(new Rect(42, 312, 1080, 2100), room, -1, 300));
        assertEquals("a keyboard below the float changes nothing", new Rect(42, 312, 1080, 1000),
            HomeFloatPolicy.keyboardShrunk(new Rect(42, 312, 1080, 1000), room, 1400, 300));
        assertEquals("never inverted", new Rect(0, 220, 1080, 221),
            HomeFloatPolicy.keyboardShrunk(new Rect(room), room, 20, 0));
    }

    @Test
    public void reframeKeepsThePixelsAFloatShows() {
        Rect shrunk = new Rect(0, 80, 600, 400);
        Rect room = new Rect(0, 80, 600, 880);
        RectF inRoom = HomeFloatPolicy.reframe(new RectF(0f, 0f, 1f, 1f), shrunk, room);
        assertEquals(0f, inRoom.left, 1e-6f);
        assertEquals(0f, inRoom.top, 1e-6f);
        assertEquals(1f, inRoom.right, 1e-6f);
        assertEquals(320f / 800f, inRoom.bottom, 1e-6f);
        assertEquals(new RectF(.1f, .2f, .3f, .4f),
            HomeFloatPolicy.reframe(new RectF(.1f, .2f, .3f, .4f), room, room));
    }

    /** Gives {@code window}'s float a shape smaller than the room and lays it out at that shape. */
    private void placeSmaller(@NonNull TerminalPaneController.Window window, @NonNull RectF frac) {
        window.active.floatFrac = new RectF(frac);
        window.active.appliedFloatFrac = null;
        // Maximise and restore: the float is laid out again at the shape it was given.
        mController.toggleFloatMaximised(window.active);
        mController.toggleFloatMaximised(window.active);
    }

    private static void assertNear(String message, RectF expected, @Nullable RectF actual) {
        assertNotNull(message, actual);
        assertEquals(message, expected.left, actual.left, 1e-4f);
        assertEquals(message, expected.top, actual.top, 1e-4f);
        assertEquals(message, expected.right, actual.right, 1e-4f);
        assertEquals(message, expected.bottom, actual.bottom, 1e-4f);
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
