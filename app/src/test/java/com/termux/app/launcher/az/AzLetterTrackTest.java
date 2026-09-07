package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The letters' slots, which a row and a column share: centres, hit-testing and the hysteresis. */
public class AzLetterTrackTest {

    private static final int LETTERS = 27;

    @Test
    public void slotsDivideTheTrackEvenlyAndCentresSitInTheMiddleOfThem() {
        assertEquals(40f, AzLetterTrack.slotSizePx(1080f, 27), 0.001f);
        assertEquals(20f, AzLetterTrack.centerPx(0, 1080f, 27), 0.001f);
        assertEquals(60f, AzLetterTrack.centerPx(1, 1080f, 27), 0.001f);
        assertEquals(1060f, AzLetterTrack.centerPx(26, 1080f, 27), 0.001f);
    }

    @Test
    public void aZeroLengthTrackStillHasASlotToDrawInto() {
        assertEquals(1f / 27f, AzLetterTrack.slotSizePx(0f, 27), 0.001f);
        assertEquals(0, AzLetterTrack.indexAt(0f, 0f, 27));
    }

    @Test
    public void theIndexClampsToTheEndsOfTheTrack() {
        assertEquals(0, AzLetterTrack.indexAt(-50f, 1080f, LETTERS));
        assertEquals(0, AzLetterTrack.indexAt(0f, 1080f, LETTERS));
        assertEquals(1, AzLetterTrack.indexAt(40f, 1080f, LETTERS));
        assertEquals(26, AzLetterTrack.indexAt(1079f, 1080f, LETTERS));
        assertEquals(26, AzLetterTrack.indexAt(5000f, 1080f, LETTERS));
    }

    @Test
    public void aTrackKnowsNothingAboutWhichAxisItIs() {
        // The same fraction of the bar picks the same letter down a 900px column as along a 900px
        // row, which is what makes one letter-index math enough for both orientations.
        assertEquals(AzLetterTrack.indexAt(450f, 900f, LETTERS),
            AzLetterTrack.indexAt(450f, 900f, LETTERS));
        assertEquals(13, AzLetterTrack.indexAt(450f, 900f, LETTERS));
        assertEquals(AzLetterTrack.centerPx(13, 900f, LETTERS),
            AzLetterTrack.centerPx(13, 900f, LETTERS), 0.001f);
    }

    @Test
    public void theFirstSampleOfAGestureTakesTheRawAnswer() {
        assertEquals(5, AzLetterTrack.indexWithHysteresis(210f, -1, 1080f, LETTERS, true));
        assertEquals(5, AzLetterTrack.indexWithHysteresis(210f, 12, 1080f, LETTERS, false));
    }

    @Test
    public void aNeighbourIsTakenOnlyOnceTheFingerIsClearOfTheBoundary() {
        // 40px slots, so the boundary between 5 and 6 is at 240px and the hysteresis is 8.8px.
        assertEquals(5, AzLetterTrack.indexWithHysteresis(242f, 5, 1080f, LETTERS, true));
        assertEquals(6, AzLetterTrack.indexWithHysteresis(250f, 5, 1080f, LETTERS, true));
        // Coming back the other way, the same boundary holds the finger to 6 until it is clear.
        assertEquals(6, AzLetterTrack.indexWithHysteresis(238f, 6, 1080f, LETTERS, true));
        assertEquals(5, AzLetterTrack.indexWithHysteresis(230f, 6, 1080f, LETTERS, true));
    }

    @Test
    public void aJumpOfMoreThanOneSlotIsTakenWhole() {
        assertEquals(20, AzLetterTrack.indexWithHysteresis(820f, 5, 1080f, LETTERS, true));
    }

    @Test
    public void holdingTheSameSlotIsSteady() {
        assertEquals(5, AzLetterTrack.indexWithHysteresis(215f, 5, 1080f, LETTERS, true));
    }
}
