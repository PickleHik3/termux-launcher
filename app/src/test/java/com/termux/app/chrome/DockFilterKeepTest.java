package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What a page's rest keeps of the dock's frost is found again when the wall comes back to it
 * (pong r4: never found, not even going back to Home just measured on the same wallpaper,
 * because it was keyed on the ink's generation, which every page change moves).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockFilterKeepTest {

    private static final int HOME = 35;
    private static final int TERMINAL = 4;
    private static final int HOME_GROUND = 0x00000000;
    private static final int TERMINAL_GROUND = 0x73000000;
    private static final int DRESS = 100 * 31 + 7;

    private ChromeInk ink;
    private DockFilterKeep<Object, float[]> keep;
    private final Object homeFilter = new Object();
    private final float[] homeMatrix = {0.857f, 0f, 0f, 0f, 20.7f};

    @Before
    public void setUp() {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        keep = new DockFilterKeep<>();
    }

    @Test
    public void aRestKeptBeforeAPageChangeIsFoundOnTheWayBack() {
        keep.keep(HOME, homeFilter, homeMatrix, HOME_GROUND, ink.framesGeneration(), DRESS);
        int inkGeneration = ink.wallpaperGeneration();
        // The page in front changes its ground: every memoised ink answer goes (refreshGroundStrip).
        ink.invalidate();
        assertNotEquals("the ink's generation moves with the page", inkGeneration,
            ink.wallpaperGeneration());
        assertSame(homeFilter, keep.filter(HOME, HOME_GROUND, ink.framesGeneration(), DRESS));
        assertSame(homeMatrix, keep.matrix(HOME, HOME_GROUND, ink.framesGeneration(), DRESS));
    }

    @Test
    public void newWallpaperFramesAnotherGroundOrDressDropIt() {
        keep.keep(HOME, homeFilter, homeMatrix, HOME_GROUND, ink.framesGeneration(), DRESS);
        assertNull("another page's ground", keep.filter(HOME, TERMINAL_GROUND,
            ink.framesGeneration(), DRESS));
        assertNull("another Opacity or stage", keep.filter(HOME, HOME_GROUND,
            ink.framesGeneration(), DRESS + 31));
        assertNull("another look", keep.filter(TERMINAL, HOME_GROUND, ink.framesGeneration(),
            DRESS));
        int frames = ink.framesGeneration();
        ink.framesCleared();
        assertEquals(frames + 1, ink.framesGeneration());
        assertNull("the wallpaper changed", keep.filter(HOME, HOME_GROUND, ink.framesGeneration(),
            DRESS));
    }

    @Test
    public void keepingTheSameFilterAgainOnlyRestampsIt() {
        keep.keep(HOME, homeFilter, homeMatrix, HOME_GROUND, 0, DRESS);
        assertTrue(keep.holds(HOME, homeFilter));
        keep.restamp(HOME, HOME_GROUND, 1, DRESS);
        assertNull(keep.filter(HOME, HOME_GROUND, 0, DRESS));
        assertSame(homeFilter, keep.filter(HOME, HOME_GROUND, 1, DRESS));
    }
}
