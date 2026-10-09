package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DockedFrameBandsTest {

    @Test
    public void thinBandIsContentPlusAirEitherSide() {
        assertEquals(104, DockedFrameBands.thinBandPx(100, 2));
        assertEquals(100, DockedFrameBands.thinBandPx(100, -5));
    }

    @Test
    public void railBandWinsWhenItStandsOnASide() {
        assertEquals(60, DockedFrameBands.sharedBandPx(60, 104, 50));
    }

    @Test
    public void withoutARailTheWiderThinBandIsShared() {
        assertEquals(104, DockedFrameBands.sharedBandPx(0, 104, 50));
        assertEquals(80, DockedFrameBands.sharedBandPx(0, 0, 80));
        assertEquals(0, DockedFrameBands.sharedBandPx(0, 0, 0));
    }

    @Test
    public void narrowerContentCentres() {
        assertEquals(27, DockedFrameBands.centringPadPx(104, 50));
        assertEquals(0, DockedFrameBands.centringPadPx(40, 50));
    }
}
