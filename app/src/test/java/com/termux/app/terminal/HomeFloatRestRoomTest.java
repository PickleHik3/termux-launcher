package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
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

/**
 * A Home float's size comes only from Home's rest, whichever page is in front: the
 * Workspace's stack and keyboard, read while it stands in front, gave the float a size its shell
 * was resized to when a crossing toward Home showed it (the Workspace's keyboard up: 44x37 to
 * 44x25 during the slide, back to 44x37 after the landing). The drive here is the activity's
 * geometry pass, which hands the float layer what covers it only when
 * {@link HomeFloatPolicy#obstructionsMayMove} says so: Home at rest, the Workspace in front with
 * its keyboard up, a crossing back, Home at rest again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeFloatRestRoomTest {

    private static final int LAYER_WIDTH = 600;
    private static final int LAYER_HEIGHT = 1000;
    /** Home's rest: the status bar's bottom, the dock's top, no keyboard. */
    private static final int HOME_ROOM_TOP = 80;
    private static final int HOME_DOCK_TOP = 880;

    private TerminalPaneController mController;
    private HomeFloatLayer mLayer;
    private TerminalPaneController.Window mFloat;

    @Before
    public void setUp() {
        android.provider.Settings.Global.putFloat(context().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        mController = new TerminalPaneController(new Host(), new FrameLayout(context()),
            LayoutInflater.from(context()));
        mLayer = new HomeFloatLayer(context());
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
        mController.setHomeFloatLayer(mLayer);
        pass(false, false, true, HOME_ROOM_TOP, HOME_DOCK_TOP, -1);
        mFloat = mController.openHomeFloat(terminal(), 0);
        layout(mLayer, LAYER_WIDTH, LAYER_HEIGHT);
    }

    @Test public void aFloatKeepsHomesRoomWhileTheWorkspaceIsInFrontWithItsKeyboardUp() {
        int[] rest = boundsOfFloat();

        // The Workspace committed, its keyboard up: the stack it reads is the Workspace's, taller
        // and with the keyboard's top standing in the room.
        pass(false, false, false, HOME_ROOM_TOP + 40, 700, 560);
        assertBounds("the Workspace in front, keyboard up", rest);
        pass(false, false, false, HOME_ROOM_TOP + 40, 700, 520);
        assertBounds("the keyboard moving there", rest);
    }

    @Test public void aCrossingBackToHomeResizesNothingAndTheLandingFindsHomesRest() {
        int[] rest = boundsOfFloat();
        pass(false, false, false, HOME_ROOM_TOP + 40, 700, 560);

        // The crossing: no pass reaches the float, whichever rest the frame stands.
        for (int step = 0; step <= 4; step++) {
            pass(true, false, false, HOME_ROOM_TOP + 40 - step * 10, 700 + step * 45, 560);
            assertBounds("crossing frame " + step, rest);
        }
        pass(true, false, true, HOME_ROOM_TOP, HOME_DOCK_TOP, -1);
        assertBounds("the last frame", rest);

        // Landed on Home: its rest is what the float was laid in.
        pass(false, false, true, HOME_ROOM_TOP, HOME_DOCK_TOP, -1);
        assertBounds("landed on Home", rest);
    }

    @Test public void homeAtRestStillMeetsItsOwnKeyboard() {
        int[] rest = boundsOfFloat();

        pass(false, false, true, HOME_ROOM_TOP, HOME_DOCK_TOP, 600);
        assertEquals("shrunk to end at Home's keyboard", 600, bottomOfFloat());
        pass(false, false, true, HOME_ROOM_TOP, HOME_DOCK_TOP, -1);
        assertBounds("grown back when it closes", rest);
    }

    /** One geometry pass: what covers the float layer reaches it only when the policy lets it. */
    private void pass(boolean crossing, boolean seamStandsLayout, boolean homeFront,
                      int roomTop, int dockTop, int keyboardTop) {
        if (!HomeFloatPolicy.obstructionsMayMove(crossing, seamStandsLayout, homeFront)) return;
        mController.setHomeFloatObstructions(roomTop, dockTop, keyboardTop);
    }

    private int[] boundsOfFloat() {
        FrameLayout.LayoutParams params = paramsOfFloat();
        return new int[] {params.leftMargin, params.topMargin, params.width, params.height};
    }

    private int bottomOfFloat() {
        FrameLayout.LayoutParams params = paramsOfFloat();
        return params.topMargin + params.height;
    }

    private void assertBounds(@NonNull String when, @NonNull int[] expected) {
        org.junit.Assert.assertArrayEquals(when, expected, boundsOfFloat());
    }

    @NonNull
    private FrameLayout.LayoutParams paramsOfFloat() {
        TerminalSession shell = mController.windowActiveSession(mFloat);
        for (int i = 0; i < mLayer.getChildCount(); i++) {
            View child = mLayer.getChildAt(i);
            if (holds(child, shell)) return (FrameLayout.LayoutParams) child.getLayoutParams();
        }
        throw new AssertionError("no container for the float");
    }

    private static boolean holds(@NonNull View view, TerminalSession shell) {
        if (view instanceof TerminalView) return ((TerminalView) view).getCurrentSession() == shell;
        if (!(view instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            if (holds(group.getChildAt(i), shell)) return true;
        return false;
    }

    private static void layout(@NonNull View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    @NonNull
    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @NonNull
    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }

    private static final class Host implements TerminalPaneController.Host {
        @Override public TerminalSession createShell(String cwd) { return terminal(); }
        @Override public void configurePaneView(TerminalView view) {}
        @Override public void removeShell(TerminalSession session) {}
        @Override public void onActivePaneChanged() {}
        @Override public void onTreesChanged() {}
        @Override public String defaultCwd() { return "/"; }
    }
}
