package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import com.termux.app.chrome.CornerZones;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

/**
 * The tab's drawn marks and bare glyphs are named for a screen reader, a state-named one names
 * what a press does now, and the tab is reachable from a keyboard only while it is out.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, application = android.app.Application.class)
public class PaneControlsViewAccessibilityTest {

    private ActivityController<Activity> mController;
    private PaneControlsView mTab;
    private boolean mOn;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = mController.get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(400, 300));
        mTab = new PaneControlsView(activity);
        mTab.setActions(
            PaneControlsView.Action.glyph(1, "", "Open settings"),
            PaneControlsView.Action.drawn(2, (canvas, button, paint, density) -> { },
                PaneControlsView.TINT_PRIMARY,
                () -> mOn ? "Leave minimal mode" : "Enter minimal mode"),
            PaneControlsView.Action.glyph(3, ""));
        host.addView(mTab, new FrameLayout.LayoutParams(400, 300));
        host.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 400, 300);
    }

    @After
    public void tearDown() {
        if (mController != null) mController.close();
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
    }

    @Test
    public void aDescribedGlyphAndADrawnMarkAreOffered_aSilentOneIsNot() {
        mTab.show(CornerZones.TOP_RIGHT);
        idle();
        assertNotNull(mTab.getAccessibilityNodeProvider());
        AccessibilityNodeInfo root =
            mTab.getAccessibilityNodeProvider().createAccessibilityNodeInfo(View.NO_ID);
        assertEquals("the two named buttons, not the silent glyph", 2, root.getChildCount());
    }

    @Test
    public void aStateNamedButtonNamesWhatAPressDoesNow() {
        PaneControlsView.Action action = PaneControlsView.Action.drawn(2,
            (canvas, button, paint, density) -> { }, PaneControlsView.TINT_PRIMARY,
            () -> mOn ? "Leave minimal mode" : "Enter minimal mode");
        assertEquals("Enter minimal mode", action.spokenDescription().toString());
        mOn = true;
        assertEquals("Leave minimal mode", action.spokenDescription().toString());
    }

    @Test
    public void theTabIsReachableByKeyboardOnlyWhileOut_andNeverInTouchMode() {
        assertFalse("resting: not focusable", mTab.isFocusable());
        assertFalse(mTab.isFocusableInTouchMode());
        mTab.show(CornerZones.TOP_RIGHT);
        idle();
        assertTrue("out: reachable", mTab.isFocusable());
        assertFalse("but never by a touch", mTab.isFocusableInTouchMode());
        mTab.dismiss();
        idle();
        assertFalse("away again", mTab.isFocusable());
    }
}
