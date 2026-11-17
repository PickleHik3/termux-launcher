package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * A Hold is named state with a backstop and a logged release reason (ADR 0028 section 4): it ends
 * once, by signal, landing or backstop, and says which.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HoldTest {

    private long now;
    private final List<String> ring = new ArrayList<>();

    @Before
    public void setUp() {
        now = 5_000L;
        Counters.clear();
        Counters.enable(true, (event, kv) -> ring.add(event + " " + kv));
    }

    @After
    public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
    }

    private Hold take(String name, long backstopMs, int maxFrames) {
        return Hold.take(name, backstopMs, maxFrames, () -> now);
    }

    @Test
    public void aHoldIsOpenUntilItsSignalAndSaysSo() {
        Hold hold = take("x11-cover", 160L, Hold.NO_FRAME_LIMIT);
        assertTrue(hold.isOpen());
        assertNull(hold.releaseReason());
        assertEquals("x11-cover", hold.name());
        assertEquals(160L, hold.backstopMs());

        assertTrue(hold.signal());

        assertFalse(hold.isOpen());
        assertEquals(Hold.SIGNAL, hold.releaseReason());
    }

    @Test
    public void theFirstReleaseWinsAndLaterOnesDoNothing() {
        Hold hold = take("x", 100L, 0);
        assertTrue(hold.landing());
        assertFalse(hold.signal());
        assertFalse(hold.release(Hold.BACKSTOP, null));

        assertEquals(Hold.LANDING, hold.releaseReason());
        assertEquals(1, Counters.countOf("hold:x:landing"));
        assertEquals(0, Counters.countOf("hold:x:signal"));
    }

    @Test
    public void everyTakeAndReleaseIsCountedByNameAndReasonAndLogged() {
        take("a", 160L, 0).signal();
        take("a", 160L, 0).release(Hold.BACKSTOP, "ms=160");

        assertEquals(2, Counters.countOf("hold:a"));
        assertEquals(1, Counters.countOf("hold:a:signal"));
        assertEquals(1, Counters.countOf("hold:a:backstop"));
        assertTrue(ring.toString(), ring.contains("hold-take hold=a backstopMs=160"));
        assertTrue(ring.toString(), ring.get(1).startsWith("hold-released hold=a reason=signal heldMs=0"));
    }

    @Test
    public void theTimeBackstopEndsAHoldThatIsAskedAboutAFrameLater() {
        Hold hold = take("x", 160L, 0);
        now += 159L;
        assertTrue(hold.pollBackstop());
        now += 1L;

        assertFalse(hold.pollBackstop());

        assertEquals(Hold.BACKSTOP, hold.releaseReason());
        assertEquals(160L, hold.heldMs());
    }

    @Test
    public void theFrameBackstopCountsOnlyFramesAskedWhileHeld() {
        Hold hold = take("relaunch", 300L, 2);

        assertTrue(hold.onFrame());
        assertTrue(hold.onFrame());
        assertEquals(2, hold.heldFrames());
        assertFalse("the third frame draws", hold.onFrame());

        assertEquals(Hold.BACKSTOP, hold.releaseReason());
        assertFalse("a released hold holds nothing", hold.onFrame());
    }

    @Test
    public void aFrameAfterTheTimeBackstopDrawsEvenWithFramesLeft() {
        Hold hold = take("relaunch", 300L, 40);
        assertTrue(hold.onFrame());
        now += 300L;

        assertFalse(hold.onFrame());
        assertEquals(Hold.BACKSTOP, hold.releaseReason());
    }

    @Test
    public void anArmedBackstopReleasesWithoutAnyoneAsking() {
        Hold hold = take("x", 160L, 0);
        hold.armBackstop(new Handler(Looper.getMainLooper()));

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(161));

        assertEquals(Hold.BACKSTOP, hold.releaseReason());
        assertEquals(1, Counters.countOf("hold:x:backstop"));
    }

    @Test
    public void aSignalCancelsTheArmedBackstop() {
        Hold hold = take("x", 160L, 0);
        hold.armBackstop(new Handler(Looper.getMainLooper()));
        hold.signal();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));

        assertEquals(Hold.SIGNAL, hold.releaseReason());
        assertEquals(0, Counters.countOf("hold:x:backstop"));
    }

    @Test
    public void theOnReleaseCallbackRunsOnceWhoeverEndsTheHold() {
        Hold hold = take("x", 160L, 0);
        int[] runs = {0};
        hold.onRelease(() -> runs[0]++);

        hold.signal();
        hold.signal();
        assertEquals(1, runs[0]);

        hold.onRelease(() -> runs[0]++);
        assertEquals("already ended: runs at once", 2, runs[0]);
    }

    @Test
    public void aQuietHoldIsCountedButOnlyABackstopReachesTheRing() {
        Hold.takeQuiet("foreground-poll", 8000L).signal();
        assertEquals(1, Counters.countOf("hold:foreground-poll"));
        assertEquals(1, Counters.countOf("hold:foreground-poll:signal"));
        assertTrue(ring.toString(), ring.isEmpty());

        Hold.takeQuiet("foreground-poll", 8000L).release(Hold.BACKSTOP, "ms=8000");
        assertEquals(1, ring.size());
        assertTrue(ring.get(0), ring.get(0).startsWith("hold-released hold=foreground-poll reason=backstop"));
    }

    @Test
    public void withTheMotionLogOffNothingIsCountedOrLogged() {
        Counters.enable(false, null);
        take("x", 160L, 0).signal();

        assertTrue(Counters.lines().isEmpty());
        assertTrue(ring.isEmpty());
    }
}
