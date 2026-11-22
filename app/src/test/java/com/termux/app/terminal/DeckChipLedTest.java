package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DeckChipLedTest {

    private static final int LIT = 0xFFFFB020;
    private static final int DARK = 0xFF101010;

    @Test
    public void theWindowInFrontIsLit() {
        assertEquals(LIT, DeckChipLed.colour(LIT, DARK, true, false, false, 0f));
        assertEquals(LIT, DeckChipLed.colour(LIT, DARK, true, false, true, 0.25f));
    }

    @Test
    public void aWindowWantingTheUserIsLitOverItsWork() {
        assertEquals(LIT, DeckChipLed.colour(LIT, DARK, false, true, false, 0f));
        assertEquals(LIT, DeckChipLed.colour(LIT, DARK, false, true, true, 0.25f));
    }

    @Test
    public void anIdleWindowIsDark() {
        assertEquals(DARK, DeckChipLed.colour(LIT, DARK, false, false, false, 1f));
    }

    @Test
    public void aWorkingWindowBreathesBetweenAQuarterAndFull() {
        assertEquals(LIT, DeckChipLed.colour(LIT, DARK, false, false, true, 1f));
        int floor = DeckChipLed.colour(LIT, DARK, false, false, true, 0.25f);
        assertEquals(DeckChipLed.mix(DARK, LIT, 0.25f), floor);
        // Below the floor is held at the floor, never dark: a working window always shows.
        assertEquals(floor, DeckChipLed.colour(LIT, DARK, false, false, true, 0f));
        assertEquals(LIT, DeckChipLed.colour(LIT, DARK, false, false, true, 3f));
    }

    @Test
    public void mixIsPerChannel() {
        assertEquals(0xFF000000, DeckChipLed.mix(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, DeckChipLed.mix(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0xFF808080, DeckChipLed.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
    }
}
