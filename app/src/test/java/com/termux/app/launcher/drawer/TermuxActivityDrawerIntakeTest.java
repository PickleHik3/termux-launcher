package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The activity's half of the drawer's three intake channels.
 *
 * <p>The drawer has no focused text field — deliberately, because an {@code EditText} would take the
 * input connection off {@code TerminalView} and the in-app keyboard's own text-input path runs
 * {@code requestAccessoryGeometrySync()}, which is the exact geometry this transition freezes. So
 * typing reaches it only through these hooks, and the failure they guard against is the mirror image
 * of the feature: a hook that claims while the drawer is <em>closed</em> eats keystrokes that belong
 * to the shell, silently, on every session that ever opened the drawer once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityDrawerIntakeTest {

    @Test
    public void aClosedDrawerClaimsNothingAndNothingLeaksIntoTheShell() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();

        // Before anything built one at all: the hooks are on the hot path of every keystroke, so
        // they are guarded on the field and must not build a controller just to answer no.
        assertFalse(activity.handleAppDrawerCodePoint('a', false));
        assertFalse(activity.handleAppDrawerKey(KeyEvent.KEYCODE_A, keyDown(KeyEvent.KEYCODE_A)));
        assertFalse(activity.isAppDrawerOpen());
        assertNull(ReflectionHelpers.getField(activity, "mAppDrawerController"));

        // And with one built but shut.
        activity.getAppDrawerController();
        assertFalse(activity.handleAppDrawerCodePoint('a', false));
        assertFalse(activity.handleAppDrawerKey(KeyEvent.KEYCODE_A, keyDown(KeyEvent.KEYCODE_A)));
    }

    @Test
    public void anOpenDrawerClaimsAPrintableCodePointAndTypesIt() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        AppDrawerController controller = openDrawer(activity);

        assertTrue(activity.handleAppDrawerCodePoint('m', false));
        assertTrue(activity.handleAppDrawerCodePoint('a', false));
        assertTrue(activity.handleAppDrawerCodePoint('p', false));

        assertEquals("map", controller.getSearchController().query());
        assertTrue(activity.isAppDrawerOpen());
    }

    @Test
    public void anOpenDrawerClaimsAHardwareStrokeToo() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        AppDrawerController controller = openDrawer(activity);

        assertTrue(activity.handleAppDrawerKey(KeyEvent.KEYCODE_A, keyDown(KeyEvent.KEYCODE_A)));

        assertEquals("a", controller.getSearchController().query());
    }

    @Test
    public void closingReleasesTheInterceptorSlotAndTheDrawerStopsClaiming() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        AppDrawerController controller = openDrawer(activity);
        assertTrue(activity.handleAppDrawerCodePoint('a', false));

        controller.closeImmediate();

        // The slot is single-occupancy and shared with the command palette. Even before the palette
        // reclaims it, the interceptor the drawer installed answers to mOpen and nothing else, so a
        // closed drawer swallows nothing on any of the three channels.
        assertFalse(controller.isSearchActive());
        assertFalse(activity.handleAppDrawerCodePoint('a', false));
        assertFalse(activity.handleAppDrawerKey(KeyEvent.KEYCODE_A, keyDown(KeyEvent.KEYCODE_A)));
        assertFalse(activity.isAppDrawerOpen());
        // Closing also empties what was typed, so the next open starts from a fresh query.
        assertFalse(controller.getSearchController().hasQuery());
    }

    @Test
    public void deactivatingTheInterceptorNeverBuildsAController() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();

        activity.setAppDrawerInterceptorActive(false);
        activity.setAppDrawerInterceptorActive(true);

        assertNull(ReflectionHelpers.getField(activity, "mAppDrawerController"));
    }

    private static KeyEvent keyDown(int keyCode) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    }

    /** An open drawer with no views bound — the intake reads state, not geometry. */
    private static AppDrawerController openDrawer(TermuxActivity activity) {
        AppDrawerController controller = activity.getAppDrawerController();
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(controller, "mEngaged", true);
        return controller;
    }
}
