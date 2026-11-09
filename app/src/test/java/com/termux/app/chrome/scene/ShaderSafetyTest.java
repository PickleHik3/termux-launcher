package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Heavy frames, context loss, the pending marker's clock, the pointer and the motion choice. */
public class ShaderSafetyTest {

    private static final long PERIOD = 33_333_333L;

    @Test
    public void thirtyHeavyFramesInARowStepDownOnce() {
        ShaderSafety.HeavyFrames h = new ShaderSafety.HeavyFrames();
        for (int i = 0; i < ShaderSafety.HEAVY_FRAMES - 1; i++) {
            assertEquals(ShaderSafety.HeavyFrames.NONE, h.onFrame(3 * PERIOD, PERIOD, ShaderSafety.Quality.HALF));
        }
        assertEquals(ShaderSafety.HeavyFrames.STEP_DOWN, h.onFrame(3 * PERIOD, PERIOD, ShaderSafety.Quality.HALF));
        // The count starts again after a step.
        assertEquals(ShaderSafety.HeavyFrames.NONE, h.onFrame(3 * PERIOD, PERIOD, ShaderSafety.Quality.QUARTER));
    }

    @Test
    public void oneLightFrameBreaksTheRun() {
        ShaderSafety.HeavyFrames h = new ShaderSafety.HeavyFrames();
        for (int i = 0; i < ShaderSafety.HEAVY_FRAMES - 1; i++) h.onFrame(3 * PERIOD, PERIOD, ShaderSafety.Quality.FULL);
        assertEquals(ShaderSafety.HeavyFrames.NONE, h.onFrame(PERIOD, PERIOD, ShaderSafety.Quality.FULL));
        // Exactly twice the period is not over it.
        for (int i = 0; i < 2 * ShaderSafety.HEAVY_FRAMES; i++) {
            assertEquals(ShaderSafety.HeavyFrames.NONE, h.onFrame(2 * PERIOD, PERIOD, ShaderSafety.Quality.FULL));
        }
    }

    @Test
    public void atQuarterTheShaderHolds() {
        ShaderSafety.HeavyFrames h = new ShaderSafety.HeavyFrames();
        int last = 0;
        for (int i = 0; i < ShaderSafety.HEAVY_FRAMES; i++) last = h.onFrame(3 * PERIOD, PERIOD, ShaderSafety.Quality.QUARTER);
        assertEquals(ShaderSafety.HeavyFrames.HOLD, last);
    }

    @Test
    public void oneOffFramesDoNotCount() {
        ShaderSafety.HeavyFrames h = new ShaderSafety.HeavyFrames();
        for (int i = 0; i < 2 * ShaderSafety.HEAVY_FRAMES; i++) {
            assertEquals(ShaderSafety.HeavyFrames.NONE, h.onFrame(1_000_000_000L, Long.MAX_VALUE, ShaderSafety.Quality.FULL));
        }
    }

    @Test
    public void qualityStepsAndParses() {
        assertEquals(ShaderSafety.Quality.HALF, ShaderSafety.Quality.FULL.lower());
        assertEquals(ShaderSafety.Quality.QUARTER, ShaderSafety.Quality.HALF.lower());
        assertNull(ShaderSafety.Quality.QUARTER.lower());
        assertEquals(ShaderSafety.Quality.HALF, ShaderSafety.Quality.parse("nonsense"));
        assertEquals(ShaderSafety.Quality.FULL, ShaderSafety.Quality.parse("full"));
        assertEquals("quarter", ShaderSafety.Quality.QUARTER.key());
    }

    @Test
    public void aSecondLossWithinTenSecondsReverts() {
        ShaderSafety.ContextLoss loss = new ShaderSafety.ContextLoss();
        assertEquals(ShaderSafety.ContextLoss.RECREATE, loss.onLoss(1_000L));
        assertEquals(ShaderSafety.ContextLoss.REVERT, loss.onLoss(1_000L + ShaderSafety.LOSS_WINDOW_MS));
        ShaderSafety.ContextLoss spaced = new ShaderSafety.ContextLoss();
        assertEquals(ShaderSafety.ContextLoss.RECREATE, spaced.onLoss(0L));
        assertEquals(ShaderSafety.ContextLoss.RECREATE, spaced.onLoss(ShaderSafety.LOSS_WINDOW_MS + 1));
    }

    @Test
    public void pendingClearsAfterThreeSecondsOfDrawing() {
        assertFalse(ShaderSafety.pendingMayClear(Long.MIN_VALUE, 10_000L));
        assertFalse(ShaderSafety.pendingMayClear(1_000L, 3_999L));
        assertTrue(ShaderSafety.pendingMayClear(1_000L, 4_000L));
    }

