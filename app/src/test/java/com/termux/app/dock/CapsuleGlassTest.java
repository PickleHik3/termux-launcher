package com.termux.app.dock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.chrome.GlassRefraction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The capsule's numbers, which the dock's host and the Terminal's extra-keys card both dress
 * through ({@link CapsuleGlass}): given the same size they are the same, and they are the host's
 * own answers (the activity's inline bodies, moved).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CapsuleGlassTest {

    @Test
    public void overscanIsTwiceTheBlurAndTwoDensityPixels() {
        // 20 dp blur on a 2.625 density phone: 52.5 px blur, 5.25 px of slack.
        assertEquals(110, CapsuleGlass.horizontalOverscanPx(52.5f, 2.625f));
        assertEquals(0, CapsuleGlass.horizontalOverscanPx(-100f, 0f));
    }

    @Test
    public void seamOverscanFollowsTheFootAndIsZeroWithout() {
        assertEquals(110, CapsuleGlass.seamOverscanPx(63, 110));
        assertEquals(0, CapsuleGlass.seamOverscanPx(0, 110));
    }

    @Test
    public void radiusIsCornersCappedAtHalfTheHeight() {
        assertEquals(49f, CapsuleGlass.radiusPx(78f, 98), 0f);
        assertEquals(26f, CapsuleGlass.radiusPx(26f, 98), 0f);
        assertEquals(0f, CapsuleGlass.radiusPx(26f, -4), 0f);
    }

    @Test
    public void capRectInsetsTheSidesByTheOverscanAndRunsToTheSeam() {
        float[] out = new float[4];
        // The capsule has no seam edges: the rect is the capsule inside its overscanned bitmap.
        CapsuleGlass.capRect(out, 110, 1038, 98, 110, 20f, 0);
        assertArrayEquals(new float[] {110f, 0f, 1148f, 208f}, out, 0f);
        // Without a foot the bottom is the surface's own.
        CapsuleGlass.capRect(out, 110, 1038, 98, 0, 20f, 0);
        assertArrayEquals(new float[] {110f, 0f, 1148f, 98f}, out, 0f);
        // A seam edge pushes the rim rect out by the reach.
        CapsuleGlass.capRect(out, 110, 1038, 98, 0, 20f, GlassRefraction.SEAM_BOTTOM);
        assertEquals(118f, out[3], 0f);
    }

    @Test
    public void targetRectReachesTheOverscanAndNeverCollapses() {
        Rect rect = CapsuleGlass.targetRect(new int[] {21, 1405}, 1038, 98, 110, 110);
        assertEquals(new Rect(-89, 1405, 1169, 1613), rect);
        assertEquals(new Rect(-110, 0, 111, 1),
            CapsuleGlass.targetRect(new int[] {0, 0}, 0, 0, 110, 0));
    }

    @Test
    public void hostAndCardGetTheSameBackdropGivenTheSameSize() {
        Application app = ApplicationProvider.getApplicationContext();
        ImageView hostBackdrop = backdropIn(app);
        ImageView cardBackdrop = backdropIn(app);
        int over = CapsuleGlass.horizontalOverscanPx(52.5f, 2.625f);
        int seam = CapsuleGlass.seamOverscanPx(63, over);
        // The host's surface and the card's, the same 1038 x 98.
        CapsuleGlass.applyOverscan(hostBackdrop, 1038, 98, over, seam);
        CapsuleGlass.applyOverscan(cardBackdrop, 1038, 98, over, seam);
        FrameLayout.LayoutParams host = (FrameLayout.LayoutParams) hostBackdrop.getLayoutParams();
        FrameLayout.LayoutParams card = (FrameLayout.LayoutParams) cardBackdrop.getLayoutParams();
        assertEquals(1258, host.width);
        assertEquals(208, host.height);
        assertEquals(-110, host.leftMargin);
        assertEquals(Gravity.TOP | Gravity.START, host.gravity);
        assertEquals(host.width, card.width);
        assertEquals(host.height, card.height);
        assertEquals(host.leftMargin, card.leftMargin);
        assertEquals(host.topMargin, card.topMargin);
        assertEquals(host.gravity, card.gravity);
        float[] hostCap = new float[4];
        float[] cardCap = new float[4];
        CapsuleGlass.capRect(hostCap, over, 1038, 98, seam, 12f, 0);
        CapsuleGlass.capRect(cardCap, over, 1038, 98, seam, 12f, 0);
        assertArrayEquals(hostCap, cardCap, 0f);
    }

    @Test
    public void overscanIsWrittenOnlyWhenANumberDiffers() {
        Application app = ApplicationProvider.getApplicationContext();
        ImageView backdrop = backdropIn(app);
        CapsuleGlass.applyOverscan(backdrop, 1038, 98, 110, 0);
        Object first = backdrop.getLayoutParams();
        CapsuleGlass.applyOverscan(backdrop, 1038, 98, 110, 0);
        assertSame(first, backdrop.getLayoutParams());
        CapsuleGlass.applyOverscan(backdrop, 1038, 99, 110, 0);
        assertEquals(99, ((FrameLayout.LayoutParams) backdrop.getLayoutParams()).height);
    }

    @Test
    public void shelfShowsOnlyWhereTheCapsuleFloatsOverRefractingGlassWithoutTheKeyboardUnderIt() {
        assertTrue(DockSurface.shelfShown(true, true, false, true, true));
        assertFalse(DockSurface.shelfShown(false, true, false, true, true));
        assertFalse(DockSurface.shelfShown(true, false, false, true, true));
        assertFalse(DockSurface.shelfShown(true, true, true, true, true));
        assertFalse(DockSurface.shelfShown(true, true, false, false, true));
        assertFalse(DockSurface.shelfShown(true, true, false, true, false));
    }

    @Test
    public void alphaReplacesTheChannelAndClamps() {
        assertEquals(0x12336699, CapsuleGlass.withAlpha(0xFF336699, 18));
        assertEquals(0xFF336699, CapsuleGlass.withAlpha(0x00336699, 999));
        assertEquals(0x00336699, CapsuleGlass.withAlpha(0xFF336699, -5));
    }

    private static ImageView backdropIn(Application app) {
        FrameLayout parent = new FrameLayout(app);
        ImageView backdrop = new ImageView(app);
        parent.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return backdrop;
    }
}
