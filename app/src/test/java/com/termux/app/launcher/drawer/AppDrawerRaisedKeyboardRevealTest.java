package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * "Open keyboard automatically" with the built-in keyboard: the committed open asks for the
 * keyboard, and the keyboard the host raises is brought up through the plane even when its own
 * open reveal keeps it {@link View#INVISIBLE} for a frame after it has laid out (the glass path).
 * Before the fix the bands were measured on that layout, found no visible keyboard, and the
 * reveal stayed at zero with the keyboard under the plane.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerRaisedKeyboardRevealTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private static final int KEYBOARD_TOP = 980;

    private TermuxAppSharedPreferences preferences;
    private AppDrawerController controller;
    private FrameLayout keyboard;
    /** What the host's raise leaves the container at; INVISIBLE is the deferred glass reveal. */
    private int raisedVisibility;
    private int raises;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).get();
        SharedPreferences raw = activity.getSharedPreferences("drawer-raised-keyboard",
            Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(activity, raw, null);

        FrameLayout root = new FrameLayout(activity);
        keyboard = new FrameLayout(activity);
        keyboard.setId(R.id.inapp_keyboard_container);
        keyboard.setVisibility(View.GONE);
        root.addView(keyboard, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            HEIGHT - KEYBOARD_TOP, Gravity.BOTTOM));
        layOut(root);

        FakeAppDrawerHost host = new FakeAppDrawerHost(activity, preferences);
        host.viewRoot = root;
        controller = new AppDrawerController(host);
        AppDrawerContentView content = new AppDrawerContentView(activity);
        content.setRevealListener(() -> { });
        // The activity's requestAppDrawerSearchKeyboard, reduced to what the drawer sees: the
        // keyboard comes up (laid out, at the visibility its reveal allows) and the drawer is told.
        content.setSearchKeyboardRequestListener(() -> {
            raises++;
            keyboard.setVisibility(raisedVisibility);
            // A whole traversal, so no layout is left pending anywhere above the keyboard either.
            layOut(root);
            controller.onBuiltInKeyboardRaised();
        });
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpenRect", new Frame(0f, 0f, WIDTH, HEIGHT));
        ReflectionHelpers.setField(controller, "mDockRect", new Frame(0f, 900f, WIDTH, 960f));
        ReflectionHelpers.setField(controller, "mHostLayout", root);
        // Engaged since the pull, as beginDrag leaves it; the keyboard was down then.
        ReflectionHelpers.setField(controller, "mEngaged", true);
    }

    @Test public void offTheOpenRaisesNothing() {
        raisedVisibility = View.VISIBLE;

        settleOpen();

        assertEquals(0, raises);
        assertFalse(hasRevealableKeyboard());
    }

    @Test public void aKeyboardShownAtOnceIsRevealedAtOnce() {
        preferences.setAppLauncherDrawerSearchOnOpenEnabled(true);
        raisedVisibility = View.VISIBLE;

        settleOpen();

        assertEquals(1, raises);
        assertTrue(hasRevealableKeyboard());
        assertEquals(1f, revealTarget(), 0f);
    }

    @Test public void aKeyboardHeldInvisibleByItsRevealIsRevealedOnceItShows() {
        preferences.setAppLauncherDrawerSearchOnOpenEnabled(true);
        raisedVisibility = View.INVISIBLE;

        settleOpen();

        assertEquals(1, raises);
        // Laid out but not yet shown: nothing measured, nothing revealed yet.
        assertFalse(hasRevealableKeyboard());
        assertEquals(0f, revealTarget(), 0f);

        // The keyboard's own reveal flips it to visible; no bounds change, only a redraw.
        keyboard.setVisibility(View.VISIBLE);
        keyboard.getViewTreeObserver().dispatchOnPreDraw();

        assertTrue(hasRevealableKeyboard());
        assertEquals(1f, revealTarget(), 0f);
        assertNull(ReflectionHelpers.getField(controller, "mRaisedKeyboardWatch"));
    }

    @Test public void theWaitEndsWithTheDrawer() {
        preferences.setAppLauncherDrawerSearchOnOpenEnabled(true);
        raisedVisibility = View.INVISIBLE;
        settleOpen();

        ReflectionHelpers.setField(controller, "mEngaged", false);
        keyboard.setVisibility(View.VISIBLE);
        keyboard.getViewTreeObserver().dispatchOnPreDraw();

        assertFalse(hasRevealableKeyboard());
        assertNull(ReflectionHelpers.getField(controller, "mRaisedKeyboardWatch"));
    }

    @Test public void onlyAShownLaidOutKeyboardIsMeasured() {
        assertTrue(AppDrawerController.isRaisedKeyboardMeasurable(View.VISIBLE, 300, false));
        assertFalse(AppDrawerController.isRaisedKeyboardMeasurable(View.INVISIBLE, 300, false));
        assertFalse(AppDrawerController.isRaisedKeyboardMeasurable(View.GONE, 300, false));
        assertFalse(AppDrawerController.isRaisedKeyboardMeasurable(View.VISIBLE, 0, false));
        assertFalse(AppDrawerController.isRaisedKeyboardMeasurable(View.VISIBLE, 300, true));
    }

    private static void layOut(@NonNull View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH, HEIGHT);
    }

    private void settleOpen() {
        ReflectionHelpers.callInstanceMethod(controller, "settle",
            ReflectionHelpers.ClassParameter.from(boolean.class, true),
            ReflectionHelpers.ClassParameter.from(float.class, 0f));
    }

    private boolean hasRevealableKeyboard() {
        return ReflectionHelpers.callInstanceMethod(controller, "hasRevealableKeyboard");
    }

    private float revealTarget() {
        Object reveal = ReflectionHelpers.getField(controller, "mReveal");
        return ReflectionHelpers.getField(reveal, "target");
    }
}
