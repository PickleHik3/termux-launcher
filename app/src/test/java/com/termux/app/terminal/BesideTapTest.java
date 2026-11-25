package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BesideTapTest {

    private static final float SLOP = 10f;

    @Test
    public void aPressBesideTheFloatsThatLiftsWhereItWentDownIsATap() {
        BesideTap tap = new BesideTap(SLOP);
        tap.down(100f, 100f, true);
        assertTrue(tap.pending());
        assertTrue(tap.up(105f, 96f));
        assertFalse("one lift, one tap", tap.up(105f, 96f));
    }

    @Test
    public void aPressOnAFloatIsNoTapBesideThem() {
        BesideTap tap = new BesideTap(SLOP);
        tap.down(100f, 100f, false);
        assertFalse(tap.up(100f, 100f));
    }

    @Test
    public void aSwipeThatStartedBesideTheFloatsIsNoTapEvenIfItComesBack() {
        BesideTap tap = new BesideTap(SLOP);
        tap.down(100f, 100f, true);
        tap.move(160f, 100f);
        tap.move(100f, 100f);
        assertFalse(tap.up(100f, 100f));
    }

    @Test
    public void aLiftPastTheSlopIsNoTap() {
        BesideTap tap = new BesideTap(SLOP);
        tap.down(100f, 100f, true);
        assertFalse(tap.up(100f, 140f));
    }

    @Test
    public void aCancelOrASecondFingerIsNoTap() {
        BesideTap tap = new BesideTap(SLOP);
        tap.down(100f, 100f, true);
        tap.cancel();
        assertFalse(tap.up(100f, 100f));
    }

    @Test
    public void aNewPressForgetsTheLast() {
        BesideTap tap = new BesideTap(SLOP);
        tap.down(100f, 100f, true);
        tap.down(300f, 300f, false);
        assertFalse(tap.up(300f, 300f));
    }
}
