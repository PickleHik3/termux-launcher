package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.WidgetInkSource;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * A widget whose ancestors move without its own frame changing is asked again at the rect it
 * stands in now, not the one it was attached at.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetInkOriginTest {

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    private static final class Page extends FrameLayout implements BuiltinWidgetView.GlassSource {
        @Nullable PaneSurfaceStyle glass;
        Page(@NonNull Context context) { super(context); }
        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public boolean widgetsOnGlass() { return true; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) { }
    }

    private static final class InkLook implements PaneSurfaceStyle, WidgetInkSource {
        final List<Rect> asked = new ArrayList<>();
        @NonNull private final Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        @NonNull @Override public ChromeInk.Polarity widgetPolarity() {
            return ChromeInk.Polarity.PALE_INK;
        }
        @Override public boolean widgetFrameReady() { return true; }
        @NonNull @Override public OnGlass.Resolution widgetInk(@NonNull Rect r, int body,
                                                               int veil, int text, int graphic) {
            asked.add(new Rect(r));
            return OnGlass.resolveWidget(0xFF101114, body, text, OnGlass.TARGET_BODY_TEXT,
                graphic, OnGlass.TARGET_LARGE_TEXT, veil);
        }
        @Override public int widgetWallpaper(@NonNull Rect r) {
            asked.add(new Rect(r));
            return 0xFF101114;
        }
        @Override public boolean isPaneGlassActive() { return true; }
        @Override public Bitmap paneGlassBlurFrame() { return frame; }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(0, 0, 100, 200); }
        @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return 0x4DE1E7F2; }
        @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneCornerRadiusDp() {
            return TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
        }
        @Override public int paneGapDp() { return 0; }
    }

    @Test
    public void ancestorMovingUnderAnUnchangedFrameAsksAgain() {
        Context context = RuntimeEnvironment.getApplication();
        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(
            new android.view.ContextThemeWrapper(context,
                com.google.android.material.R.style.Theme_Material3_Light),
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        ClockWidgetView clock = new ClockWidgetView(activity, services, style);
        InkLook look = new InkLook();
        Page page = new Page(activity);
        page.glass = look;
        FrameLayout cell = new FrameLayout(activity);
        clock.bind(null);
        cell.addView(clock, new FrameLayout.LayoutParams(400, 200));
        page.addView(cell, new FrameLayout.LayoutParams(400, 200));
        activity.setContentView(page);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        clock.onGlassStyle(look);
        assertTrue(look.asked.size() > 0);
        look.asked.clear();

        // Re-attached while its ancestor stands elsewhere; its own frame is unchanged.
        cell.removeView(clock);
        cell.layout(cell.getLeft() + 150, cell.getTop() + 300, cell.getRight() + 150,
            cell.getBottom() + 300);
        cell.addView(clock, new FrameLayout.LayoutParams(400, 200));
        clock.layout(0, 0, 400, 200);
        look.asked.clear();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        page.getViewTreeObserver().dispatchOnPreDraw();
        int[] origin = new int[2];
        com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(clock, origin, new int[2]);
        Rect expected = new Rect(origin[0], origin[1], origin[0] + 400, origin[1] + 200);
        assertTrue("asked again at the new rect: " + look.asked, look.asked.contains(expected));
        int count = look.asked.size();
        cell.setTranslationX(90f);
        cell.layout(cell.getLeft(), cell.getTop(), cell.getRight(), cell.getBottom());
        assertEquals("a translation is not a new rect", count, look.asked.size());
    }
}
