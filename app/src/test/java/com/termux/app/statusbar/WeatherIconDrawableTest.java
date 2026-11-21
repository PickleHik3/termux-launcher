package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.theme.SchemeTone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The two-layer drawable on stand-in layers that record the tint they are given and whether they
 * were drawn, so the test needs no resources and no rendering.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class WeatherIconDrawableTest {
    private static final int INK = 0xFF112233;
    private static final int ACCENT = 0xFFAA5500;

    private static final class Recording extends Drawable {
        final int res;
        int tint;
        int draws;

        Recording(int res) { this.res = res; }

        @Override public void setTint(int tintColor) { tint = tintColor; }
        @Override public void draw(@NonNull Canvas canvas) { draws++; }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private final List<Recording> loaded = new ArrayList<>();

    private WeatherIconDrawable drawable() {
        return new WeatherIconDrawable(res -> {
            Recording layer = new Recording(res);
            loaded.add(layer);
            return layer;
        });
    }

    private static Recording rec(Drawable layer) { return (Recording) layer; }

    @Test
    public void theBaseTakesTheInkAndTheAccentTakesTheAccent() {
        WeatherIconDrawable drawable = drawable();
        WeatherIcons.Icon icon = WeatherIcons.forCode(61, true);
        drawable.set(icon, INK, ACCENT);
        assertEquals(icon.base, rec(drawable.baseLayer()).res);
        assertEquals(INK, rec(drawable.baseLayer()).tint);
        assertEquals(icon.accent, rec(drawable.accentLayer()).res);
        assertEquals(WeatherIconDrawable.legibleAccent(ACCENT, drawable.inkTint()),
            rec(drawable.accentLayer()).tint);
        assertEquals(INK, drawable.inkTint());
        assertEquals(WeatherIconDrawable.legibleAccent(ACCENT, INK), drawable.accentTint());
    }

    @Test
    public void aFlipOfTheInkRetintsWithoutReloadingTheLayers() {
        WeatherIconDrawable drawable = drawable();
        WeatherIcons.Icon icon = WeatherIcons.forCode(95, false);
        drawable.set(icon, INK, ACCENT);
        Drawable base = drawable.baseLayer();
        int loads = loaded.size();
        drawable.set(icon, 0xFFEEEEEE, ACCENT);
        assertSame(base, drawable.baseLayer());
        assertEquals(loads, loaded.size());
        assertEquals(0xFFEEEEEE, rec(drawable.baseLayer()).tint);
        assertEquals(WeatherIconDrawable.legibleAccent(ACCENT, drawable.inkTint()),
            rec(drawable.accentLayer()).tint);
    }

    @Test
    public void aMissingLayerIsSkippedAndTheOthersAreDrawn() {
        WeatherIconDrawable drawable = drawable();
        drawable.set(WeatherIcons.CLEAR_DAY, INK, ACCENT);
        assertNull(drawable.baseLayer());
        assertEquals(WeatherIconDrawable.legibleAccent(ACCENT, drawable.inkTint()),
            rec(drawable.accentLayer()).tint);
        drawable.set(WeatherIcons.OVERCAST, INK, ACCENT);
        assertNull(drawable.accentLayer());
        assertEquals(INK, rec(drawable.baseLayer()).tint);
    }

    @Test
    public void bothLayersAreDrawnWhenTheIconHasBoth() {
        WeatherIconDrawable drawable = drawable();
        drawable.set(WeatherIcons.forCode(80, true), INK, ACCENT);
        drawable.draw(new Canvas());
        assertEquals(1, rec(drawable.baseLayer()).draws);
        assertEquals(1, rec(drawable.accentLayer()).draws);
    }

    @Test
    public void aViewsOwnSingleTintCannotFlattenTheTwoLayers() {
        WeatherIconDrawable drawable = drawable();
        drawable.set(WeatherIcons.forCode(80, true), INK, ACCENT);
        drawable.setTintList(ColorStateList.valueOf(0xFF00FF00));
        assertEquals(INK, rec(drawable.baseLayer()).tint);
        assertEquals(WeatherIconDrawable.legibleAccent(ACCENT, drawable.inkTint()),
            rec(drawable.accentLayer()).tint);
        assertTrue(drawable.getOpacity() == PixelFormat.TRANSLUCENT);
    }

    private static final int DARK_PURPLE = 0xFF4A3B7C;
    private static final int LIGHT_PEACH = 0xFFF2D9C4;

    /** The crescent and the "24" as the emulator drew them over a teal/blue wallpaper. */
    private static final int CRESCENT = 0xFF67548E;
    private static final int NEAR_WHITE_INK = 0xFFDED3EC;

    @Test
    public void theEmulatorsDimCrescentBesideItsNearWhiteInkIsClearlyLighter() {
        int out = WeatherIconDrawable.legibleAccent(CRESCENT, NEAR_WHITE_INK);
        assertTrue(String.format("%08X", out), SchemeTone.tone(out) >= 79d);
        assertTrue(SchemeTone.tone(CRESCENT) < 50d);
        assertHueKept(CRESCENT, out);
    }

    @Test
    public void aDarkAccentBesideLightInkIsRaisedToTheLightTone() {
        int out = WeatherIconDrawable.legibleAccent(DARK_PURPLE, 0xFFF5F5F5);
        assertTrue(SchemeTone.tone(out) >= WeatherIconDrawable.ACCENT_TONE_ON_LIGHT_INK - 1d);
        assertHueKept(DARK_PURPLE, out);
    }

    @Test
    public void aLightAccentBesideDarkInkIsLoweredToTheDarkTone() {
        int out = WeatherIconDrawable.legibleAccent(LIGHT_PEACH, 0xFF101010);
        assertTrue(SchemeTone.tone(out) <= WeatherIconDrawable.ACCENT_TONE_ON_DARK_INK + 1d);
        assertHueKept(LIGHT_PEACH, out);
    }

    @Test
    public void anAccentAlreadyOnTheInksSideIsReturnedUnchanged() {
        assertEquals(0xFFFFD180, WeatherIconDrawable.legibleAccent(0xFFFFD180, 0xFFF5F5F5));
        assertEquals(0xFF5E35B1, WeatherIconDrawable.legibleAccent(0xFF5E35B1, 0xFF101010));
    }

    private static void assertHueKept(int before, int after) {
        double gap = Math.abs(Hct.fromInt(after).getHue() - Hct.fromInt(before).getHue());
        assertTrue("hue " + gap, Math.min(gap, 360 - gap) < 25);
    }

    @Test
    public void theDrawnAccentIsTheLegibleOneAndKeepsItsAlpha() {
        WeatherIconDrawable drawable = drawable();
        drawable.set(WeatherIcons.CLEAR_NIGHT, 0xFFF5F5F5, DARK_PURPLE);
        assertEquals(WeatherIconDrawable.legibleAccent(DARK_PURPLE, 0xFFF5F5F5),
            rec(drawable.accentLayer()).tint);
        assertEquals(0x80, WeatherIconDrawable.legibleAccent(0x804A3B7C, 0xFFF5F5F5) >>> 24);
    }
}