    @Test
    public void mouseFollowsShadertoy() {
        SceneTouch t = new SceneTouch();
        float[] m = new float[4];
        t.fill(m, 1000f, 0.5f);
        assertEquals(0f, m[0] + m[1] + m[2] + m[3], 0f);
        t.down(100f, 200f);
        t.fill(m, 1000f, 0.5f);
        // Render px at half scale, y up; z positive while down, w positive on the click frame only.
        assertEquals(50f, m[0], 0f);
        assertEquals(400f, m[1], 0f);
        assertEquals(50f, m[2], 0f);
        assertEquals(400f, m[3], 0f);
        t.frameDrawn();
        t.move(300f, 600f);
        t.fill(m, 1000f, 0.5f);
        assertEquals(150f, m[0], 0f);
        assertEquals(200f, m[1], 0f);
        assertEquals(50f, m[2], 0f);
        assertEquals(-400f, m[3], 0f);
        t.up(300f, 600f);
        t.move(900f, 900f);
        t.fill(m, 1000f, 0.5f);
        assertEquals(150f, m[0], 0f);
        assertEquals(-50f, m[2], 0f);
        assertFalse(t.isDown());
    }

    @Test
    public void mouseSharesTheRenderTargetsSpaceOnEachAxis() {
        // A 1080 x 2401 view at Half renders 540 x 1201: y scales by 1201/2401, not by 0.5.
        SceneTouch t = new SceneTouch();
        float[] m = new float[4];
        float sx = 540f / 1080f, sy = 1201f / 2401f;
        t.down(1080f, 0f);
        t.fill(m, 2401f, sx, sy);
        assertEquals(540f, m[0], 1e-3f);
        assertEquals(1201f, m[1], 1e-3f);
        t.move(0f, 2401f);
        t.fill(m, 2401f, sx, sy);
        assertEquals(0f, m[0], 0f);
        assertEquals(0f, m[1], 0f);
    }

    @Test
    public void shaderGuardIsOnUnlessOff() {
        assertTrue(SceneFramePolicy.shaderGuard(null));
        assertTrue(SceneFramePolicy.shaderGuard(""));
        assertTrue(SceneFramePolicy.shaderGuard("on"));
        assertFalse(SceneFramePolicy.shaderGuard(" off "));
    }

    @Test
    public void motionParsesAndFormats() {
        assertEquals(WallpaperMotion.Kind.PHOTO, WallpaperMotion.parse("").kind);
        assertEquals(WallpaperMotion.Kind.PHOTO, WallpaperMotion.parse(null).kind);
        assertEquals(WallpaperMotion.Kind.PHOTO, WallpaperMotion.parse("aurora").kind);
        WallpaperMotion s = WallpaperMotion.parse("shader:abc123");
        assertEquals(WallpaperMotion.Kind.SHADER, s.kind);
        assertEquals("abc123", s.id);
        assertEquals("shader:abc123", s.format());
        assertEquals(s, WallpaperMotion.parse(s.format()));
        WallpaperMotion v = WallpaperMotion.parse("video:loop.mp4");
        assertEquals(WallpaperMotion.Kind.VIDEO, v.kind);
        assertEquals("video:loop.mp4", v.format());
        assertEquals("", WallpaperMotion.photo().format());
        assertFalse(WallpaperMotion.photo().moves());
        assertTrue(v.moves());
    }

    @Test
    public void unsafeIdsReadAsPhoto() {
        assertEquals(WallpaperMotion.Kind.PHOTO, WallpaperMotion.parse("shader:").kind);
        assertEquals(WallpaperMotion.Kind.PHOTO, WallpaperMotion.parse("shader:../x").kind);
        assertEquals(WallpaperMotion.Kind.PHOTO, WallpaperMotion.parse("video:a/b.mp4").kind);
        assertEquals(WallpaperMotion.Kind.PHOTO, WallpaperMotion.parse("shader:..").kind);
    }

    @Test
    public void migrationRunsOnlyForAStoredScene() {
        assertEquals(WallpaperMotion.Migration.Plan.NONE, WallpaperMotion.Migration.plan(null));
        assertEquals(WallpaperMotion.Migration.Plan.NONE, WallpaperMotion.Migration.plan("  "));
        assertEquals(WallpaperMotion.Migration.Plan.CLEAR_AND_RESTORE_PHOTO,
            WallpaperMotion.Migration.plan("aurora"));
        assertEquals(5, WallpaperMotion.Migration.OLD_KEYS.length);
    }
}
