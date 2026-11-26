package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The marks' title capsule wears Global's glass on a plain view that does not clip it: the stack
 * cuts itself to its rounded outline, so the corners stay clear on the glass path and the scheme
 * path alike. Without the cut the frame (and a scheme's tint) paints the whole rectangle: the
 * lighter square pong showed around the capsule.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OutlinedGlassTest {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 60;
    private static final float RADIUS_PX = 30f;

    private GlassSurfaceFactory glass;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        glass = new GlassSurfaceFactory(new FakeChromeSurfaces(context));
    }

    private static SharedFrameDrawable backdrop() {
        Bitmap frame = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        frame.eraseColor(Color.WHITE);
        return new SharedFrameDrawable(frame, new Rect(0, 0, WIDTH, HEIGHT), null,
            out -> { out[0] = 0; out[1] = 0; });
    }

    private static GlassStack.Spec glassSpec() {
        return GlassStack.Spec.of(12, 0.5f, 0, RADIUS_PX, null).withRim(true);
    }

    private static Bitmap draw(Drawable stack) {
        Bitmap out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        stack.setBounds(0, 0, WIDTH, HEIGHT);
        stack.draw(new Canvas(out));
        return out;
    }

    private static void assertCutToItsOutline(Bitmap pixels) {
        assertEquals("top-left corner", 0, Color.alpha(pixels.getPixel(1, 1)));
        assertEquals("bottom-right corner", 0,
            Color.alpha(pixels.getPixel(WIDTH - 2, HEIGHT - 2)));
        assertTrue("centre painted", Color.alpha(pixels.getPixel(WIDTH / 2, HEIGHT / 2)) > 0);
    }

    @Test
    public void withoutTheCutTheFramePaintsTheWholeRectangle() {
        // The layer that drew pong's square: the frame is drawn over its full bounds.
        Bitmap pixels = draw(GlassStack.build(glass, glassSpec(), 2f, backdrop()));
        assertNotEquals(0, Color.alpha(pixels.getPixel(1, 1)));
    }

    @Test
    public void theGlassPathIsCutToItsOutline() {
        Drawable stack = GlassStack.build(glass, glassSpec().withOwnOutline(true), 2f, backdrop());
        assertTrue(stack instanceof OutlinedGlass);
        assertCutToItsOutline(draw(stack));
    }

    @Test
    public void theSchemeTintPathIsCutToItsOutline() {
        Drawable stack = GlassStack.build(glass,
            glassSpec().withTintColor(0xFF336699).withOwnOutline(true), 2f, backdrop());
        assertCutToItsOutline(draw(stack));
    }

    @Test
    public void theStackAlphaAndTheFrameAreReachedThroughTheCut() {
        SharedFrameDrawable frame = backdrop();
        Drawable stack = GlassStack.build(glass, glassSpec().withOwnOutline(true), 2f, frame);
        assertSame(frame, SharedFrameDrawable.of(stack));
        GlassStack.applyStackAlpha(stack, 128);
        assertEquals(128, frame.getAlpha());
    }

    @Test
    public void theRadiusIsHeldToHalfTheSmallerSide() {
        assertEquals(21f, OutlinedGlass.fittedRadiusPx(100f, 200f, 42f), 0f);
        assertEquals(10f, OutlinedGlass.fittedRadiusPx(10f, 200f, 42f), 0f);
        assertEquals(0f, OutlinedGlass.fittedRadiusPx(10f, 0f, 42f), 0f);
    }
}
