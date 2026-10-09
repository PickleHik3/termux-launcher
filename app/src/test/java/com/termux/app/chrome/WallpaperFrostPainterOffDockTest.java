package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The sheets bars lie on off the dock — the shared plank and the alphabets capsule — are frosted
 * from the shared wallpaper frame like every other sheet: at the dock's own blur radius, with the
 * look and the sheet's own corner handed over, and not at all where there is no frame to show.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperFrostPainterOffDockTest {

    private FakeChromeSurfaces surfaces;
    private ChromeRenderer chrome;
    private View wallpaperFrame;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        surfaces = new FakeChromeSurfaces(context);
        chrome = new ChromeRenderer(surfaces, null);
        wallpaperFrame = new View(context);
        surfaces.views.put(R.id.activity_termux_root_view, wallpaperFrame);
    }

    /** A frost filling a laid-out glass sheet, as the layout nests it. */
    private ImageView sheet(int width, int height) {
        FrameLayout glass = new FrameLayout(context);
        ImageView frost = new ImageView(context);
        frost.setVisibility(View.GONE);
        glass.addView(frost, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        glass.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        glass.layout(0, 0, width, height);
        return frost;
    }

    @Test
    public void aSheetOffTheDockShowsTheDocksOwnFrame() {
        surfaces.dockBlurRadiusDp = 14;
        surfaces.statusBlurRadiusDp = 6;
        ImageView frost = sheet(300, 40);

        assertTrue(chrome.frost().applyOffDockSheet(frost,
            SurfaceDirtyLedger.FrostRect.OFF_DOCK_PLANK, 20f));

        assertEquals(View.VISIBLE, frost.getVisibility());
        SharedFrameDrawable drawable = SharedFrameDrawable.of(frost.getDrawable());
        assertNotNull("the shared frame, not a crop", drawable);
        assertSame("the dock's radius, so the plank reads as the dock's glass",
            chrome.blurCache().obtain(14, wallpaperFrame), drawable.frame());
    }

    @Test
    public void theLookAndTheSheetsOwnCornerReachTheFrost() {
        surfaces.look = GlassRefraction.Look.DEFAULT;
        ImageView frost = sheet(300, 40);

        assertTrue(chrome.frost().applyOffDockSheet(frost,
            SurfaceDirtyLedger.FrostRect.AZ_BAR_HOST, 20f));

        SharedFrameDrawable drawable = SharedFrameDrawable.of(frost.getDrawable());
        assertNotNull(drawable);
        assertEquals(GlassRefraction.Look.DEFAULT, drawable.refractionLook());
        assertEquals("the outline's own radius", 20f, drawable.refractionRadiusPx(), 0f);
        assertEquals("a sheet off the dock meets no other glass: a rim on all four sides",
            0, drawable.refractionSeams());
        assertFalse("no program below API 33", drawable.refracts());

        // Turned off again on the next pass, with nothing re-installed.
        surfaces.look = null;
        assertTrue(chrome.frost().applyOffDockSheet(frost,
            SurfaceDirtyLedger.FrostRect.AZ_BAR_HOST, 20f));
        assertSame(drawable, SharedFrameDrawable.of(frost.getDrawable()));
        assertNull(drawable.refractionLook());
    }

    @Test
    public void noBlurOnTheDockIsNoFrostAndTheLiveBlurKeepsTheSheet() {
        surfaces.dockBlurRadiusDp = 0;
        ImageView frost = sheet(300, 40);

        assertFalse(chrome.frost().applyOffDockSheet(frost,
            SurfaceDirtyLedger.FrostRect.OFF_DOCK_PLANK, 20f));

        assertEquals(View.GONE, frost.getVisibility());
        assertNull(frost.getDrawable());
    }

    @Test
    public void aWallpaperTheLauncherCannotSeeIsNoFrost() {
        surfaces.passthrough = false;
        ImageView frost = sheet(300, 40);

        assertFalse(chrome.frost().applyOffDockSheet(frost,
            SurfaceDirtyLedger.FrostRect.OFF_DOCK_PLANK, 20f));
        assertEquals(View.GONE, frost.getVisibility());
    }

    @Test
    public void aSheetNotLaidOutYetWaitsForThePassAfterLayout() {
        FrameLayout glass = new FrameLayout(context);
        ImageView frost = new ImageView(context);
        glass.addView(frost);

        assertFalse(chrome.frost().applyOffDockSheet(frost,
            SurfaceDirtyLedger.FrostRect.OFF_DOCK_PLANK, 20f));
        assertEquals(View.GONE, frost.getVisibility());
    }
}
