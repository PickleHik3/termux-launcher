package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.page.Side;
import com.termux.app.window.Counters;
import com.termux.app.window.FrameGuards;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A Home and Terminal landing that changes a row's visibility or parent is counted; a landing
 * between pages of one side whose layouts differ may move rows and counts nothing; commits that
 * are not a landing's count nothing.
 */
public class RowsLandingGuardTest {

    private static final class FakeRows implements RowsLandingGuard.Rows {
        final float[] visibility = {0f, 8f};
        final float[] parent = {10f, 10f};
        @Override public int count() { return 2; }
        @NonNull @Override public String name(int row) { return row == 0 ? "apps" : "keys"; }
        @Override public float visibility(int row) { return visibility[row]; }
        @Override public float parent(int row) { return parent[row]; }
    }

    private final FakeRows rows = new FakeRows();
    private final FrameGuards guards = new FrameGuards();
    private final RowsLandingGuard guard = new RowsLandingGuard(rows);

    @Before public void setUp() {
        Counters.clear();
        Counters.enable(true, null);
        guard.attach(guards);
    }

    @After public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
    }

    @Test public void aHomeAndTerminalLandingThatSwapsRowsIsCounted() {
        guard.onLanding(Side.HOME, Side.TERMINAL, true);
        rows.visibility[1] = 0f;
        guard.afterCommit();
        assertEquals(1, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertTrue(Counters.lines().toString(), Counters.lines().toString().contains("keys.visibility"));
    }

    @Test public void aHomeAndTerminalLandingThatWritesNothingIsSilent() {
        guard.onLanding(Side.TERMINAL, Side.HOME, false);
        guard.afterCommit();
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void aLandingWithinASideMayMoveRowsToNewEdges() {
        guard.onLanding(Side.HOME, Side.HOME, true);
        rows.parent[0] = 11f;
        guard.afterCommit();
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void aCommitThatIsNoLandingsCountsNothing() {
        guard.onLanding(Side.HOME, Side.TERMINAL, false);
        guard.afterCommit();
        rows.visibility[0] = 8f;
        guard.afterCommit();
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    /** A crossing's commit that switches rows before the landing is the rows' own write. */
    @Test public void aCommitBeforeTheLandingIsNoForeignWrite() {
        guard.onLanding(Side.TERMINAL, Side.HOME, false);
        guard.afterCommit();
        rows.visibility[0] = 8f;
        guard.afterCommit();
        guard.onLanding(Side.HOME, Side.TERMINAL, false);
        guard.afterCommit();
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }
}
