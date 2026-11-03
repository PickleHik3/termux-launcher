package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The navigation strip's two tints across the seam, blended by the way's share. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CrossfadeDrawableTest {

    @Test
    public void atEitherEndOnlyThatEndsDressShows() {
        CrossfadeDrawable blend = new CrossfadeDrawable(new ColorDrawable(Color.RED),
            new ColorDrawable(Color.BLUE), 0f);
        assertEquals(255, blend.hereAlpha());
        assertEquals(0, blend.thereAlpha());
        blend.setShare(1f);
        assertEquals(0, blend.hereAlpha());
        assertEquals(255, blend.thereAlpha());
    }

    @Test
    public void betweenTheEndsBothShowByTheShare() {
        CrossfadeDrawable blend = new CrossfadeDrawable(new ColorDrawable(Color.RED),
            new ColorDrawable(Color.BLUE), 0.25f);
        assertEquals(191, blend.hereAlpha());
        assertEquals(64, blend.thereAlpha());
    }

    @Test
    public void aDressOnOneEndOnlyIsDrawnWholeForTheHolderToFade() {
        CrossfadeDrawable incoming = new CrossfadeDrawable(null, new ColorDrawable(Color.BLUE), 0.3f);
        assertEquals(0, incoming.hereAlpha());
        assertEquals(255, incoming.thereAlpha());
        CrossfadeDrawable outgoing = new CrossfadeDrawable(new ColorDrawable(Color.RED), null, 0.7f);
        assertEquals(255, outgoing.hereAlpha());
        assertEquals(0, outgoing.thereAlpha());
    }

    @Test
    public void theShareIsClampedAndOnlyAChangeCounts() {
        CrossfadeDrawable blend = new CrossfadeDrawable(new ColorDrawable(Color.RED),
            new ColorDrawable(Color.BLUE), 0f);
        assertFalse("0 again is no change", blend.setShare(-2f));
        assertTrue(blend.setShare(0.5f));
        assertFalse(blend.setShare(0.5f));
        assertTrue(blend.setShare(9f));
        assertEquals(1f, blend.share(), 0f);
    }

    @Test
    public void itDrawsAtEveryShareWithItsLayersAtItsBounds() {
        CrossfadeDrawable blend = new CrossfadeDrawable(new ColorDrawable(Color.RED),
            new ColorDrawable(Color.BLUE), 0f);
        blend.setBounds(0, 0, 4, 4);
        Canvas canvas = new Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888));
        blend.draw(canvas);
        blend.setShare(0.5f);
        blend.draw(canvas);
        blend.setShare(1f);
        blend.draw(canvas);
        assertEquals(4, blend.here().getBounds().width());
        assertEquals(4, blend.there().getBounds().width());
    }
}
