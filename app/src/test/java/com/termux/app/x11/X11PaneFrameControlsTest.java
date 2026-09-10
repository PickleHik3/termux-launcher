package com.termux.app.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;

import com.termux.app.wall.PaneControlsView;
import com.termux.app.terminal.PaneGlassBackdropView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/**
 * The display page's border tab and its scale rail come out together, and they go away together:
 * a tap anywhere else, a tapped button, or a used rail puts both back. The rail must never be
 * left standing on its own after the tab has gone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11PaneFrameControlsTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;

    /**
     * The frame with its glass and corner mask, wired as inflation wires it, but without the
     * display view its layout carries: that view binds the native server, so it cannot exist on
     * the JVM.
     */
    private static X11PaneFrame page() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        X11PaneFrame page = new X11PaneFrame(activity);
        page.addView(backdrop(activity, R.id.x11_pane_glass));
        page.addView(backdrop(activity, R.id.x11_pane_corner_mask));
        page.onFinishInflate();
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        page.applyRunning(true);
        return page;
    }

    private static PaneGlassBackdropView backdrop(Activity activity, int id) {
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
        backdrop.setId(id);
        backdrop.setVisibility(View.GONE);
        backdrop.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return backdrop;
    }

    private static void touch(X11PaneFrame page, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        page.dispatchTouchEvent(event);
        event.recycle();
    }

    private static void tap(X11PaneFrame page, float x, float y) {
        touch(page, MotionEvent.ACTION_DOWN, x, y);
        touch(page, MotionEvent.ACTION_UP, x, y);
    }

    /** Let the slide-out land so the controls answer touches. */
    private static void settle() {
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
    }

    /** A tap on the top border brings the tab and, with a display running, the rail. */
    private static X11PaneFrame pageWithControlsOut() {
        X11PaneFrame page = page();
        tap(page, WIDTH / 2f, 2f);
        settle();
        assertTrue(page.isControlsTabShown());
        assertTrue(page.isScaleRailShown());
        return page;
    }

    /** A point on the rail's track: the first one down the leading edge the rail answers to. */
    private static float railY(X11PaneFrame page) {
        DisplayScaleRailView rail = page.scaleRail();
        for (float y = 0; y < HEIGHT; y += 4f) {
            if (rail.hits(8f, y)) return y;
        }
        throw new AssertionError("the rail answers nowhere along the edge");
    }

    @Test
    public void aTapOnTheDisplayPutsTheRailAwayWithTheTab() {
        X11PaneFrame page = pageWithControlsOut();
        tap(page, WIDTH / 2f, HEIGHT / 2f);
        assertFalse(page.isControlsTabShown());
        assertFalse(page.isScaleRailShown());
    }

    @Test
    public void aTappedButtonPutsTheRailAwayToo() {
        X11PaneFrame page = pageWithControlsOut();
        PaneControlsView tab = page.controlsTab();
        RectF bounds = new RectF();
        tab.tabBounds(bounds);
        float y = bounds.centerY();
        float x = -1f;
        for (float probe = bounds.left; probe <= bounds.right; probe += 2f) {
            if (tab.actionAt(probe, y) != PaneControlsView.ACTION_NONE) {
                x = probe;
                break;
            }
        }
        assertNotEquals("no button found on the tab", -1f, x, 0f);
        tap(page, x, y);
        assertFalse(page.isControlsTabShown());
        assertFalse(page.isScaleRailShown());
    }

    @Test
    public void aUsedRailPutsItselfAwayWithTheTab() {
        X11PaneFrame page = pageWithControlsOut();
        float y = railY(page);
        touch(page, MotionEvent.ACTION_DOWN, 8f, y);
        touch(page, MotionEvent.ACTION_MOVE, 8f, y + 60f);
        touch(page, MotionEvent.ACTION_UP, 8f, y + 60f);
        assertFalse(page.isScaleRailShown());
        assertFalse(page.isControlsTabShown());
    }

    @Test
    public void aTapElsewherePutsARailAwayEvenWhenTheTabIsAlreadyGone() {
        X11PaneFrame page = pageWithControlsOut();
        page.controlsTab().dismiss();
        settle();
        assertFalse(page.isControlsTabShown());
        assertTrue(page.isScaleRailShown());
        tap(page, WIDTH / 2f, HEIGHT / 2f);
        assertFalse(page.isScaleRailShown());
    }
}
