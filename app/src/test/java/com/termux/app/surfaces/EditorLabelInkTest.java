package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EditorLabelInkTest {
    private static final int DARK = 0xFF1B1B1F;
    private static final int PALE = 0xFFE3E2E6;

    @Test
    public void aLightPictureGetsDarkInkWhateverTheTheme() {
        assertEquals(DARK, EditorLabelInk.pick(0xFFF3EFE6, DARK, PALE));
    }

    @Test
    public void aDarkPictureGetsPaleInk() {
        assertEquals(PALE, EditorLabelInk.pick(0xFF202020, DARK, PALE));
    }

    @Test
    public void aLightPictureWithNoDimKeepsDarkInk() {
        int[] px = {0xFFF3EFE6, 0xFFEDE8DC};
        assertEquals(DARK, EditorLabelInk.pick(EditorLabelInk.average(px, 2, 0), DARK, PALE));
    }

    @Test
    public void theDimIsLaidOverTheAverage() {
        int[] px = {0xFFFFFFFF, 0xFFFFFFFF};
        assertEquals(0xFFFFFFFF, EditorLabelInk.average(px, 2, 0x00000000));
        assertEquals(PALE, EditorLabelInk.pick(EditorLabelInk.average(px, 2, 0xE6000000), DARK, PALE));
    }

    /** A picture dark in its top half and light in its bottom half, shaped like a phone. */
    private static int[] splitPicture() {
        int w = EditorLabelInk.SAMPLE_WIDTH;
        int h = EditorLabelInk.sampleHeight(1080, 2400);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                px[y * w + x] = y < h / 2 ? 0xFF202020 : 0xFFF3EFE6;
        return px;
    }

    @Test
    public void theLabelReadsThePictureAtItsOwnHeight() {
        int h = EditorLabelInk.sampleHeight(1080, 2400);
        assertEquals(36, h);
        int[] px = splitPicture();
        int w = EditorLabelInk.SAMPLE_WIDTH;
        // The label over the card, two thirds down the screen: the light half, so dark ink in
        // either theme, since the theme is not asked.
        assertEquals(DARK, EditorLabelInk.pick(EditorLabelInk.band(px, w, h, 0.66f), DARK, PALE));
        assertEquals(PALE, EditorLabelInk.pick(EditorLabelInk.band(px, w, h, 0.2f), DARK, PALE));
        // Out of range is held to the picture's edges.
        assertEquals(DARK, EditorLabelInk.pick(EditorLabelInk.band(px, w, h, 1.4f), DARK, PALE));
        assertEquals(PALE, EditorLabelInk.pick(EditorLabelInk.band(px, w, h, -1f), DARK, PALE));
    }

    @Test
    public void withNoPictureTheSystemsDescriptionStandsIn() {
        // A fresh install cannot read the system's wallpaper: the hint is what the label is on.
        int light = EditorLabelInk.backdrop(null, 0, 0, Float.NaN, 0xFFF3EFE6);
        assertTrue(EditorLabelInk.isKnown(light));
        assertEquals(DARK, EditorLabelInk.pick(light, DARK, PALE));
        int dark = EditorLabelInk.backdrop(null, 0, 0, Float.NaN, 0xFF202020);
        assertEquals(PALE, EditorLabelInk.pick(dark, DARK, PALE));
        // A hint with alpha still describes an opaque picture.
        assertEquals(0xFFF3EFE6, EditorLabelInk.backdrop(null, 0, 0, Float.NaN, 0x80F3EFE6));
    }

    @Test
    public void withNeitherNothingIsKnown() {
        assertFalse(EditorLabelInk.isKnown(EditorLabelInk.backdrop(null, 0, 0, Float.NaN, 0)));
    }

    @Test
    public void aPictureReadWinsOverTheHint() {
        int h = EditorLabelInk.sampleHeight(1080, 2400);
        int w = EditorLabelInk.SAMPLE_WIDTH;
        int backdrop = EditorLabelInk.backdrop(splitPicture(), w, h, 0.2f, 0xFFF3EFE6);
        assertEquals(PALE, EditorLabelInk.pick(backdrop, DARK, PALE));
    }

    @Test
    public void thePictureIsCentreCroppedToTheScreensShape() {
        // A square picture into a tall sample: scaled to the height, the sides cut evenly.
        int[] square = EditorLabelInk.cropBounds(1000, 1000, 16, 36);
        assertEquals(0, square[1]);
        assertEquals(36, square[3]);
        assertEquals(-10, square[0]);
        assertEquals(26, square[2]);
        // A picture with no size of its own fills the sample.
        int[] none = EditorLabelInk.cropBounds(-1, -1, 16, 36);
        assertEquals(0, none[0]);
        assertEquals(36, none[3]);
    }
}
