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
        assertEquals(ACCENT, rec(drawable.accentLayer()).tint);
        assertEquals(INK, drawable.inkTint());
        assertEquals(ACCENT, drawable.accentTint());
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
        assertEquals(ACCENT, rec(drawable.accentLayer()).tint);
    }

    @Test
    public void aMissingLayerIsSkippedAndTheOthersAreDrawn() {
        WeatherIconDrawable drawable = drawable();
        drawable.set(WeatherIcons.CLEAR_DAY, INK, ACCENT);
        assertNull(drawable.baseLayer());
        assertEquals(ACCENT, rec(drawable.accentLayer()).tint);
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
        assertEquals(ACCENT, rec(drawable.accentLayer()).tint);
        assertTrue(drawable.getOpacity() == PixelFormat.TRANSLUCENT);
    }
}
