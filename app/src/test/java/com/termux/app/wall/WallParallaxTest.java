package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/**
 * Where the wallpaper sits under the wall. The places keep fixed positions on the picture, the
 * pan follows the wall's own offset continuously — through a drag, the settle that follows it and
 * a wrap round the ring — and a picture that is only one screen wide never moves at all.
 */
public class WallParallaxTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2412;
    /** Half a screen: what a 1.5x picker crop leaves beyond one screen. */
    private static final int SPARE = 540;
    private static final float EPS = 0.01f;

    private static List<PaneWallPage> ring() {
        return PaneWallPolicy.availablePages(false, true, true);
    }

    private static List<PaneWallPage> terminalAndDisplay() {
        return PaneWallPolicy.availablePages(false, false, true);
    }

    // ---- span: which pictures pan ---------------------------------------------------------

    @Test
    public void thePickerCropsOneAndAHalfScreens() {
        assertEquals(1620, WallParallax.pickerWidthPx(WIDTH));
    }

    @Test
    public void aWidePictureSpansItsWholeWidthAtScreenHeight() {
        assertEquals(1620, WallParallax.spanPx(1620, HEIGHT, WIDTH, HEIGHT));
        // The same picture stored smaller still spans by its aspect, not its pixels.
        assertEquals(1620, WallParallax.spanPx(810, 1206, WIDTH, HEIGHT));
    }

    @Test
    public void aLegacyOneScreenPictureSpansOneScreenAndSoNeverPans() {
        assertEquals(WIDTH, WallParallax.spanPx(1080, 2412, WIDTH, HEIGHT));
        // A few pixels either side, from an aspect measured against a different decor, is no pan.
        assertEquals(WIDTH, WallParallax.spanPx(1100, 2412, WIDTH, HEIGHT));
        assertEquals(WIDTH, WallParallax.spanPx(1060, 2412, WIDTH, HEIGHT));
        assertEquals(0f, WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, 300f, WIDTH,
            WallParallax.spanPx(1080, 2412, WIDTH, HEIGHT) - WIDTH), 0f);
    }

    @Test
    public void nothingWiderThanThePickerCropIsEverSpanned() {
        assertEquals(1620, WallParallax.spanPx(4000, 2412, WIDTH, HEIGHT));
    }

    @Test
    public void anUnreadablePictureSpansOneScreen() {
        assertEquals(WIDTH, WallParallax.spanPx(0, 0, WIDTH, HEIGHT));
    }

    // ---- rest positions -------------------------------------------------------------------

    @Test
    public void eachPlaceRestsAtItsOwnPositionOnThePicture() {
        assertEquals(0f, WallParallax.offsetPx(ring(), PaneWallPage.WIDGETS, 0f, WIDTH, SPARE), EPS);
        assertEquals(270f, WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, 0f, WIDTH, SPARE), EPS);
        assertEquals(540f, WallParallax.offsetPx(ring(), PaneWallPage.DISPLAY, 0f, WIDTH, SPARE), EPS);
    }

    @Test
    public void positionsAreFixedPerPlaceNotPerSlotOnTheWall() {
        // Without a Home page the Terminal still sits over the centre — the part the system was
        // handed — and Display over the right edge.
        assertEquals(270f, WallParallax.offsetPx(terminalAndDisplay(), PaneWallPage.TERMINAL, 0f,
            WIDTH, SPARE), EPS);
        assertEquals(540f, WallParallax.offsetPx(terminalAndDisplay(), PaneWallPage.DISPLAY, 0f,
            WIDTH, SPARE), EPS);
        assertEquals(270f, WallParallax.offsetPx(List.of(PaneWallPage.TERMINAL),
            PaneWallPage.TERMINAL, 0f, WIDTH, SPARE), EPS);
    }

    // ---- motion ---------------------------------------------------------------------------

    @Test
    public void aDragPansAQuarterScreenPerPlaceStep() {
        // Pages to the right of their rest: the place on the left (Home) is coming in.
        assertEquals(270f - 135f, WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, 540f,
            WIDTH, SPARE), EPS);
        // Pages to the left: Display is coming in.
        assertEquals(270f + 135f, WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, -540f,
            WIDTH, SPARE), EPS);
        assertEquals(0f, WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, 1080f, WIDTH, SPARE), EPS);
    }

    @Test
    public void aCommittedSlideContinuesFromWhereTheDragLeftOff() {
        // A drag of 0.4 widths from Terminal towards Display...
        float dragging = WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, -432f, WIDTH, SPARE);
        // ...commits: the wall re-bases the offset against Display (offset += +1 width).
        float sliding = WallParallax.offsetPx(ring(), PaneWallPage.DISPLAY, -432f + 1080f, WIDTH, SPARE);
        assertEquals(dragging, sliding, EPS);
        assertEquals(270f + 0.4f * 270f, sliding, EPS);
    }

    @Test
    public void aRingWrapSweepsThePictureAcrossItsWholeSpan() {
        // Past Display comes Home: dragging on from Display (pages moving left) heads for the
        // picture's left edge, all the way across the span rather than a quarter of it.
        assertEquals(540f, WallParallax.offsetPx(ring(), PaneWallPage.DISPLAY, 0f, WIDTH, SPARE), EPS);
        assertEquals(270f, WallParallax.offsetPx(ring(), PaneWallPage.DISPLAY, -540f, WIDTH, SPARE), EPS);
        assertEquals(0f, WallParallax.offsetPx(ring(), PaneWallPage.DISPLAY, -1080f, WIDTH, SPARE), EPS);
        // And the other way round: from Home, the place on the left is Display.
        assertEquals(270f, WallParallax.offsetPx(ring(), PaneWallPage.WIDGETS, 540f, WIDTH, SPARE), EPS);
    }

    @Test
    public void aRingWrapHasNoJumpWhenTheWallCommits() {
        // 0.3 widths into the wrap from Display towards Home...
        float dragging = WallParallax.offsetPx(ring(), PaneWallPage.DISPLAY, -324f, WIDTH, SPARE);
        // ...the wall commits to Home, one step to the right on the ring: offset += +1 width.
        float sliding = WallParallax.offsetPx(ring(), PaneWallPage.WIDGETS, -324f + 1080f, WIDTH, SPARE);
        assertEquals(dragging, sliding, EPS);
        assertEquals(540f * 0.7f, sliding, EPS);
    }

    @Test
    public void aTwoPlaceWallHasNoWrapAndTheOuterEdgeHoldsStill() {
        // Terminal and Display only: dragging from Terminal towards the missing Home resists,
        // and the picture stays put since there is no place coming in.
        assertEquals(270f, WallParallax.offsetPx(terminalAndDisplay(), PaneWallPage.TERMINAL, 300f,
            WIDTH, SPARE), EPS);
        // Towards Display it pans as on the full wall.
        assertEquals(270f + 135f, WallParallax.offsetPx(terminalAndDisplay(), PaneWallPage.TERMINAL,
            -540f, WIDTH, SPARE), EPS);
        // Past Display there is nothing, so the right edge holds.
        assertEquals(540f, WallParallax.offsetPx(terminalAndDisplay(), PaneWallPage.DISPLAY, -300f,
            WIDTH, SPARE), EPS);
    }

    @Test
    public void noSpareMeansNoMotionWhateverTheWallDoes() {
        assertEquals(0f, WallParallax.offsetPx(ring(), PaneWallPage.DISPLAY, -700f, WIDTH, 0), 0f);
        assertEquals(0f, WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, 0f, WIDTH, 0), 0f);
    }

    @Test
    public void anUnmeasuredWallRestsAtThePlacesPosition() {
        assertEquals(270f, WallParallax.offsetPx(ring(), PaneWallPage.TERMINAL, 100f, 0, SPARE), EPS);
    }
}
