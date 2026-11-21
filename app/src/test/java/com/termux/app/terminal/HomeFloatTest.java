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

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Home floats (docs/GLOSSARY.md): windows of the session drawn over one Home page, pinned to it,
 * born a window inside the room between the status bar and the dock, remembering their shape per orientation,
 * shaped above the keyboard, and the status bar on Home that comes with them.
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
    public void aHomeFloatIsAWindowOutOfTheRowBornAWindowAndFocused() {
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
        // The regression: the first float filled the whole room, so it took the screen. It is a
        // window: centred, narrower than the room, below its top, the wallpaper round it.
        int roomHeight = DOCK_TOP - ROOM_TOP;
        int width = Math.round(HomeFloatPolicy.BIRTH_WIDTH_FRAC * LAYER_WIDTH);
        assertEquals("narrower than the room", width, params.width, 1);
        assertEquals("centred", (LAYER_WIDTH - width) / 2, params.leftMargin, 1);
        assertEquals("under the status bar's edge by the side gap", ROOM_TOP + params.leftMargin,
            params.topMargin, 1);
        assertEquals("no keyboard known yet: ending at the room's middle",
            ROOM_TOP + roomHeight / 2, params.topMargin + params.height, 1);
        assertTrue(params.width < LAYER_WIDTH && params.height < roomHeight);
        assertFalse("not maximised", FloatTitlebarPolicy.isMaximised(window.active.floatRestoreFrac));
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
        assertEquals("the same height", under.height, over.height, 1);
        assertEquals("the same width, a step right", under.leftMargin + under.width + step,
            over.leftMargin + over.width, 1);
    }

    @Test
    public void aFloatOnAnotherPageOrOverAMinimisedOneIsBornAsTheFirstWas() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        FrameLayout.LayoutParams born = (FrameLayout.LayoutParams) containerOf(first).getLayoutParams();
        int bornTop = born.topMargin;
        int bornLeft = born.leftMargin;
        TerminalPaneController.Window elsewhere = mController.openHomeFloat(terminal(), 1);
        mController.minimiseHomeFloat(first);
        TerminalPaneController.Window again = mController.openHomeFloat(terminal(), 0);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) containerOf(again).getLayoutParams();
        assertEquals(bornTop, params.topMargin);
        assertEquals(bornLeft, params.leftMargin);
        assertNotNull(elsewhere);
    }

    /** A 390 dp portrait phone at 3 px/dp: a room of 1122 by about 1700 px. */
    private static final Rect PORTRAIT_ROOM = new Rect(24, 270, 1146, 1900);
    /** Landscape: 2400 by 900 px, the status bar's edge at 60. */
    private static final Rect LANDSCAPE_ROOM = new Rect(24, 84, 2376, 876);
    private static final int GAP = 12;

    private static RectF px(RectF frac, Rect room) {
        return new RectF(room.left + frac.left * room.width(), room.top + frac.top * room.height(),
            room.left + frac.right * room.width(), room.top + frac.bottom * room.height());
    }

    @Test
    public void birthWithTheKeyboardUpStandsAGapAboveItsTopUnderTheStatusBarByTheSideGap() {
        RectF born = px(HomeFloatPolicy.birthFrac(PORTRAIT_ROOM, 246, 1300, GAP, 360, 270, 105),
            PORTRAIT_ROOM);
        float side = (1122 + 48 - HomeFloatPolicy.BIRTH_WIDTH_FRAC * 1122) / 2f;
        assertEquals("86 % wide", HomeFloatPolicy.BIRTH_WIDTH_FRAC * 1122, born.width(), 1f);
        assertEquals("centred, a side gap from the screen's edge", side, born.left, 1f);
        assertEquals("centred", 1170 - side, born.right, 1f);
        assertEquals("under the status bar's edge by the side gap", 246 + side, born.top, 1f);
        assertEquals("a small gap above the keyboard's top", 1300 - GAP, born.bottom, 1f);
    }

    @Test
    public void birthWithTheKeyboardDownStandsWhereTheRaisedKeyboardWillBe() {
        RectF up = HomeFloatPolicy.birthFrac(PORTRAIT_ROOM, 246, 1300, GAP, 360, 270, 105);
        int raised = HomeFloatPolicy.raisedKeyboardTopPx(-1, 600, true, 1900);
        assertEquals("the rise it last had, from the layer's bottom", 1300, raised);
        assertEquals("the same rect with the keyboard down", up,
            HomeFloatPolicy.birthFrac(PORTRAIT_ROOM, 246, raised, GAP, 360, 270, 105));
        assertEquals("live wins over what was remembered", 1250,
            HomeFloatPolicy.raisedKeyboardTopPx(1250, 600, true, 1900));
        assertEquals("never up in this orientation", -1,
            HomeFloatPolicy.raisedKeyboardTopPx(-1, 600, false, 1900));
        assertEquals("never up at all", -1, HomeFloatPolicy.raisedKeyboardTopPx(-1, 0, true, 1900));
    }

    @Test
    public void birthWithNoKeyboardKnownEndsAtTheRoomsMiddle() {
        RectF born = px(HomeFloatPolicy.birthFrac(PORTRAIT_ROOM, 246, -1, GAP, 360, 270, 105),
            PORTRAIT_ROOM);
        assertEquals("the room's vertical middle", (270 + 1900) / 2f, born.bottom, 1f);
        assertEquals("under the status bar's edge by the side gap", 246 + born.left, born.top, 1f);
    }

    @Test
    public void birthInLandscapeKeepsTheSameGapsAndNeverUndercutsTheLeastHeight() {
        RectF born = px(HomeFloatPolicy.birthFrac(LANDSCAPE_ROOM, 60, 600, GAP, 360, 270, 105),
            LANDSCAPE_ROOM);
        assertEquals("86 % wide", HomeFloatPolicy.BIRTH_WIDTH_FRAC * 2352, born.width(), 1f);
        assertEquals("under the bar by the side gap", 60 + born.left, born.top, 1f);
        assertEquals("above the keyboard", 600 - GAP, born.bottom, 1f);
        // A keyboard so high that the room above it is shorter than the least size: the bottom
        // goes down for the least height.
        RectF high = px(HomeFloatPolicy.birthFrac(LANDSCAPE_ROOM, 60, 200, GAP, 360, 270, 105),
            LANDSCAPE_ROOM);
        assertEquals("never shorter than the least height", 270, high.height(), 1f);
        // A room below the least size: all of it on that axis.
        assertEquals(new RectF(0f, 0f, 1f, 1f),
            HomeFloatPolicy.birthFrac(new Rect(0, 0, 100, 60), 0, -1, GAP, 120, 90, 35));
        RectF narrow = HomeFloatPolicy.birthFrac(new Rect(0, 80, 300, 880), 80, 600, GAP, 280, 90, 35);
        assertEquals("never narrower than the least width", 280 / 300f, narrow.width(), 1e-4f);
        assertEquals("still centred", 10 / 300f, narrow.left, 1e-4f);
        assertEquals("unmeasured", HomeFloatPolicy.birthFrac(),
            HomeFloatPolicy.birthFrac(new Rect(), 0, 600, GAP, 120, 90, 35));
    }

    @Test
    public void steppedFloatsKeepTheBirthSizeStepDownAndClampRightThenWrapAtTheBottom() {
        Rect room = PORTRAIT_ROOM;
        int w = room.width();
        int h = room.height();
        RectF birth = HomeFloatPolicy.birthFrac(room, 246, 1300, GAP, 360, 270, 105);
        assertEquals("nothing under it: the first position", birth,
            HomeFloatPolicy.cascadeFrac(null, birth, w, h, 48, 105));
        assertEquals("a room not measured yet", HomeFloatPolicy.birthFrac(),
            HomeFloatPolicy.cascadeFrac(birth, birth, 0, 0, 48, 105));

        RectF second = HomeFloatPolicy.cascadeFrac(birth, birth, w, h, 48, 105);
        assertEquals("a step right", birth.left + 48f / w, second.left, 1e-4f);
        assertEquals("a band down", birth.top + 105f / h, second.top, 1e-4f);
        assertEquals("the full birth size", birth.width(), second.width(), 1e-4f);
        assertEquals(birth.height(), second.height(), 1e-4f);

        // The side gap is used up after two steps: the float stays at the right limit.
        RectF third = HomeFloatPolicy.cascadeFrac(second, birth, w, h, 48, 105);
        RectF fourth = HomeFloatPolicy.cascadeFrac(third, birth, w, h, 48, 105);
        assertEquals("stops at the room's right limit", 1f, fourth.right, 1e-4f);
        assertEquals("does not shrink", birth.width(), fourth.width(), 1e-4f);
        assertEquals("still a band lower each step", third.top + 105f / h, fourth.top, 1e-4f);
        assertEquals("clamped, not wrapped", 1f, HomeFloatPolicy.cascadeFrac(fourth, birth, w, h,
            48, 105).right, 1e-4f);

        // A float whose full-size bottom would pass the room's bottom wraps to the birth rect.
        RectF low = new RectF(.1f, 1f - birth.height() - 50f / h, .1f + birth.width(),
            1f - 50f / h);
        assertEquals("wraps to the first position at full size", birth,
            HomeFloatPolicy.cascadeFrac(low, birth, w, h, 48, 105));
        assertEquals("and steps from there", second,
            HomeFloatPolicy.cascadeFrac(HomeFloatPolicy.cascadeFrac(low, birth, w, h, 48, 105),
                birth, w, h, 48, 105));
    }

    @Test
    public void aNewFloatTakesTheFirstFreePositionWhicheverFloatHasFocus() {
        Rect room = PORTRAIT_ROOM;
        int w = room.width();
        int h = room.height();
        RectF birth = HomeFloatPolicy.birthFrac(room, 246, 1300, GAP, 360, 270, 105);
        RectF p2 = HomeFloatPolicy.cascadeFrac(birth, birth, w, h, 48, 105);
        RectF p3 = HomeFloatPolicy.cascadeFrac(p2, birth, w, h, 48, 105);
        java.util.List<RectF> none = new java.util.ArrayList<>();
        assertEquals(birth, HomeFloatPolicy.openingFrac(none, birth, w, h, 48, 105));
        // Floats in any order of focus: the order of the list never matters.
        assertEquals(p2, HomeFloatPolicy.openingFrac(java.util.Arrays.asList(birth), birth, w, h, 48, 105));
        assertEquals(p3, HomeFloatPolicy.openingFrac(java.util.Arrays.asList(birth, p2), birth, w, h, 48, 105));
        assertEquals(p3, HomeFloatPolicy.openingFrac(java.util.Arrays.asList(p2, birth), birth, w, h, 48, 105));
        // The middle one closed: its position is free again.
        assertEquals(p2, HomeFloatPolicy.openingFrac(java.util.Arrays.asList(birth, p3), birth, w, h, 48, 105));
        // A float the user moved away does not block the place it left.
        RectF moved = new RectF(p2);
        moved.offset(.2f, .1f);
        assertEquals(p2, HomeFloatPolicy.openingFrac(java.util.Arrays.asList(birth, moved), birth, w, h, 48, 105));
        // A float within a couple of px still blocks it.
        RectF near = new RectF(p2);
        near.offset(1f / w, 1f / h);
        assertEquals(p3, HomeFloatPolicy.openingFrac(java.util.Arrays.asList(birth, near), birth, w, h, 48, 105));
        // Every position taken up to the wrap: the birth rect.
        java.util.List<RectF> all = new java.util.ArrayList<>();
        RectF at = birth;
        do {
            all.add(at);
            at = HomeFloatPolicy.cascadeFrac(at, birth, w, h, 48, 105);
        } while (!at.equals(birth));
        assertEquals(birth, HomeFloatPolicy.openingFrac(all, birth, w, h, 48, 105));
    }

    @Test
    public void steppingInLandscapeAndInARoomAtTheLeastSize() {
        Rect room = LANDSCAPE_ROOM;
        int w = room.width();
        int h = room.height();
        RectF birth = HomeFloatPolicy.birthFrac(room, 60, 600, GAP, 360, 270, 105);
        RectF second = HomeFloatPolicy.cascadeFrac(birth, birth, w, h, 48, 105);
        assertEquals(birth.top + 105f / h, second.top, 1e-4f);
        assertEquals(birth.height(), second.height(), 1e-4f);
        assertEquals("landscape's room is short: the second float wraps once it cannot fit",
            birth, HomeFloatPolicy.cascadeFrac(
                HomeFloatPolicy.cascadeFrac(second, birth, w, h, 48, 105), birth, w, h, 48, 105));
        // A room the size of the least window: nothing to step into.
        Rect tiny = new Rect(0, 0, 360, 270);
        RectF whole = HomeFloatPolicy.birthFrac(tiny, 0, -1, GAP, 360, 270, 105);
        assertEquals(whole, HomeFloatPolicy.cascadeFrac(whole, whole, 360, 270, 48, 105));
    }

    @Test
    public void howManyFloatsFitBeforeTheWrapOnA1080x2412RoomAt420dpi() {
        float d = 420f / 160f;
        // Status bar about 28 dp, Margin 8 dp, dock about 96 dp, keyboard about 300 dp.
        int layerW = 1080;
        int layerH = 2412;
        int edgeTop = Math.round(28 * d);
        Rect room = HomeFloatPolicy.room(layerW, layerH, edgeTop, Math.round(96 * d),
            Math.round(8 * d));
        int keyboardTop = layerH - Math.round(300 * d);
        RectF birth = HomeFloatPolicy.birthFrac(room, edgeTop, keyboardTop, Math.round(4 * d),
            Math.round(120 * d), Math.round(90 * d), Math.round(36 * d));
        int fits = 1;
        RectF at = birth;
        while (true) {
            RectF next = HomeFloatPolicy.cascadeFrac(at, birth, room.width(), room.height(),
                Math.round(16 * d), Math.round(36 * d));
            if (next.equals(birth)) break;
            at = next;
            fits++;
        }
        assertEquals("floats before the first wraps", 6, fits);
    }

    private int keyboardGap() {
        return Math.round(com.termux.app.chrome.LiveChromeShape.TERMINAL_VERTICAL_GAP_DP * (
            context().getResources().getDisplayMetrics().density));
    }

    @Test
    public void aFloatBornWithTheKeyboardUpEndsASmallGapAboveIt() {
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 600);
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) containerOf(window).getLayoutParams();
        assertEquals("a small gap above the keyboard's top", 600 - keyboardGap(),
            params.topMargin + params.height, 1);
        assertEquals("under the status bar's edge by the side gap", ROOM_TOP + params.leftMargin,
            params.topMargin, 1);
    }

    @Test
    public void aFloatBornWithTheKeyboardDownEndsWhereTheKeyboardWasRaised() {
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 600);
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) containerOf(window).getLayoutParams();
        assertEquals("the same rect as with the keyboard up", 600 - keyboardGap(),
            params.topMargin + params.height, 1);
    }

    @Test
    public void theFocusedFloatExtendedBelowTheKeyboardShrinksFromTheBottomAndGrowsBack() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        RectF extended = new RectF(.1f, .2f, .8f, .95f);
        placeSmaller(window, extended);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        FrameLayout.LayoutParams before = (FrameLayout.LayoutParams) container.getLayoutParams();
        int top = before.topMargin;
        int left = before.leftMargin;
        int width = before.width;
        int height = before.height;

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 500);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("the bottom holds a small gap above the keyboard", 500 - keyboardGap(),
            params.topMargin + params.height, 1);
        assertEquals("the top does not move", top, params.topMargin);
        assertEquals("nor the sides", left, params.leftMargin);
        assertEquals(width, params.width);
        assertNear("the stored rect is the user's and is never written back", extended,
            window.active.floatFrac);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("back at the height the user set", height, params.height);
        assertEquals(top, params.topMargin);
    }

    @Test
    public void aFloatWhollyAboveTheKeyboardLineIsUntouched() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        RectF high = new RectF(.1f, .05f, .8f, .3f);
        placeSmaller(window, high);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        int height = container.getLayoutParams().height;
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 600);
        assertEquals(height, container.getLayoutParams().height);
    }

    @Test
    public void onlyTheFocusedFloatMakesRoomAndFocusMovesIt() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        placeSmaller(first, new RectF(.1f, .1f, .8f, .95f));
        TerminalPaneController.Window second = mController.openHomeFloat(terminal(), 0);
        placeSmaller(second, new RectF(.1f, .2f, .8f, .95f));
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        int firstHeight = containerOf(first).getLayoutParams().height;
        int secondHeight = containerOf(second).getLayoutParams().height;

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 500);
        FrameLayout.LayoutParams p2 = (FrameLayout.LayoutParams) containerOf(second).getLayoutParams();
        assertEquals("the focused one holds the gap", 500 - keyboardGap(), p2.topMargin + p2.height, 1);
        assertEquals("an unfocused float keeps its rect", firstHeight,
            containerOf(first).getLayoutParams().height);

        mController.focusHomeFloat(first);
        FrameLayout.LayoutParams p1 = (FrameLayout.LayoutParams) containerOf(first).getLayoutParams();
        assertEquals("the newly focused one makes room", 500 - keyboardGap(),
            p1.topMargin + p1.height, 1);
        assertEquals("the old one returns to its own rect", secondHeight,
            containerOf(second).getLayoutParams().height);
    }

    @Test
    public void aFocusedFloatNeverShrinksBelowTheLeastHeightAboveTheKeyboard() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        placeSmaller(window, new RectF(.1f, .5f, .8f, .95f));
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 300);
        assertEquals("the least height it keeps over the keyboard", 160,
            containerOf(window).getLayoutParams().height, 1);
    }

    @Test
    public void aNewFloatAboveTheKeyboardTakesAFreshFloatThatComesUpWithIt() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        int bornTop = ((FrameLayout.LayoutParams) container.getLayoutParams()).topMargin;

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("its titlebar stays where it was born", bornTop, params.topMargin);
        assertEquals("its bottom a gap above the keyboard's top", 400 - keyboardGap(),
            params.topMargin + params.height, 1);
        assertEquals("shrunk, not lifted", 0f, container.getTranslationY(), 0f);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("grown back when the keyboard closes", (ROOM_TOP + DOCK_TOP) / 2,
            params.topMargin + params.height, 1);
        // Maximise still fills the whole room.
        mController.toggleFloatMaximised(window.active);
        assertEquals(FloatTitlebarPolicy.fullRoom(), window.active.floatFrac);
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
        assertTrue(mLayer.holdsPoint(300, 300));
        assertFalse("above the room, under the status bar", mLayer.holdsPoint(300, ROOM_TOP / 2f));
        mLayer.onHomePageShown(1);
        assertFalse("another page's float holds nothing here", mLayer.holdsPoint(300, 300));
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
    public void closingTheFocusedFloatLeavesTheTerminalAlone() {
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

    // --- Saved sessions ---

    @Test
    public void aSavedFloatLandsOnItsPageOrTheLastOneAndTiledWithoutHomePages() {
        assertEquals(1, HomeFloatPolicy.restoredPage(1, 3));
        assertEquals(0, HomeFloatPolicy.restoredPage(0, 1));
        assertEquals("a page that is gone lands it on the last one", 2,
            HomeFloatPolicy.restoredPage(5, 3));
        assertEquals(0, HomeFloatPolicy.restoredPage(-3, 2));
        assertEquals("no Home pages: a tiled window", HomeFloatPolicy.NOT_HOME_FLOAT,
            HomeFloatPolicy.restoredPage(1, 0));
    }

    @Test
    public void everySavedFloatFloatsEvenWhenTheRowIsLeftEmpty() {
        boolean[] mixed = {false, true, false, true};
        assertArrayEquals(mixed, HomeFloatPolicy.restoredAsFloats(mixed, true));
        assertArrayEquals("no Home pages: every window loads tiled",
            new boolean[]{false, false, false, false},
            HomeFloatPolicy.restoredAsFloats(mixed, false));
        assertArrayEquals("floats only: floats only, and the Terminal is empty",
            new boolean[]{true, true, true},
            HomeFloatPolicy.restoredAsFloats(new boolean[]{true, true, true}, true));
        assertArrayEquals("floats only without Home pages: all in the row",
            new boolean[]{false, false},
            HomeFloatPolicy.restoredAsFloats(new boolean[]{true, true}, false));
        assertEquals(0, HomeFloatPolicy.restoredAsFloats(new boolean[0], true).length);
    }

    @Test
    public void theCurrentWindowIsCountedInTheRowWithoutTheFloats() {
        boolean[] asFloat = {false, true, false, true, false};
        assertEquals(0, HomeFloatPolicy.restoredRowIndex(asFloat, 0));
        assertEquals("a float current leaves the row on the window before it", 0,
            HomeFloatPolicy.restoredRowIndex(asFloat, 1));
        assertEquals(1, HomeFloatPolicy.restoredRowIndex(asFloat, 2));
        assertEquals(1, HomeFloatPolicy.restoredRowIndex(asFloat, 3));
        assertEquals(2, HomeFloatPolicy.restoredRowIndex(asFloat, 4));
        assertEquals("past the end: the last window", 2, HomeFloatPolicy.restoredRowIndex(asFloat, 9));
        assertEquals("a float first: the row's first", 0,
            HomeFloatPolicy.restoredRowIndex(new boolean[]{true, false}, 0));
        assertEquals("floats only: no current window in the row", -1,
            HomeFloatPolicy.restoredRowIndex(new boolean[]{true, true}, 1));
        assertEquals(-1, HomeFloatPolicy.restoredRowIndex(new boolean[0], 0));
    }

    @Test
    public void savedShapesKeepTheLiveShapeUnderItsOrientationAndTheRememberedOther() {
        RectF portrait = new RectF(.1f, .1f, .6f, .5f);
        RectF landscape = new RectF(.3f, .2f, .9f, .9f);
        RectF[] none = HomeFloatPolicy.savedShapes(null, portrait, false);
        assertEquals(portrait, none[0]);
        assertNull(none[1]);

        HomeFloatPolicy.ShapeMemory memory = new HomeFloatPolicy.ShapeMemory();
        memory.turn(false, portrait, null);
        memory.turn(true, portrait, null);
        // Laid out in landscape now; portrait is what it left.
        RectF[] both = HomeFloatPolicy.savedShapes(memory, landscape, false);
        assertEquals(portrait, both[0]);
        assertEquals("the memory's orientation wins over the screen's", landscape, both[1]);
    }

    @Test
    public void aSeededShapeComesBackOnTheFirstTurnIntoItsOrientation() {
        HomeFloatPolicy.ShapeMemory memory = new HomeFloatPolicy.ShapeMemory();
        RectF landscape = new RectF(.3f, .2f, .9f, .9f);
        memory.seed(true, landscape);
        RectF portrait = new RectF(.1f, .1f, .6f, .5f);
        assertNull(memory.turn(false, portrait, null));
        assertEquals(landscape, memory.remembered(true));
        assertNull("the live orientation keeps its shape on the float", memory.remembered(false));
        HomeFloatPolicy.Shape back = memory.turn(true, portrait, null);
        assertNotNull(back);
        assertEquals(landscape, back.frac);
        assertNull(back.restoreFrac);
    }

    @Test
    public void aFloatSavedAndLoadedComesBackOnItsPageMinimisedAtItsShape() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 1);
        RectF shaped = new RectF(.1f, .2f, .6f, .7f);
        window.active.floatFrac = new RectF(shaped);
        mController.minimiseHomeFloat(window);
        TerminalWorkspace.Window saved = mController.snapshotWorkspaceWindow(window,
            shell -> new TerminalWorkspace.Pane("/home", null, null));

        assertNotNull(saved.homeFloat);
        assertEquals(1, saved.homeFloat.pageId);
        assertTrue(saved.homeFloat.minimised);
        assertTrue(saved.root instanceof TerminalWorkspace.Pane);
        assertTrue(saved.floats.isEmpty());
        boolean landscape = landscapeScreen();
        TerminalWorkspace.FloatShape live = landscape ? saved.homeFloat.landscape
            : saved.homeFloat.portrait;
        assertNotNull(live);
        assertEquals(.1f, live.left, .001f);
        assertEquals(.2f, live.top, .001f);
        assertEquals(.5f, live.width, .001f);
        assertEquals(.5f, live.height, .001f);
        assertNull("never shaped in the other orientation",
            landscape ? saved.homeFloat.portrait : saved.homeFloat.landscape);

        TerminalSession shell = terminal();
        TerminalPaneController.Window loaded = mController.newWorkspaceHomeFloat(saved,
            java.util.Collections.singletonList(shell), 1);
        assertTrue(loaded.isHomeFloat());
        assertEquals(1, loaded.homePage());
        assertTrue("it comes back as a chip", loaded.isMinimised());
        assertSame(loaded, mController.windowOf(shell));
        assertTrue(mController.homeFloats().contains(loaded));
        assertNull("a loaded float takes no focus", mController.focusedHomeFloat());
        RectF frac = loaded.active.floatFrac;
        assertNotNull(frac);
        assertEquals(shaped.left, frac.left, .001f);
        assertEquals(shaped.top, frac.top, .001f);
        assertEquals(shaped.right, frac.right, .001f);
        assertEquals(shaped.bottom, frac.bottom, .001f);
        assertEquals(View.GONE, containerOf(loaded).getVisibility());
    }

    @Test
    public void aLoadedFloatSeedsTheOtherOrientationAndOneWithoutAShapeIsBornAsNew() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        boolean landscape = landscapeScreen();
        TerminalWorkspace.FloatShape other = new TerminalWorkspace.FloatShape(.2f, .1f, .5f, .6f);
        TerminalWorkspace.Window definition = new TerminalWorkspace.Window(0,
            new TerminalWorkspace.Pane("/", null, null), null, null,
            new TerminalWorkspace.HomeFloat(0, false, landscape ? other : null,
                landscape ? null : other));

        TerminalPaneController.Window loaded = mController.newWorkspaceHomeFloat(definition,
            java.util.Collections.singletonList(terminal()), 0);
        assertFalse(loaded.isMinimised());
        assertNotNull(loaded.active.shapeMemory);
        RectF seeded = loaded.active.shapeMemory.remembered(!landscape);
        assertNotNull("the other orientation's shape is remembered", seeded);
        assertEquals(.2f, seeded.left, .001f);
        assertEquals(.1f, seeded.top, .001f);
        assertEquals(.7f, seeded.right, .001f);
        assertEquals(.7f, seeded.bottom, .001f);
        assertTrue("no shape for now: cascaded off the float shown on its page",
            loaded.active.floatFrac.top > first.active.floatFrac.top);
        assertEquals(View.VISIBLE, containerOf(loaded).getVisibility());
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
        int roomWidth = LAYER_WIDTH - 2 * margin;
        int roomHeight = DOCK_TOP - ROOM_TOP - 2 * margin;
        RectF frac = window.active.floatFrac;
        assertEquals("the birth's fractions of the smaller room",
            HomeFloatPolicy.BIRTH_WIDTH_FRAC, frac.width(), 1e-3f);
        assertEquals(margin + Math.round(frac.left * roomWidth), params.leftMargin, 1);
        assertEquals(ROOM_TOP + margin + Math.round(frac.top * roomHeight), params.topMargin, 1);
        assertEquals(Math.round(frac.width() * roomWidth), params.width, 1);
        assertEquals("ending at the smaller room's middle", ROOM_TOP + margin + roomHeight / 2,
            params.topMargin + params.height, 1);
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
    public void aSmallerFloatUnderTheKeyboardRisesToStandOnItAndComesBack() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        RectF small = new RectF(.1f, .5f, .8f, .9f);
        placeSmaller(window, small);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        int height = params.height;
        int top = params.topMargin;
        // Its top is below the keyboard's: shrinking cannot help, so it rises at the least height.
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("it stands on the keyboard's line", 400 - keyboardGap(),
            params.topMargin + params.height, 1);
        assertEquals("at the least height it keeps over the keyboard", 160, params.height, 1);
        assertTrue("never past the room's top", params.topMargin >= ROOM_TOP);
        assertEquals("moved, not lifted", 0f, container.getTranslationY(), 0f);
        assertNear("its shape is its own and stays", small, window.active.floatFrac);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("back where it was when the keyboard closes", top, params.topMargin);
        assertEquals(height, params.height);
    }

    @Test
    public void aMovedFloatThatTheKeyboardOverlapsShrinksAboveItKeepingItsTop() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        RectF moved = new RectF(.1f, .2f, .8f, .9f);
        placeSmaller(window, moved);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        int top = ((FrameLayout.LayoutParams) container.getLayoutParams()).topMargin;
        int height = container.getLayoutParams().height;
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 600);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("its titlebar stays where it was", top, params.topMargin, 1);
        assertEquals("its bottom a gap above the keyboard's top", 600 - keyboardGap(),
            params.topMargin + params.height, 1);
        assertNear("its shape is its own and stays", moved, window.active.floatFrac);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        assertEquals(height, container.getLayoutParams().height);
        // A keyboard below the float leaves it alone.
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, top + height + 10);
        assertEquals(height, container.getLayoutParams().height);
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
        int cascadedBottom = cascadedTop
            + ((FrameLayout.LayoutParams) container.getLayoutParams()).height;
        int firstTop = ((FrameLayout.LayoutParams) containerOf(first).getLayoutParams()).topMargin;
        assertTrue("born below the first", cascadedTop > firstTop);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("its titlebar stays where it was", cascadedTop, params.topMargin);
        assertEquals("its bottom a gap above the keyboard's top", 400 - keyboardGap(),
            params.topMargin + params.height, 1);
        assertEquals("shrunk, not lifted", 0f, container.getTranslationY(), 0f);
        assertEquals("the first sits flat at its size", firstTop,
            ((FrameLayout.LayoutParams) containerOf(first).getLayoutParams()).topMargin);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals(cascadedTop, params.topMargin);
        assertEquals("grown back when the keyboard closes", cascadedBottom,
            params.topMargin + params.height, 1);
    }

    @Test
    public void aFullSizeFloatShrinksAboveTheKeyboardAndGrowsBack() {
        TerminalPaneController.Window window = mController.openHomeFloat(terminal(), 0);
        mController.toggleFloatMaximised(window.active);
        View container = containerOf(window);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        RectF born = new RectF(window.active.floatFrac);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals("its titlebar at the room's top", ROOM_TOP, params.topMargin);
        assertEquals("its bottom a gap above the keyboard's top", 400 - keyboardGap() - ROOM_TOP,
            params.height);
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
        assertEquals(400 - keyboardGap() - ROOM_TOP, container.getLayoutParams().height);
        assertTrue("still maximised", FloatTitlebarPolicy.isMaximised(window.active.floatRestoreFrac));
        assertNear("the restore shape is kept", small, window.active.floatRestoreFrac);

        // Restore with the keyboard up: the float is the shape it was, standing on the keyboard
        // (its top lies below the keyboard's) at the least height it keeps there.
        mController.toggleFloatMaximised(window.active);
        assertNear("restored", small, window.active.floatFrac);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
        assertEquals(400 - keyboardGap(), params.topMargin + params.height, 1);
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        assertEquals(Math.round(small.height() * (DOCK_TOP - ROOM_TOP)),
            container.getLayoutParams().height);
    }

    @Test
    public void onlyTheFocusedFloatOnThePageMeetsTheKeyboardAndAnotherPagesWaits() {
        TerminalPaneController.Window first = mController.openHomeFloat(terminal(), 0);
        mController.toggleFloatMaximised(first.active);
        TerminalPaneController.Window second = mController.openHomeFloat(terminal(), 0);
        // Born cascaded off the first; maximised, it fills the room as the first does.
        titlebarOf(containerOf(second)).buttonFor(FloatTitlebarPolicy.ACTION_MAXIMISE).performClick();
        TerminalPaneController.Window elsewhere = mController.openHomeFloat(terminal(), 1);
        mController.toggleFloatMaximised(elsewhere.active);
        mController.focusHomeFloat(second);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        int line = 400 - keyboardGap();
        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, 400);
        assertEquals(line - ROOM_TOP, containerOf(second).getLayoutParams().height);
        assertEquals("an unfocused float on the page keeps its rect", DOCK_TOP - ROOM_TOP,
            containerOf(first).getLayoutParams().height);
        assertEquals("a float on another page keeps its shape", DOCK_TOP - ROOM_TOP,
            containerOf(elsewhere).getLayoutParams().height);

        mController.focusHomeFloat(first);
        assertEquals(line - ROOM_TOP, containerOf(first).getLayoutParams().height);
        assertEquals(DOCK_TOP - ROOM_TOP, containerOf(second).getLayoutParams().height);

        mController.setHomeFloatObstructions(ROOM_TOP, DOCK_TOP, -1);
        assertEquals(DOCK_TOP - ROOM_TOP, containerOf(first).getLayoutParams().height);
        assertEquals(DOCK_TOP - ROOM_TOP, containerOf(second).getLayoutParams().height);
    }

    @Test
    public void theKeyboardRoomShapesOnlyTheFocusedFloatThatReachesBelowTheKeyboardsGap() {
        Rect room = new Rect(0, 80, 600, 880);
        RectF full = FloatTitlebarPolicy.fullRoom();
        assertEquals(new Rect(0, 80, 600, 400),
            HomeFloatPolicy.keyboardRoom(room, full, true, true, 400, 0, 90));
        // A smaller float whose top is below the keyboard's: it rises to stand on the keyboard.
        RectF low = new RectF(.1f, .5f, .8f, .9f);
        Rect laidOut = HomeFloatPolicy.keyboardRoom(room, low, true, true, 400, 0, 90);
        int top = laidOut.top + Math.round(low.top * laidOut.height());
        int bottom = top + Math.round(low.height() * laidOut.height());
        assertEquals("it stands on the keyboard", 400, bottom, 1);
        assertEquals("at the least height", 90, bottom - top, 1);
        assertEquals("a float clear of the keyboard keeps the room", room,
            HomeFloatPolicy.keyboardRoom(room, new RectF(.1f, .1f, .8f, .3f), true, true, 400, 0, 90));
        assertEquals("keyboard down", room, HomeFloatPolicy.keyboardRoom(room, full, false, true, 400, 0, 90));
        assertEquals("an unfocused float keeps the room", room,
            HomeFloatPolicy.keyboardRoom(room, full, true, false, 400, 0, 90));
        assertEquals("the line stands a gap above the keyboard", new Rect(0, 80, 600, 388),
            HomeFloatPolicy.keyboardRoom(room, full, true, true, 400, 12, 90));
        assertEquals("a keyboard below the room changes nothing", room,
            HomeFloatPolicy.keyboardRoom(room, full, true, true, 900, 0, 90));
        assertEquals("never inverted", new Rect(0, 80, 600, 81),
            HomeFloatPolicy.keyboardRoom(room, full, true, true, 20, 0, 0));
    }

    @Test
    public void aCascadedFloatLaidOutInItsKeyboardRoomKeepsItsTopAndEndsAtTheKeyboard() {
        Rect room = new Rect(0, 80, 600, 880);
        // Cascaded 40px down and 16px right off a full-size float.
        RectF frac = new RectF(16f / 600f, 40f / 800f, 1f, 1f);
        Rect laidOut = HomeFloatPolicy.keyboardRoom(room, frac, true, true, 400, 0, 90);
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
    public void aFloatShapedForTheKeyboardKeepsItsOwnTopOrStandsOnIt() {
        // The emulator's numbers: a room from 220 to 2100, the keyboard's top at 1400, a float
        // cascaded 92px down off a full-size one, so its titlebar sits at 312.
        Rect room = new Rect(0, 220, 1080, 2100);
        assertEquals("the titlebar stays put, the bottom meets the keyboard",
            new Rect(42, 312, 1080, 1400),
            HomeFloatPolicy.keyboardShape(new Rect(42, 312, 1080, 2100), room, 1400, 300));
        assertEquals("a float at the room's top ends at the keyboard, as today",
            new Rect(0, 220, 1080, 1400),
            HomeFloatPolicy.keyboardShape(new Rect(room), room, 1400, 300));
        assertEquals("too little left above the keyboard: it rises at the least height",
            new Rect(42, 1100, 1080, 1400),
            HomeFloatPolicy.keyboardShape(new Rect(42, 1200, 1080, 2100), room, 1400, 300));
        assertEquals("never grown past the height it had",
            new Rect(42, 1200, 1080, 1400),
            HomeFloatPolicy.keyboardShape(new Rect(42, 1300, 1080, 1500), room, 1400, 300));
        assertEquals("never risen past the room's top: all the room above the keyboard",
            new Rect(42, 220, 1080, 400),
            HomeFloatPolicy.keyboardShape(new Rect(42, 300, 1080, 2100), room, 400, 300));
        assertEquals("keyboard down", new Rect(42, 312, 1080, 2100),
            HomeFloatPolicy.keyboardShape(new Rect(42, 312, 1080, 2100), room, -1, 300));
        assertEquals("a keyboard below the float changes nothing", new Rect(42, 312, 1080, 1000),
            HomeFloatPolicy.keyboardShape(new Rect(42, 312, 1080, 1000), room, 1400, 300));
        assertEquals("never inverted", new Rect(0, 220, 1080, 221),
            HomeFloatPolicy.keyboardShape(new Rect(room), room, 20, 0));
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
        mController.toggleFloatMaximised(window.active);
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
        mController.toggleFloatMaximised(window.active);
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

    private static boolean landscapeScreen() {
        return context().getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
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
