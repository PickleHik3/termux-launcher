package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
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
    private static final Rect DOCK_RECT = new Rect(0, 120, 1080, 196);
    /** Two Home widgets side by side, each its own rect of the widget band. */
    private static final Rect WIDGET_A = new Rect(40, 300, 500, 700);
    private static final Rect WIDGET_B = new Rect(580, 300, 1040, 700);

    /** A day scheme's widget card, its body-and-label ink; the dark card's and its ink. */
    private static final int DAY_CARD = 0xFFECEEF4;
    private static final int DAY_CARD_TEXT = 0xFF44474E;
    private static final int DARK_CARD = 0xFF1D2024;
    private static final int DARK_CARD_TEXT = 0xFFC4C6D0;
    /** The Look's day glass at Base's opacity 30, the slab under a glass widget. */
    private static final int DAY_TINT_30 = 0x4DE1E7F2;

    /** A sampler that answers per rect and remembers what it was asked. */
    private static final class Wallpaper implements GlassBackdropCache.Sampler {
        int status = DARK_WALLPAPER;
        int paneTop = LIGHT_WALLPAPER;
        int paneBottom = DARK_WALLPAPER;
        int keyboard = LIGHT_WALLPAPER;
        int other = DARK_WALLPAPER;
        int widgetA = DARK_WALLPAPER;
        int widgetB = LIGHT_WALLPAPER;
        @NonNull final List<Rect> asked = new ArrayList<>();

        @Override
        public int sampleWallpaper(@NonNull Rect rect) {
            asked.add(new Rect(rect));
            if (rect.equals(WIDGET_A)) return widgetA;
            if (rect.equals(WIDGET_B)) return widgetB;
            if (rect.equals(STATUS_RECT)) return status;
            if (rect.equals(PANE_TOP)) return paneTop;
            if (rect.equals(PANE_BOTTOM)) return paneBottom;
            // The keyboard is measured row by row too: every row of it is the same wallpaper.
            if (KEYBOARD_RECT.contains(rect)) return keyboard;
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
        assertEquals(2, ink.paneAnswerCountForTests());
    }

    /** No surface buys a veil: a pane over a light wallpaper is the bare glass, its ink moves. */
    @Test
    public void aTerminalPaneOverALightWallpaperIsNeverVeiled() {
        assertEquals(0, android.graphics.Color.alpha(pane(PANE_TOP).veil));
        assertEquals(0, android.graphics.Color.alpha(pane(PANE_BOTTOM).veil));
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

    /**
     * The docked, non-capsule keyboard stands on the unified dock glass and draws no glass of its
     * own; its host wears the band's veil alone. Over a light sample that layer exists, and it is
     * exactly the band's veil.
     */
    @Test
    public void theDockedKeyboardWearsAVeilOnlyLayerOverALightWallpaper() {
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        int cap = OnGlass.withAlpha(0xFF2B2930, 96);
        ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, TERMINAL_FG, TERMINAL_FG,
            cap, OnGlass.TARGET_BODY_TEXT);

        android.graphics.drawable.Drawable veil = glass.veilOnlyLayer(
            GlassBackdropCache.Band.KEYBOARD, 0.35f, 0f, 0.8f, false);

        assertTrue("a light wallpaper under the docked keyboard is veiled", veil != null);
        int alpha = Color.alpha(ink.bandVeil(GlassBackdropCache.Band.KEYBOARD));
        assertTrue("the layer is the band's own veil: " + alpha, alpha > 0);
        assertTrue(veil instanceof android.graphics.drawable.GradientDrawable);

        wallpaper.keyboard = DARK_WALLPAPER;
        ink.invalidate();
        ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, TERMINAL_FG, TERMINAL_FG,
            cap, OnGlass.TARGET_BODY_TEXT);
        assertTrue("and a dark one needs none",
            glass.veilOnlyLayer(GlassBackdropCache.Band.KEYBOARD, 0.35f, 0f, 0.8f, false) == null);
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
    public void theLegibilityLevelScalesBandTargetsWhileThePaneKeepsItsCeiling() {
        LegibilityLevel[] levels = {LegibilityLevel.SOFTER, LegibilityLevel.DEFAULT,
            LegibilityLevel.HARDER};
        double[] body = {3.0d, 4.5d, 7.0d};
        int ceiling = Math.round(255f * (77f / 255f + 0.15f));
        for (int i = 0; i < levels.length; i++) {
            surfaces.legibility = levels[i];
            OnGlass.Resolution status = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR,
                STATUS_RECT, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
            assertEquals(body[i], status.target, 1e-9);
            assertTrue(levels[i] + ": " + status, status.ratio >= body[i] || status.shortfall);

            // The pane never spends more than its ceiling, whatever the level asks for
            // (the level itself now only ever reads DEFAULT from preferences).
            OnGlass.Resolution top = pane(PANE_TOP);
            assertTrue(levels[i] + " pane within the ceiling: " + top,
                Color.alpha(top.veil) <= ceiling);
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

    // ------------------------------------------------------------------ the widget band

    /** A glass widget's body: the slab tint and the body's lift of the card over it. */
    private static int glassBody(int card) {
        int lift = OnGlass.withAlpha(card, OnGlass.widgetLiftAlpha255(Color.alpha(DAY_TINT_30)));
        return OnGlass.composite(lift, DAY_TINT_30);
    }

    private OnGlass.Resolution dayGlassWidget(@NonNull Rect rect) {
        return ink.widget(rect, glassBody(DAY_CARD), DAY_CARD, DAY_CARD_TEXT, LIGHT_INK);
    }

    /**
     * The Scratchpad defect: dark ink on day glass over a dark wallpaper. The widget band veils
     * the glass in the card's colour until body text holds 4.5:1 and graphics 3:1.
     */
    @Test
    public void aDayGlassWidgetOverADarkWallpaperIsVeiledUntilItsTextReads() {
        OnGlass.Resolution answer = dayGlassWidget(WIDGET_A);
        assertTrue("veiled: " + answer, Color.alpha(answer.veil) > 0);
        assertEquals(DAY_CARD, OnGlass.opaque(answer.veil));
        assertTrue(answer.toString(),
            OnGlass.ratio(DAY_CARD_TEXT, answer.surface) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(answer.toString(),
            OnGlass.ratio(LIGHT_INK, answer.surface) >= OnGlass.TARGET_LARGE_TEXT);

        OnGlass.Resolution light = dayGlassWidget(WIDGET_B);
        assertTrue("the same card over a light wallpaper needs none: " + light, light.isBare());
    }

    /** Pale ink on its dark card's glass over the dark wallpaper reads as the glass is. */
    @Test
    public void aDarkCardWidgetOverADarkWallpaperIsBare() {
        OnGlass.Resolution answer = ink.widget(WIDGET_A, glassBody(DARK_CARD), DARK_CARD,
            DARK_CARD_TEXT, NIGHT_INK);
        assertTrue(answer.toString(), answer.isBare());
        assertTrue(OnGlass.ratio(DARK_CARD_TEXT, answer.surface) >= OnGlass.TARGET_BODY_TEXT);
    }

    /** The solid card is opaque: nothing behind it matters to its ink, and no veil is spent. */
    @Test
    public void aSolidCardWidgetIsBareOnEveryWallpaper() {
        for (int behind : new int[] {Color.WHITE, Color.BLACK, 0xFF7A6E66}) {
            wallpaper.widgetA = behind;
            ink.invalidate();
            OnGlass.Resolution answer = ink.widget(WIDGET_A, DAY_CARD, DAY_CARD, DAY_CARD_TEXT,
                LIGHT_INK);
            assertTrue(answer.toString(), answer.isBare() && !answer.shortfall);
            assertEquals(DAY_CARD, answer.surface);
        }
    }

    /**
     * One rect per widget, like the terminal pane: each is sampled once and remembered on its own
     * rect, so the dress passes that ask again read no pixels and search nothing.
     */
    @Test
    public void widgetsAreMemoisedPerRect() {
        OnGlass.Resolution a = dayGlassWidget(WIDGET_A);
        OnGlass.Resolution b = dayGlassWidget(WIDGET_B);
        int reads = wallpaper.asked.size();
        for (int pass = 0; pass < 3; pass++) {
            assertSame(a, dayGlassWidget(WIDGET_A));
            assertSame(b, dayGlassWidget(WIDGET_B));
            ink.widgetWallpaper(WIDGET_A);
        }
        assertEquals("no widget re-sampled", reads, wallpaper.asked.size());
        assertEquals(2, ink.widgetAnswerCountForTests());
    }

    /** A new wallpaper drops every widget's answer, and the next ask follows it. */
    @Test
    public void aNewWallpaperReSamplesTheWidgets() {
        assertTrue(Color.alpha(dayGlassWidget(WIDGET_A).veil) > 0);
        ink.invalidate();
        assertEquals(0, ink.widgetAnswerCountForTests());
        wallpaper.widgetA = Color.WHITE;
        assertTrue("the widget follows the new wallpaper", dayGlassWidget(WIDGET_A).isBare());
    }

    /** A widget asked before its wallpaper is readable is answered nominally and asks again. */
    @Test
    public void anUnreadableWidgetIsAskedAgain() {
        wallpaper.widgetA = GlassBackdropCache.UNREADABLE;
        dayGlassWidget(WIDGET_A);
        int reads = wallpaper.asked.size();
        wallpaper.widgetA = DARK_WALLPAPER;
        OnGlass.Resolution answer = dayGlassWidget(WIDGET_A);
        assertEquals("read again", reads + 1, wallpaper.asked.size());
        assertTrue("and answered on what it read", Color.alpha(answer.veil) > 0);
    }

    /**
     * Widgets take the dock's polarity and cast no vote: a widget over a white wallpaper does not
     * pull a dock drawn in pale ink over to the dark one, and widgets alone decide nothing.
     */
    @Test
    public void theWidgetBandSharesTheDocksPolarityAndCastsNoVote() {
        wallpaper.widgetA = Color.WHITE;
        wallpaper.widgetB = Color.WHITE;
        dayGlassWidget(WIDGET_A);
        dayGlassWidget(WIDGET_B);
        assertEquals("widgets alone leave the default", ChromeInk.Polarity.DARK_INK,
            ink.polarity());

        ink.onGlass(GlassBackdropCache.Band.DOCK, DOCK_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        assertEquals("the dock over a dark wallpaper", ChromeInk.Polarity.PALE_INK,
            ink.polarity());
        ink.invalidate();
        ink.onGlass(GlassBackdropCache.Band.DOCK, DOCK_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        dayGlassWidget(WIDGET_A);
        dayGlassWidget(WIDGET_B);
        assertEquals("two white widgets do not flip it", ChromeInk.Polarity.PALE_INK,
            ink.polarity());
    }

    /** The one legibility control holds widgets too: body text at 7.0 at Harder. */
    @Test
    public void theLegibilityLevelHoldsWidgets() {
        surfaces.legibility = LegibilityLevel.HARDER;
        OnGlass.Resolution answer = dayGlassWidget(WIDGET_A);
        assertTrue(answer.toString(), OnGlass.ratio(DAY_CARD_TEXT, answer.surface)
            >= LegibilityLevel.HARDER.target(OnGlass.TARGET_BODY_TEXT));
        assertTrue(answer.toString(), OnGlass.ratio(LIGHT_INK, answer.surface)
            >= LegibilityLevel.HARDER.target(OnGlass.TARGET_LARGE_TEXT));
    }

    /** What the card's rim is judged against: the widget's wallpaper under the launcher's dim. */
    @Test
    public void theWidgetsWallpaperIsTheBackdropUnderTheDim() {
        surfaces.dim = 0x66000000;
        assertEquals(OnGlass.backdrop(LIGHT_WALLPAPER, 0x66000000, Color.TRANSPARENT),
            ink.widgetWallpaper(WIDGET_B));
    }
}
