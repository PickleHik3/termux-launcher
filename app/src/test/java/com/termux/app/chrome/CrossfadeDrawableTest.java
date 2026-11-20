package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

    /**
     * The band behind the system bar across Home 2 to the Terminal: Home's glass, the
     * Terminal's beside it, crossing with the finger and resting on the Terminal's.
     */
    @Test
    public void theBandCrossesWithTheFingerAndLandsOnTheArrivingDress() {
        ColorDrawable home = new ColorDrawable(0x22FFFFFF);
        ColorDrawable terminal = new ColorDrawable(0xAA000000);
        CrossfadeDrawable band = new CrossfadeDrawable(home, terminal, 0f);
        int last = -1;
        for (int i = 0; i <= 20; i++) {
            band.setShare(com.termux.app.place.SeamFade.share(i / 20f));
            assertTrue("the arriving dress only grows", band.thereAlpha() >= last);
            last = band.thereAlpha();
        }
        assertEquals(255, band.thereAlpha());
        assertEquals(0, band.hereAlpha());
        assertTrue(band.endpoint() == terminal);
    }

    /** A cancelled crossing fades back the same way and rests on the dress it began in. */
    @Test
    public void aCancelFadesBackAndRestsOnTheLeavingDress() {
        ColorDrawable home = new ColorDrawable(0x22FFFFFF);
        ColorDrawable terminal = new ColorDrawable(0xAA000000);
        CrossfadeDrawable band = new CrossfadeDrawable(home, terminal, 0f);
        band.setShare(com.termux.app.place.SeamFade.share(0.6f));
        assertTrue(band.thereAlpha() > 0 && band.hereAlpha() > 0);
        assertTrue("past the middle it would rest on the arriving one", band.endpoint() == terminal);
        int last = 256;
        for (int i = 12; i >= 0; i--) {
            band.setShare(com.termux.app.place.SeamFade.share(i / 20f));
            assertTrue("the arriving dress only shrinks", band.thereAlpha() <= last);
            last = band.thereAlpha();
        }
        assertEquals(0, band.thereAlpha());
        assertEquals(255, band.hereAlpha());
        assertTrue(band.endpoint() == home);
    }

    /** A band the leaving half did not show rests on nothing after a cancel. */
    @Test
    public void aBandThatWasNotThereRestsOnNothingAfterACancel() {
        CrossfadeDrawable band = new CrossfadeDrawable(null, new ColorDrawable(Color.BLACK), 0.4f);
        band.setShare(0f);
        assertTrue(band.endpoint() == null);
        band.setShare(1f);
        assertTrue(band.endpoint() == band.there());
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

    /**
     * 4a9d2d180's re-wrap skipped a pass that put up nothing: under Floating the keyboard host
     * went bare mid-crossing and the blend ran on detached. The blend now goes back up as it was.
     */
    @Test
    public void underFloatingAPassThatPutUpNothingPutsTheBlendBack() {
        ColorDrawable home = new ColorDrawable(Color.TRANSPARENT);
        ColorDrawable card = new ColorDrawable(Color.GRAY);
        CrossfadeDrawable blend = new CrossfadeDrawable(home, card, 0.4f);
        assertSame("arriving", blend, CrossfadeDrawable.afterPass(blend, null, true, false, 0.5f));
        assertSame("leaving", blend, CrossfadeDrawable.afterPass(blend, null, false, false, 0.5f));
        assertSame("the blend still up is kept", blend,
            CrossfadeDrawable.afterPass(blend, blend, true, false, 0.5f));
    }

    @Test
    public void dockedKeepsTheHostAsAPassThatPutUpNothingLeftIt() {
        CrossfadeDrawable blend = new CrossfadeDrawable(new ColorDrawable(Color.BLACK),
            new ColorDrawable(Color.GRAY), 0.4f);
        assertNull(CrossfadeDrawable.afterPass(blend, null, true, true, 0.5f));
    }

    @Test
    public void aPassThatDressedTheHostIsTheEndOnItsSide() {
        ColorDrawable here = new ColorDrawable(Color.BLACK);
        ColorDrawable there = new ColorDrawable(Color.GRAY);
        ColorDrawable now = new ColorDrawable(Color.WHITE);
        CrossfadeDrawable blend = new CrossfadeDrawable(here, there, 0.4f);
        CrossfadeDrawable arriving = CrossfadeDrawable.afterPass(blend, now, true, false, 0.5f);
        assertSame(here, arriving.here());
        assertSame(now, arriving.there());
        assertEquals(0.5f, arriving.share(), 0f);
        CrossfadeDrawable leaving = CrossfadeDrawable.afterPass(blend, now, false, true, 0.5f);
        assertSame(now, leaving.here());
        assertSame(there, leaving.there());
    }
}
