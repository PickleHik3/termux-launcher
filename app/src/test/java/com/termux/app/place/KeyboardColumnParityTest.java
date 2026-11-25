package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.RestCards.Card;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.wall.PageRef;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Home's keyboard column is the Terminal's (#307, 2026-11-25): with the keyboard up at both rests
 * the column, the keys and the bands under them are laid out and drawn at one rect, so a crossing
 * Home ⇄ Terminal moves no key. Held on pong (2412 px, bands under the keyboard) and tl16 (2400
 * px), Docked and Floating Peek.
 */
public class KeyboardColumnParityTest {

    private static final LayoutFixtures.Fixture[] SCREENS = {LayoutFixtures.PONG,
        LayoutFixtures.TL16};

    private static RestCards rest(LayoutFixtures.Fixture f, PageRef page, boolean floating) {
        return RestCards.of(f.cardsRest(page, true, true, floating));
    }

    /** The cards a rest draws on the keyboard's column and under it, as drawn boxes. */
    private static List<String> columnCards(RestCards cards) {
        List<String> out = new ArrayList<>();
        for (Card card : cards.cards())
            if (card.view == StackView.KEYBOARD_COLUMN || card.view == StackView.TERMINAL_KEYBOARD
                || card.view == StackView.UNDER)
                out.add(card.id + " " + card.box);
        return out;
    }

    private static void assertParity(LayoutFixtures.Fixture f, boolean floating) {
        String at = f.name + (floating ? " Floating" : " Docked");
        RestCards home = rest(f, PageRef.HOME, floating);
        RestCards terminal = rest(f, PageRef.TERMINAL, floating);
        Box homeColumn = home.laidOut(StackView.KEYBOARD_COLUMN);
        Box terminalColumn = terminal.laidOut(StackView.KEYBOARD_COLUMN);
        assertNotNull(at, homeColumn);
        assertEquals(at + " column laid out", terminalColumn, homeColumn);
        assertEquals(at + " bands under the keys", terminal.laidOut(StackView.UNDER),
            home.laidOut(StackView.UNDER));
        // The Terminal's keys on its own host (the unified Docked glass, tl16): Home's keyboard
        // card stands at that host's keyboard rect, the column's.
        if (terminal.card(CardId.KEYBOARD) == null)
            assertEquals(at + " keys drawn", terminalColumn, home.card(CardId.KEYBOARD).box);
        else
            assertEquals(at + " keys and bands drawn", columnCards(terminal), columnCards(home));
        assertEquals(at + " the column's lift", terminal.terminalSideLiftPx(),
            home.terminalSideLiftPx());
        // One keyboard card shared by both rests: none parked for the other side.
        for (Card card : home.parked()) assertFalse(at, card.view == StackView.TERMINAL_KEYBOARD);
        for (Card card : terminal.parked()) assertFalse(at, card.view == StackView.TERMINAL_KEYBOARD);
    }

    @Test public void homesColumnIsTheTerminalsDocked() {
        for (LayoutFixtures.Fixture f : SCREENS) assertParity(f.withDesign(DockStyle.GLASS_TRAY), false);
    }

    @Test public void homesColumnIsTheTerminalsFloatingPeek() {
        for (LayoutFixtures.Fixture f : SCREENS) assertParity(f.withDesign(DockStyle.PEEK_GLASS), true);
    }

    @Test public void homesColumnIsTheTerminalsInEveryDesign() {
        for (LayoutFixtures.Fixture f : SCREENS)
            for (DockStyle design : DockStyle.CHOICES)
                for (boolean floating : new boolean[] {false, true})
                    assertParity(f.withDesign(design), floating);
    }

    /** Pong Docked: the keys stand on the Terminal's apps band, 151 px over Home's old column. */
    @Test public void pongDockedKeysStandOnTheTerminalsApps() {
        RestCards home = rest(LayoutFixtures.PONG.withDesign(DockStyle.GLASS_TRAY), PageRef.HOME,
            false);
        assertEquals(new Box(0f, 1591f, 1080f, 2412f), home.laidOut(StackView.KEYBOARD_COLUMN));
        assertEquals(new Box(0f, 1591f, 1080f, 2261f), home.card(CardId.KEYBOARD).box);
        assertNotNull(home.card(CardId.UNDER));
        assertEquals(new Box(0f, 2261f, 1080f, 2412f), home.card(CardId.UNDER).box);
        // Home's held dock card stays under the cover the column draws.
        assertTrue(home.covered().contains(home.card(CardId.DOCK)));
    }

    /**
     * The shared foot (#307, 2026-11-25): pong with a kept band, Docked Peek, keyboard up at both
     * rests: one foot under the bands, the same card at both rests, from the bands' bottom to the
     * screen's, so neither rest leaves the strip under the gesture pill bare.
     */
    @Test public void oneFootUnderTheBandsAtBothRests() {
        LayoutFixtures.Fixture f = LayoutFixtures.PONG.withFoot(LayoutFixtures.KEPT_BAND_PX,
            LayoutFixtures.FLUSH_PX);
        for (DockStyle design : DockStyle.CHOICES) {
            LayoutFixtures.Fixture d = f.withDesign(design);
            RestCards home = rest(d, PageRef.HOME, false);
            RestCards terminal = rest(d, PageRef.TERMINAL, false);
            String at = d.name;
            Card foot = terminal.columnFoot();
            assertNotNull(at, foot);
            assertEquals(at, foot.box, home.columnFoot().box);
            assertEquals(at, (float) d.heightPx, foot.box.bottom, 0f);
            assertEquals(at, terminal.laidOut(StackView.KEYBOARD_COLUMN).bottom, foot.box.top, 0f);
            assertEquals(at, StackView.KEYBOARD_COLUMN, foot.view);
            // Floating keeps its air under the capsule: no foot there.
            assertEquals(at + " Floating", null, rest(d, PageRef.TERMINAL, true).columnFoot());
        }
    }

    /** With Home's keyboard down its column is its own, whatever the Terminal's keyboard does. */
    @Test public void homesKeyboardDownColumnIsItsOwn() {
        LayoutFixtures.Fixture f = LayoutFixtures.PONG.withDesign(DockStyle.GLASS_TRAY);
        RestCards alone = RestCards.of(f.cardsRestOf(PageRef.HOME, false, false));
        RestCards withTerminal = RestCards.of(f.cardsRest(PageRef.HOME, false, true, false));
        assertEquals(alone.laidOut(StackView.KEYBOARD_COLUMN),
            withTerminal.laidOut(StackView.KEYBOARD_COLUMN));
    }

    @Test public void homeTakesTheTerminalsColumnOnlyUnderItsCover() {
        assertTrue(KeyboardColumn.homeTakesTerminals(true, false, KeyboardForm.DOCKED));
        assertFalse(KeyboardColumn.homeTakesTerminals(false, false, KeyboardForm.DOCKED));
        // A keyboard always on lifts Home's rows: Home keeps its own column.
        assertFalse(KeyboardColumn.homeTakesTerminals(true, true, KeyboardForm.DOCKED));
        assertFalse(KeyboardColumn.homeTakesTerminals(true, false, KeyboardForm.FLOATING));
    }
}
