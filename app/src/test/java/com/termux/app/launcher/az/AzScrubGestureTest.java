package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.az.AzScrubGesture.Bounds;
import com.termux.app.launcher.az.AzScrubGesture.Decision;
import com.termux.app.launcher.az.AzScrubGesture.Edge;
import com.termux.app.launcher.az.AzScrubGesture.EdgeAction;
import com.termux.app.launcher.az.AzScrubGesture.EdgeFrame;
import com.termux.app.launcher.az.AzScrubGesture.EdgeIntake;
import com.termux.app.launcher.az.AzScrubGesture.FrameAction;
import com.termux.app.launcher.az.AzScrubGesture.Geometry;
import com.termux.app.launcher.az.AzScrubGesture.Mode;
import com.termux.app.launcher.az.AzScrubGesture.Track;

import org.junit.Before;
import org.junit.Test;

/**
 * The A–Z scrub's three modes, the thresholds between them and the edge-paging timers.
 *
 * <p>The regressions these guard are the ones a position-only classifier produces: a diagonal
 * thumb arc out of a horizontal scrub that refuses to lock because it never climbed vertically, a
 * lock that pops open again because the thumb drifted a pixel past a row boundary sideways, and an
 * edge dwell that pages once per frame instead of once per entry. Hence the direction-ratio pairs
 * (just-below / just-above), the sticky-unlock pairs, and the full page → cooldown → re-entry
 * sequence.
 *
 * <p>Geometry throughout is a 40px letter row at y=1000, the apps row directly above it at
 * y=900..980, and a 100px extra-keys row below at y=1040..1140, at density 1 so dp == px. That
 * makes the derived thresholds: upward lock at touchY <= 24, unlock band touchY 22..157.2, minimum
 * upward travel 10px, apps-row corridor rawY 898..984, return band rawY 990..1150.
 */
public class AzScrubGestureTest {

    private static final float AZ_LEFT = 0f;
    private static final float AZ_TOP = 1000f;
    private static final float AZ_HEIGHT = 40f;
    private static final float AZ_RIGHT = 1080f;
    private static final float EXTRA_KEYS_HEIGHT = 100f;

    private static final Geometry GEO = new Geometry(
        AZ_LEFT, AZ_TOP, AZ_HEIGHT, EXTRA_KEYS_HEIGHT,
        new Bounds(AZ_LEFT, AZ_TOP, AZ_RIGHT, AZ_TOP + AZ_HEIGHT),
        new Bounds(AZ_LEFT, 900f, AZ_RIGHT, 980f),
        new Bounds(AZ_LEFT, AZ_TOP + AZ_HEIGHT, AZ_RIGHT, AZ_TOP + AZ_HEIGHT + EXTRA_KEYS_HEIGHT),
        1f);

    /** A touch point somewhere inside the apps row's corridor, i.e. picking icons. */
    private static final float CORRIDOR_TOUCH_Y = 950f - AZ_TOP;
    /** A touch point above the letter row but short of the corridor, i.e. an upward lock. */
    private static final float ABOVE_ROW_TOUCH_Y = -10f;

    private static final class FakeClock implements AzScrubGesture.Clock {
        long now = 10_000L;

        @Override
        public long uptimeMillis() {
            return now;
        }
    }

    private FakeClock clock;
    private AzScrubGesture gesture;
    private long eventTime;
    private float lastTouchX;

    @Before
    public void setUp() {
        clock = new FakeClock();
        gesture = new AzScrubGesture(clock);
        eventTime = 5_000L;
        lastTouchX = 500f;
    }

    // --- driving helpers -------------------------------------------------------------------

    private Decision down(char letter, float touchY) {
        lastTouchX = 500f;
        return gesture.onDown(letter, 0, lastTouchX, touchY, AZ_LEFT + lastTouchX, AZ_TOP + touchY,
            eventTime, GEO);
    }

    private Decision move(char letter, float touchY, long dtMs) {
        return move(letter, 0, lastTouchX, touchY, dtMs);
    }

    private Decision move(char letter, int selectionIndex, float touchX, float touchY, long dtMs) {
        eventTime += dtMs;
        lastTouchX = touchX;
        return gesture.onMove(letter, selectionIndex, touchX, touchY, AZ_LEFT + touchX,
            AZ_TOP + touchY, eventTime, GEO);
    }

    private Decision up(char letter, float touchY, long dtMs) {
        eventTime += dtMs;
        return gesture.onUp(letter, 0, lastTouchX, touchY, AZ_LEFT + lastTouchX, AZ_TOP + touchY,
            eventTime, GEO);
    }

