package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.termux.R;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.launcher.widget.WidgetGridView;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HelpPresentationTest {
    private Activity activity;
    private FrameLayout root;
    private View wall;
    private View status;
    private HelpOverlayView overlay;
    private int dismissed;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        root = new FrameLayout(activity);
        activity.setContentView(root);
        wall = new View(activity); wall.setId(R.id.terminal_pane_wall);
        root.addView(wall, new FrameLayout.LayoutParams(400,500));
        status = new View(activity); status.setId(R.id.terminal_window_bar_host);
        root.addView(status, new FrameLayout.LayoutParams(400,40));
        overlay = new HelpOverlayView(activity, new HelpTargets.ViewFinder() {
            @Override public View findHelpView(int id) {
                return id == android.R.id.content ? root : root.findViewById(id);
            }
            @Override public View activePane() { return wall; }
            @Override public int paneCount() { return 1; }
            @Override public boolean keyRectOnScreen(String name,Rect out) { return false; }
        }, () -> dismissed++);
        root.addView(overlay,new FrameLayout.LayoutParams(400,800));
        layout();
    }
    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        root.layout(0,0,400,800);
        wall.layout(0,60,400,560);
        status.layout(0,0,400,40);
        overlay.layout(0,0,400,800);
    }
    private void open(PaneWallPage place) {
        overlay.show(place); layout(); overlay.refresh(); layout();
    }
    private void tap(float x,float y) {
        MotionEvent down = MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,y,0);
        MotionEvent up = MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,x,y,0);
        assertTrue(overlay.dispatchTouchEvent(down));
        assertTrue(overlay.dispatchTouchEvent(up));
        down.recycle(); up.recycle();
    }
    @Test public void outsideTapConsumesAndDismissesOnlyOnce() {
        open(PaneWallPage.WIDGETS);
        assertTrue(overlay.isShowing());
        tap(200,760);
        assertFalse(overlay.isShowing());
        overlay.dismiss();
        assertEquals(1,dismissed);
        assertEquals(0,overlay.getChildCount());
    }
    @Test public void cardTapStaysOpenAndRemeasureDropsHiddenTarget() {
        open(PaneWallPage.WIDGETS);
        TextView card = null;
        for (int i=0; i<overlay.getChildCount(); i++) {
            View v = overlay.getChildAt(i);
            if (v instanceof TextView && ((TextView)v).getText().toString().startsWith("Status bar\n"))
                card = (TextView)v;
        }
        assertNotNull(card);
        tap(card.getLeft()+4,card.getTop()+4);
        assertTrue(overlay.isShowing());
        status.setVisibility(View.GONE);
        overlay.refresh(); layout();
        for (int i=0; i<overlay.getChildCount(); i++) {
            View v=overlay.getChildAt(i);
            if (v instanceof TextView) assertFalse(((TextView)v).getText().toString().startsWith("Status bar\n"));
        }
        overlay.dismiss();
        open(PaneWallPage.DISPLAY);
        assertTrue(overlay.isShowing());
    }
    @Test public void configuredExtraKeyLabelsIncludeSecondaryAndPlainGlyph() throws Exception {
        ExtraKeysInfo keys = new ExtraKeysInfo("[[{key:'tool:pane.split',popup:'tool:window.new'},'LEFT']]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals("Split",HelpCopy.keyLabel(activity,keys.getMatrix()[0][0]));
        assertEquals("window",HelpCopy.keyLabel(activity,keys.getMatrix()[0][0].getPopup()));
        assertEquals(keys.getMatrix()[0][1].getDisplay(),HelpCopy.keyLabel(activity,keys.getMatrix()[0][1]));
        assertEquals("Ctrl, Alt, Shift, C",HelpTargets.displayChord("ctrl+alt+shift+c"));
    }
    @Test public void emptyWidgetRegionUsesGridMetrics() {
        WidgetGridView grid = new WidgetGridView(activity);
        grid.layout(0,0,400,500);
        assertEquals(grid.metrics().contentBounds(),HelpTargets.largestEmptyRegion(grid));
        View occupied = new View(activity);
        grid.addView(occupied);
        occupied.layout(0,0,400,500);
        assertNull(HelpTargets.largestEmptyRegion(grid));
    }
}
