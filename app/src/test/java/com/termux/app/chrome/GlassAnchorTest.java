package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.app.wall.PageSink;
import com.termux.app.wall.PaneWallLayout;
import com.termux.app.wall.PlankTilt;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The wall's glass shows wallpaper fixed to the screen. A page that slides, sinks under a held
 * border or tips as a plank carries its slabs with it, and the frame each slab draws is aimed past
 * the page's whole transform, so the wallpaper in the slab is the wallpaper on the screen under
 * it — lined up with the backdrop beside the page — while the slab's own layers move.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GlassAnchorTest {

    private static final float TOLERANCE_PX = 1e-2f;
    /** The frame is captured at a quarter of the screen, as the blur cache holds it. */
    private static final Rect FRAME_RECT = new Rect(0, -60, 1080, 2340);
    private static final int FRAME_WIDTH = 270;
    private static final int FRAME_HEIGHT = 600;
    private static final float PARALLAX_PX = 37.5f;

    private Activity mActivity;
    private FrameLayout mAboveWall;
    private FrameLayout mPage;
    private FrameLayout mPaneFrame;
    private View mGlass;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mAboveWall = new FrameLayout(mActivity);
        PaneWallLayout wall = new PaneWallLayout(mActivity);
        mPage = new FrameLayout(mActivity);
        mPaneFrame = new FrameLayout(mActivity);
        mGlass = new View(mActivity);
        mPaneFrame.addView(mGlass);
        mPage.addView(mPaneFrame);
        wall.addView(mPage);
        mAboveWall.addView(wall);
        mActivity.setContentView(mAboveWall);
        mAboveWall.layout(0, 0, 1080, 2220);
        wall.layout(0, 60, 1080, 2160);
        mPage.layout(0, 0, 1080, 2100);
        mPaneFrame.layout(40, 100, 1040, 1900);
        mGlass.layout(0, 0, 1000, 1800);
    }

    /** Sunk to the bottom of the spring and tipped as far as a plank goes, about its centre. */
    private void sinkAndTipThePage() {
        mPage.setTranslationX(-324f);
        mPage.setPivotX(mPage.getWidth() / 2f);
        mPage.setPivotY(mPage.getHeight() / 2f);
        mPage.setScaleX(PageSink.SCALE);
        mPage.setScaleY(PageSink.SCALE);
        mPage.setCameraDistance(PlankTilt.CAMERA_DISTANCE_DP
            * mActivity.getResources().getDisplayMetrics().density);
        mPage.setRotationY(PlankTilt.MAX_TILT_DEG);
    }

    private GlassAnchor.Placement place() {
        GlassAnchor.Placement placement = new GlassAnchor.Placement();
        GlassAnchor.place(mGlass, placement);
        return placement;
    }

    private static float[] map(Matrix matrix, float x, float y) {
        float[] point = {x, y};
        matrix.mapPoints(point);
        return point;
    }

    @Test
    public void theComposedMatrixIsTheFrameworksOwnChainThroughTheTippedPage() {
        sinkAndTipThePage();
        Matrix framework = new Matrix();
        mGlass.transformMatrixToGlobal(framework);
        int[] inWindow = new int[2];
        int[] onScreen = new int[2];
        mAboveWall.getLocationInWindow(inWindow);
        mAboveWall.getLocationOnScreen(onScreen);
        framework.postTranslate(onScreen[0] - inWindow[0], onScreen[1] - inWindow[1]);

        Matrix composed = place().toScreen;

        assertTrue("the tilt is a perspective, not an affine squeeze", !composed.isAffine());
        float[][] points = {{0f, 0f}, {1000f, 0f}, {0f, 1800f}, {1000f, 1800f}, {317f, 911f}};
        for (float[] p : points) {
            float[] expected = map(framework, p[0], p[1]);
            float[] actual = map(composed, p[0], p[1]);
            assertEquals(expected[0], actual[0], TOLERANCE_PX);
            assertEquals(expected[1], actual[1], TOLERANCE_PX);
        }
    }

    /**
     * The frame pixel the slab draws at a screen pixel: through the shader's matrix to the slab's
     * own px, and through the slab's placement to the screen.
     */
    private static float[] framePxAtScreen(GlassAnchor.Placement placement, float sx, float sy) {
        GlassAnchor.Aim aim = new GlassAnchor.Aim();
        aim.set(placement.toScreen, FRAME_RECT, FRAME_WIDTH, FRAME_HEIGHT, PARALLAX_PX);
        Matrix shader = new Matrix();
        aim.shaderMatrix(shader);
        Matrix frameToScreen = new Matrix(shader);
        frameToScreen.postConcat(placement.toScreen);
        Matrix screenToFrame = new Matrix();
        assertTrue(frameToScreen.invert(screenToFrame));
        return map(screenToFrame, sx, sy);
    }

    @Test
    public void theWallpaperUnderAScreenPixelIsTheSameAtRestAndSunkAndTipped() {
        GlassAnchor.Placement rest = place();
        float[][] screen = {{300f, 700f}, {540f, 1200f}, {820f, 400f}};
        float[][] atRest = new float[screen.length][];
        for (int i = 0; i < screen.length; i++) {
            atRest[i] = framePxAtScreen(rest, screen[i][0], screen[i][1]);
            // The backdrop's own mapping: the capture over its rect, panned by the parallax.
            float scaleX = FRAME_RECT.width() / (float) FRAME_WIDTH;
            float scaleY = FRAME_RECT.height() / (float) FRAME_HEIGHT;
            assertEquals((screen[i][0] - FRAME_RECT.left + PARALLAX_PX) / scaleX, atRest[i][0],
                TOLERANCE_PX);
            assertEquals((screen[i][1] - FRAME_RECT.top) / scaleY, atRest[i][1], TOLERANCE_PX);
        }

        sinkAndTipThePage();
        GlassAnchor.Placement moved = place();
        assertFalse("the page did move", moved.toScreen.equals(rest.toScreen));
        for (int i = 0; i < screen.length; i++) {
            float[] sunk = framePxAtScreen(moved, screen[i][0], screen[i][1]);
            assertEquals("the wallpaper stays on the screen", atRest[i][0], sunk[0], TOLERANCE_PX);
            assertEquals(atRest[i][1], sunk[1], TOLERANCE_PX);
        }
    }

    @Test
    public void anUntransformedPageAimsExactlyAsItAlwaysHas() {
        int[] origin = new int[2];
        float slide = GlassAnchor.layoutOriginOnScreen(mGlass, origin, new int[2]);
        GlassAnchor.Aim aim = new GlassAnchor.Aim();
        aim.set(place().toScreen, FRAME_RECT, FRAME_WIDTH, FRAME_HEIGHT, PARALLAX_PX);

        float shiftX = PARALLAX_PX + slide;
        float[] old = {
            FRAME_RECT.width() / (float) Math.max(1, FRAME_WIDTH),
            FRAME_RECT.height() / (float) Math.max(1, FRAME_HEIGHT),
            FRAME_RECT.left - origin[0] - shiftX,
            FRAME_RECT.top - origin[1],
        };
        for (int i = 0; i < 4; i++) assertEquals(old[i], aim.scaleOffset[i], 0f);
        assertTrue("nothing past the old aim at rest", aim.local.isIdentity());

        Matrix oldMatrix = new Matrix();
        oldMatrix.setScale(old[0], old[1]);
        oldMatrix.postTranslate(old[2], old[3]);
        Matrix shader = new Matrix();
        aim.shaderMatrix(shader);
        assertEquals(oldMatrix, shader);
    }

    /** Fancier Glass at rest: its local matrix is identity and its mapping is the old aim, exactly. */
    @Test
    public void theRefractionsMappingIsTheOldScaleAndOffsetAtRest() {
        GlassAnchor.Aim aim = new GlassAnchor.Aim();
        aim.set(place().toScreen, FRAME_RECT, FRAME_WIDTH, FRAME_HEIGHT, PARALLAX_PX);
        float[] local = new float[9];
        aim.local.getValues(local);
        float[] s = aim.scaleOffset;
        float[] out = new float[2];
        float[][] points = {{0.5f, 0.5f}, {317.25f, 911.75f}, {999.5f, 1799.5f}};
        for (float[] p : points) {
            GlassRefraction.framePx(local, s[0], s[1], s[2], s[3], p[0], p[1], out);
            assertEquals((p[0] - s[2]) / s[0], out[0], 0f);
            assertEquals((p[1] - s[3]) / s[1], out[1], 0f);
        }
    }

    /** Fancier Glass on a sunk, tipped page samples the very pixel the plain draw does. */
    @Test
    public void theRefractionSamplesWhatThePlainDrawDoesOnATippedPage() {
        sinkAndTipThePage();
        GlassAnchor.Aim aim = new GlassAnchor.Aim();
        aim.set(place().toScreen, FRAME_RECT, FRAME_WIDTH, FRAME_HEIGHT, PARALLAX_PX);
        assertFalse(aim.local.isAffine());
        float[] local = new float[9];
        aim.local.getValues(local);
        Matrix shader = new Matrix();
        aim.shaderMatrix(shader);
        float[] s = aim.scaleOffset;
        float[] frame = new float[2];
        float[][] points = {{0f, 0f}, {1000f, 0f}, {0f, 1800f}, {1000f, 1800f}, {317f, 911f}};
        for (float[] p : points) {
            GlassRefraction.framePx(local, s[0], s[1], s[2], s[3], p[0], p[1], frame);
            float[] back = map(shader, frame[0], frame[1]);
            assertEquals(p[0], back[0], TOLERANCE_PX);
            assertEquals(p[1], back[1], TOLERANCE_PX);
        }
    }

    @Test
    public void aSlideAloneIsStillThePlainAimWithoutRounding() {
        mPage.setTranslationX(-324.25f);
        GlassAnchor.Aim aim = new GlassAnchor.Aim();
        aim.set(place().toScreen, FRAME_RECT, FRAME_WIDTH, FRAME_HEIGHT, 0f);
        assertTrue(aim.local.isIdentity());
        int[] origin = new int[2];
        GlassAnchor.layoutOriginOnScreen(mGlass, origin, new int[2]);
        assertEquals(FRAME_RECT.left - (origin[0] - 324.25f), aim.scaleOffset[2], 1e-3f);
    }

    @Test
    public void aPanesPressStaysOutOfTheMatrix() {
        Matrix rest = new Matrix(place().toScreen);
        mPaneFrame.setTranslationX(24f);
        mPaneFrame.setTranslationY(-8f);
        mPaneFrame.setRotationY(1.1f);
        mPaneFrame.setScaleX(0.99f);
        assertEquals(rest, place().toScreen);
    }

    @Test
    public void aPublishedMotionIsFollowedToTheSubPixel() {
        Matrix rest = new Matrix(place().toScreen);
        GlassAnchor.setMotion(mPaneFrame, -300.5f, 120.25f);
        Matrix moving = place().toScreen;
        float[] before = map(rest, 0f, 0f);
        float[] after = map(moving, 0f, 0f);
        assertEquals(before[0] - 300.5f, after[0], 0f);
        assertEquals(before[1] + 120.25f, after[1], 0f);
        GlassAnchor.clearMotion(mPaneFrame);
        assertEquals(rest, place().toScreen);
    }

    /**
     * The sink dims the page's layer, glass and all; the wallpaper behind does not darken. The
     * frame is lifted by as much in advance, so frost, lift and dim together are the frost alone.
     */
    @Test
    public void theSinkDimIsUndoneOnTheWallpaperTheGlassDraws() {
        assertEquals("no page is sinking", 0xFF, place().keptBrightness);

        ColorMatrixColorFilter frost = GlassFilters.frost();
        assertSame("nothing to undo at rest", frost, GlassFilters.undimmed(frost, 0xFF));
        int kept = PageSink.dimMultiplier(15, 16) & 0xFF;
        ColorFilter lifted = GlassFilters.undimmed(frost, kept);
        assertSame("one filter per level, built once", lifted, GlassFilters.undimmed(frost, kept));

        ColorMatrix frostMatrix = new ColorMatrix();
        frost.getColorMatrix(frostMatrix);
        ColorMatrix liftedMatrix = new ColorMatrix();
        ((ColorMatrixColorFilter) lifted).getColorMatrix(liftedMatrix);
        float[] plain = frostMatrix.getArray();
        float[] undone = liftedMatrix.getArray();
        float dim = kept / 255f;
        for (int i = 0; i < 15; i++) assertEquals(plain[i], undone[i] * dim, 1e-4f);
        for (int i = 15; i < 20; i++) assertEquals("alpha is left alone", plain[i], undone[i], 0f);

        ColorFilter foreign = new ColorMatrixColorFilter(new ColorMatrix());
        assertSame("a filter it did not build is handed back", foreign,
            GlassFilters.undimmed(foreign, kept));
    }

    /**
     * The Appearance editor scales the container the backdrop shares with the wall: the glass
     * and the backdrop already move together there, so that scale stays out.
     */
    @Test
    public void aTransformAboveTheWallStaysOut() {
        Matrix rest = new Matrix(place().toScreen);
        mAboveWall.setPivotX(540f);
        mAboveWall.setPivotY(0f);
        mAboveWall.setScaleX(0.8f);
        mAboveWall.setScaleY(0.8f);
        assertEquals(rest, place().toScreen);
    }
}
