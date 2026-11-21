package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.terminal.PaneGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Shared legibility (#297): with Adaptive legibility off every item reads the one measurement of
 * the whole picture, so two items at different rects over very different wallpaper get the same
 * answer. The same setup with it on gives different answers, which is what proves this can fail.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SharedLegibilityParityTest {

    private static final Rect WHOLE = new Rect(0, 0, 1080, 2400);
    private static final Rect LEFT = new Rect(0, 200, 500, 1800);
    private static final Rect RIGHT = new Rect(560, 200, 1060, 1800);
    private static final int DARK = 0xFF202428;
    private static final int LIGHT = 0xFFE4E6EA;
    private static final int MID = 0xFF808890;
    private static final int FG = 0xFFE6E1E5;
    private static final int VEIL = 0xFF101418;
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;
    private EvenOut.Ceiling[] ceilings;

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
                int color = colorFor(screenRect);
                double tone = color == DARK ? 12d : color == LIGHT ? 90d : 50d;
                return new GlassBackdropCache.Lightness(tone - 5d, tone, tone + 5d,
                    Hct.from(250d, 6d, tone).toInt());
            }
        });
        ceilings = MaterialTerminalColorScheme.evenOutCeilings(RuntimeEnvironment.getApplication(),
            LEVEL);
        ink.setEvenOutPolicy(new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0], ceilings[1],
            ChromeInk.Polarity.PALE_INK));
    }

    private static int colorFor(@NonNull Rect rect) {
        if (rect.equals(WHOLE)) return MID;
        return rect.centerX() < WHOLE.centerX() ? DARK : LIGHT;
    }

    private OnGlass.Resolution pane(@NonNull Rect rect, @NonNull Object key) {
        return ink.terminalPane(rect, key, Color.TRANSPARENT, VEIL, FG,
            PaneGlass.dimTerminalInk(FG));
    }

    private static void assertSameResolution(OnGlass.Resolution a, OnGlass.Resolution b) {
        assertEquals(a.ink, b.ink);
        assertEquals(a.veil, b.veil);
        assertEquals(a.surface, b.surface);
    }

    private static void assertDifferent(OnGlass.Resolution a, OnGlass.Resolution b) {
        assertFalse("answers should differ", a.ink == b.ink && a.veil == b.veil
            && a.surface == b.surface);
    }

    @Test
    public void sharedPanesAtDifferentRectsAgree() {
        Object first = new Object();
        Object second = new Object();
        OnGlass.Resolution left = pane(LEFT, first);
        int groundAlone = ink.terminalPaneGround(Color.TRANSPARENT);
        OnGlass.Resolution right = pane(RIGHT, second);
        assertSameResolution(left, right);
        ColorMatrix leftMatrix = ink.evenOutMatrix(LEFT, first);
        ColorMatrix rightMatrix = ink.evenOutMatrix(RIGHT, second);
        assertNotNull(leftMatrix);
        assertNotNull(rightMatrix);
        assertArrayEquals(leftMatrix.getArray(), rightMatrix.getArray(), 0f);
        assertEquals(groundAlone, ink.terminalPaneGround(Color.TRANSPARENT));
    }

    @Test
    public void adaptivePanesAtDifferentRectsDiffer() {
        surfaces.adaptive = true;
        Object first = new Object();
        Object second = new Object();
        OnGlass.Resolution left = pane(LEFT, first);
        OnGlass.Resolution right = pane(RIGHT, second);
        assertDifferent(left, right);
        ColorMatrix leftMatrix = ink.evenOutMatrix(LEFT, first);
        ColorMatrix rightMatrix = ink.evenOutMatrix(RIGHT, second);
        // An identity mapping is null, so a pane that needs none differs from one that does.
        assertFalse(java.util.Arrays.equals(
            leftMatrix == null ? null : leftMatrix.getArray(),
            rightMatrix == null ? null : rightMatrix.getArray()));
    }

    @Test
    public void sharedWidgetsAtDifferentRectsAgree() {
        OnGlass.Resolution left = ink.widget(LEFT, 0x66202020, VEIL, 0xFFCCCCCC, 0xFF88AAFF);
        OnGlass.Resolution right = ink.widget(RIGHT, 0x66202020, VEIL, 0xFFCCCCCC, 0xFF88AAFF);
        assertSameResolution(left, right);
        assertEquals(ink.widgetWallpaper(LEFT), ink.widgetWallpaper(RIGHT));
    }

    @Test
    public void adaptiveWidgetsAtDifferentRectsDiffer() {
        surfaces.adaptive = true;
        OnGlass.Resolution left = ink.widget(LEFT, 0x66202020, VEIL, 0xFFCCCCCC, 0xFF88AAFF);
        OnGlass.Resolution right = ink.widget(RIGHT, 0x66202020, VEIL, 0xFFCCCCCC, 0xFF88AAFF);
        assertDifferent(left, right);
        assertNotEquals(ink.widgetWallpaper(LEFT), ink.widgetWallpaper(RIGHT));
    }

    @Test
    public void sharedBareTextAtDifferentRectsGivesTheSameInk() {
        assertEquals(ink.wallpaperSample(LEFT), ink.wallpaperSample(RIGHT));
        assertNotEquals("it read something", Color.TRANSPARENT, ink.wallpaperSample(LEFT));
    }

    @Test
    public void adaptiveBareTextStillFollowsItsRect() {
        surfaces.adaptive = true;
        assertNotEquals(ink.wallpaperSample(LEFT), ink.wallpaperSample(RIGHT));
    }

    @Test
    public void sharedBandsAtDifferentRectsGiveTheSameInk() {
        OnGlass.Resolution left = ink.onGlass(GlassBackdropCache.Band.DOCK, LEFT,
            0xFF101010, 0xFFF0F0F0, OnGlass.TARGET_BODY_TEXT);
        OnGlass.Resolution right = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, RIGHT,
            0xFF101010, 0xFFF0F0F0, OnGlass.TARGET_BODY_TEXT);
        assertEquals(left.ink, right.ink);
        assertEquals(left.veil, right.veil);
    }

    @Test
    public void adaptiveBandsAtDifferentRectsDiffer() {
        surfaces.adaptive = true;
        OnGlass.Resolution left = ink.onGlass(GlassBackdropCache.Band.DOCK, LEFT,
            0xFF101010, 0xFFF0F0F0, OnGlass.TARGET_BODY_TEXT);
        OnGlass.Resolution right = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, RIGHT,
            0xFF101010, 0xFFF0F0F0, OnGlass.TARGET_BODY_TEXT);
        assertNotEquals(left.ink, right.ink);
    }

    @Test
    public void flippingAdaptiveMovesTheGenerationAndTheAnswers() {
        Object key = new Object();
        OnGlass.Resolution shared = pane(LEFT, key);
        int generation = ink.evenOutGeneration();
        surfaces.adaptive = true;
        OnGlass.Resolution adaptive = pane(LEFT, key);
        assertNotEquals(generation, ink.evenOutGeneration());
        assertDifferent(shared, adaptive);
    }
}
