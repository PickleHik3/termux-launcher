package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What the terminal's text stands on, as the palette's ink is chosen from it (glass tuning
 * 2026-10-17): the pane band's ground is the face the panes are drawn with now — their tint at
 * the opacity, Shade and Look the user has — on a pale face as on a dark one, with or without a
 * slab, and it follows every opacity change rather than the glass a pane once stood on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalPaneGroundTest {

    /** The light theme's overlay base: what a light terminal's tint is mixed from. */
    private static final int LIGHT_BASE = 0xFFF7F2FA;
    /** A dark wallpaper, blurred, the case where pale glass and pale ink used to meet. */
    private static final int DARK_WALLPAPER = 0xFF202226;
    /** A dark palette's default foreground, the ink a pale face must not keep. */
    private static final int TERMINAL_FG = 0xFFE6E1E5;

    private static final Rect PANE_BEFORE = new Rect(0, 200, 1080, 1800);
    private static final Rect PANE_NOW = new Rect(0, 200, 1080, 1100);

    private ChromeInk ink;
    private int groundMoves;

    @Before
    public void setUp() {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = LIGHT_BASE;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(rect -> DARK_WALLPAPER);
        ink.setOnTerminalPaneGroundChanged(() -> groundMoves++);
    }

    private void measure(@NonNull Rect rect, int tint) {
        ink.terminalPane(rect, tint, LIGHT_BASE, TERMINAL_FG, PaneGlass.dimTerminalInk(TERMINAL_FG));
    }

    /** True when dark ink beats pale ink on {@code ground} by the margin that flips a polarity. */
    private static boolean darkInkWins(int ground) {
        return OnGlass.ratio(Color.BLACK, ground)
            > OnGlass.ratio(Color.WHITE, ground) * ChromeInk.POLARITY_FLIP_MARGIN;
    }

    @Test
    public void aPaleFaceAtFullOpacityIsAPaleGround() {
        int opaque = OnGlass.withAlpha(LIGHT_BASE, 255);
        measure(PANE_NOW, opaque);
        int ground = ink.terminalPaneGround(opaque);
        assertEquals("opacity 100 is the tint itself", OnGlass.opaque(LIGHT_BASE), ground);
        assertTrue("dark ink on a white face", darkInkWins(ground));
    }

    /**
     * Clear's 10% taken to 100 in the editor, after the pane has stood at another rect (the
     * keyboard down, the editor closed): the ground is the live pane's white face, not a mean
     * with the rect the pane has left.
     */
    @Test
    public void anOpacityChangeReChoosesTheGround() {
        int clear = OnGlass.withAlpha(LIGHT_BASE, 26);
        measure(PANE_BEFORE, clear);
        measure(PANE_NOW, clear);
        int thin = ink.terminalPaneGround(clear);
        assertFalse("pale ink on thin glass over a dark wallpaper", darkInkWins(thin));
        int moves = groundMoves;

        int opaque = OnGlass.withAlpha(LIGHT_BASE, 255);
        measure(PANE_NOW, opaque);
        int ground = ink.terminalPaneGround(opaque);
        assertTrue("the palette is told", groundMoves > moves);
        assertEquals("the live pane's face alone", OnGlass.opaque(LIGHT_BASE), ground);
        assertTrue(darkInkWins(ground));

        // And back: the ground follows the slider down as well.
        measure(PANE_NOW, clear);
        assertEquals(thin, ink.terminalPaneGround(clear));
    }

    /** Two live panes under one tint are both counted. */
    @Test
    public void everyLivePaneUnderTheTintVotes() {
        int tint = OnGlass.withAlpha(LIGHT_BASE, 128);
        measure(PANE_BEFORE, tint);
        measure(PANE_NOW, tint);
        assertEquals(OnGlass.backdrop(DARK_WALLPAPER, Color.TRANSPARENT, tint),
            ink.terminalPaneGround(tint));
    }

    /**
     * Solid wears no slab (no blur, no grain), so nothing used to measure its panes: the ground
     * stayed on whatever the last slab measured, and pale ink stood on Solid's near-white face.
     * The dress pass asks about a slab-less terminal pane too.
     */
    @Test
    public void aPaneWithNoSlabIsMeasuredByItsDressPass() {
        ink.seedPaneGround(DARK_WALLPAPER); // the Look before, or the last run
        int solid = OnGlass.withAlpha(LIGHT_BASE, 235); // Solid's 92
        Style style = new Style(solid);
        FrameLayout frame = new FrameLayout(RuntimeEnvironment.getApplication());
        frame.layout(0, 200, 1080, 1100);
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());

        assertFalse("no slab", PaneGlass.apply(style, frame, backdrop, 0f, true));
        assertEquals("asked once, for the pane's rect", 1, style.asked);
        int ground = ink.terminalPaneGround(solid);
        assertEquals(OnGlass.backdrop(DARK_WALLPAPER, Color.TRANSPARENT, solid), ground);
        assertTrue("dark ink on Solid's light face", darkInkWins(ground));

        // A wall page's frame is not the terminal's and is never asked.
        Style page = new Style(solid);
        PaneGlass.apply(page, frame, backdrop, 0f, false);
        assertEquals(0, page.asked);
    }

    /** A terminal pane's style whose glass is off, answering its veil through the ink. */
    private final class Style implements PaneSurfaceStyle {
        final int tint;
        int asked;

        Style(int tint) {
            this.tint = tint;
        }

        @Override public boolean isPaneGlassActive() { return false; }
        @Nullable @Override public Bitmap paneGlassBlurFrame() { return null; }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(); }
        @Nullable @Override public ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return tint; }
        @Nullable @Override public Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneGapDp() { return 0; }

        @Override public int paneGlassVeil(@NonNull Rect rootRect) {
            asked++;
            return ink.terminalPane(rootRect, tint, LIGHT_BASE, TERMINAL_FG,
                PaneGlass.dimTerminalInk(TERMINAL_FG)).veil;
        }
    }
}
