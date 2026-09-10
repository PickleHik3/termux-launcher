package com.termux.app.terminal.inappkeyboard;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The floating keyboard as it is hosted: the card collapses with the keyboard inside it, the handle
 * drags it and holds it inside the content, and the controller moves the keyboard's own container
 * between the accessory stack and the frame without rebuilding anything.
 *
 * <p>The bottom-left grip is here too: which touches it claims, that a drag on it resizes the card
 * while a drag on the pill only moves it, and that both are written on release.
 *
 * <p>The container here is a stand-in with a height the test owns, because what is being pinned is
 * the hosting and the placement rather than the keys. Two measure/layout passes are spelled out
 * where a device would take two frames: the frame's width is known before its height is.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class FloatingKeyboardFrameTest {

    private static final int HOST_WIDTH = 1000;
    private static final int HOST_HEIGHT = 1800;
    private static final int KEYBOARD_HEIGHT = 400;

    private Context context;
    private FrameLayout host;
    private RelativeLayout dock;
    private FixedHeightView container;
    private FakeHost fakeHost;
    private FloatingKeyboardController controller;
    private PlaceLayoutStore store;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        context = app;
        SharedPreferences prefs =
            app.getSharedPreferences("floating-keyboard-frame-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));

        host = new FrameLayout(context);
        dock = new RelativeLayout(context);
        // The keyboard's container is the dock's second child, so putting it back has an index to
        // put it back at.
        dock.addView(new View(context), new RelativeLayout.LayoutParams(HOST_WIDTH, 20));
        container = new FixedHeightView(context, KEYBOARD_HEIGHT);
        RelativeLayout.LayoutParams dockParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        dock.addView(container, dockParams);

        fakeHost = new FakeHost();
        controller = new FloatingKeyboardController(fakeHost);
        layoutHost();
    }

    // ------------------------------------------------------------------- the card

    @Test
    public void theHandleRowIs18dpAndItsPillIs52By3Point2dp() {
        // Robolectric's default qualifiers are mdpi, so a dp is a pixel here.
        FloatingKeyboardFrame frame = newHostedFrame();
        assertEquals(18, frame.grabHandle().getMeasuredHeight());
        assertEquals(52, frame.grabPill().getMeasuredWidth());
        assertEquals(3, frame.grabPill().getMeasuredHeight());
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    public void theHandleRowAndPillScaleWithTheDensity() {
        FloatingKeyboardFrame frame = newHostedFrame();
        assertEquals(54, frame.grabHandle().getMeasuredHeight());
        assertEquals(156, frame.grabPill().getMeasuredWidth());
        assertEquals(10, frame.grabPill().getMeasuredHeight());
    }

    @Test
    public void thePillNeverThinsBelowTwoPixels() {
        assertEquals(10, FloatingKeyboardFrame.pillHeightPx(3f));
        assertEquals(3, FloatingKeyboardFrame.pillHeightPx(1f));
        // Under a density of 0.625 the rounded pill would be a hairline, so the floor takes over.
        assertEquals(2, FloatingKeyboardFrame.pillHeightPx(0.625f));
        assertEquals(2, FloatingKeyboardFrame.pillHeightPx(0.1f));
    }

    /** A frame with a keyboard inside it, measured and laid out once. */
    @NonNull
    private FloatingKeyboardFrame newHostedFrame() {
        FloatingKeyboardFrame frame = new FloatingKeyboardFrame(context);
        frame.setWidthScaleSource(() -> 1f);
        frame.contentHost().addView(new FixedHeightView(context, KEYBOARD_HEIGHT),
            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        measure(frame, 600);
        frame.layout(0, 0, frame.getMeasuredWidth(), frame.getMeasuredHeight());
        return frame;
    }

    @Test
    public void theCardCollapsesWhileTheKeyboardInsideItIsGone() {
        FloatingKeyboardFrame frame = new FloatingKeyboardFrame(context);
        frame.setWidthScaleSource(() -> 1f);
        // Nothing hosted yet: a handle on its own is not a keyboard.
        measure(frame, 600);
        assertEquals(0, frame.getMeasuredHeight());

        FixedHeightView hosted = new FixedHeightView(context, KEYBOARD_HEIGHT);
        frame.contentHost().addView(hosted, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        measure(frame, 600);
        int handleHeight = frame.grabHandle().getMeasuredHeight();
        assertTrue("the handle has to have a height to be grabbed", handleHeight > 0);
        assertEquals(handleHeight + KEYBOARD_HEIGHT, frame.getMeasuredHeight());
        assertEquals(600, frame.getMeasuredWidth());

        // A closed keyboard takes its card with it rather than leaving a handle floating.
        hosted.setVisibility(View.GONE);
        measure(frame, 600);
        assertEquals(0, frame.getMeasuredHeight());

        // INVISIBLE is the reveal gate staging the first frame; the card keeps its size through it.
        hosted.setVisibility(View.INVISIBLE);
        measure(frame, 600);
        assertEquals(handleHeight + KEYBOARD_HEIGHT, frame.getMeasuredHeight());
    }

    @Test
    public void theHandleDragsTheCardAndHoldsItInsideTheContent() {
        FloatingKeyboardFrame frame = new FloatingKeyboardFrame(context);
        frame.setTravelPx(400, 1000);
        frame.setPositionPx(200, 1000);
        int[] last = new int[3];
        frame.setOnFrameMovedListener((x, y, committed) -> {
            last[0] = x;
            last[1] = y;
            last[2] = committed ? 1 : 0;
        });

        dispatch(frame, MotionEvent.ACTION_DOWN, 500, 900);
        dispatch(frame, MotionEvent.ACTION_MOVE, 460, 700);
        assertEquals(160f, frame.getTranslationX(), 0f);
        assertEquals(800f, frame.getTranslationY(), 0f);
        assertEquals(160, last[0]);
        assertEquals(800, last[1]);
        assertEquals("a frame in flight is not a place worth remembering", 0, last[2]);

        // Past the edge is held at the edge, in both directions.
        dispatch(frame, MotionEvent.ACTION_MOVE, 0, 0);
        assertEquals(0f, frame.getTranslationX(), 0f);
        assertEquals(100f, frame.getTranslationY(), 0f);
        dispatch(frame, MotionEvent.ACTION_MOVE, 5000, 5000);
        assertEquals(400f, frame.getTranslationX(), 0f);
        assertEquals(1000f, frame.getTranslationY(), 0f);

        dispatch(frame, MotionEvent.ACTION_UP, 700, 1100);
        assertEquals(400, frame.positionXPx());
        assertEquals(1000, frame.positionYPx());
        assertEquals("the finger leaving the handle is what commits the place", 1, last[2]);
    }

    // ---------------------------------------------------------------- the hosting

    @Test
    public void aFloatingTypeMovesTheKeyboardIntoTheCardAndBackAgain() {
        controller.onKeyboardVisibilityRequested(true);
        controller.onKeyboardFormResolved(KeyboardForm.FLOATING);

        FloatingKeyboardFrame frame = controller.frame();
        assertNotNull("floating has to build the card", frame);
        assertTrue(controller.isFloating());
        assertSame("the very same container is re-hosted, not a new one",
            frame.contentHost(), container.getParent());
        assertEquals(View.VISIBLE, host.getVisibility());
        assertEquals("the dock keeps its other rows", 1, dock.getChildCount());
        assertTrue(fakeHost.hostingChanges >= 1);

        controller.onKeyboardFormResolved(KeyboardForm.DOCKED);
        assertEquals(dock, container.getParent());
        assertEquals("back exactly where it came from", 1, dock.indexOfChild(container));
        assertEquals(View.GONE, host.getVisibility());
        assertEquals(0, frame.contentHost().getChildCount());
        assertTrue(container.getLayoutParams() instanceof RelativeLayout.LayoutParams);

        // And a docked keyboard is the stack's again: no reference, so it is measured and reserved
        // against the content root the way it always was.
        assertNull(controller.reference());
    }

    @Test
    public void aClosedKeyboardTakesItsCardOffTheScreen() {
        controller.onKeyboardVisibilityRequested(true);
        controller.onKeyboardFormResolved(KeyboardForm.FLOATING);
        assertEquals(View.VISIBLE, host.getVisibility());

        controller.onKeyboardVisibilityRequested(false);
        assertEquals(View.GONE, host.getVisibility());
        // Hidden, not un-hosted: re-opening must not have to rebuild anything.
        assertSame(controller.frame().contentHost(), container.getParent());

        controller.onKeyboardVisibilityRequested(true);
        assertEquals(View.VISIBLE, host.getVisibility());
    }

    @Test
    public void anUnmovedKeyboardStartsAlongTheBottomCentred() {
        floatAndLayout();

        FloatingKeyboardFrame frame = controller.frame();
        int widthPx = FloatingKeyboardFrame.frameWidthPx(context, HOST_WIDTH, 0.6f);
        assertEquals(600, widthPx);
        assertEquals(widthPx, frame.frameWidthPx(HOST_WIDTH));
        assertEquals(widthPx, frame.getWidth());
        assertEquals("centred sideways", (HOST_WIDTH - widthPx) / 2, frame.positionXPx());
        assertEquals("against the bottom", HOST_HEIGHT - frame.getHeight(), frame.positionYPx());
        // Nothing was written: an unmoved keyboard has no remembered place.
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardX(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), 0f);
    }

    @Test
    public void aDraggedPlaceIsRememberedForThatPlaceAndOrientationOnly() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();
        int travelY = HOST_HEIGHT - frame.getHeight();

        dispatch(frame, MotionEvent.ACTION_DOWN, 500, 1700);
        dispatch(frame, MotionEvent.ACTION_UP, 500, 1700 - travelY);

        assertEquals(0, frame.positionYPx());
        assertEquals(0f, store.floatingKeyboardY(PaneWallPage.TERMINAL,
            PlaceOrientation.LANDSCAPE), 1e-6f);
        assertEquals(0.5f, store.floatingKeyboardX(PaneWallPage.TERMINAL,
            PlaceOrientation.LANDSCAPE), 1e-6f);
        // The other orientation and the other places keep their own memory, which is none.
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardY(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0f);
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardY(PaneWallPage.WIDGETS, PlaceOrientation.LANDSCAPE), 0f);
        assertTrue("a moved frame needs its backdrop cropped again", fakeHost.frameMoves > 0);
    }

    @Test
    public void aRotationPutsTheFrameTheSameDistanceAlongTheNewRoom() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();

        // Parked a quarter of the way across and a quarter of the way down.
        store.setFloatingKeyboardPosition(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT,
            0.25f, 0.25f);
        fakeHost.landscape = false;
        fakeHost.widthScale = 0.9f;
        controller.onKeyboardFormResolved(KeyboardForm.FLOATING);
        layoutHost(600, 2000);
        layoutHost(600, 2000);

        int widthPx = FloatingKeyboardFrame.frameWidthPx(context, 600, 0.9f);
        assertEquals(540, widthPx);
        assertEquals("the width follows the orientation's own share", widthPx, frame.getWidth());
        assertEquals(Math.round(0.25f * (600 - widthPx)), frame.positionXPx());
        assertEquals(Math.round(0.25f * (2000 - frame.getHeight())), frame.positionYPx());
        assertEquals(widthPx, controller.reference().widthPx);
        assertEquals(2000, controller.reference().availableHeightPx);
    }

    // -------------------------------------------------------------------- the grip

    @Test
    public void theGripIsTheBottomLeftCornerAndNowhereElse() {
        // mdpi, so a dp is a pixel: a 600 x 418 card with a 36dp grip and an 18dp handle row.
        FloatingKeyboardFrame frame = newHostedFrame();
        int height = frame.getHeight();
        assertEquals(418, height);

        assertTrue(frame.isInGripZone(0, height));
        assertTrue(frame.isInGripZone(18, height - 18));
        assertTrue(frame.isInGripZone(36, height - 36));
        // A pixel outside it in either direction is the keyboard's again.
        assertFalse(frame.isInGripZone(37, height - 18));
        assertFalse(frame.isInGripZone(18, height - 37));
        // Not the other three corners, and not the handle row the pill lives in.
        assertFalse(frame.isInGripZone(frame.getWidth() - 4, height - 4));
        assertFalse(frame.isInGripZone(4, 4));
        assertFalse(frame.isInGripZone(frame.getWidth() - 4, 4));
        assertFalse(frame.isInGripZone(4, height / 2f));
    }

    @Test
    public void theGripGivesWayToTheHandleRowRatherThanGrowingIntoIt() {
        FloatingKeyboardFrame frame = new FloatingKeyboardFrame(context);
        frame.setWidthScaleSource(() -> 1f);
        frame.contentHost().addView(new FixedHeightView(context, 6),
            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        measure(frame, 600);
        frame.layout(0, 0, frame.getMeasuredWidth(), frame.getMeasuredHeight());
        // 18 of handle and 6 of keyboard: the 36dp zone would swallow the pill, so it stops at it.
        assertEquals(24, frame.getHeight());
        assertTrue(frame.isInGripZone(4, 20));
        assertFalse(frame.isInGripZone(4, 17));
        // And a card with nothing in it has no corner to grip.
        assertFalse(new FloatingKeyboardFrame(context).isInGripZone(0, 0));
    }

    @Test
    public void aDownInTheGripIsTakenFromTheKeysUnderIt() {
        FloatingKeyboardFrame frame = newHostedFrame();
        assertTrue(intercepts(frame, MotionEvent.ACTION_DOWN, 8, 410));
        assertFalse(intercepts(frame, MotionEvent.ACTION_DOWN, 300, 200));
        assertFalse(intercepts(frame, MotionEvent.ACTION_MOVE, 8, 410));
    }

    @Test
    public void aGripDragWidensTheCardTowardTheFingerAndWritesBothScalesOnRelease() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();
        assertEquals(600, frame.getWidth());
        assertEquals(200, frame.positionXPx());

        // 100px out to the left: 100px of new width on a 1000px host, so 0.6 becomes 0.7, and the
        // right edge stays at 800 because the left edge came out to 100.
        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, 410);
        dispatchToFrame(frame, MotionEvent.ACTION_MOVE, -92, 410);
        assertTrue("a card being dragged has to be the size it is dragged to",
            frame.isResizing());
        assertEquals(0.7f, fakeHost.previewedWidthScale, 1e-6f);
        assertEquals("nothing is written while the finger is down", 0, fakeHost.resizeCommits);
        assertTrue(fakeHost.resizePreviews > 0);
        assertEquals(100, frame.positionXPx());
        layoutHost();
        assertEquals(700, frame.getWidth());
        assertEquals("the right edge did not move", 800,
            frame.positionXPx() + frame.getWidth());

        dispatchToFrame(frame, MotionEvent.ACTION_UP, -92, 410);
        assertFalse(frame.isResizing());
        assertEquals(1, fakeHost.resizeCommits);
        assertEquals(0.7f, fakeHost.widthScale, 1e-6f);
        assertEquals("a level drag leaves the height alone", 1f, fakeHost.heightScale, 1e-6f);
        // The card kept the width it was dragged to once the store took over from the preview.
        layoutHost();
        assertEquals(700, frame.getWidth());
        assertEquals(100f / 300f, store.floatingKeyboardX(PaneWallPage.TERMINAL,
            PlaceOrientation.LANDSCAPE), 1e-6f);
    }

    @Test
    public void theWidthDragStopsAtTheHostWidthAndAtTheKeyboardFloor() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();

        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, 410);
        dispatchToFrame(frame, MotionEvent.ACTION_UP, -5000, 410);
        assertEquals(1f, fakeHost.widthScale, 1e-6f);
        layoutHost();
        assertEquals("never wider than the room it floats in", HOST_WIDTH, frame.getWidth());
        assertEquals(0, frame.positionXPx());

        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, 410);
        dispatchToFrame(frame, MotionEvent.ACTION_UP, 5000, 410);
        // 0.35 of the room is 350px, which is above the 240dp keyboard floor, so it is the stop.
        assertEquals(0.35f, fakeHost.widthScale, 1e-6f);
        layoutHost();
        assertEquals(350, frame.getWidth());
    }

    @Test
    public void draggingTheGripUpwardsMakesTheRowsTaller() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();

        // A quarter of the keyboard's 400px is a quarter more row height — the direction the
        // dock's own height pill uses, and the only one a card along the bottom has room for.
        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, 410);
        dispatchToFrame(frame, MotionEvent.ACTION_MOVE, 8, 310);
        assertEquals(1.25f, fakeHost.previewedHeightScale, 1e-6f);
        dispatchToFrame(frame, MotionEvent.ACTION_UP, 8, 310);
        assertEquals(1.25f, fakeHost.heightScale, 1e-6f);
        assertEquals("a straight-up drag leaves the width alone",
            0.6f, fakeHost.widthScale, 1e-6f);

        // Both ends of the height range are reachable and neither is passed.
        layoutHost();
        int gripY = frame.getHeight() - 8;
        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, gripY);
        dispatchToFrame(frame, MotionEvent.ACTION_UP, 8, -5000);
        assertEquals(1.6f, fakeHost.heightScale, 1e-6f);
        layoutHost();
        gripY = frame.getHeight() - 8;
        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, gripY);
        dispatchToFrame(frame, MotionEvent.ACTION_UP, 8, 5000);
        assertEquals(0.6f, fakeHost.heightScale, 1e-6f);
    }

    @Test
    public void aHeightDragLeavesTheCardsBottomEdgeExactlyWhereItWas() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();
        assertEquals("parked along the bottom", HOST_HEIGHT,
            frame.positionYPx() + frame.getHeight());
        assertEquals(418, frame.getHeight());

        // 100px up out of a 400px keyboard: a quarter taller, and the keyboard answers with the
        // height that asks for, the way the real one does a layout later.
        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, 410);
        dispatchToFrame(frame, MotionEvent.ACTION_MOVE, 8, 310);
        assertEquals(1.25f, fakeHost.previewedHeightScale, 1e-6f);
        layoutHost();
        assertEquals("the card grew upward", 518, frame.getHeight());
        assertEquals("the bottom edge did not move", HOST_HEIGHT,
            frame.positionYPx() + frame.getHeight());

        dispatchToFrame(frame, MotionEvent.ACTION_UP, 8, 310);
        layoutHost();
        assertEquals(518, frame.getHeight());
        assertEquals(HOST_HEIGHT, frame.positionYPx() + frame.getHeight());
        assertEquals("and the place it came to rest is the one remembered", 1f,
            store.floatingKeyboardY(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), 1e-6f);
    }

    @Test
    public void aCardAgainstTheTopOfTheRoomHasNowhereLeftToGrowUpward() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();

        // Parked against the top, where the bottom edge is the one that has to give.
        dispatch(frame, MotionEvent.ACTION_DOWN, 500, 1700);
        dispatch(frame, MotionEvent.ACTION_UP, 500, 0);
        assertEquals(0, frame.positionYPx());

        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, 410);
        dispatchToFrame(frame, MotionEvent.ACTION_MOVE, 8, 310);
        layoutHost();
        assertEquals(518, frame.getHeight());
        assertEquals("held at the top of the room", 0, frame.positionYPx());
    }

    @Test
    public void aCancelledDragCommitsTheSizeItReachedRatherThanHalfOfIt() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();

        dispatchToFrame(frame, MotionEvent.ACTION_DOWN, 8, 410);
        dispatchToFrame(frame, MotionEvent.ACTION_MOVE, -92, 410);
        assertEquals("nothing is written while the finger is down", 0, fakeHost.resizeCommits);

        // The system taking the gesture away mid-drag is a release, not an undo.
        dispatchToFrame(frame, MotionEvent.ACTION_CANCEL, -92, 410);
        assertFalse(frame.isResizing());
        assertEquals(1, fakeHost.resizeCommits);
        assertEquals(0.7f, fakeHost.widthScale, 1e-6f);
        layoutHost();
        assertEquals(700, frame.getWidth());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void theGripIsExcludedFromTheSystemGesturesThatWouldStealItsDrag() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();

        Rect grip = frame.gripRect();
        assertEquals(new Rect(0, frame.getHeight() - 36, 36, frame.getHeight()), grip);
        assertEquals("the grip, and nothing else of the card",
            Collections.singletonList(grip), frame.getSystemGestureExclusionRects());

        // The platform maps the rect through the card's translation when it is handed the rect,
        // so a move has to hand it over again.
        dispatch(frame, MotionEvent.ACTION_DOWN, 500, 1700);
        dispatch(frame, MotionEvent.ACTION_UP, 400, 1500);
        assertEquals(100, frame.positionXPx());
        assertEquals(Collections.singletonList(grip), frame.getSystemGestureExclusionRects());
        assertEquals(grip, frame.gripRect());
    }

    @Test
    public void draggingThePillMovesTheCardAndResizesNothing() {
        floatAndLayout();
        FloatingKeyboardFrame frame = controller.frame();

        dispatch(frame, MotionEvent.ACTION_DOWN, 500, 1700);
        dispatch(frame, MotionEvent.ACTION_MOVE, 400, 1500);
        dispatch(frame, MotionEvent.ACTION_UP, 400, 1500);

        assertEquals("the handle drag is untouched", 100, frame.positionXPx());
        assertEquals(0, fakeHost.resizePreviews);
        assertEquals(0, fakeHost.resizeCommits);
        assertEquals(0.6f, fakeHost.widthScale, 1e-6f);
        assertEquals(1f, fakeHost.heightScale, 1e-6f);
        assertEquals(600, frame.getWidth());
    }

    // ------------------------------------------------------------------- fixtures

    private void floatAndLayout() {
        controller.onKeyboardVisibilityRequested(true);
        controller.onKeyboardFormResolved(KeyboardForm.FLOATING);
        // Two passes: the first gives the frame its width, the second its height, which is what the
        // vertical travel is measured from.
        layoutHost();
        layoutHost();
    }

    private void layoutHost() {
        layoutHost(HOST_WIDTH, HOST_HEIGHT);
    }

    private void layoutHost(int width, int height) {
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, width, height);
    }

    private static void measure(@NonNull View view, int widthPx) {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
    }

    private static void dispatch(@NonNull FloatingKeyboardFrame frame, int action,
                                 float rawX, float rawY) {
        MotionEvent event = motionEvent(action, rawX, rawY);
        frame.grabHandle().dispatchTouchEvent(event);
        event.recycle();
    }

    /** Straight at the card, which is where a touch on the grip lands. */
    private static void dispatchToFrame(@NonNull FloatingKeyboardFrame frame, int action,
                                        float x, float y) {
        MotionEvent event = motionEvent(action, x, y);
        frame.dispatchTouchEvent(event);
        event.recycle();
    }

    private static boolean intercepts(@NonNull FloatingKeyboardFrame frame, int action,
                                      float x, float y) {
        MotionEvent event = motionEvent(action, x, y);
        boolean intercepted = frame.onInterceptTouchEvent(event);
        event.recycle();
        return intercepted;
    }

    @NonNull
    private static MotionEvent motionEvent(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, x, y, 0);
    }

    /** A stand-in for the keyboard's container: a height the test owns. */
    private static final class FixedHeightView extends View {

        private int mHeightPx;

        FixedHeightView(@NonNull Context context, int heightPx) {
            super(context);
            mHeightPx = heightPx;
        }

        void setHeightPx(int heightPx) {
            if (mHeightPx == heightPx) return;
            mHeightPx = heightPx;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), mHeightPx);
        }
    }

    private final class FakeHost implements FloatingKeyboardController.Host {

        boolean landscape = true;
        float widthScale = 0.6f;
        float heightScale = 1f;
        int hostingChanges;
        int frameMoves;
        int resizePreviews;
        int resizeCommits;
        float previewedHeightScale = Float.NaN;
        float previewedWidthScale = Float.NaN;

        @Nullable @Override public View floatingHost() {
            return host;
        }

        @Nullable @Override public View keyboardContainer() {
            return container;
        }

        @Override public float floatingKeyboardWidthScale() {
            return widthScale;
        }

        @Override public float floatingKeyboardHeightScale() {
            return heightScale;
        }

        @Nullable @Override public PlaceLayoutStore placeLayoutStore() {
            return store;
        }

        @NonNull @Override public PaneWallPage place() {
            return PaneWallPage.TERMINAL;
        }

        @NonNull @Override public PlaceOrientation orientation() {
            return landscape ? PlaceOrientation.LANDSCAPE : PlaceOrientation.PORTRAIT;
        }

        @Override public void onFloatingHostingChanged() {
            hostingChanges++;
        }

        @Override public void onFloatingFrameMoved(boolean committed) {
            frameMoves++;
        }

        /** Stands in for the two preferences: a committed drag is what the store would keep. */
        @Override public void onFloatingFrameResized(float newWidthScale, float newHeightScale,
                                                     boolean committed) {
            previewedWidthScale = newWidthScale;
            previewedHeightScale = newHeightScale;
            // The real keyboard is told the new row height and comes back taller on the next
            // layout; the stand-in does the same, which is what the bottom-edge pin answers to.
            container.setHeightPx(Math.round(KEYBOARD_HEIGHT * newHeightScale));
            if (committed) {
                resizeCommits++;
                widthScale = newWidthScale;
                heightScale = newHeightScale;
            } else {
                resizePreviews++;
            }
        }
    }
}