    /** Down on the row, then a quick vertical flick up to {@code touchY}. */
    private Decision flickUpTo(float touchY, float fromTouchY) {
        down('B', fromTouchY);
        return move('B', touchY, 16L);
    }

    /** Leaves the machine in {@link Mode#UPWARD_LOCKED} holding 'B'. */
    private void lockUpward() {
        Decision locked = flickUpTo(19f, 40f);
        assertEquals(Mode.UPWARD_LOCKED, locked.mode);
    }

    /** Leaves the machine in {@link Mode#ICON_TRACKING_LOCKED} holding 'B'. */
    private void lockIconTrack() {
        down('B', 20f);
        Decision locked = move('Q', CORRIDOR_TOUCH_Y, 16L);
        assertEquals(Mode.ICON_TRACKING_LOCKED, locked.mode);
    }

    // --- the letter track -----------------------------------------------------------------

    @Test
    public void downStartsTrackingAndPersistsThePreview() {
        assertEquals(Mode.IDLE, gesture.mode());
        assertFalse(gesture.isActive());

        Decision decision = down('D', 20f);

        assertEquals(Mode.AZ_TRACKING, decision.mode);
        assertTrue(gesture.isActive());
        assertEquals(Track.WAVE, decision.track);
        assertTrue(decision.applyLockedInline);
        assertEquals(Decision.NO_INLINE_LETTER, decision.lockedInlineLetter);
        assertTrue(decision.persistPreview);
        assertEquals('D', decision.previewLetter);
        assertFalse(decision.requestFocusResolve);
        assertFalse(decision.releasing);
        assertFalse(decision.clearFocusedEntry);
    }

    @Test
    public void pinnedSymbolEndsTheScrubWithoutTouchingAnythingElse() {
        Decision decision = down(AzScrubGesture.PINNED_APPS_SYMBOL, 20f);

        assertTrue(decision.pinnedSymbolReset);
        assertFalse(decision.persistPreview);
        assertNull(decision.track);
        // Still recorded the point the FX layers are drawn from before bailing out.
        assertEquals(AZ_TOP + 20f, gesture.lastRawY(), 0.001f);
    }

    @Test
    public void horizontalScrubKeepsPersistingTheLetterUnderTheFinger() {
        down('B', 20f);
        Decision decision = move('F', 3, 700f, 20f, 16L);

        assertEquals(Mode.AZ_TRACKING, decision.mode);
        assertTrue(decision.persistPreview);
        assertEquals('F', decision.previewLetter);
        assertEquals(3, decision.previewSelectionIndex);
        assertNull(decision.track);
        assertFalse(decision.applyLockedInline);
    }

    @Test
    public void filterBandUpperEdgeDecidesWhetherAScrubStillPersists() {
        // A sideways-dominated drag out of the top of the row: no lock, so only the band decides.
        // touchY >= -(rowHeight * 0.10) == -4 persists; anything higher is off the band.
        float[] touchYs = {-4f, -4.5f};
        boolean[] expectedPersist = {true, false};
        for (int i = 0; i < touchYs.length; i++) {
            setUp();
            down('B', 20f);
            Decision decision = move('B', 0, 600f, touchYs[i], 16L);
            assertEquals("touchY=" + touchYs[i], Mode.AZ_TRACKING, decision.mode);
            assertEquals("touchY=" + touchYs[i], expectedPersist[i], decision.persistPreview);
        }
    }

    // --- the upward lock ------------------------------------------------------------------

    @Test
    public void upwardLockNeedsTheTouchRatio() {
        // From touchY=40, straight up: the position gate is touchY <= rowHeight * 0.60 == 24.
        float[] touchYs = {24f, 24.5f};
        Mode[] expected = {Mode.UPWARD_LOCKED, Mode.AZ_TRACKING};
        for (int i = 0; i < touchYs.length; i++) {
            setUp();
            Decision decision = flickUpTo(touchYs[i], 40f);
            assertEquals("touchY=" + touchYs[i], expected[i], decision.mode);
        }
    }

    @Test
    public void upwardLockNeedsTheMinimumTravel() {
        // From touchY=33, straight up, both landing inside the position gate: the travel gate is
        // max(density * 10, rowHeight * 0.22) == 10px.
        float[] touchYs = {23f, 23.5f};
        Mode[] expected = {Mode.UPWARD_LOCKED, Mode.AZ_TRACKING};
        for (int i = 0; i < touchYs.length; i++) {
            setUp();
            Decision decision = flickUpTo(touchYs[i], 33f);
            assertEquals("touchY=" + touchYs[i], expected[i], decision.mode);
        }
    }

