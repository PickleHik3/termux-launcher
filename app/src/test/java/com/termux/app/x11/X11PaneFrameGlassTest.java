package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Display page wears the Canvas surface's glass slab, from the same {@link PaneSurfaceStyle}
 * the terminal panes and the Widgets page read: with the glass on it carries a dressed slab and
 * the arcs that round it, and with the glass off it carries neither.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11PaneFrameGlassTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;
    private static final float RADIUS_PX = 24f;

    /**
     * The frame with its glass and its corner mask, but without the display view its layout
     * carries: that view binds the native server on construction, so it cannot exist on the JVM.
     * Everything else the page wires on inflation is wired here the same way.
     */
    private static X11PaneFrame page(Activity activity) {
        X11PaneFrame page = new X11PaneFrame(activity);
        page.addView(backdrop(activity, R.id.x11_pane_glass));
        page.addView(backdrop(activity, R.id.x11_pane_corner_mask));
        page.onFinishInflate();
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
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

    private static Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        return activity;
    }

    @Test
    public void glassOnDressesThePagesOwnSlab() {
        X11PaneFrame page = page(activity());
        page.applyStyle(new Style(true));
        PaneGlassBackdropView glass = page.findViewById(R.id.x11_pane_glass);
        assertNotNull(glass);
        assertEquals(View.VISIBLE, glass.getVisibility());
    }

    @Test
    public void glassOffLeavesThePageBare() {
        X11PaneFrame page = page(activity());
        page.applyStyle(new Style(false));
        PaneGlassBackdropView glass = page.findViewById(R.id.x11_pane_glass);
        assertEquals(View.GONE, glass.getVisibility());
    }

    @Test
    public void theSlabIsBehindTheDisplayAndItsEmptyState() {
        X11PaneFrame page = page(activity());
        // Whatever else the page holds, the slab is drawn first: the display's surface punches
        // its own rect out of the window, so a slab above it would be the thing punched away.
        assertEquals(0, page.indexOfChild(page.findViewById(R.id.x11_pane_glass)));
    }

    @Test
    public void theSlabStaysDressedWhileADisplayRuns() {
        X11PaneFrame page = page(activity());
        page.applyStyle(new Style(true));
        page.applyRunning(true);
        PaneGlassBackdropView glass = page.findViewById(R.id.x11_pane_glass);
        assertEquals(View.VISIBLE, glass.getVisibility());
    }

    /** The arcs round the slab as well as the surface, so they follow the radius either way. */
    @Test
    public void theCornerMaskFollowsTheRadiusRatherThanTheServer() {
        X11PaneFrame page = page(activity());
        page.applyStyle(new Style(true));
        PaneGlassBackdropView mask = page.findViewById(R.id.x11_pane_corner_mask);
        assertEquals(View.VISIBLE, mask.getVisibility());
        page.applyRunning(false);
        assertEquals(View.VISIBLE, mask.getVisibility());
        page.applyStyle(new Style(false));
        assertEquals(View.GONE, mask.getVisibility());
    }

    /** A style with nothing but the switch and a radius: the slab needs no wallpaper to show. */
    private static final class Style implements PaneSurfaceStyle {
        private final boolean mGlass;

        Style(boolean glass) {
            mGlass = glass;
        }

        @Override public boolean isPaneGlassActive() { return mGlass; }
        @Override @Nullable public Bitmap paneGlassBlurFrame() { return null; }
        @Override @NonNull public Rect paneGlassBlurFrameRect() { return new Rect(); }
        @Override @Nullable public android.graphics.ColorFilter paneGlassFrostFilter() {
            return null;
        }
        @Override public int paneGlassTintColor() { return 0x40000000; }
        @Override @Nullable public android.graphics.drawable.Drawable paneGlassGrainLayer() {
            return null;
        }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return RADIUS_PX; }
        @Override public int paneGapDp() { return 4; }
    }
}
