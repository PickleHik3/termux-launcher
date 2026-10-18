package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Glass switch of a built-in widget: off is exactly today's card, on is the see-through card
 * over the launcher's own wallpaper blur, fed by the page, and back off returns to today's card.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetGlassTest {

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    private static final class Plain extends BuiltinWidgetView {
        Plain(@NonNull Context context, @NonNull BuiltinWidgetServices services,
              @NonNull BuiltinWidgetStyle style) {
            super(context, BuiltinWidgetKind.BATTERY, services, style);
        }

        @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                         @NonNull BuiltinWidgetUi ui) {
            frame.addView(new TextView(getContext()));
        }
    }

    /** A page that hands out a glass with a frame and keeps who wears it. */
    private static final class Page extends FrameLayout implements BuiltinWidgetView.GlassSource {
        final Set<BuiltinWidgetView> wearing = new LinkedHashSet<>();
        @Nullable PaneSurfaceStyle glass;

        Page(@NonNull Context context) { super(context); }

        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { wearing.add(widget); }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) {
            wearing.remove(widget);
        }
    }

    private static PaneSurfaceStyle glass(boolean active, @Nullable Bitmap frame) {
        return new PaneSurfaceStyle() {
            @Override public boolean isPaneGlassActive() { return active; }
            @Override public Bitmap paneGlassBlurFrame() { return frame; }
            @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(0, 0, 100, 200); }
            @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
            @Override public int paneGlassTintColor() { return 0x80000000; }
            @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
            @Override public int paneGlassGrainStrength() { return 0; }
            @Override public float paneGlassCornerRadiusPx() { return 0f; }
            @Override public int paneGapDp() { return 0; }
        };
    }

    private Context context;
    private BuiltinWidgetServices services;
    private BuiltinWidgetStyle style;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        services = new BuiltinWidgetServices(context, HOST);
        style = BuiltinWidgetStyle.resolve(context, BuiltinWidgetStyle.Direction.TONAL, false, null);
    }

    private static Bundle glassOn() {
        Bundle config = new Bundle();
        config.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
        return config;
    }

    @Nullable private static PaneGlassBackdropView slabOf(@NonNull BuiltinWidgetView view) {
        for (int i = 0; i < view.getChildCount(); i++) {
            if (view.getChildAt(i) instanceof PaneGlassBackdropView)
                return (PaneGlassBackdropView) view.getChildAt(i);
        }
        return null;
    }

    @Test
    public void theSwitchIsOffUnlessStoredOn() {
        assertFalse(BuiltinWidgetGlass.isOn(null));
        assertFalse(BuiltinWidgetGlass.isOn(""));
        assertFalse(BuiltinWidgetGlass.isOn("true"));
        assertTrue(BuiltinWidgetGlass.isOn(BuiltinWidgetGlass.ON));
        Map<String, String> values = new HashMap<>();
        BuiltinWidgetGlass.write(values, true);
        assertEquals(BuiltinWidgetGlass.ON, values.get(BuiltinWidgetGlass.CONFIG_KEY));
        BuiltinWidgetGlass.write(values, false);
        assertFalse("off is stored as nothing",
            BuiltinWidgetHost.configOf(values).containsKey(BuiltinWidgetGlass.CONFIG_KEY));
    }

    @Test
    public void theBlurShowsOnlyOnALiveWidgetWithTheLaunchersGlassUp() {
        assertTrue(BuiltinWidgetGlass.slabShown(true, false, true));
        assertFalse(BuiltinWidgetGlass.slabShown(false, false, true));
        assertFalse("a picker card samples nothing", BuiltinWidgetGlass.slabShown(true, true, true));
        assertFalse("no glass to give: the card alone", BuiltinWidgetGlass.slabShown(true, false,
            false));
        assertTrue(BuiltinWidgetGlass.cardGlass(true, false));
        assertTrue(BuiltinWidgetGlass.cardGlass(false, true));
        assertFalse(BuiltinWidgetGlass.cardGlass(false, false));
    }

    @Test
    public void offIsExactlyTodaysCardAndOnIsTheGlassCard() {
        Plain view = new Plain(context, services, style);
        view.bind(null);
        assertSame("off: the page's own look", style, view.style());
        view.bind(glassOn());
        assertTrue(view.isGlassOn());
        assertTrue(view.style().glass);
        assertEquals(style.cornerRadiusPx, view.style().cornerRadiusPx, 0f);
        assertEquals(style.onSurface, view.style().onSurface);
        view.bind(new Bundle());
        assertFalse(view.isGlassOn());
        assertSame("back off: today's look again", style, view.style());
    }

    @Test
    public void aGlassWidgetWearsThePagesBlurUnderItsCardAndGivesItBackWhenOff() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        page.glass = glass(true, frame);
        Plain view = new Plain(activity, services, style);
        view.bind(glassOn());
        page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);

        assertTrue("registered with its page", page.wearing.contains(view));
        PaneGlassBackdropView slab = slabOf(view);
        assertTrue(slab != null && slab.getVisibility() == View.VISIBLE);
        assertSame("under the card", slab, view.getChildAt(0));
        assertSame(frame, slab.heldFrame());

        // The launcher's glass goes (no blur configured): the see-through card stays, no blur.
        view.onGlassStyle(glass(false, null));
        assertEquals(View.GONE, slab.getVisibility());
        view.onGlassStyle(page.glass);
        assertEquals(View.VISIBLE, slab.getVisibility());

        view.bind(new Bundle());
        assertFalse("off: no longer fed", page.wearing.contains(view));
        assertEquals(View.GONE, slab.getVisibility());

        view.bind(glassOn());
        page.removeView(view);
        assertFalse("detached: no longer fed", page.wearing.contains(view));
    }

    @Test
    public void aWidgetWithGlassOffNeverMakesASlab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        page.glass = glass(true, Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888));
        Plain view = new Plain(activity, services, style);
        view.bind(null);
        page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);
        assertTrue(page.wearing.isEmpty());
        assertNull(slabOf(view));
    }
}
