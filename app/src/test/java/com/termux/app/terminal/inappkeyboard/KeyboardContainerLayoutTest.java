package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A keyboard that is laid out but down takes no touch: the part of it a translation leaves
 * on the screen's bottom edge, under the gap the stack stands above, would type into the terminal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class KeyboardContainerLayoutTest {

    private static MotionEvent down(float x, float y) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
    }

    @Test public void aRefusingContainerTakesNoTouchAndTheViewUnderItDoes() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout parent = new FrameLayout(context);
        View under = new View(context);
        final int[] hits = {0};
        under.setOnTouchListener((v, e) -> { hits[0]++; return true; });
        KeyboardContainerLayout keyboard = new KeyboardContainerLayout(context);
        final int[] keys = {0};
        keyboard.setOnTouchListener((v, e) -> { keys[0]++; return true; });
        parent.addView(under, new FrameLayout.LayoutParams(100, 100));
        parent.addView(keyboard, new FrameLayout.LayoutParams(100, 100));
        parent.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 100, 100);

        assertTrue(parent.dispatchTouchEvent(down(50, 50)));
        assertEquals(1, keys[0]);
        assertEquals(0, hits[0]);

        keyboard.setTouchRefused(true);
        assertTrue(parent.dispatchTouchEvent(down(50, 50)));
        assertEquals("the keyboard heard nothing", 1, keys[0]);
        assertEquals("the touch fell to what is under it", 1, hits[0]);
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            keyboard.getImportantForAccessibility());

        keyboard.setTouchRefused(false);
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, keyboard.getImportantForAccessibility());
        assertFalse(keyboard.isTouchRefused());
    }

    @Test public void aCoveringContainerKeepsTheTouchInItsGapFromTheDockUnderIt() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout parent = new FrameLayout(context);
        View under = new View(context);
        final int[] hits = {0};
        // A cancel of the last touch also reaches it; only a down counts.
        under.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) hits[0]++;
            return true;
        });
        KeyboardContainerLayout keyboard = new KeyboardContainerLayout(context);
        parent.addView(under, new FrameLayout.LayoutParams(100, 100));
        parent.addView(keyboard, new FrameLayout.LayoutParams(100, 100));
        parent.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 100, 100);

        // No key takes the touch: it falls to the dock under, as it always did.
        assertTrue(parent.dispatchTouchEvent(down(50, 50)));
        assertEquals(1, hits[0]);

        // Covering Home's dock (#309): the gap swallows it.
        keyboard.setCoversBelow(true);
        assertTrue(parent.dispatchTouchEvent(down(50, 50)));
        assertEquals("the dock under heard nothing", 1, hits[0]);

        // A refused (down) keyboard still lets the touch through.
        keyboard.setTouchRefused(true);
        assertTrue(parent.dispatchTouchEvent(down(50, 50)));
        assertEquals(2, hits[0]);
    }
}
