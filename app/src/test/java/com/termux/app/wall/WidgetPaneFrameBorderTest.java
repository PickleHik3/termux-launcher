package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;

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
 * The Home page's frame. A Home page wears no borders (ADR 0008): whatever the glass and the
 * border preference say for the windows, the page draws no slab and no line, and the widgets
 * stand on the wallpaper as on any launcher.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPaneFrameBorderTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;

    private static WidgetPaneFrame page(Activity activity) {
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneFrame page = (WidgetPaneFrame) LayoutInflater.from(activity)
            .inflate(R.layout.view_widget_pane, null);
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        return page;
    }

    private static Activity activity() {
        return Robolectric.buildActivity(Activity.class).setup().get();
    }

    @Test
    public void theBorderPreferenceDrawsNoLineOnAHomePage() {
        WidgetPaneFrame page = page(activity());
        page.applyStyle(new Style(false, true));
        assertNull("no plain stroke", page.getForeground());
        PaneGlassBackdropView glass = page.findViewById(R.id.widget_pane_glass);
        assertEquals("no slab", View.GONE, glass.getVisibility());
    }

    @Test
    public void theGlassDrawsNoSlabAndNoRimOnAHomePage() {
        WidgetPaneFrame page = page(activity());
        page.applyStyle(new Style(true, true));
        assertNull("no rim", page.getForeground());
        PaneGlassBackdropView glass = page.findViewById(R.id.widget_pane_glass);
        assertEquals("no slab", View.GONE, glass.getVisibility());
    }

    @Test
    public void withNeitherTheGlassNorTheBorderThePageWearsNothing() {
        WidgetPaneFrame page = page(activity());
        page.applyStyle(new Style(false, false));
        assertNull(page.getForeground());
    }

    /** A style with nothing but the two switches and a radius. */
    private static final class Style implements PaneSurfaceStyle {
        private final boolean mGlass;
        private final boolean mBorder;

        Style(boolean glass, boolean border) {
            mGlass = glass;
            mBorder = border;
        }

        @Override public boolean isPaneGlassActive() { return mGlass; }
        @Override public boolean paneBorderEnabled() { return mBorder; }
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
        @Override public float paneGlassCornerRadiusPx() { return 24f; }
        @Override public int paneGapDp() { return 4; }
    }
}
