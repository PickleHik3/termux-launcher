package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The display view may hold the keys only while its window is at rest. Once the wall leaves it, or
 * before it goes, the keys go back to the terminal (where the palette and every focusless overlay
 * read them) and the display can no longer take them back — the window regaining focus after the
 * Stop confirmation used to hand them straight back to a display nobody could see.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DisplayViewFocusTest {

    private View mTerminal;
    private View mDisplay;
    private EditText mStrayField;
    private int mHandBacks;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout root = new LinearLayout(activity);
        // Android's own choice for a focus that has to land somewhere: first in the tree.
        mStrayField = new EditText(activity);
        mTerminal = focusable(new View(activity));
        mDisplay = focusable(new View(activity));
        root.addView(mStrayField, 10, 10);
        root.addView(mTerminal, 10, 10);
        root.addView(mDisplay, 10, 10);
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 100, 100);
    }

    private static View focusable(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        return view;
    }

    private void release() {
        DisplayViewFocus.release(mDisplay, focused -> focused == mTerminal, () -> {
            mHandBacks++;
            mTerminal.requestFocus();
        });
    }

    @Test
    public void leavingTheDisplayHandsTheKeysToTheTerminal() {
        assertTrue(mDisplay.requestFocus());
        release();
        assertTrue(mTerminal.isFocused());
        assertFalse(mDisplay.hasFocus());
    }

    @Test
    public void aReleasedDisplayCannotTakeTheKeysBack() {
        assertTrue(mDisplay.requestFocus());
        release();
        // What the display view does on its own whenever the window regains focus.
        assertFalse(mDisplay.requestFocus());
        assertTrue(mTerminal.isFocused());
    }

    @Test
    public void keysAndroidLeftOnAStrayViewAreTakenBack() {
        // The page went invisible or was removed while the display held the keys.
        assertTrue(mStrayField.requestFocus());
        release();
        assertTrue(mTerminal.isFocused());
    }

    @Test
    public void aTerminalThatHoldsTheKeysIsLeftAlone() {
        assertTrue(mTerminal.requestFocus());
        release();
        assertSame(0, mHandBacks);
        assertTrue(mTerminal.isFocused());
    }

    @Test
    public void arrivingAdmitsTheDisplayAgain() {
        release();
        DisplayViewFocus.admit(mDisplay);
        assertTrue(mDisplay.requestFocus());
    }

    @Test
    public void aHandBackThatDidNotLandIsMadeAgainOnceTheDisplayLetsGo() {
        // The terminal could not take the keys while the display still held them (its page was
        // changing under it): the first hand-back comes to nothing.
        assertTrue(mDisplay.requestFocus());
        int[] calls = {0};
        DisplayViewFocus.release(mDisplay, focused -> focused == mTerminal, () -> {
            if (++calls[0] > 1) mTerminal.requestFocus();
        });
        assertTrue(mTerminal.isFocused());
        assertTrue(DisplayViewFocus.holdsKeys(mTerminal));
    }

    @Test
    public void aHandBackSaysWhetherItLanded() {
        assertTrue(DisplayViewFocus.handBackTo(mTerminal, mTerminal::requestFocus));
        // Out of the window a view can call itself focused; the keys never reach it.
        View detached = focusable(new View(mTerminal.getContext()));
        assertFalse(DisplayViewFocus.handBackTo(detached, detached::requestFocus));
        assertFalse(DisplayViewFocus.handBackTo(null, () -> { }));
    }

    @Test
    public void aHandBackThatDidNotLandIsRetriedAfterTheNextLayouts() {
        int[] tries = {0};
        boolean[] gaveUp = {false};
        DisplayViewFocus.retryAfterLayout(mTerminal, 3, () -> ++tries[0] == 2,
            () -> gaveUp[0] = true);
        assertEquals(0, tries[0]);
        layout();
        layout();
        layout();
        assertEquals(2, tries[0]);
        assertFalse(gaveUp[0]);
    }

    @Test
    public void retriesStopAfterTheirLast() {
        int[] tries = {0};
        boolean[] gaveUp = {false};
        DisplayViewFocus.retryAfterLayout(mTerminal, 2, () -> {
            tries[0]++;
            return false;
        }, () -> gaveUp[0] = true);
        layout();
        layout();
        layout();
        assertEquals(2, tries[0]);
        assertTrue(gaveUp[0]);
    }

    private void layout() {
        mTerminal.getViewTreeObserver().dispatchOnGlobalLayout();
    }

    @Test
    public void aKeyNobodyHoldsGoesToTheTerminal() {
        assertSame(DisplayViewFocus.Route.TERMINAL,
            DisplayViewFocus.routeFor(DisplayViewFocus.Holder.NONE, KeyEvent.KEYCODE_G, true));
        assertSame(DisplayViewFocus.Route.TERMINAL,
            DisplayViewFocus.routeFor(DisplayViewFocus.Holder.NONE, KeyEvent.KEYCODE_ENTER, true));
    }

    @Test
    public void aKeyAGoneDisplayHoldsGoesToTheTerminal() {
        assertSame(DisplayViewFocus.Route.TERMINAL, DisplayViewFocus.routeFor(
            DisplayViewFocus.Holder.DISPLAY_AWAY, KeyEvent.KEYCODE_G, true));
    }

    @Test
    public void withNoTerminalToTypeIntoTheOverlaysTakeTheKey() {
        assertSame(DisplayViewFocus.Route.OVERLAYS,
            DisplayViewFocus.routeFor(DisplayViewFocus.Holder.NONE, KeyEvent.KEYCODE_G, false));
        assertSame(DisplayViewFocus.Route.OVERLAYS, DisplayViewFocus.routeFor(
            DisplayViewFocus.Holder.DISPLAY_AWAY, KeyEvent.KEYCODE_G, false));
    }

    @Test
    public void keysAHolderTakesKeepTheirPath() {
        assertSame(DisplayViewFocus.Route.PASS,
            DisplayViewFocus.routeFor(DisplayViewFocus.Holder.OTHER, KeyEvent.KEYCODE_G, true));
        assertSame(DisplayViewFocus.Route.PASS, DisplayViewFocus.routeFor(
            DisplayViewFocus.Holder.DISPLAY_SHOWING, KeyEvent.KEYCODE_G, true));
    }

    @Test
    public void backKeepsItsOwnRouteEvenWithNobodyHoldingTheKeys() {
        assertSame(DisplayViewFocus.Route.PASS,
            DisplayViewFocus.routeFor(DisplayViewFocus.Holder.NONE, KeyEvent.KEYCODE_BACK, true));
        assertSame(DisplayViewFocus.Route.PASS, DisplayViewFocus.routeFor(
            DisplayViewFocus.Holder.DISPLAY_AWAY, KeyEvent.KEYCODE_BACK, false));
    }
}