    @Test
    public void aLingeringSidewaysComponentReAnchorsTheClimbAndBlocksTheLock() {
        // Two samples: a sideways scrub of dx px, then an identical 10px climb into the position
        // gate. While the smoothed vector is still sideways-dominated (by 1.3) the climb reference
        // keeps moving to the finger, so the climb never counts as travel.
        //
        // This — not UPWARD_DIRECTION_RATIO — is what actually rejects a sideways-dominated drag:
        // failing the 0.45 direction ratio implies failing the 1.3 dominance test, which re-anchors
        // the reference on the same sample and zeroes the travel first.
        float[] scrubDx = {17f, 19f};
        Mode[] expected = {Mode.UPWARD_LOCKED, Mode.AZ_TRACKING};
        for (int i = 0; i < scrubDx.length; i++) {
            setUp();
            down('B', 33f);
            move('C', 0, 500f + scrubDx[i], 33f, 16L);
            Decision decision = move('C', 0, 500f + scrubDx[i], 23f, 16L);
            assertEquals("scrubDx=" + scrubDx[i], expected[i], decision.mode);
        }
    }

    @Test
    public void theUpwardLockHoldsThePreviewAnchorNotTheLetterUnderTheFinger() {
        down('B', 33f);
        move('M', 7, 517f, 33f, 16L);
        Decision locked = move('Z', 25, 517f, 23f, 16L);

        assertEquals(Mode.UPWARD_LOCKED, locked.mode);
        assertEquals(Track.INLINE_EMPHASIS, locked.track);
        assertTrue(locked.applyLockedInline);
        assertEquals('M', locked.lockedInlineLetter);
        assertTrue(locked.persistPreview);
        assertEquals('M', locked.previewLetter);
        assertEquals(7, locked.previewSelectionIndex);
        assertEquals('M', gesture.lockedLetter());
        assertEquals(7, gesture.lockedSelectionIndex());
        // The overlay follows the lock, not the finger.
        assertEquals('M', locked.overlayLetter);
    }

    @Test
    public void theUpwardLockIsStickyUntilDeliberateDownwardMotionInsideTheBand() {
        // touchY 22..157.2 with downward-dominant recent motion releases; outside it, nothing does.
        float[] touchYs = {22f, 21.9f, 157f, 158f};
        Mode[] expected = {
            Mode.AZ_TRACKING,     // exactly on the 0.55 return ratio
            Mode.UPWARD_LOCKED,   // a tenth of a pixel short of it
            Mode.AZ_TRACKING,     // just inside the far edge of the unlock band
            Mode.UPWARD_LOCKED,   // past the band: the finger is somewhere else entirely
        };
        for (int i = 0; i < touchYs.length; i++) {
            setUp();
            lockUpward();
            Decision decision = move('B', touchYs[i], 200L);
            assertEquals("touchY=" + touchYs[i], expected[i], decision.mode);
            if (expected[i] == Mode.AZ_TRACKING) {
                assertTrue(decision.clearFocusedEntry);
                assertEquals(Track.WAVE, decision.track);
                assertTrue(decision.applyLockedInline);
                assertEquals(Decision.NO_INLINE_LETTER, decision.lockedInlineLetter);
            } else {
                assertFalse(decision.clearFocusedEntry);
                assertEquals('B', decision.lockedInlineLetter);
            }
        }
    }

    @Test
    public void aSlowDriftBackDownDoesNotBreakTheUpwardLock() {
        lockUpward();
        // Well inside the unlock band by position, but the smoothed vector is still climbing.
        Decision decision = move('B', 30f, 16L);
        assertEquals(Mode.UPWARD_LOCKED, decision.mode);
    }

    // --- the icon track -------------------------------------------------------------------

    @Test
    public void enteringTheAppsRowCorridorLocksOntoTheIconTrack() {
        down('B', 20f);
        Decision decision = move('Q', CORRIDOR_TOUCH_Y, 16L);

        assertEquals(Mode.ICON_TRACKING_LOCKED, decision.mode);
        assertEquals('B', decision.lockedInlineLetter);
        assertEquals('B', decision.previewLetter);
        assertEquals(Track.INLINE_EMPHASIS, decision.track);
        assertTrue(decision.requestFocusResolve);
    }

