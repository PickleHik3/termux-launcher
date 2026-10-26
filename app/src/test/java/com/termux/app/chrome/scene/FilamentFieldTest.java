package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilamentFieldTest {

    private static final float ASPECT = 0.45f;

    private static FilamentField started() {
        FilamentField f = new FilamentField();
        f.setAspect(ASPECT);
        f.advanceTo(1000L);
        return f;
    }

    private static void run(FilamentField f, long fromMs, long toMs) {
        for (long t = fromMs; t <= toMs; t += 33L) f.advanceTo(t);
    }

    /** Distance in screen heights. */
    private static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot((x1 - x0) * ASPECT, y1 - y0);
    }

    @Test
    public void objectsDriftAndStayInsideTheWrapMargins() {
        FilamentField f = started();
        float[] x = new float[FilamentField.POOL], y = new float[FilamentField.POOL];
        for (int i = 0; i < x.length; i++) {
            x[i] = f.x(i);
            y[i] = f.y(i);
        }
        run(f, 1033L, 3000L);
        for (int i = 0; i < x.length; i++) {
            float moved = dist(x[i], y[i], f.x(i), f.y(i));
            float floor = 0.7f * FilamentField.SPEED[FilamentField.band(i)] * 2f;
            // Anything that wrapped in these two seconds shows as a far jump: skip it.
            if (moved < 0.5f) assertTrue("object " + i + " moved " + moved, moved > floor);
        }
        run(f, 3033L, 200_000L);
        for (int i = 0; i < FilamentField.POOL; i++) {
            assertTrue(f.x(i) >= -FilamentField.MARGIN_X && f.x(i) <= 1f + FilamentField.MARGIN_X);
            assertTrue(f.y(i) >= -FilamentField.MARGIN_Y && f.y(i) <= 1f + FilamentField.MARGIN_Y);
        }
    }

    @Test
    public void aTouchPushesNearObjectsAwayAndTheKickDecays() {
        FilamentField f = started();
        int i = 6;
        float tx = f.x(i) + 0.01f, ty = f.y(i);
        float before = dist(f.x(i), f.y(i), tx, ty);
        f.setRepel(0, tx, ty, 0.55f, 0.2f);
        run(f, 1033L, 1400L);
        assertTrue("pushed away", dist(f.x(i), f.y(i), tx, ty) > before + 0.01f);
        assertTrue("kick builds", f.kickSpeed(i) > 0.01f);
        assertTrue("kick capped", f.kickSpeed(i) <= FilamentField.KICK_MAX + 1e-4f);
        f.setRepel(0, tx, ty, 0f, 0.2f);
        float k = f.kickSpeed(i);
        run(f, 1433L, 8000L);
        assertTrue("decays by itself", f.kickSpeed(i) < 0.02f * k);
    }

    @Test
    public void anObjectOutOfReachIsNotPushed() {
        FilamentField f = started();
        // A touch two screens away from object 2.
        f.setRepel(0, f.x(2) + 2f, f.y(2), 0.55f, 0.05f);
        run(f, 1033L, 1400L);
        assertEquals(0f, f.kickSpeed(2), 0f);
    }

    @Test
    public void holdFreezesThenMotionContinuesWithoutAJump() {
        FilamentField f = started();
        run(f, 1033L, 5000L);
        long held = 1000L + (5000L - 1033L) / 33L * 33L + 33L;
        f.advanceTo(held);
        float[] x = new float[FilamentField.POOL], y = new float[FilamentField.POOL];
        for (int i = 0; i < x.length; i++) {
            x[i] = f.x(i);
            y[i] = f.y(i);
        }
        float angle = f.angle(1);
        // The scene clock stands still while held: the same time again moves nothing.
        for (int k = 0; k < 50; k++) f.advanceTo(held);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], f.x(i), 0f);
            assertEquals(y[i], f.y(i), 0f);
        }
        assertEquals(angle, f.angle(1), 0f);
        // The next frame after the hold steps by one frame's worth, no more.
        f.advanceTo(held + 33L);
        for (int i = 0; i < x.length; i++) {
            float step = dist(x[i], y[i], f.x(i), f.y(i));
            if (step > 0.5f) continue; // wrapped
            assertTrue("object " + i + " step " + step,
                step <= 1.3f * FilamentField.SPEED[FilamentField.band(i)] * 0.033f + 1e-5f);
        }
    }

    @Test
    public void aLongGapTakesOneBoundedStep() {
        FilamentField f = started();
        float x = f.x(11), y = f.y(11);
        f.advanceTo(1000L + 60_000L);
        float step = dist(x, y, f.x(11), f.y(11));
        if (step < 0.5f) {
            assertTrue(step <= 1.3f * FilamentField.SPEED[2] * FilamentField.MAX_STEP_S + 1e-5f);
        }
    }

    @Test
    public void poolIsOrderedFarToNearWithSevenNixies() {
        int nixies = 0;
        for (int i = 0; i < FilamentField.POOL; i++) {
            if (i > 0) assertTrue(FilamentField.band(i) >= FilamentField.band(i - 1));
            if (FilamentField.kind(i) == FilamentField.NIXIE) {
                assertEquals(nixies, FilamentField.nixieSlot(i));
                nixies++;
            }
        }
        assertEquals(FilamentField.NIXIE_COUNT, nixies);
    }
}
