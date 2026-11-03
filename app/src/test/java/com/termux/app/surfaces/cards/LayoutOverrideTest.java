package com.termux.app.surfaces.cards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.LayoutVariant;
import com.termux.app.surfaces.cards.LayoutOverride.Decision;
import com.termux.app.surfaces.cards.LayoutOverride.Reading;
import com.termux.app.surfaces.cards.LayoutOverride.Side;

import org.junit.Test;

import java.util.Arrays;
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
        assertEquals(EnumSet.of(Side.WORKSPACE), decision.writes);
    }

    @Test
    public void agreeingSidesChangeTogetherWithoutAsking() {
        Decision on = LayoutOverride.decide(false, false, true);
        assertFalse(on.ask);
        assertNull(on.own);
        assertEquals(EnumSet.of(Side.HOME, Side.WORKSPACE), on.writes);
        Decision off = LayoutOverride.decide(true, true, false);
        assertFalse(off.ask);
        assertEquals(EnumSet.of(Side.HOME, Side.WORKSPACE), off.writes);
    }

    @Test
    public void aSideWithItsOwnValueIsNamedAndOverrideWritesIt() {
        // The A–Z row hidden on Home, shown on the Workspace: the switch reads off, so a tap
        // asks for on, which would change Home.
        Decision decision = LayoutOverride.decide(false, true, true);
        assertTrue(decision.ask);
        assertEquals(Side.HOME, decision.own);
        assertEquals(EnumSet.of(Side.HOME), decision.writes);

        Decision workspace = LayoutOverride.decide(true, false, true);
        assertTrue(workspace.ask);
        assertEquals(Side.WORKSPACE, workspace.own);
    }

    @Test
    public void nothingMovesWhenTheChangeIsAlreadyWorn() {
        Decision decision = LayoutOverride.decide(true, true, true);
        assertFalse(decision.ask);
        assertTrue(decision.writes.isEmpty());
    }

    @Test
    public void theWorkspaceIsTheNormalAndDisplayLayouts() {
        assertEquals(Arrays.asList(LayoutVariant.HOME, LayoutVariant.NORMAL, LayoutVariant.DISPLAY),
            LayoutOverride.variantsOf(EnumSet.of(Side.HOME, Side.WORKSPACE)));
        assertEquals(Arrays.asList(LayoutVariant.HOME),
            LayoutOverride.variantsOf(EnumSet.of(Side.HOME)));
    }
}