    @Test
    public void releasingOverTheCorridorNeverReachesTheIconTrack() {
        // The icon-track lock is the one transition that refuses to happen on the releasing sample,
        // so a finger lifted over the apps row cannot launch anything: it locks upward instead, and
        // the activity has no focus result to launch from.
        down('B', 20f);
        Decision decision = up('Q', CORRIDOR_TOUCH_Y, 16L);

        assertEquals(Mode.UPWARD_LOCKED, decision.mode);
        assertTrue(decision.releasing);
        assertFalse(decision.requestFocusResolve);
    }

    @Test
    public void theUpwardLockIsPromotedToTheIconTrackWhenTheFingerReachesTheCorridor() {
        lockUpward();
        Decision decision = move('B', ABOVE_ROW_TOUCH_Y, 16L);
        assertEquals(Mode.UPWARD_LOCKED, decision.mode);

        Decision promoted = move('B', CORRIDOR_TOUCH_Y, 16L);
        assertEquals(Mode.ICON_TRACKING_LOCKED, promoted.mode);
        assertFalse(promoted.clearFocusedEntry);
        assertNull(promoted.track);
        assertEquals('B', promoted.lockedInlineLetter);
        assertTrue(promoted.requestFocusResolve);
    }

    @Test
    public void theIconLockReleasesOnlyOnDeliberateDownwardMotionIntoTheReturnBand() {
        // rawY 990..1150 is the return band; 1100 is inside it, 1200 is below everything.
        float[] touchYs = {1100f - AZ_TOP, 1200f - AZ_TOP};
        Mode[] expected = {Mode.AZ_TRACKING, Mode.ICON_TRACKING_LOCKED};
        for (int i = 0; i < touchYs.length; i++) {
            setUp();
            lockIconTrack();
            Decision decision = move('B', touchYs[i], 200L);
            assertEquals("touchY=" + touchYs[i], expected[i], decision.mode);
            // Either way the preview stays on the locked letter, never the letter under the finger.
            assertEquals('B', decision.previewLetter);
        }
    }

    @Test
    public void theIconLockSurvivesSidewaysWanderingInsideTheCorridor() {
        lockIconTrack();
        Decision decision = move('B', 0, 200f, CORRIDOR_TOUCH_Y, 200L);
        assertEquals(Mode.ICON_TRACKING_LOCKED, decision.mode);
    }

    @Test
    public void releasingOnTheIconTrackKeepsTheModeSoTheActivityCanLaunch() {
        lockIconTrack();
        Decision decision = up('B', CORRIDOR_TOUCH_Y, 16L);

        assertEquals(Mode.ICON_TRACKING_LOCKED, decision.mode);
        assertTrue(decision.releasing);
        assertTrue(decision.requestFocusResolve);
    }

    // --- edge paging ----------------------------------------------------------------------

    @Test
    public void edgePagingIsOnlyPlannedWhileTheIconTrackIsLocked() {
        assertEquals(EdgeAction.STOP, gesture.onEdgeFocus(Edge.RIGHT, false).action);
        down('B', 20f);
        assertEquals(EdgeAction.STOP, gesture.onEdgeFocus(Edge.RIGHT, false).action);
        lockUpward();
        assertEquals(EdgeAction.STOP, gesture.onEdgeFocus(Edge.RIGHT, false).action);
    }

    @Test
    public void aDwellAtTheEdgePagesOnceAndThenNeedsTheFingerToLeaveAndComeBack() {
        lockIconTrack();

        EdgeIntake started = gesture.onEdgeFocus(Edge.RIGHT, false);
        assertEquals(EdgeAction.START, started.action);
        assertEquals(0f, started.dwellProgress, 0.001f);
        assertEquals(Edge.RIGHT, gesture.edgePagingEdge());

        clock.now += 140L;
        EdgeIntake same = gesture.onEdgeFocus(Edge.RIGHT, true);
        assertEquals(EdgeAction.CONTINUE, same.action);
        assertEquals(0.25f, same.dwellProgress, 0.001f);

        EdgeFrame early = gesture.onEdgeFrame(Edge.RIGHT);
        assertEquals(FrameAction.WAIT, early.action);
        assertEquals(0.25f, early.dwellProgress, 0.001f);
        assertEquals(0, early.pageDelta);

        // One millisecond short of the initial delay is still a wait.
        clock.now += AzScrubGesture.EDGE_PAGE_INITIAL_DELAY_MS - 140L - 1L;
        assertEquals(FrameAction.WAIT, gesture.onEdgeFrame(Edge.RIGHT).action);

        clock.now += 1L;
        EdgeFrame paged = gesture.onEdgeFrame(Edge.RIGHT);
        assertEquals(FrameAction.PAGE, paged.action);
        assertEquals(1, paged.pageDelta);
        assertEquals(1f, paged.dwellProgress, 0.001f);
        assertTrue(gesture.edgeRequiresReentry());

        // Parked at the same edge: the latch, not the clock, is what stops a second flip.
        clock.now += 10_000L;
        EdgeFrame latched = gesture.onEdgeFrame(Edge.RIGHT);
        assertEquals(FrameAction.WAIT, latched.action);
        assertEquals(0f, latched.dwellProgress, 0.001f);
        assertEquals(EdgeAction.SUPPRESS, gesture.onEdgeFocus(Edge.RIGHT, true).action);

        // Leaving the edge clears the latch; coming back starts a fresh dwell.
        assertEquals(EdgeAction.STOP, gesture.onEdgeFocus(Edge.NONE, true).action);
        assertFalse(gesture.edgeRequiresReentry());
        assertEquals(EdgeAction.START, gesture.onEdgeFocus(Edge.RIGHT, false).action);
    }

