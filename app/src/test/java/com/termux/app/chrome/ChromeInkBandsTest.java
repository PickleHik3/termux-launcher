package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.terminal.PaneGlass;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Every surface a band (appearance-layout-editor SPEC §2): the terminal pane and the keyboard are
 * veiled until their own ink reads, the one legibility control moves every target, and a root the
 * editor has scaled is sampled where its wallpaper really is.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeInkBandsTest {

    /** The Material dark neutral: the terminal's background and the glass base in dark mode. */
    private static final int NIGHT_BASE = 0xFF1C1B1F;
    /** A dark palette's default foreground. */
    private static final int TERMINAL_FG = 0xFFE6E1E5;
    /** {@code termux_primary}, light and night: the chrome's two inks. */
    private static final int LIGHT_INK = 0xFF345CA8;
    private static final int NIGHT_INK = 0xFFB8C7FF;
    /** A light-topped wallpaper, blurred: the case the spec was written for. */
    private static final int LIGHT_WALLPAPER = 0xFFE8E4DA;
    /** A dark one. */
    private static final int DARK_WALLPAPER = 0xFF202226;
    /** The terminal at 30% opacity, the kind of setting that lets a light wallpaper wash it out. */
    private static final int TERMINAL_TINT_30 = OnGlass.withAlpha(NIGHT_BASE, 77);

    private static final Rect STATUS_RECT = new Rect(0, 0, 1080, 96);
    private static final Rect PANE_TOP = new Rect(0, 200, 1080, 1100);
    private static final Rect PANE_BOTTOM = new Rect(0, 1110, 1080, 1800);
    private static final Rect KEYBOARD_RECT = new Rect(0, 1800, 1080, 2400);

    /** A sampler that answers per rect and remembers what it was asked. */
    private static final class Wallpaper implements GlassBackdropCache.Sampler {
        int status = DARK_WALLPAPER;
        int paneTop = LIGHT_WALLPAPER;
        int paneBottom = DARK_WALLPAPER;
        int keyboard = LIGHT_WALLPAPER;
        int other = DARK_WALLPAPER;
        @NonNull final List<Rect> asked = new ArrayList<>();

        @Override
        public int sampleWallpaper(@NonNull Rect rect) {
            asked.add(new Rect(rect));
            if (rect.equals(STATUS_RECT)) return status;
            if (rect.equals(PANE_TOP)) return paneTop;
            if (rect.equals(PANE_BOTTOM)) return paneBottom;
            if (rect.equals(KEYBOARD_RECT)) return keyboard;
            return other;
        }
    }

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;
    private Wallpaper wallpaper;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = NIGHT_BASE;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        wallpaper = new Wallpaper();
        ink.backdrops().setSampler(wallpaper);
    }

    private OnGlass.Resolution pane(@NonNull Rect rect) {
        return ink.terminalPane(rect, TERMINAL_TINT_30, NIGHT_BASE, TERMINAL_FG,
            PaneGlass.dimTerminalInk(TERMINAL_FG));
    }

    // ------------------------------------------------------------------ the terminal pane

    /**
     * A light-topped wallpaper under a thin dark terminal: the palette's pale foreground cannot
     * read on it bare, so the pane buys a veil of its own background, and the foreground itself
     * does not move.
     */
    @Test
    public void aTerminalPaneOverALightWallpaperIsVeiledUntilItsForegroundReads() {
        OnGlass.Resolution top = pane(PANE_TOP);

        int bare = OnGlass.backdrop(LIGHT_WALLPAPER, Color.TRANSPARENT, TERMINAL_TINT_30);
        assertTrue("bare, the dim foreground misses: "
                + OnGlass.ratio(PaneGlass.dimTerminalInk(TERMINAL_FG), bare),
            OnGlass.ratio(PaneGlass.dimTerminalInk(TERMINAL_FG), bare) < OnGlass.TARGET_BODY_TEXT);
        assertTrue("so the pane is veiled", Color.alpha(top.veil) > 0);
        assertEquals("toward the terminal's own background", NIGHT_BASE,
            OnGlass.opaque(top.veil));
        assertTrue("and both inks read on what is drawn",
            OnGlass.ratio(TERMINAL_FG, top.surface) >= OnGlass.TARGET_BODY_TEXT
                && OnGlass.ratio(PaneGlass.dimTerminalInk(TERMINAL_FG), top.surface)
                    >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("the ink is the palette's, never a re-tone",
            top.ink == TERMINAL_FG || top.ink == PaneGlass.dimTerminalInk(TERMINAL_FG));
    }

    /** A dark wallpaper under the same pane needs nothing: the veil follows the wallpaper. */
    @Test
    public void aTerminalPaneOverADarkWallpaperStaysBare() {
        OnGlass.Resolution bottom = pane(PANE_BOTTOM);
        assertTrue(bottom.isBare());
    }

    /**
     * A split window is two rects of one band. Each is sampled once and remembered on its own
     * rect, so asking again for both — as every chrome pass does — reads no pixels.
     */
    @Test
    public void splitPanesAreMemoisedPerRect() {
        OnGlass.Resolution top = pane(PANE_TOP);
        OnGlass.Resolution bottom = pane(PANE_BOTTOM);
        int reads = wallpaper.asked.size();

        for (int pass = 0; pass < 3; pass++) {
            assertEquals(top.veil, pane(PANE_TOP).veil);
            assertEquals(bottom.veil, pane(PANE_BOTTOM).veil);
        }
        assertEquals("no pane re-sampled the other's wallpaper", reads, wallpaper.asked.size());
        assertNotEquals("and each pane has its own answer", top.veil, bottom.veil);
        assertEquals(2, ink.paneAnswerCountForTests());
    }

    /** A pane that moves re-samples; an invalidate (new wallpaper, palette, mode) drops them all. */
    @Test
    public void aMovedPaneOrANewWallpaperReSamples() {
        pane(PANE_TOP);
        int reads = wallpaper.asked.size();
        pane(new Rect(0, 200, 1080, 1000));
        assertEquals(reads + 1, wallpaper.asked.size());

        ink.invalidate();
        assertEquals(0, ink.paneAnswerCountForTests());
        wallpaper.paneTop = DARK_WALLPAPER;
        assertTrue("the pane follows the new wallpaper", pane(PANE_TOP).isBare());
    }

    // ------------------------------------------------------------------ the keyboard

    /**
     * Key labels stand on translucent caps over the host. Over a light wallpaper the pale label
     * misses on a half-clear dark cap, so the host is veiled; the label and the cap stay the
     * theme's.
     */
    @Test
    public void theKeyboardHostIsVeiledSoLabelsReadOnTheirCaps() {
        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f);
        int cap = OnGlass.withAlpha(0xFF2B2930, 96);
        OnGlass.Resolution keys = ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT,
            TERMINAL_FG, TERMINAL_FG, cap, OnGlass.TARGET_BODY_TEXT);

        assertTrue("the host is veiled", Color.alpha(keys.veil) > 0);
        assertEquals("with the glass's own base colour", NIGHT_BASE, OnGlass.opaque(keys.veil));
        assertEquals("the label is untouched", TERMINAL_FG, keys.ink);
        assertTrue("and reads on the cap over the veiled host: " + keys.ratio,
            keys.ratio >= OnGlass.TARGET_BODY_TEXT && !keys.shortfall);
        assertEquals("the surface builder draws exactly that veil", keys.veil,
            ink.bandVeil(GlassBackdropCache.Band.KEYBOARD));
    }

    /** An opaque cap hides the host: nothing under it can help, so nothing is spent. */
    @Test
    public void anOpaqueKeyCapNeedsNoVeil() {
        OnGlass.Resolution keys = ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT,
            TERMINAL_FG, TERMINAL_FG, 0xFF2B2930, OnGlass.TARGET_BODY_TEXT);
        assertTrue(keys.isBare());
        assertEquals(Color.TRANSPARENT, ink.bandVeil(GlassBackdropCache.Band.KEYBOARD));
    }

    // ------------------------------------------------------------------ the coherence rule

    /**
     * The new bands vote. A dark status strip alone puts the chrome in its pale ink; a terminal
     * pane over a bright wallpaper with no tint of its own is most of the screen, and moves the
     * mean far enough past the flip margin that the whole chrome takes the dark ink.
     */
    @Test
    public void theTerminalPaneVotesInTheChromesPolarity() {
        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        assertEquals(ChromeInk.Polarity.PALE_INK, ink.polarity());

        wallpaper.paneTop = Color.WHITE;
        ink.terminalPane(PANE_TOP, Color.TRANSPARENT, NIGHT_BASE, TERMINAL_FG, TERMINAL_FG);
        assertEquals(ChromeInk.Polarity.DARK_INK, ink.polarity());
    }

    /** And so does the keyboard. */
    @Test
    public void theKeyboardVotesInTheChromesPolarity() {
        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        assertEquals(ChromeInk.Polarity.PALE_INK, ink.polarity());

        wallpaper.keyboard = Color.WHITE;
        ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, TERMINAL_FG,
            TERMINAL_FG, 0xFF2B2930, OnGlass.TARGET_BODY_TEXT);
        assertEquals(ChromeInk.Polarity.DARK_INK, ink.polarity());
    }

    // ------------------------------------------------------------------ the legibility control

    /** Softer / Default / Harder hold body text at 3.0 / 4.5 / 7.0 on every band. */
    @Test
    public void theLegibilityControlMultipliesEveryBandsTarget() {
        LegibilityLevel[] levels = {LegibilityLevel.SOFTER, LegibilityLevel.DEFAULT,
            LegibilityLevel.HARDER};
        double[] body = {3.0d, 4.5d, 7.0d};
        int previousVeil = -1;
        for (int i = 0; i < levels.length; i++) {
            surfaces.legibility = levels[i];
            OnGlass.Resolution status = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR,
                STATUS_RECT, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
            assertEquals(body[i], status.target, 1e-9);
            assertTrue(levels[i] + ": " + status, status.ratio >= body[i] || status.shortfall);

            OnGlass.Resolution top = pane(PANE_TOP);
            assertEquals(body[i], top.target, 1e-9);
            // At Harder the dim foreground of this palette cannot reach 7.0 even on its own
            // opaque background (5.9:1), so the pane goes as far as it can: the opaque ground.
            assertTrue(levels[i] + " pane: " + top, top.ratio >= body[i]
                || (top.shortfall && Color.alpha(top.veil) == 255));
            assertTrue("a harder level spends more veil",
                Color.alpha(top.veil) > previousVeil);
            previousVeil = Color.alpha(top.veil);
        }
    }

    /** A second ink on a band is held to the level too, at its own scaled tier. */
    @Test
    public void inkOnABandIsHeldToTheLevel() {
        surfaces.legibility = LegibilityLevel.HARDER;
        OnGlass.Resolution status = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        int glyph = ink.inkOn(status, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.ratio(glyph, status.surface)
            >= LegibilityLevel.HARDER.target(OnGlass.TARGET_LARGE_TEXT));
    }

    // ------------------------------------------------------------------ root space

    /**
     * The editor scales the root to 0.76 about its centre. A band that is on screen at its scaled
     * rect is sampled at its unscaled one — the rect the wallpaper frame is captured in.
     */
    @Test
    public void mapIntoRootUndoesTheRootsScaleAndTranslation() {
        float scale = 0.76f;
        float pivotX = 540f;
        float pivotY = 1200f;
        float tx = 12f;
        float ty = -30f;
        Rect local = new Rect(0, 1800, 1080, 2400);
        Rect onScreen = new Rect(
            Math.round(pivotX + scale * (local.left - pivotX) + tx),
            Math.round(pivotY + scale * (local.top - pivotY) + ty),
            Math.round(pivotX + scale * (local.right - pivotX) + tx),
            Math.round(pivotY + scale * (local.bottom - pivotY) + ty));
        Rect out = new Rect();
        ChromeInk.mapIntoRoot(onScreen, 0f, 0f, scale, scale, pivotX, pivotY, tx, ty, out);
        assertTrue("within a pixel of the unscaled rect: " + out,
            Math.abs(out.left - local.left) <= 1 && Math.abs(out.top - local.top) <= 1
                && Math.abs(out.right - local.right) <= 1
                && Math.abs(out.bottom - local.bottom) <= 1);

        ChromeInk.mapIntoRoot(local, 0f, 0f, 1f, 1f, pivotX, pivotY, 0f, 0f, out);
        assertEquals("an untransformed root maps nothing", local, out);
    }

    /** Through the real root view: a band on a half-scaled root is asked about at full size. */
    @Test
    public void aBandOnAScaledRootIsSampledAtItsUnscaledRect() {
        FrameLayout parent = new FrameLayout(RuntimeEnvironment.getApplication());
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        parent.addView(root, new FrameLayout.LayoutParams(1080, 2400));
        parent.measure(0, 0);
        parent.layout(0, 0, 1080, 2400);
        root.layout(0, 0, 1080, 2400);
        root.setPivotX(540f);
        root.setPivotY(1200f);
        root.setScaleX(0.5f);
        root.setScaleY(0.5f);
        surfaces.views.put(R.id.terminal_root_container, root);

        // STATUS_RECT (0,0)-(1080,96) drawn at half size about the centre.
        Rect onScreen = new Rect(270, 600, 810, 648);
        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, onScreen, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);

        assertTrue("the sampler was asked for the unscaled rect: " + wallpaper.asked,
            wallpaper.asked.contains(STATUS_RECT));
        assertTrue("and never for the scaled one", !wallpaper.asked.contains(onScreen));
    }
}
