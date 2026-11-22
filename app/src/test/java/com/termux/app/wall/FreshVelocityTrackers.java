package com.termux.app.wall;

import android.view.VelocityTracker;

import org.robolectric.util.ReflectionHelpers;

/**
 * Empties the framework's pool of recycled {@link VelocityTracker}s, so the next
 * {@link VelocityTracker#obtain()} in a test builds a new one.
 *
 * <p>Robolectric's velocity tracker (its {@code ShadowVelocityTracker}) does not forget its history
 * on {@code clear()}: a cleared movement keeps its position and time, and the velocity reads it
 * back as long as it is no older than the newest movement less 200 ms. A tracker that took 20 or
 * more movements and went back to the pool (the pool is static, and lives as long as the SDK's
 * sandbox, across test classes) hands those movements to the next border drag or keyboard swipe
 * that obtains it: a test whose events carry small times then reads a fling the finger never
 * made. A real device clears the tracker for real; this only keeps the tests apart.
 */
final class FreshVelocityTrackers {

    private FreshVelocityTrackers() {
    }

    /** Drops every tracker the pool holds; call before a test that releases a drag or a swipe. */
    static void drainPool() {
        Object pool = ReflectionHelpers.getStaticField(VelocityTracker.class, "sPool");
        if (pool == null) return;
        while (ReflectionHelpers.callInstanceMethod(pool, "acquire") != null) {
            // Each acquired tracker is dropped, so it is never handed out again.
        }
    }
}
