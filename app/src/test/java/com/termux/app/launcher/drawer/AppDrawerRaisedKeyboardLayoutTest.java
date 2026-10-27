package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The built-in keyboard the drawer's search raises is laid out whole. The accessory stack is
 * frozen at the pull with the keyboard down; a keyboard raised inside that stack was measured
 * against its height and lost its bottom rows (three rows of four, a wallpaper strip under them).
 * The drawer now asks the host for one stack pass at the raise, and measures the bands, the
 * list's room and the dock it will close onto from the layout that pass books.
 *
 * <p>The tree is the activity's stack in miniature, attached to a real window so the traversal
 * and its pre-draw are the platform's: a bottom-aligned stack of fixed height over the
 * navigation inset, the dock above the keyboard column, the keyboard at the column's top.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w360dp-h640dp-mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppDrawerRaisedKeyboardLayoutTest {

    private static final int NAV_INSET = 24;
    private static final int DOCK = 60;
    private static final int KEYBOARD = 240;
    /** What the activity's stack pass gives the stack with the keyboard down: just the dock. */
    private static final int STACK_KEYBOARD_DOWN = DOCK;

    /** Read off the attached root: the window the qualifiers above give the activity. */
    private int width;
    private int height;
    private FakeAppDrawerHost host;
    private AppDrawerController controller;
    private FrameLayout root;
    private RelativeLayout stack;
    private View dock;
    private LinearLayout keyboard;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SharedPreferences raw = activity.getSharedPreferences("drawer-raised-keyboard-layout",
            Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(activity, raw,
            null);
        preferences.setAppLauncherDrawerSearchOnOpenEnabled(true);

        root = new FrameLayout(activity);
        RelativeLayout window = new RelativeLayout(activity);
        root.addView(window, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        stack = new RelativeLayout(activity);
        stack.setId(R.id.accessory_stack_container);
        stack.setClipChildren(false);
        RelativeLayout.LayoutParams stackParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, STACK_KEYBOARD_DOWN);
        stackParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        stackParams.bottomMargin = NAV_INSET;
        window.addView(stack, stackParams);

        LinearLayout column = new LinearLayout(activity);
        column.setId(R.id.accessory_keyboard_column);
        column.setOrientation(LinearLayout.VERTICAL);
        RelativeLayout.LayoutParams columnParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        stack.addView(column, columnParams);

        keyboard = new LinearLayout(activity);
        keyboard.setId(R.id.inapp_keyboard_container);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setVisibility(View.GONE);
        // The keys: four rows, as tall as the keyboard wants to be.
        keyboard.addView(new View(activity), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, KEYBOARD));
        column.addView(keyboard, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dock = new View(activity);
        dock.setId(R.id.accessory_surface_host);
        RelativeLayout.LayoutParams dockParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dockParams.addRule(RelativeLayout.ABOVE, R.id.accessory_keyboard_column);
        stack.addView(dock, dockParams);

        activity.setContentView(root);
        frame();
        width = root.getWidth();
        height = root.getHeight();
        assertTrue("the window holds the stack: " + height,
            height > NAV_INSET + DOCK + KEYBOARD);

        host = new FakeAppDrawerHost(activity, preferences);
        host.viewRoot = root;
        controller = new AppDrawerController(host);
        AppDrawerContentView content = new AppDrawerContentView(activity);
        content.setRevealListener(() -> { });
        // The activity's requestAppDrawerSearchKeyboard, reduced to what the drawer sees: the
        // keyboard is raised into the frozen stack and the drawer is told.
        content.setSearchKeyboardRequestListener(() -> {
            keyboard.setVisibility(View.VISIBLE);
            controller.onBuiltInKeyboardRaised();
        });
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpenRect", new Frame(0f, 0f, width, height));
        ReflectionHelpers.setField(controller, "mDockRect", frameOf(dock));
        ReflectionHelpers.setField(controller, "mAccessorySurface", dock);
        ReflectionHelpers.setField(controller, "mHostLayout", root);
        // Engaged since the pull, as beginDrag leaves it; the keyboard was down then.
        ReflectionHelpers.setField(controller, "mEngaged", true);
    }

    @Test public void theRaisedKeyboardIsLaidOutWhole() {
        host.stackLayoutForRaisedKeyboard = this::stackPassForShownKeyboard;

        settleOpenAndDraw();

        assertEquals(1, host.stackLayoutsForRaisedKeyboard);
        assertEquals("bottom on the keyboard's normal bottom, above the navigation inset",
            height - NAV_INSET, bottomOf(keyboard));
        assertEquals("every row", KEYBOARD, keyboard.getHeight());
        assertTrue(hasRevealableKeyboard());
        assertEquals(1f, revealTarget(), 0f);
        Frame list = contentRect();
        assertNotNull(list);
        assertTrue("the list ends above the keyboard: " + list.bottom,
            height - list.bottom >= keyboard.getHeight());
        assertEquals("the list ends where the keyboard's band starts, less the gap",
            topOf(keyboard) - capturedGapPx(), list.bottom, 0.5f);
    }

    @Test public void aFrozenStackCutsTheKeyboardOff() {
        // The defect, kept as the control: with the stack left at its keyboard-down height the
        // keyboard gets that height and no more.
        host.stackLayoutForRaisedKeyboard = null;

        settleOpenAndDraw();

        assertTrue(keyboard.getHeight() < KEYBOARD);
    }

    @Test public void theCloseLandsOnTheDockAboveTheKeyboardOnceFullyOpen() {
        host.stackLayoutForRaisedKeyboard = this::stackPassForShownKeyboard;
        Frame pullDock = frameOf(dock);
        setProgress(1f);

        settleOpenAndDraw();

        Frame dockRect = ReflectionHelpers.getField(controller, "mDockRect");
        assertEquals(topOf(keyboard) - DOCK, dockRect.top, 0.5f);
        assertEquals(topOf(keyboard), dockRect.bottom, 0.5f);
        assertTrue(dockRect.top < pullDock.top);
    }

    @Test public void aStillOpeningPlaneKeepsItsSeedUntilFullyOpen() {
        host.stackLayoutForRaisedKeyboard = this::stackPassForShownKeyboard;
        Frame pullDock = frameOf(dock);
        setProgress(0.4f);

        settleOpenAndDraw();

        Frame dockRect = ReflectionHelpers.getField(controller, "mDockRect");
        assertEquals("a seed swapped mid-open would jump the plane", pullDock.top, dockRect.top,
            0f);
        assertNotNull(ReflectionHelpers.getField(controller, "mRaisedKeyboardDockRect"));
    }

    @Test public void aListThatFitsNeverArmsTheScrollAway() {
        // Fits: the content is no taller than the viewport, so nothing scrolls.
        assertFalse(AppDrawerKeyboardScrollHide.armsOnDrag(900, 900, 0));
        assertFalse(AppDrawerKeyboardScrollHide.armsOnDrag(600, 900, 0));
        // Scrolls, from the top or the middle.
        assertTrue(AppDrawerKeyboardScrollHide.armsOnDrag(2000, 900, 0));
        assertTrue(AppDrawerKeyboardScrollHide.armsOnDrag(2000, 900, 500));
        // At the end already: nowhere further down to go.
        assertFalse(AppDrawerKeyboardScrollHide.armsOnDrag(2000, 900, 1100));
    }

    /** The activity's stack pass, for a keyboard that is showing: dock plus the whole keyboard. */
    private void stackPassForShownKeyboard() {
        ViewGroup.LayoutParams params = stack.getLayoutParams();
        params.height = STACK_KEYBOARD_DOWN
            + (keyboard.getVisibility() == View.GONE ? 0 : KEYBOARD);
        stack.setLayoutParams(params);
    }

    /** The committed open, then the frame that follows it: a traversal and its pre-draw. */
    private void settleOpenAndDraw() {
        ReflectionHelpers.callInstanceMethod(controller, "settle",
            ReflectionHelpers.ClassParameter.from(boolean.class, true),
            ReflectionHelpers.ClassParameter.from(float.class, 0f));
        frame();
    }

    private static void frame() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void setProgress(float value) {
        Object progress = ReflectionHelpers.getField(controller, "mProgress");
        ReflectionHelpers.setField(progress, "value", value);
    }

    /** In the drawer host's coordinates, which is the root's, as the controller measures. */
    private int topOf(@NonNull View view) {
        int[] location = new int[2];
        int[] origin = new int[2];
        view.getLocationOnScreen(location);
        root.getLocationOnScreen(origin);
        return location[1] - origin[1];
    }

    private int bottomOf(@NonNull View view) {
        return topOf(view) + view.getHeight();
    }

    @NonNull
    private Frame frameOf(@NonNull View view) {
        int top = topOf(view);
        return new Frame(0f, top, view.getWidth(), top + view.getHeight());
    }

    private boolean hasRevealableKeyboard() {
        return ReflectionHelpers.callInstanceMethod(controller, "hasRevealableKeyboard");
    }

    private float revealTarget() {
        Object reveal = ReflectionHelpers.getField(controller, "mReveal");
        return ReflectionHelpers.getField(reveal, "target");
    }

    private float capturedGapPx() {
        return ReflectionHelpers.getField(controller, "mCapturedGapPx");
    }

    private Frame contentRect() {
        return ReflectionHelpers.callInstanceMethod(controller, "contentRect");
    }
}
