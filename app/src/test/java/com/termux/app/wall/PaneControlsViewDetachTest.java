package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.RectF;
import android.os.Looper;
import android.view.ViewGroup;
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
 * What a detach mid-motion leaves. The terminal's pane tree takes the tab off its host and puts
 * it back on every render, and the minimal glyph's own tap is a dismiss followed by a render in
 * the same call — so the retract's animator was cancelled a frame in, with the tab fully drawn
 * and marked both shown and retracting: a tab nothing could put away (a tap off it asks
 * {@link PaneControlsView#isControlsShown}, which said no) and every button of which still
 * answered a finger.
 *
 * <p>On a window, because a view that was never attached is never told it has been detached.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, application = android.app.Application.class)
public class PaneControlsViewDetachTest {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    private ActivityController<Activity> mController;
    private FrameLayout mHost;
    private PaneControlsView mTab;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = mController.get();
        mHost = new FrameLayout(activity);
        activity.setContentView(mHost, new ViewGroup.LayoutParams(WIDTH, HEIGHT));
        mTab = new PaneControlsView(activity);
        mTab.setActions(PaneControlsView.Action.glyph(1, ""),
            PaneControlsView.Action.glyph(2, ""),
            PaneControlsView.Action.glyph(3, ""));
        attach();
        idle();
    }

    @After
    public void tearDown() {
        if (mController != null) mController.close();
    }

    private void attach() {
        mHost.addView(mTab, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
        mHost.measure(android.view.View.MeasureSpec.makeMeasureSpec(WIDTH,
                android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(HEIGHT,
                android.view.View.MeasureSpec.EXACTLY));
        mHost.layout(0, 0, WIDTH, HEIGHT);
    }

    /** The reveal and the retract are 190 ms; this is past either. */
    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
    }

    /** Where the first button is while the tab is out. */
    private RectF firstButton() {
        RectF out = new RectF();
        assertTrue("the tab is out", mTab.actionBounds(1, out));
        return out;
    }

    @Test
    public void aRetractCutShortByADetachIsATabThatIsGone() {
        mTab.show(CornerZones.TOP_RIGHT);
        idle();
        RectF button = firstButton();
        assertTrue(mTab.isControlsShown());

        // The dismiss, then the render that takes the tab off the host in the same call.
        mTab.dismiss();
        mHost.removeView(mTab);
        attach();

        assertFalse("nothing is out", mTab.isControlsShown());
        assertEquals("and nothing answers a finger", PaneControlsView.ACTION_NONE,
            mTab.actionAt(button.centerX(), button.centerY()));
        idle();
        assertEquals(PaneControlsView.ACTION_NONE,
            mTab.actionAt(button.centerX(), button.centerY()));
        assertFalse(mTab.actionBounds(1, new RectF()));
    }

    @Test
    public void aTabPutAwayByADetachComesBackOnTheNextShow() {
        mTab.show(CornerZones.TOP_RIGHT);
        idle();
        mTab.dismiss();
        mHost.removeView(mTab);
        attach();

        mTab.show(CornerZones.TOP_RIGHT);
        assertTrue(mTab.isControlsShown());
        idle();
        assertTrue(mTab.actionBounds(1, new RectF()));

        // And the ordinary dismiss still works on it afterwards.
        mTab.dismiss();
        assertFalse(mTab.isControlsShown());
        idle();
        assertFalse(mTab.actionBounds(1, new RectF()));
    }

    @Test
    public void aRevealCutShortByADetachIsATabThatIsOut() {
        mTab.show(CornerZones.TOP_RIGHT);
        // No frame has passed: the tab is at the start of its slide.
        mHost.removeView(mTab);
        attach();

        assertTrue(mTab.isControlsShown());
        RectF button = firstButton();
        assertEquals("fully out, every button answers", 1,
            mTab.actionAt(button.centerX(), button.centerY()));
    }

    @Test
    public void aTabAtRestIsLeftAloneByADetach() {
        mTab.show(CornerZones.TOP_RIGHT);
        idle();
        mHost.removeView(mTab);
        attach();
        assertTrue(mTab.isControlsShown());
        assertTrue(mTab.actionBounds(1, new RectF()));

        mTab.dismiss();
        idle();
        mHost.removeView(mTab);
        attach();
        assertFalse(mTab.isControlsShown());
        assertFalse(mTab.actionBounds(1, new RectF()));
    }
}
