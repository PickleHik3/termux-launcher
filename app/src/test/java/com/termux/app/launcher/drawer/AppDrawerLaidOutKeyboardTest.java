package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/**
 * The in-app keyboard stands laid out while it is down: visible, with a height, below the
 * screen and cut by the stack's one writer ({@code StackRenderer}). A drag that begins over it
 * found a keyboard band to choreograph and wrote its translation, alpha and clip, and the clip it
 * cleared (the writer's, which hides the laid-out keyboard) was a foreign write on the stack.
 * A keyboard that is down has no band: the drawer leaves it alone. A keyboard that is up is
 * still the drawer's to move.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w360dp-h640dp-mdpi")
public class AppDrawerLaidOutKeyboardTest {

    private static final int WIDTH = 360;
    private static final int HEIGHT = 640;
    private static final int KEYS = 60;
    private static final int KEYBOARD = 240;

    private FakeAppDrawerHost host;
    private AppDrawerController controller;
    private View keyboard;
    /** What the stack's writer leaves a down keyboard with: clipped away whole. */
    private final Rect writerClip = new Rect(-WIDTH, -HEIGHT, 2 * WIDTH, 0);

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        View keys = new View(activity);
        keys.setId(R.id.terminal_toolbar_view_pager);
        root.addView(keys, new FrameLayout.LayoutParams(WIDTH, KEYS, android.view.Gravity.BOTTOM));
        keyboard = new View(activity);
        keyboard.setId(R.id.inapp_keyboard_container);
        root.addView(keyboard, new FrameLayout.LayoutParams(WIDTH, KEYBOARD,
            android.view.Gravity.BOTTOM));
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH, HEIGHT);
        assertTrue(keyboard.getHeight() > 0);

        host = new FakeAppDrawerHost(activity, null);
        host.viewRoot = root;
        controller = new AppDrawerController(host);
        ReflectionHelpers.setField(controller, "mHostLayout", root);
    }

    @Test public void aKeyboardThatIsDownKeepsItsTranslationAlphaAndClip() {
        host.inAppKeyboardShown = false;
        keyboard.setClipBounds(new Rect(writerClip));
        keyboard.setTranslationY(7f);

        capture();
        applyBands(0.5f);
        applyBands(1f);

        Rect clip = new Rect();
        assertTrue("the writer's clip stands", keyboard.getClipBounds(clip));
        assertEquals(writerClip, clip);
        assertEquals(7f, keyboard.getTranslationY(), 0f);
        assertEquals(1f, keyboard.getAlpha(), 0f);
        assertFalse("no band was captured for it", hasKeyboardBand());
    }

    @Test public void closingLeavesAKeyboardThatIsDownAlone() {
        host.inAppKeyboardShown = false;
        keyboard.setClipBounds(new Rect(writerClip));
        capture();

        ReflectionHelpers.callInstanceMethod(controller, "applyBand",
            ReflectionHelpers.ClassParameter.from(View.class,
                ReflectionHelpers.getField(controller, "mKeyboardView")),
            ReflectionHelpers.ClassParameter.from(float.class, 0f),
            ReflectionHelpers.ClassParameter.from(float.class, 0f),
            ReflectionHelpers.ClassParameter.from(float.class, 1f));

        Rect clip = new Rect();
        assertTrue(keyboard.getClipBounds(clip));
        assertEquals(writerClip, clip);
    }

    @Test public void aKeyboardThatIsUpIsStillTheDrawersToMove() {
        host.inAppKeyboardShown = true;

        capture();
        applyBands(1f);

        assertTrue("a band was captured for it", hasKeyboardBand());
        assertTrue("the drawer moved it", keyboard.getTranslationY() != 0f
            || keyboard.getAlpha() != 1f);
    }

    private void capture() {
        ReflectionHelpers.callInstanceMethod(controller, "captureBands",
            ReflectionHelpers.ClassParameter.from(Frame.class,
                new Frame(0f, HEIGHT - KEYS - KEYBOARD - 40f, WIDTH, HEIGHT - KEYS - KEYBOARD)));
    }

    private void applyBands(float p) {
        ReflectionHelpers.callInstanceMethod(controller, "applyAccessoryBands",
            ReflectionHelpers.ClassParameter.from(float.class, p),
            ReflectionHelpers.ClassParameter.from(float.class, (float) HEIGHT),
            ReflectionHelpers.ClassParameter.from(float.class, 0f),
            ReflectionHelpers.ClassParameter.from(float.class, 0f),
            ReflectionHelpers.ClassParameter.from(float.class, 0f),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
    }

    private boolean hasKeyboardBand() {
        Object band = ReflectionHelpers.getField(controller, "mKeyboardBand");
        return band != null && (float) ReflectionHelpers.getField(band, "heightPx") > 0f;
    }
}
