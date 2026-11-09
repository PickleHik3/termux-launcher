package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Home floats across an activity recreate while the terminal service lives (wall rework slice 9):
 * a recreated activity's pane controller puts each float back around the same running shell, on
 * its page, minimised as it was, at its shape, instead of the shell turning up as a plain window
 * of the row.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeFloatRecreateTest {

    private TerminalPaneController mBefore;
    private TerminalPaneController mAfter;

    @Before
    public void setUp() {
        android.provider.Settings.Global.putFloat(context().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        mBefore = controller();
        mAfter = controller();
    }

    @Test
    public void aMinimisedFloatComesBackOnItsPageAroundTheSameShellAtItsShape() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mBefore.openHomeFloat(shell, 1);
        window.active.floatFrac = new RectF(.1f, .2f, .6f, .7f);
        window.active.fontSize = 18;
        mBefore.minimiseHomeFloat(window);
        Bundle state = mBefore.saveHomeFloat(window);

        TerminalPaneController.Window back = mAfter.restoreHomeFloat(state, live(shell), 3);

        assertNotNull(back);
        assertTrue(back.isHomeFloat());
        assertEquals(1, back.homePage());
        assertTrue("still in its chip", back.isMinimised());
        assertSame("the same shell, not a fresh one", shell, mAfter.windowActiveSession(back));
        assertSame(back, mAfter.windowOf(shell));
        assertTrue(mAfter.homeFloats().contains(back));
        assertNull("a restored float takes no focus", mAfter.focusedHomeFloat());
        assertFrac(new RectF(.1f, .2f, .6f, .7f), back.active.floatFrac);
        assertEquals(18, back.active.fontSize);
    }

    @Test
    public void aShownFloatStaysShownAndAMaximisedOneKeepsWhatRestoreReturnsTo() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mBefore.openHomeFloat(shell, 0);
        window.active.floatFrac = new RectF(0f, 0f, 1f, 1f);
        window.active.floatRestoreFrac = new RectF(.2f, .2f, .7f, .8f);

        TerminalPaneController.Window back =
            mAfter.restoreHomeFloat(mBefore.saveHomeFloat(window), live(shell), 1);

        assertNotNull(back);
        assertFalse(back.isMinimised());
        assertFrac(new RectF(0f, 0f, 1f, 1f), back.active.floatFrac);
        assertFrac(new RectF(.2f, .2f, .7f, .8f), back.active.floatRestoreFrac);
    }

    @Test
    public void aFloatPastTheLastPageComesBackOnTheLastPage() {
        TerminalSession shell = terminal();
        Bundle state = mBefore.saveHomeFloat(mBefore.openHomeFloat(shell, 4));
        TerminalPaneController.Window back = mAfter.restoreHomeFloat(state, live(shell), 2);
        assertNotNull(back);
        assertEquals(1, back.homePage());
    }

    @Test
    public void aGoneShellOrNoHomePageLeavesTheFloatToTheHost() {
        TerminalSession shell = terminal();
        Bundle state = mBefore.saveHomeFloat(mBefore.openHomeFloat(shell, 0));
        assertNull("its shell is gone", mAfter.restoreHomeFloat(state, new HashMap<>(), 2));
        assertNull("nothing to float over: the host adopts the shell into the row",
            mAfter.restoreHomeFloat(state, live(shell), 0));
        assertNull(mAfter.windowOf(shell));
        assertNull(mAfter.restoreHomeFloat(null, live(shell), 2));
    }

    @Test
    public void aShellAlreadyInAWindowIsNeverFloatedTwice() {
        TerminalSession shell = terminal();
        Bundle state = mBefore.saveHomeFloat(mBefore.openHomeFloat(shell, 0));
        TerminalPaneController.Window row = mAfter.newWindow(shell);
        assertNull(mAfter.restoreHomeFloat(state, live(shell), 2));
        assertSame(row, mAfter.windowOf(shell));
    }

    @Test
    public void aTurnWhileAwayKeepsTheShapeAndRemembersItForTheOrientationLeft() {
        TerminalSession shell = terminal();
        TerminalPaneController.Window window = mBefore.openHomeFloat(shell, 0);
        window.active.floatFrac = new RectF(.1f, .1f, .5f, .5f);
        Bundle state = mBefore.saveHomeFloat(window);
        boolean landscape = landscapeScreen();
        // Laid out in the other orientation before the recreate, never shaped in this one.
        state.putBoolean("home_float_landscape", !landscape);

        TerminalPaneController.Window back = mAfter.restoreHomeFloat(state, live(shell), 1);

        assertNotNull(back);
        assertFrac(new RectF(.1f, .1f, .5f, .5f), back.active.floatFrac);
        assertNotNull(back.active.shapeMemory);
        assertFrac(new RectF(.1f, .1f, .5f, .5f), back.active.shapeMemory.remembered(!landscape));
    }

    private static void assertFrac(@NonNull RectF expected, RectF actual) {
        assertNotNull(actual);
        assertEquals(expected.left, actual.left, .001f);
        assertEquals(expected.top, actual.top, .001f);
        assertEquals(expected.right, actual.right, .001f);
        assertEquals(expected.bottom, actual.bottom, .001f);
    }

    @NonNull
    private static TerminalPaneController controller() {
        TerminalPaneController controller = new TerminalPaneController(new SilentHost(),
            new FrameLayout(context()), LayoutInflater.from(context()));
        HomeFloatLayer layer = new HomeFloatLayer(context());
        layer.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        layer.layout(0, 0, 600, 1000);
        controller.setHomeFloatLayer(layer);
        controller.setHomeFloatObstructions(80, 880, -1);
        return controller;
    }

    @NonNull
    private static Map<String, TerminalSession> live(@NonNull TerminalSession... shells) {
        Map<String, TerminalSession> byHandle = new HashMap<>();
        for (TerminalSession shell : shells) byHandle.put(shell.mHandle, shell);
        return byHandle;
    }

    private static boolean landscapeScreen() {
        return context().getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @NonNull
    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }

    private static final class SilentHost implements TerminalPaneController.Host {
        @Override public TerminalSession createShell(String cwd) { return terminal(); }
        @Override public void configurePaneView(TerminalView view) {}
        @Override public void removeShell(TerminalSession session) {}
        @Override public void onActivePaneChanged() {}
        @Override public void onTreesChanged() {}
        @Override public String defaultCwd() { return "/"; }
    }
}
