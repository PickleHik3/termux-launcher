package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The structural half of the light-mode contrast bug, as arithmetic.
 *
 * <p>Where {@code OnGlassTest} states what the user could not read, this states what they could not
 * <em>find</em>: the rim around the drawer plane, the cards in the app grid, the shell of a pinned
 * folder, the row under the finger in a menu, the ring saying which icon a tap will launch. Every
 * backdrop here is one of the two the reporting device was measured at — {@code #6A5755} behind the
 * status bar, {@code #657271} behind the A&ndash;Z strip — or the glass base those bands veil
 * toward.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeShadeTest {

    /** Measured behind the status bar in light mode, on the reporting device. */
    private static final int MEASURED_STATUS = 0xFF6A5755;
    /** Measured behind the A&ndash;Z strip in light mode, on the reporting device. */
    private static final int MEASURED_AZ = 0xFF657271;
    /** {@code termux_surface_panel_high}, light: the glass base a light-mode band veils toward. */
    private static final int LIGHT_GLASS = 0xFFE1E7F2;
    /** {@code termux_surface_panel_high}, night. */
    private static final int NIGHT_GLASS = 0xFF202837;

    /** The glass rim's base stroke as it was authored, and the drawer's card wash and stroke. */
    private static final int RIM_SEED = 0x3DFFFFFF;
    private static final int TILE_FILL_SEED = 0x0EFFFFFF;
    private static final int TILE_FILL_PRESSED_SEED = 0x1CFFFFFF;
    private static final int TILE_STROKE_SEED = 0x21FFFFFF;
    private static final int FOLDER_FILL_SEED = 0x26FFFFFF;
    private static final int FOLDER_STROKE_SEED = 0x33FFFFFF;

    @After
    public void clearSnapshot() {
        ChromeShade.clear();
    }

    // ------------------------------------------------------------------ the snapshot

    /** Nothing has measured the chrome: every constant comes back exactly as it was authored. */
    @Test
    public void unmeasuredChromeDrawsWhatItAlwaysDrew() {
        ChromeShade.clear();
        assertEquals(RIM_SEED, ChromeShade.rim(RIM_SEED));
        assertEquals(TILE_FILL_SEED, ChromeShade.fill(TILE_FILL_SEED));
        assertEquals(TILE_FILL_PRESSED_SEED,
            ChromeShade.stateFill(TILE_FILL_PRESSED_SEED, TILE_FILL_SEED));
        assertEquals(0xB8000000, ChromeShade.plate(0xB8000000, 0xB8FFFFFF));
    }

    /** The bootstrap: the mode's own glass says which way the chrome is standing. */
    @Test
    public void nominalGlassDecidesThePolarityBeforeAnyBandIsMeasured() {
        assertEquals(ChromeInk.Polarity.DARK_INK, ChromeShade.polarityOf(LIGHT_GLASS));
        assertEquals(ChromeInk.Polarity.PALE_INK, ChromeShade.polarityOf(NIGHT_GLASS));
    }

    // ------------------------------------------------------------------ A · the glass rim

    /**
     * The rim on a dark backdrop is left alone, and it did not need help: white light at 24% over
     * the night glass already separates past the decoration floor.
     */
    @Test
    public void rimOnDarkBackdropIsUntouchedAndAlreadyReads() {
        ChromeShade.note(ChromeInk.Polarity.PALE_INK, NIGHT_GLASS);
        int rim = ChromeShade.rim(RIM_SEED);
        assertEquals(RIM_SEED, rim);
        assertTrue("white rim over the night glass separates by "
                + ChromeShade.separation(rim, NIGHT_GLASS),
            ChromeShade.separation(rim, NIGHT_GLASS) >= ChromeShade.TARGET_RIM);
    }

    /**
     * The same rim on the light band. As authored it separates by 1.26 — the containing edge of the
     * drawer plane, the dock capsule and every anchored menu, invisible. Restated it clears 2.0.
     */
    @Test
    public void rimOnLightBackdropBecomesShadowAndReaches() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        double before = ChromeShade.separation(RIM_SEED, LIGHT_GLASS);
        int rim = ChromeShade.rim(RIM_SEED);
        double after = ChromeShade.separation(rim, LIGHT_GLASS);
        assertTrue("the authored white rim already read: " + before, before < 1.35d);
        assertTrue("restated rim separates by " + after, after >= ChromeShade.TARGET_RIM);
        assertEquals(Color.BLACK, rim | 0xFF000000);
        assertTrue(Color.alpha(rim) <= ChromeShade.MAX_STRUCTURAL_ALPHA);
    }

    /**
     * One reference is enough for a rim. Resolved against the nominal glass, the same rim still
     * reads on the bands the reporting device was actually measured at once phase 1's veil is over
     * them — those land a little darker than the nominal glass, so the answer comes in a little
     * under 2.0 rather than a lot, which is the slack a single reference buys and the reason
     * structure does not need the per-band sampling text does.
     */
    @Test
    public void theRimAnswerHoldsOnTheMeasuredBandsToo() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int rim = ChromeShade.rim(RIM_SEED);
        for (int backdrop : new int[] {MEASURED_STATUS, MEASURED_AZ}) {
            int veiled = OnGlass.opaque(OnGlass.composite(
                OnGlass.withAlpha(LIGHT_GLASS, OnGlass.MAX_VEIL_ALPHA_255), backdrop));
            assertTrue("rim over the veiled #" + Integer.toHexString(backdrop) + " separates by "
                    + ChromeShade.separation(rim, veiled),
                ChromeShade.separation(rim, veiled) >= 1.75d);
            assertTrue("the authored white rim read better there: "
                    + ChromeShade.separation(RIM_SEED, veiled),
                ChromeShade.separation(rim, veiled) > ChromeShade.separation(RIM_SEED, veiled));
        }
    }

    /** A tinted rim keeps its hue — that hue is the focus indicator — and only gains alpha. */
    @Test
    public void aTintedRimKeepsItsHue() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int seed = 0xB0345CA8;   // termux_primary at the tinted base strength
        int tinted = ChromeShade.tinted(seed, ChromeShade.TARGET_RIM);
        assertEquals(seed & 0x00FFFFFF, tinted & 0x00FFFFFF);
        assertTrue(Color.alpha(tinted) >= Color.alpha(seed));
        assertTrue(ChromeShade.separation(tinted, LIGHT_GLASS) >= ChromeShade.TARGET_RIM);
    }

    // ------------------------------------------------------------------ B · the drawer's cards

    /** The category card's wash and stroke, the grid's whole structure. */
    @Test
    public void drawerCategoryTileFindsItsFillAndStroke() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        double fillBefore = ChromeShade.separation(TILE_FILL_SEED, LIGHT_GLASS);
        double strokeBefore = ChromeShade.separation(TILE_STROKE_SEED, LIGHT_GLASS);
        assertTrue("the authored 5.5% wash already read: " + fillBefore, fillBefore < 1.06d);
        assertTrue("the authored 13% stroke already read: " + strokeBefore, strokeBefore < 1.16d);

        int fill = ChromeShade.fill(TILE_FILL_SEED);
        int stroke = ChromeShade.rim(TILE_STROKE_SEED);
        assertTrue("card wash separates by " + ChromeShade.separation(fill, LIGHT_GLASS),
            ChromeShade.separation(fill, LIGHT_GLASS) >= ChromeShade.TARGET_FILL);
        assertTrue("card stroke separates by " + ChromeShade.separation(stroke, LIGHT_GLASS),
            ChromeShade.separation(stroke, LIGHT_GLASS) >= ChromeShade.TARGET_RIM);
        // The stroke still reads as the card's edge against the card's own wash.
        assertTrue(Color.alpha(stroke) > Color.alpha(fill));
    }

    /** A pressed card has to stop looking like the card beside it. */
    @Test
    public void aPressedCardSeparatesFromItsRestState() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int rest = ChromeShade.fill(TILE_FILL_SEED);
        int pressed = ChromeShade.stateFill(TILE_FILL_PRESSED_SEED, TILE_FILL_SEED);
        assertNotEquals(rest, pressed);
        assertTrue("press separates from rest by "
                + ChromeShade.separation(pressed, rest, LIGHT_GLASS),
            ChromeShade.separation(pressed, rest, LIGHT_GLASS) >= ChromeShade.TARGET_STATE);
    }

    /** Dark mode keeps the mock exactly: not one of the drawer's washes moves. */
    @Test
    public void darkModeKeepsEveryAuthoredWash() {
        ChromeShade.note(ChromeInk.Polarity.PALE_INK, NIGHT_GLASS);
        assertEquals(TILE_FILL_SEED, ChromeShade.fill(TILE_FILL_SEED));
        assertEquals(TILE_STROKE_SEED, ChromeShade.rim(TILE_STROKE_SEED));
        assertEquals(TILE_FILL_PRESSED_SEED,
            ChromeShade.stateFill(TILE_FILL_PRESSED_SEED, TILE_FILL_SEED));
        assertEquals(RIM_SEED, ChromeShade.rim(RIM_SEED));
    }

    // ------------------------------------------------------------------ C · the dock

    /** The pinned folder's shell: the disc that says these icons are one thing. */
    @Test
    public void theDockFolderShellReads() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int fill = ChromeShade.fill(FOLDER_FILL_SEED);
        int stroke = ChromeShade.rim(FOLDER_STROKE_SEED);
        assertTrue("folder shell separates by " + ChromeShade.separation(fill, LIGHT_GLASS),
            ChromeShade.separation(fill, LIGHT_GLASS) >= ChromeShade.TARGET_FILL);
        assertTrue("folder shell stroke separates by "
                + ChromeShade.separation(stroke, LIGHT_GLASS),
            ChromeShade.separation(stroke, LIGHT_GLASS) >= ChromeShade.TARGET_RIM);
    }

    /** The overflow badge is a plate, so it flips whole and its text follows the plate. */
    @Test
    public void theOverflowBadgeFlipsWithItsText() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int plate = ChromeShade.plate(0xB8000000, 0xB8FFFFFF);
        assertEquals(0xB8FFFFFF, plate);
        int ink = ChromeShade.onPlate(plate, LIGHT_GLASS, OnGlass.TARGET_LARGE_TEXT);
        int surface = OnGlass.opaque(OnGlass.composite(plate, LIGHT_GLASS));
        assertTrue("badge text on its plate reads at " + OnGlass.ratio(ink, surface),
            OnGlass.ratio(ink, surface) >= OnGlass.TARGET_LARGE_TEXT);

        ChromeShade.note(ChromeInk.Polarity.PALE_INK, NIGHT_GLASS);
        assertEquals(0xB8000000, ChromeShade.plate(0xB8000000, 0xB8FFFFFF));
    }

    // ------------------------------------------------------------------ D · the dark panels

    /**
     * The widget picker's sheet and the pane menu: panels, so they flip whole and everything on
     * them is read off the panel. A dark sheet in a light theme is legible but foreign, and its
     * hint, field wash and rim were all tuned to it, so they move together or not at all.
     */
    @Test
    public void aDarkPanelFlipsWholeAndItsContentsFollowIt() {
        final int darkPanel = 0xEE202124;
        final int lightPanel = 0xEEF8F9FA;
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int plate = ChromeShade.plate(darkPanel, lightPanel);
        assertEquals(lightPanel, plate);
        int surface = ChromeShade.plateSurface(plate, LIGHT_GLASS);
        int ink = ChromeShade.onPlate(plate, LIGHT_GLASS, OnGlass.TARGET_BODY_TEXT);
        assertTrue("panel text reads at " + OnGlass.ratio(ink, surface),
            OnGlass.ratio(ink, surface) >= OnGlass.TARGET_BODY_TEXT);
        // The hint is the same ink at 60%, and still has to be readable against the panel.
        int hint = (ink & 0x00FFFFFF) | (0x99 << 24);
        assertTrue("panel hint separates by " + ChromeShade.separation(hint, surface),
            ChromeShade.separation(hint, surface) >= OnGlass.TARGET_LARGE_TEXT);
        // The search field's wash is measured against the panel it is cut into, not the chrome.
        int wash = ChromeShade.inPlate(0x1AFFFFFF, surface, ChromeShade.TARGET_FILL);
        assertTrue("field wash separates by " + ChromeShade.separation(wash, surface),
            ChromeShade.separation(wash, surface) >= ChromeShade.TARGET_FILL);
    }

    /**
     * A panel carries its own polarity: a dark sheet keeps its white washes even when the chrome
     * around it has gone dark-inked. Nothing inside a card is decided by the band outside it.
     */
    @Test
    public void aPanelsOwnWashesFollowThePanelNotTheChrome() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int darkSurface = ChromeShade.plateSurface(0xEE202124, LIGHT_GLASS);
        assertEquals(0x1AFFFFFF,
            ChromeShade.inPlate(0x1AFFFFFF, darkSurface, ChromeShade.TARGET_FILL));
    }

    // ------------------------------------------------------------------ F · the scrollbar

    /**
     * The scrollbar thumb, which cannot have a mode-qualified answer: it is drawn over the
     * terminal, and the terminal's background comes from the colour scheme rather than the theme,
     * so a light background in dark mode is an ordinary choice. 40% white was invisible on any
     * light one; the mid grey that replaced it is found on both.
     */
    @Test
    public void theScrollbarThumbIsFoundOnALightAndADarkTerminal() {
        final int asShipped = 0x66FFFFFF;
        final int restated = 0xB3808080;          // launcher_scheme_scrollbar / termux_scrollbar
        final int lightTerminal = 0xFFF4F6FB;     // termux_surface_base, light
        final int darkTerminal = 0xFF0F141B;      // termux_surface_base, night
        assertTrue("40% white on a light terminal separated by "
                + ChromeShade.separation(asShipped, lightTerminal),
            ChromeShade.separation(asShipped, lightTerminal) < 1.1d);
        for (int terminal : new int[] {lightTerminal, darkTerminal}) {
            assertTrue("the thumb on #" + Integer.toHexString(terminal) + " separates by "
                    + ChromeShade.separation(restated, terminal),
                ChromeShade.separation(restated, terminal) >= OnGlass.TARGET_DECORATION);
        }
    }

    // ------------------------------------------------------------------ bounds

    /** No structural constant is ever allowed to become a drawn slab. */
    @Test
    public void nothingIsEverPushedPastTheCap() {
        // A surface where black can never reach the rim target: black itself.
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, Color.BLACK);
        assertEquals(ChromeShade.MAX_STRUCTURAL_ALPHA, Color.alpha(ChromeShade.rim(RIM_SEED)));
        assertEquals(ChromeShade.MAX_STRUCTURAL_ALPHA, Color.alpha(ChromeShade.fill(0x0EFFFFFF)));
    }

    /**
     * A constant already strong enough keeps its authored strength rather than being inflated, and
     * a constant authored above the cap is not pulled back down to it either — the cap bounds what
     * the arithmetic may spend, not what the mock may ask for.
     */
    @Test
    public void anAlreadyStrongConstantIsNotInflated() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        assertEquals(0xC8, Color.alpha(ChromeShade.rim(0xC8FFFFFF)));
        assertEquals(0x4A, Color.alpha(ChromeShade.rim(RIM_SEED)));
    }
}
