package com.termux.app.surfaces.cards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.cards.LayoutOverride.Decision;
import com.termux.app.surfaces.cards.LayoutOverride.Reading;
import com.termux.app.surfaces.cards.LayoutOverride.Side;

import org.junit.Test;

import java.util.EnumSet;

/** Customization › Layout: what the switch reads, and when a change asks first (§2). */
public class LayoutOverrideTest {

    @Test
    public void switchReadsOnOnlyWhereEverySideShowsIt() {
        assertEquals(Reading.ON, LayoutOverride.read(true, true));
        assertEquals(Reading.OFF, LayoutOverride.read(false, false));
        assertEquals(Reading.HOME_ONLY, LayoutOverride.read(true, false));
        assertEquals(Reading.WORKSPACE_ONLY, LayoutOverride.read(false, true));
        assertFalse(LayoutOverride.read(true, false).switchOn());
        assertFalse(LayoutOverride.read(false, true).switchOn());
    }

    @Test
    public void aSideThatDoesNotWearTheElementIsLeftOut() {
        // Home never wears the extra keys.
        assertEquals(Reading.ON, LayoutOverride.read(null, true));
        assertEquals(Reading.OFF, LayoutOverride.read(null, false));
        Decision decision = LayoutOverride.decide(null, false, true);
        assertFalse(decision.ask);
        assertEquals(EnumSet.of(Side.WORKSPACE), decision.overrideWrites);
    }

    @Test
    public void agreeingSidesChangeTogetherWithoutAsking() {
        Decision on = LayoutOverride.decide(false, false, true);
        assertFalse(on.ask);
        assertEquals(EnumSet.of(Side.HOME, Side.WORKSPACE), on.overrideWrites);
        Decision off = LayoutOverride.decide(true, true, false);
        assertFalse(off.ask);
        assertEquals(EnumSet.of(Side.HOME, Side.WORKSPACE), off.overrideWrites);
    }

    @Test
    public void aSideWithItsOwnValueIsNamedAndOverrideWritesIt() {
        // The A–Z row hidden on Home, shown on the Workspace: the switch reads off, so a tap
        // asks for on, which would change Home.
        Decision decision = LayoutOverride.decide(false, true, true);
        assertTrue(decision.ask);
        assertEquals(EnumSet.of(Side.HOME), decision.own);
        assertEquals(EnumSet.of(Side.HOME), decision.overrideWrites);

        Decision workspace = LayoutOverride.decide(true, false, true);
        assertTrue(workspace.ask);
        assertEquals(EnumSet.of(Side.WORKSPACE), workspace.own);
    }

    @Test
    public void keepIsNotOfferedWhenItWouldWriteNothing() {
        // Keeping the one side that would change leaves nothing to write: no second Cancel.
        Decision decision = LayoutOverride.decide(false, true, true);
        assertTrue(decision.keepWrites.isEmpty());
        assertFalse(decision.offersKeep());
    }

    @Test
    public void nothingMovesWhenTheChangeIsAlreadyWorn() {
        Decision decision = LayoutOverride.decide(true, true, true);
        assertFalse(decision.ask);
        assertTrue(decision.overrideWrites.isEmpty());
    }
}
