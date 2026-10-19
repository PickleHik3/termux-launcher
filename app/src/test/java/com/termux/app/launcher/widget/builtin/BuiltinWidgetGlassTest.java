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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

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
 * The Glass switch of a built-in widget: off is the solid card, on is the Look's glass — the slab
 * a terminal pane wears, fed by the page from the Look (Base's values) — and back off returns to
 * the solid card. Every widget's corner is the Look's.
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

        int groundAlpha() { return cardAlpha(); }
    }

    /** A page that hands out the Look and keeps who is live on it. */
    private static final class Page extends FrameLayout implements BuiltinWidgetView.GlassSource {
        final Set<BuiltinWidgetView> live = new LinkedHashSet<>();
        @Nullable PaneSurfaceStyle glass;

        Page(@NonNull Context context) { super(context); }

        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { live.add(widget); }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) {
            live.remove(widget);
        }
    }

    /** The Look's glass, at the shipped Base Corners unless told otherwise. */
    private static PaneSurfaceStyle glass(boolean active, @Nullable Bitmap frame) {
        return glass(active, frame, TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS);
    }

    private static PaneSurfaceStyle glass(boolean active, @Nullable Bitmap frame, int cornersDp) {
        return new PaneSurfaceStyle() {
            @Override public boolean isPaneGlassActive() { return active; }
            @Override public Bitmap paneGlassBlurFrame() { return frame; }
            @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(0, 0, 100, 200); }
            @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
            @Override public int paneGlassTintColor() { return 0x80000000; }
            @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
            @Override public int paneGlassGrainStrength() { return 0; }
            @Override public float paneGlassCornerRadiusPx() { return 0f; }
            @Override public int paneCornerRadiusDp() { return cornersDp; }
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

    @NonNull private static View cardOf(@NonNull BuiltinWidgetView view) {
        return view.findViewById(android.R.id.background);
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
    public void theLooksSlabShowsOnlyOnALiveWidgetWithItsSwitchOnAndTheLookWorn() {
        assertTrue(BuiltinWidgetGlass.slabShown(true, false, true));
        assertFalse(BuiltinWidgetGlass.slabShown(false, false, true));
        assertFalse("a picker card samples nothing", BuiltinWidgetGlass.slabShown(true, true, true));
        assertFalse("off the wallpaper: the see-through card alone",
            BuiltinWidgetGlass.slabShown(true, false, false));
    }

    @Test
    public void onlyCornersThatReallyMovedRestyle() {
        assertFalse(BuiltinWidgetGlass.cornersMoved(48f, 48f));
        assertFalse("float noise from the same dp", BuiltinWidgetGlass.cornersMoved(48f, 48.1f));
        assertTrue(BuiltinWidgetGlass.cornersMoved(48f, 40f));
        assertTrue(BuiltinWidgetGlass.cornersMoved(0f, 1f));
    }

    @Test
    public void everyStyleIsCutAtTheLooksCornersWhateverTheStyleSetting() {
        float density = context.getResources().getDisplayMetrics().density;
        float look = TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS * density;
        assertEquals(look, style.cornerRadiusPx, 0.01f);
        assertEquals("Pane is no longer its own 12dp", look, BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Direction.PANE, false, null).cornerRadiusPx, 0.01f);
    }

    @Test
    public void offIsTheSolidCardAndOnIsTheSeeThroughOne() {
        Plain view = new Plain(context, services, style);
        view.bind(null);
        assertSame("off: the host's own look", style, view.style());
        assertEquals("the solid card's ground is whole", 255, view.groundAlpha());
        view.bind(glassOn());
        assertTrue(view.isGlassOn());
        assertTrue(view.style().glass);
        assertEquals(style.cornerRadiusPx, view.style().cornerRadiusPx, 0f);
        assertEquals(style.onSurface, view.style().onSurface);
        view.bind(new Bundle());
        assertFalse(view.isGlassOn());
        assertSame("back off: the solid card again", style, view.style());
    }

    @Test
    public void aGlassWidgetWearsTheLooksSlabAsItsCardAndPutsItDownWhenOff() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        page.glass = glass(true, frame);
        Plain view = new Plain(activity, services, style);
        view.bind(glassOn());
        page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);

        assertTrue("registered with its page", page.live.contains(view));
        PaneGlassBackdropView slab = slabOf(view);
        assertTrue(slab != null && slab.getVisibility() == View.VISIBLE);
        assertSame("under the card", slab, view.getChildAt(0));
        assertSame(frame, slab.heldFrame());
        assertNull("the slab's tint is the card: no fill over it", cardOf(view).getBackground());
        assertEquals("the ground is the Look's tint", 0x80, view.groundAlpha());

        // Off the wallpaper there is no glass to wear: the see-through card comes back.
        view.onGlassStyle(glass(false, null));
        assertEquals(View.GONE, slab.getVisibility());
        assertTrue(cardOf(view).getBackground() instanceof GradientDrawable);
        view.onGlassStyle(page.glass);
        assertEquals(View.VISIBLE, slab.getVisibility());

        view.bind(new Bundle());
        assertTrue("still fed the Look's corners", page.live.contains(view));
        assertEquals(View.GONE, slab.getVisibility());
        assertTrue(cardOf(view).getBackground() instanceof GradientDrawable);

        view.bind(glassOn());
        page.removeView(view);
        assertFalse("detached: no longer fed", page.live.contains(view));
    }

    @Test
    public void aWidgetWithGlassOffIsFedTheLookButNeverMakesASlab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        page.glass = glass(true, Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888));
        Plain view = new Plain(activity, services, style);
        view.bind(null);
        page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);
        assertTrue(page.live.contains(view));
        view.onGlassStyle(page.glass);
        assertNull(slabOf(view));
    }

    @Test
    public void cornersThatMovedUnderTheWidgetAskForOneRestyle() {
        int[] restyles = {0};
        services.setRestyleRequest(() -> restyles[0]++);
        Plain view = new Plain(context, services, style);
        view.bind(null);
        view.onGlassStyle(glass(true, null));
        assertEquals("the Look's corners are the style's", 0, restyles[0]);
        view.onGlassStyle(glass(true, null, 8));
        assertEquals(1, restyles[0]);
        view.onGlassStyle(null);
        assertEquals("no Look to read: nothing asked", 1, restyles[0]);
    }
}
