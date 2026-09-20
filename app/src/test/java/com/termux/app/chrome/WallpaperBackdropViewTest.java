package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** When the self-drawn wallpaper shows, what it keeps, and when it gets out of the way. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperBackdropViewTest {

    private static final Rect PORTRAIT = new Rect(0, 0, 100, 200);
    private static final Rect LANDSCAPE = new Rect(0, 0, 200, 100);
    private static final int DIM = 0x80000000;

    private WallpaperBackdropView backdrop;

    @Before
    public void setUp() {
        backdrop = new WallpaperBackdropView((Application) RuntimeEnvironment.getApplication());
        layout(backdrop, PORTRAIT.width(), PORTRAIT.height());
    }

    private static void layout(@androidx.annotation.NonNull View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static Bitmap frame(@androidx.annotation.NonNull Rect rect) {
        return Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888);
    }

    @Test
    public void startsOutOfTheWayWithNothingToShow() {
        assertEquals(View.INVISIBLE, backdrop.getVisibility());
        assertNull(backdrop.heldFrame());
        assertFalse(backdrop.isOpaque());
    }

    @Test
    public void aFrameShowsOpaqueAndIsHeldForTheCache() {
        Bitmap wallpaper = frame(PORTRAIT);
        backdrop.showFrame(wallpaper, PORTRAIT, DIM);
        assertEquals(View.VISIBLE, backdrop.getVisibility());
        assertSame(wallpaper, backdrop.heldFrame());
        assertTrue(backdrop.isOpaque());
    }

    @Test
    public void noFrameYetLeavesTheSystemWallpaperShowing() {
        backdrop.showFrame(null, PORTRAIT, DIM);
        assertEquals(View.INVISIBLE, backdrop.getVisibility());
        assertNull(backdrop.heldFrame());
    }

    @Test
    public void aPendingFrameKeepsTheOneAlreadyUpWhileTheGeometryHolds() {
        Bitmap wallpaper = frame(PORTRAIT);
        backdrop.showFrame(wallpaper, PORTRAIT, DIM);
        backdrop.showFrame(null, PORTRAIT, DIM);
        assertEquals(View.VISIBLE, backdrop.getVisibility());
        assertSame(wallpaper, backdrop.heldFrame());
    }

    @Test
    public void aMovedFrameRectDropsTheHeldFrameRatherThanShowItShifted() {
        backdrop.showFrame(frame(PORTRAIT), PORTRAIT, DIM);
        layout(backdrop, LANDSCAPE.width(), LANDSCAPE.height());
        backdrop.showFrame(null, LANDSCAPE, DIM);
        assertEquals(View.INVISIBLE, backdrop.getVisibility());
        assertNull(backdrop.heldFrame());
    }

    @Test
    public void aRecycledFrameIsNeverDrawn() {
        Bitmap wallpaper = frame(PORTRAIT);
        wallpaper.recycle();
        backdrop.showFrame(wallpaper, PORTRAIT, DIM);
        assertEquals(View.INVISIBLE, backdrop.getVisibility());
        assertNull(backdrop.heldFrame());
    }

    @Test
    public void hidingLetsGoOfTheFrameSoTheCacheCanRecycleIt() {
        backdrop.showFrame(frame(PORTRAIT), PORTRAIT, DIM);
        backdrop.hide();
        assertEquals(View.INVISIBLE, backdrop.getVisibility());
        assertNull(backdrop.heldFrame());
        assertFalse(backdrop.isOpaque());
    }

    @Test
    public void aFrameThatDoesNotCoverTheViewIsNotClaimedOpaque() {
        Rect halfHeight = new Rect(0, 0, PORTRAIT.width(), PORTRAIT.height() / 2);
        backdrop.showFrame(frame(halfHeight), halfHeight, Color.TRANSPARENT);
        assertEquals(View.VISIBLE, backdrop.getVisibility());
        assertFalse(backdrop.isOpaque());
    }
}