    @Test
    public void theCooldownSuppressesAReEntryThatComesBackTooFast() {
        lockIconTrack();
        gesture.onEdgeFocus(Edge.LEFT, false);
        clock.now += AzScrubGesture.EDGE_PAGE_INITIAL_DELAY_MS;
        assertEquals(-1, gesture.onEdgeFrame(Edge.LEFT).pageDelta);

        // Off the edge and straight back inside the cooldown window: suppressed.
        assertEquals(EdgeAction.STOP, gesture.onEdgeFocus(Edge.NONE, true).action);
        clock.now += AzScrubGesture.EDGE_PAGE_COOLDOWN_MS - 1L;
        assertEquals(EdgeAction.SUPPRESS, gesture.onEdgeFocus(Edge.LEFT, false).action);

        clock.now += 1L;
        assertEquals(EdgeAction.START, gesture.onEdgeFocus(Edge.LEFT, false).action);
    }

    @Test
    public void aFrameOnADifferentEdgeAsksForAReFocusInsteadOfPaging() {
        lockIconTrack();
        gesture.onEdgeFocus(Edge.RIGHT, false);
        clock.now += AzScrubGesture.EDGE_PAGE_INITIAL_DELAY_MS * 2;

        EdgeFrame frame = gesture.onEdgeFrame(Edge.LEFT);
        assertEquals(FrameAction.REFOCUS, frame.action);
        assertEquals(0, frame.pageDelta);
        // Nothing was consumed: the edge the loop was started for is still the live one.
        assertEquals(Edge.RIGHT, gesture.edgePagingEdge());
        assertFalse(gesture.edgeRequiresReentry());
    }

    // --- reset ----------------------------------------------------------------------------

    @Test
    public void cancelDropsEverythingButTheLastPointAndTheCooldown() {
        lockIconTrack();
        gesture.onEdgeFocus(Edge.RIGHT, false);
        clock.now += AzScrubGesture.EDGE_PAGE_INITIAL_DELAY_MS;
        assertEquals(FrameAction.PAGE, gesture.onEdgeFrame(Edge.RIGHT).action);

        gesture.onCancel();

        assertEquals(Mode.IDLE, gesture.mode());
        assertFalse(gesture.isActive());
        assertFalse(gesture.hasLockedSelection());
        assertEquals(AzScrubGesture.NO_LETTER, gesture.lockedLetter());
        assertEquals(Edge.NONE, gesture.edgePagingEdge());
        assertFalse(gesture.edgeRequiresReentry());
        // The FX layers are still clearing themselves from the last point, so it survives.
        assertEquals(950f, gesture.lastRawY(), 0.001f);

        // The cooldown deliberately outlives the gesture: a regrab cannot page again immediately.
        lockIconTrack();
        assertEquals(EdgeAction.SUPPRESS, gesture.onEdgeFocus(Edge.RIGHT, false).action);
        clock.now += AzScrubGesture.EDGE_PAGE_COOLDOWN_MS;
        assertEquals(EdgeAction.START, gesture.onEdgeFocus(Edge.RIGHT, false).action);
    }

    @Test
    public void aFreshDownAfterAResetStartsFromTheLetterTrackAgain() {
        lockIconTrack();
        gesture.reset();

        Decision decision = down('K', 20f);
        assertEquals(Mode.AZ_TRACKING, decision.mode);
        assertEquals('K', decision.previewLetter);
        // No stale preview anchor, so a corridor entry this time locks onto 'K'.
        Decision locked = move('Q', CORRIDOR_TOUCH_Y, 16L);
        assertEquals('K', locked.lockedInlineLetter);
    }
}
