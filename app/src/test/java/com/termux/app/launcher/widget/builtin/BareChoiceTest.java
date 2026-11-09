package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.WidgetInkSource;
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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bare as an opt-in for Clock and Weather (ticket #123, spec #101): chosen in each widget's Glass
 * row, off by default, never turned on by the Look's Clear stop, its ink held to the card's
 * contrast, and no Glass row at all on Glance.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BareChoiceTest {

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    /** A page that hands out the Look. */
    private static final class Page extends FrameLayout implements BuiltinWidgetView.GlassSource {
        @Nullable PaneSurfaceStyle glass;
        boolean onGlass;

        Page(@NonNull Context context) { super(context); }

        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public boolean widgetsOnGlass() { return onGlass; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) { }
    }

    /** The Look on the wallpaper, with the widget band answering for {@link #wallpaper}. */
    private static final class InkLook implements PaneSurfaceStyle, WidgetInkSource {
        @NonNull ChromeInk.Polarity polarity = ChromeInk.Polarity.PALE_INK;
        int wallpaper = 0xFF101114;
        int tint = 0x4DE1E7F2;
        @NonNull private final Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);

        @NonNull @Override public ChromeInk.Polarity widgetPolarity() { return polarity; }
        @Override public boolean widgetFrameReady() { return true; }
        @NonNull @Override public OnGlass.Resolution widgetInk(@NonNull Rect rootRect, int body,
                                                               int veilColor, int textInk,
                                                               int graphicInk) {
            return OnGlass.resolveWidget(wallpaper, body, textInk, OnGlass.TARGET_BODY_TEXT,
                graphicInk, OnGlass.TARGET_LARGE_TEXT, veilColor);
        }
        @Override public int widgetWallpaper(@NonNull Rect rootRect) { return wallpaper; }

        @Override public boolean isPaneGlassActive() { return true; }
        @Override public Bitmap paneGlassBlurFrame() { return frame; }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(0, 0, 100, 200); }
        @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return tint; }
        @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneCornerRadiusDp() {
            return TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
        }
        @Override public int paneGapDp() { return 0; }
    }

    private Context context;
    private BuiltinWidgetServices services;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        services = new BuiltinWidgetServices(context, HOST);
    }

    private BuiltinWidgetStyle dayStyle() {
        return BuiltinWidgetStyle.resolve(new android.view.ContextThemeWrapper(context,
                com.google.android.material.R.style.Theme_Material3_Light),
            BuiltinWidgetStyle.Numerals.SANS, false, null);
    }

    private static Bundle bare() {
        Bundle b = new Bundle();
        b.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.BARE);
        return b;
    }

    private static void live(@NonNull Activity activity, @NonNull InkLook look,
                             @NonNull BuiltinWidgetView view, @Nullable Bundle config) {
        Page page = new Page(activity);
        page.glass = look;
        page.onGlass = true;
        view.bind(config);
        page.addView(view, new FrameLayout.LayoutParams(400, 200));
        activity.setContentView(page);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        view.onGlassStyle(look);
    }

    @Test
    public void bareChosenIsBareWithGlassOff() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ClockWidgetView clock = new ClockWidgetView(activity, services, dayStyle());
        live(activity, new InkLook(), clock, bare());
        assertTrue(clock.asksBare());
        assertTrue(clock.isBare());
        assertTrue(clock.style().bare);
        assertFalse("a Bare widget wears no glass, though the page's switch is on",
            clock.isGlassOn());

        WeatherWidgetView weather = new WeatherWidgetView(activity, services, dayStyle());
        weather.bind(bare());
        assertTrue(weather.asksBare());
        assertFalse(weather.isGlassOn());
        assertFalse(BuiltinWidgetGlass.resolve(BuiltinWidgetGlass.BARE, true));
        assertTrue(BuiltinWidgetGlass.choseBare(BuiltinWidgetGlass.BARE));
    }

    @Test
    public void bareIsOffByDefaultAndTheClearLookDoesNotTurnItOn() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        InkLook clear = new InkLook();
        clear.tint = 0x1AE1E7F2;
        ClockWidgetView clock = new ClockWidgetView(activity, services, dayStyle());
        live(activity, clear, clock, null);
        assertFalse(clock.asksBare());
        assertFalse(clock.isBare());
        assertFalse(clock.style().bare);
        assertTrue("on the Look's glass", clock.isGlassOn());

        Bundle on = new Bundle();
        on.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
        WeatherWidgetView weather = new WeatherWidgetView(activity, services, dayStyle());
        weather.bind(on);
        assertFalse(weather.asksBare());
        weather.bind(new Bundle());
        assertFalse(weather.asksBare());
    }

    @Test
    public void bareInkHoldsTheCardsContrastAtTheWidgetBand() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        InkLook look = new InkLook();
        look.wallpaper = 0xFF606060;
        ClockWidgetView clock = new ClockWidgetView(activity, services, dayStyle());
        live(activity, look, clock, bare());
        assertTrue(clock.isBare());
        BuiltinWidgetStyle pale = dayStyle().onInk(true);
        for (int wallpaper : new int[] {0xFF101114, 0xFF606060, 0xFFB0B0B0, 0xFFFFFFFF}) {
            look.wallpaper = wallpaper;
            clock.onGlassStyle(look);
            OnGlass.Resolution answer = OnGlass.resolveBareWidget(wallpaper, pale.onSurfaceVariant,
                OnGlass.TARGET_BODY_TEXT, pale.primary, OnGlass.TARGET_LARGE_TEXT, pale.card, true);
            int text = OnGlass.bareInk(answer, pale.onSurfaceVariant, OnGlass.TARGET_BODY_TEXT, true);
            int graphic = OnGlass.bareInk(answer, pale.primary, OnGlass.TARGET_LARGE_TEXT, true);
            assertTrue(Integer.toHexString(wallpaper) + " text " + answer,
                OnGlass.ratio(text, answer.surface) >= OnGlass.TARGET_BODY_TEXT);
            assertTrue(Integer.toHexString(wallpaper) + " graphics " + answer,
                OnGlass.ratio(graphic, answer.surface) >= OnGlass.TARGET_LARGE_TEXT);
            assertTrue("never past 35%", Color.alpha(answer.veil) <= OnGlass.BARE_MAX_DIM_255);
        }
    }

    @Test
    public void theGlassRowOffersBareOnClockAndWeatherAndNothingOnGlance() {
        List<String> four = Arrays.asList("", "on", "off", "bare");
        assertEquals(four, Arrays.asList(BuiltinWidgetGlass.choices(BuiltinWidgetKind.CLOCK)));
        assertEquals(four, Arrays.asList(BuiltinWidgetGlass.choices(BuiltinWidgetKind.WEATHER)));
        assertEquals("Glance is always Bare: no Glass row", 0,
            BuiltinWidgetGlass.choices(BuiltinWidgetKind.GLANCE).length);
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            if (kind == BuiltinWidgetKind.CLOCK || kind == BuiltinWidgetKind.WEATHER
                || kind == BuiltinWidgetKind.GLANCE) continue;
            assertEquals(kind + " keeps Page, On, Off", 3,
                BuiltinWidgetGlass.choices(kind).length);
        }
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        BuiltinWidgetGlass.write(values, BuiltinWidgetGlass.BARE);
        assertEquals("bare", values.get(BuiltinWidgetGlass.CONFIG_KEY));
    }
}
