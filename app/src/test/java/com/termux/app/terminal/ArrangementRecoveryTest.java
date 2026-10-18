package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.ArrangementRecovery.Saved;
import com.termux.app.terminal.ArrangementRecovery.Start;
import com.termux.app.wall.WallRow;

import org.junit.Test;

/** What a start does with the arrangement after a background kill (wall rework slice 9). */
public class ArrangementRecoveryTest {

    private static final String HOME = WallRow.START_IN_HOME;
    private static final String WORKSPACE = WallRow.START_IN_WORKSPACE;

    @Test
    public void liveShellsWithALayoutComeBackAroundTheSameShells() {
        for (boolean cold : new boolean[]{false, true})
            for (Saved saved : Saved.values())
                assertEquals(Start.RECREATE,
                    ArrangementRecovery.decide(true, true, cold, false, saved, HOME));
        assertEquals("a launch asking for a shell still recreates first", Start.RECREATE,
            ArrangementRecovery.decide(true, true, false, true, Saved.NONE, WORKSPACE));
    }

    @Test
    public void liveShellsWithNoLayoutAreAdoptedAsBefore() {
        assertEquals(Start.ADOPT,
            ArrangementRecovery.decide(false, true, false, false, Saved.ARRANGEMENT, HOME));
        assertEquals("shells started before any activity (a plugin's run)", Start.ADOPT,
            ArrangementRecovery.decide(false, true, true, false, Saved.ARRANGEMENT, WORKSPACE));
    }

    @Test
    public void aColdStartWithAnAutosavedArrangementRebuildsItWhateverStartInSays() {
        assertEquals(Start.REBUILD,
            ArrangementRecovery.decide(false, false, true, false, Saved.ARRANGEMENT, HOME));
        assertEquals(Start.REBUILD,
            ArrangementRecovery.decide(false, false, true, false, Saved.ARRANGEMENT, WORKSPACE));
        assertEquals("a stale layout without shells is no reason to skip it", Start.REBUILD,
            ArrangementRecovery.decide(true, false, true, false, Saved.ARRANGEMENT, null));
    }

    @Test
    public void anEmptyAutosaveKeepsTheWorkspaceEmptyUnlessStartInIsTheWorkspace() {
        assertEquals(Start.EMPTY,
            ArrangementRecovery.decide(false, false, true, false, Saved.EMPTY, HOME));
        assertEquals("an unknown Start in reads as Home", Start.EMPTY,
            ArrangementRecovery.decide(false, false, true, false, Saved.EMPTY, null));
        assertEquals(Start.FRESH,
            ArrangementRecovery.decide(false, false, true, false, Saved.EMPTY, WORKSPACE));
    }

    @Test
    public void aColdStartWithNoAutosaveGetsItsOneShell() {
        assertEquals(Start.FRESH,
            ArrangementRecovery.decide(false, false, true, false, Saved.NONE, HOME));
        assertEquals(Start.FRESH,
            ArrangementRecovery.decide(false, false, true, false, Saved.NONE, WORKSPACE));
    }

    @Test
    public void aLaunchThatNamesItsShellNeverRebuilds() {
        for (Saved saved : Saved.values())
            assertEquals(Start.FRESH,
                ArrangementRecovery.decide(false, false, true, true, saved, HOME));
        assertEquals(Start.FRESH,
            ArrangementRecovery.decide(false, false, false, true, Saved.ARRANGEMENT, HOME));
    }

    @Test
    public void anEmptyServiceInASettledProcessIsTheUsersEmptyWorkspace() {
        for (Saved saved : Saved.values())
            for (String startIn : new String[]{HOME, WORKSPACE})
                assertEquals(Start.EMPTY,
                    ArrangementRecovery.decide(false, false, false, false, saved, startIn));
    }

    @Test
    public void aRebuildOfFloatsOnlyOpensAWindowOnlyForStartInWorkspace() {
        assertTrue(ArrangementRecovery.rebuildWantsFreshWindow(0, WORKSPACE));
        assertFalse(ArrangementRecovery.rebuildWantsFreshWindow(0, HOME));
        assertFalse(ArrangementRecovery.rebuildWantsFreshWindow(2, WORKSPACE));
    }
}
