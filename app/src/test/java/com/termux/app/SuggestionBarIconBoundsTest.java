package com.termux.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class SuggestionBarIconBoundsTest {

    @Test
    public void visibleAlphaBounds_ignoreTransparentCustomIconPadding() {
        Bitmap bitmap = Bitmap.createBitmap(12, 10, Bitmap.Config.ARGB_8888);
        for (int y = 3; y < 8; y++) {
            for (int x = 2; x < 9; x++) {
                bitmap.setPixel(x, y, Color.WHITE);
            }
        }

        assertEquals(new Rect(2, 3, 9, 8), SuggestionBarView.findVisibleAlphaBounds(bitmap));
        bitmap.recycle();
    }

    @Test
    public void visibleAlphaBounds_ignoreLowAlphaShadow() {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        bitmap.setPixel(1, 1, 0x20000000);
        for (int y = 3; y < 7; y++) {
            for (int x = 4; x < 8; x++) {
                bitmap.setPixel(x, y, Color.WHITE);
            }
        }

        assertEquals(new Rect(4, 3, 8, 7), SuggestionBarView.findVisibleAlphaBounds(bitmap));
        bitmap.recycle();
    }

    @Test
    public void focusOutlineMask_followsArtworkInsteadOfBoundingCircle() {
        Bitmap source = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888);
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) {
                source.setPixel(x, y, Color.WHITE);
            }
        }

        Bitmap outline = SuggestionBarView.buildFocusOutlineMask(source, 1, 1);
        // Source starts at (4,4) in the padded output. The one-pixel gap and source stay clear.
        assertEquals(0, Color.alpha(outline.getPixel(5, 5)));
        assertEquals(0, Color.alpha(outline.getPixel(3, 5)));
        // The next pixel is the external contour generated from the actual square silhouette.
        assertTrue(Color.alpha(outline.getPixel(2, 5)) > 0);

        outline.recycle();
        source.recycle();
    }

    @Test
    public void focusOutlineVisual_reservesStrokeAndSixDpHaloOutsideCleanArtwork() {
        Bitmap cleanArtwork = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888);
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) cleanArtwork.setPixel(x, y, Color.WHITE);
        }

        FocusOutlineRenderer.Visual visual = FocusOutlineRenderer.buildVisual(cleanArtwork, 1f);

        // 1.5dp rounds to a 2px crisp contour; the halo reserves another 6px per edge.
        assertEquals(8, visual.outerPadding);
        assertEquals(23, visual.crispMask.getWidth());
        assertEquals(23, visual.haloMask.getHeight());
        // The clean artwork's centre remains a hole: no shadow-offset pixels entered the contour.
        assertEquals(0, Color.alpha(visual.crispMask.getPixel(11, 11)));
        assertEquals(0, Color.alpha(visual.haloMask.getPixel(11, 11)));

        visual.crispMask.recycle();
        visual.haloMask.recycle();
        cleanArtwork.recycle();
    }

    @Test
    public void focusOutlineIncomingScale_revivesThroughOvershootAndSettles() {
        assertEquals(1.04f, FocusOutlineRenderer.incomingScale(0.98f, 0.45f), 0.0001f);
        assertEquals(1f, FocusOutlineRenderer.incomingScale(0.98f, 1f), 0.0001f);
        assertEquals(1.04f, FocusOutlineRenderer.incomingScale(1.04f, 0f), 0.0001f);
    }
}
