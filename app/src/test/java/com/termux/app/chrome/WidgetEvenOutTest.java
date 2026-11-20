package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.ColorMatrix;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Home widget glass wears the terminal's legibility correction at its own Opacity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetEvenOutTest {

    private static final Rect WHOLE = new Rect(0, 0, 1080, 2400);
    private static final Rect LEFT = new Rect(0, 200, 500, 1800);
    private static final Rect RIGHT = new Rect(560, 200, 1060, 1800);
    private static final int DARK = 0xFF202428;
    private static final int LIGHT = 0xFFF0F2F6;
    private static final int BODY = 0x66202020;
    private static final int VEIL = 0xFF101418;
    private static final int TEXT = 0xFFCCCCCC;
    private static final int GRAPHIC = 0xFF88AAFF;
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.adaptive = false;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.setWholePictureForTest(WHOLE);
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return colorFor(screenRect);
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                // The dark side needs no correction; the rest spans nearly black to near white.
                if (colorFor(screenRect) == DARK) {
                    return new GlassBackdropCache.Lightness(7d, 12d, 17d,
                        Hct.from(250d, 6d, 12d).toInt());
                }
                return new GlassBackdropCache.Lightness(5d, 50d, 95d,
                    Hct.from(250d, 6d, 50d).toInt());
            }
        });
        EvenOut.Ceiling[] ceilings = MaterialTerminalColorScheme.evenOutCeilings(
            RuntimeEnvironment.getApplication(), LEVEL);
        ink.setEvenOutPolicy(new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0], ceilings[1],
            ChromeInk.Polarity.PALE_INK));
    }

    private static int colorFor(@NonNull Rect rect) {
        if (rect.equals(WHOLE)) return LIGHT;  // the whole picture reads as the wide range
        return rect.centerX() < WHOLE.centerX() ? DARK : LIGHT;
    }

    private OnGlass.Resolution widget(@NonNull Rect rect, int opacity) {
        return ink.widget(rect, BODY, VEIL, TEXT, GRAPHIC, opacity);
    }

    private static double distance(@NonNull ColorMatrix m) {
        float[] a = m.getArray();
        float[] id = new ColorMatrix().getArray();
        double sum = 0d;
        for (int i = 0; i < a.length; i++) sum += Math.abs(a[i] - id[i]);
        return sum;
    }

    @Test
    public void noMatrixAtOpacityZero() {
        widget(RIGHT, 0);
        assertNull(ink.widgetEvenOutMatrix(RIGHT));
    }

    @Test
    public void matrixAtDefaultAndFullOpacity() {
        widget(RIGHT, 34);
        assertNotNull(ink.widgetEvenOutMatrix(RIGHT));
        widget(RIGHT, 100);
        assertNotNull(ink.widgetEvenOutMatrix(RIGHT));
    }

    @Test
    public void halfStrengthSitsBetween() {
        widget(RIGHT, 100);
        double full = distance(ink.widgetEvenOutMatrix(RIGHT));
        widget(RIGHT, 17);
        ColorMatrix half = ink.widgetEvenOutMatrix(RIGHT);
        assertNotNull(half);
        double d = distance(half);
        assertTrue(d > 0d && d < full);
    }

    @Test
    public void sameInstanceOnSecondAsk() {
        widget(RIGHT, 34);
        ColorMatrix first = ink.widgetEvenOutMatrix(RIGHT);
        widget(RIGHT, 34);
        assertSame(first, ink.widgetEvenOutMatrix(RIGHT));
    }

    @Test
    public void noTerminalStageNoMatrix() {
        ink.setEvenOutPolicy(null);
        widget(RIGHT, 100);
        assertNull(ink.widgetEvenOutMatrix(RIGHT));
    }

    @Test
    public void sharedWidgetsAgree() {
        widget(LEFT, 100);
        widget(RIGHT, 100);
        ColorMatrix left = ink.widgetEvenOutMatrix(LEFT);
        ColorMatrix right = ink.widgetEvenOutMatrix(RIGHT);
        assertNotNull(left);
        assertNotNull(right);
        assertArrayEquals(left.getArray(), right.getArray(), 0f);
    }

    @Test
    public void adaptiveWidgetsDiffer() {
        surfaces.adaptive = true;
        widget(LEFT, 100);
        widget(RIGHT, 100);
        ColorMatrix left = ink.widgetEvenOutMatrix(LEFT);
        ColorMatrix right = ink.widgetEvenOutMatrix(RIGHT);
        assertFalse(java.util.Arrays.equals(left == null ? null : left.getArray(),
            right == null ? null : right.getArray()));
    }

    @Test
    public void correctionReachesTheVeil() {
        OnGlass.Resolution plain = widget(RIGHT, 0);
        OnGlass.Resolution evened = widget(RIGHT, 100);
        assertNotNull(ink.widgetEvenOutMatrix(RIGHT));
        assertNotEquals(plain.veil, evened.veil);
    }
}
