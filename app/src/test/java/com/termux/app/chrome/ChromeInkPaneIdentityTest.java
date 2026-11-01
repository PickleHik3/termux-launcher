package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.terminal.PaneGlass;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Each terminal pane keeps one answer in {@link ChromeInk}, under the pane's own key: a float over
 * a tiled pane no longer evicts it (pong 2026-11-01, 126 measurements in six seconds of a float
 * drag), and a pane laid out at a new rect replaces only its own answer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeInkPaneIdentityTest {

    private static final Rect TILED = new Rect(24, 221, 1056, 2224);
    private static final Rect FLOAT = new Rect(200, 600, 840, 1631);
    private static final Rect FLOAT_MOVED = new Rect(230, 640, 870, 1671);
    private static final Rect LEFT = new Rect(0, 0, 500, 2000);
    private static final Rect RIGHT = new Rect(504, 0, 1000, 2000);
    private static final Rect RIGHT_DRAGGED = new Rect(604, 0, 1000, 2000);
    private static final int FG = 0xFFE6E1E5;
    private static final int TINT = 0x75213F5F;

    private ChromeInk ink;
    private int samples;
    private final Object tiled = new Object();
    private final Object floating = new Object();

    @Before
    public void setUp() {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = Color.TRANSPARENT;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                samples++;
                return screenRect.left >= 200 ? 0xFF202020 : 0xFFB7C9E9;
            }
        });
    }

    private OnGlass.Resolution ask(Rect rect, Object key) {
        return ink.terminalPane(rect, key, TINT, 0xFF101418, FG, PaneGlass.dimTerminalInk(FG));
    }

    @Test
    public void aFloatOverATiledPaneDoesNotEvictItsAnswer() {
        OnGlass.Resolution first = ask(TILED, tiled);
        ask(FLOAT, floating);
        int samplesBefore = samples;
        int generation = ink.evenOutGeneration();
        assertSame(first, ask(TILED, tiled));
        assertEquals("served from its answer", samplesBefore, samples);
        assertEquals(generation, ink.evenOutGeneration());
        assertEquals(2, ink.paneAnswerCountForTests());
    }

    @Test
    public void theSamePaneAtANewRectReplacesItsOwnAnswerOnce() {
        ask(TILED, tiled);
        ask(FLOAT, floating);
        int generation = ink.evenOutGeneration();
        ask(FLOAT_MOVED, floating);
        assertEquals(2, ink.paneAnswerCountForTests());
        assertEquals(generation + 1, ink.evenOutGeneration());
        ask(FLOAT_MOVED, floating);
        assertEquals(generation + 1, ink.evenOutGeneration());
    }

    @Test
    public void aLonePaneResizedKeepsOneAnswer() {
        ask(TILED, tiled);
        int generation = ink.evenOutGeneration();
        ask(new Rect(24, 221, 1056, 1500), tiled);
        assertEquals(1, ink.paneAnswerCountForTests());
        assertEquals(generation + 1, ink.evenOutGeneration());
    }

    @Test
    public void aDividerMoveKeepsTwoAnswers() {
        Object left = new Object();
        Object right = new Object();
        ask(LEFT, left);
        ask(RIGHT, right);
        ask(RIGHT_DRAGGED, right);
        ask(new Rect(0, 0, 600, 2000), left);
        assertEquals(2, ink.paneAnswerCountForTests());
    }

    @Test
    public void aForgottenPaneNoLongerCounts() {
        ask(TILED, tiled);
        ask(FLOAT, floating);
        ink.forgetPane(floating);
        assertEquals(1, ink.paneAnswerCountForTests());
        ink.forgetPane(floating);
        assertEquals(1, ink.paneAnswerCountForTests());
    }

    @Test
    public void aParkedPaneKeepsItsAnswerAndComesBackWithoutAMeasure() {
        OnGlass.Resolution first = ask(FLOAT, floating);
        ask(TILED, tiled);
        int both = ink.terminalPaneSurface(TINT);
        ink.parkPane(floating, true);
        assertEquals(1, ink.paneAnswerCountForTests());
        int tiledAlone = ink.terminalPaneSurface(TINT);
        org.junit.Assert.assertNotEquals("the parked pane is out of the mean", both, tiledAlone);
        int samplesBefore = samples;
        int generation = ink.evenOutGeneration();
        ink.parkPane(floating, false);
        assertEquals(2, ink.paneAnswerCountForTests());
        assertEquals(both, ink.terminalPaneSurface(TINT));
        assertSame(first, ask(FLOAT, floating));
        assertEquals(samplesBefore, samples);
        assertEquals(generation, ink.evenOutGeneration());
    }

    @Test
    public void aClosedPaneIsGoneEvenWhileParked() {
        ask(TILED, tiled);
        ask(FLOAT, floating);
        ink.parkPane(floating, true);
        ink.forgetPane(floating);
        ink.parkPane(floating, false);
        assertEquals(1, ink.paneAnswerCountForTests());
    }
}
