package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/** A landing's commit dresses the dock once: the Dock design step finds the place pass's dress. */
public class CommitDockDressTest {

    private static CommitDockDress.Inputs inputs(DockStyle design, boolean drawerTile,
                                                 int revision, boolean statusCompact,
                                                 boolean keyboardUp, int halfRedresses) {
        return new CommitDockDress.Inputs(PaneWallPage.WIDGETS, PaneWallPage.WIDGETS, design,
            false, drawerTile, revision, statusCompact, PlaceLayout.Edge.TOP, keyboardUp,
            halfRedresses);
    }

    private static CommitDockDress.Inputs base() {
        return inputs(DockStyle.GLASS_TRAY, true, 3, true, false, 0);
    }

    @Test
    public void aDressInTheCommitOnTheSameInputsIsWorn() {
        CommitDockDress dress = new CommitDockDress();
        dress.begin();
        assertFalse("nothing dressed yet", dress.worn(base()));
        dress.dressed(base());
        assertTrue(dress.worn(base()));
    }

    @Test
    public void anyInputThatMovedSinceTheDressIsNotWorn() {
        CommitDockDress dress = new CommitDockDress();
        dress.begin();
        dress.dressed(base());
        DockStyle other = DockStyle.values()[1];
        assertFalse(dress.worn(inputs(other, true, 3, true, false, 0)));
        assertFalse(dress.worn(inputs(DockStyle.GLASS_TRAY, false, 3, true, false, 0)));
        assertFalse(dress.worn(inputs(DockStyle.GLASS_TRAY, true, 4, true, false, 0)));
        assertFalse(dress.worn(inputs(DockStyle.GLASS_TRAY, true, 3, false, false, 0)));
        assertFalse(dress.worn(inputs(DockStyle.GLASS_TRAY, true, 3, true, true, 0)));
        assertFalse("a half re-dress ran after it",
            dress.worn(inputs(DockStyle.GLASS_TRAY, true, 3, true, false, 1)));
        assertFalse(dress.worn(new CommitDockDress.Inputs(PaneWallPage.TERMINAL,
            PaneWallPage.WIDGETS, DockStyle.GLASS_TRAY, false, true, 3, true,
            PlaceLayout.Edge.TOP, false, 0)));
        assertFalse(dress.worn(new CommitDockDress.Inputs(PaneWallPage.WIDGETS,
            PaneWallPage.TERMINAL, DockStyle.GLASS_TRAY, false, true, 3, true,
            PlaceLayout.Edge.TOP, false, 0)));
        assertFalse(dress.worn(new CommitDockDress.Inputs(PaneWallPage.WIDGETS,
            PaneWallPage.WIDGETS, DockStyle.GLASS_TRAY, true, true, 3, true,
            PlaceLayout.Edge.TOP, false, 0)));
        assertFalse(dress.worn(new CommitDockDress.Inputs(PaneWallPage.WIDGETS,
            PaneWallPage.WIDGETS, DockStyle.GLASS_TRAY, false, true, 3, true,
            PlaceLayout.Edge.BOTTOM, false, 0)));
    }

    @Test
    public void outsideACommitNothingIsNotedAndNothingCarriesToTheNext() {
        CommitDockDress dress = new CommitDockDress();
        dress.dressed(base());
        dress.begin();
        assertFalse("a dress before the commit is not the commit's", dress.worn(base()));
        dress.dressed(base());
        dress.end();
        assertFalse(dress.open());
        assertFalse(dress.worn(base()));
        dress.begin();
        assertFalse("the last commit's dress is not this one's", dress.worn(base()));
    }

    @Test
    public void theDesignStepDressesOnlyWhatTheCommitHasNotWorn() {
        assertEquals(CommitDockDress.Action.NONE,
            CommitDockDress.decide(true, false, false, false));
        assertEquals(CommitDockDress.Action.SYNC_DRAWER_TILE,
            CommitDockDress.decide(false, true, false, false));
        assertEquals(CommitDockDress.Action.CANCEL_EASE_AND_RESTYLE,
            CommitDockDress.decide(false, false, false, false));
        assertEquals(CommitDockDress.Action.ALREADY_WORN,
            CommitDockDress.decide(false, false, true, false));
    }

    @Test
    public void aDressMadeBesideABandEaseIsDressedAgainAtTheLanding() {
        assertEquals(CommitDockDress.Action.CANCEL_EASE_AND_RESTYLE,
            CommitDockDress.decide(false, false, true, true));
    }
}
