package com.termux.app.terminal.inappkeyboard;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The floating keyboard as it is hosted: the card collapses with the keyboard inside it, the handle
 * drags it and holds it inside the content, and the controller moves the keyboard's own container
 * between the accessory stack and the frame without rebuilding anything.
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
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, rawX, rawY, 0);
        frame.grabHandle().dispatchTouchEvent(event);
        event.recycle();
    }

    /** A stand-in for the keyboard's container: a height the test owns. */
    private static final class FixedHeightView extends View {

        private final int mHeightPx;

        FixedHeightView(@NonNull Context context, int heightPx) {
            super(context);
            mHeightPx = heightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), mHeightPx);
        }
    }

    private final class FakeHost implements FloatingKeyboardController.Host {

        boolean landscape = true;
        float widthScale = 0.6f;
        int hostingChanges;
        int frameMoves;

        @Nullable @Override public View floatingHost() {
            return host;
        }

        @Nullable @Override public View keyboardContainer() {
            return container;
        }

        @Override public float floatingKeyboardWidthScale() {
            return widthScale;
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
    }
}
