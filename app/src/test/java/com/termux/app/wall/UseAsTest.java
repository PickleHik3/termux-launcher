package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.surfaces.EditorCard;
import com.termux.app.surfaces.UseAsCards;
import com.termux.app.surfaces.EditorRows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Use as (editor-redesign §1): what Terminal only takes away (the Home page, Home's settings,
 * Home in Start in, the Home card and both Match rows) and what it leaves, and that an install
 * which never answered is Home screen.
 */
public class UseAsTest {

    @Test public void anythingButTerminalReadsAsHomeScreen() {
        assertSame(UseAs.HOME, UseAs.read(null));
        assertSame(UseAs.HOME, UseAs.read(""));
        assertSame(UseAs.HOME, UseAs.read("home"));
        assertSame(UseAs.HOME, UseAs.read("launcher"));
        assertSame(UseAs.TERMINAL, UseAs.read("terminal"));
        assertEquals("home", UseAs.HOME.value);
        assertEquals("terminal", UseAs.TERMINAL.value);
    }

    @Test public void homeScreenShowsEverything() {
        UseAs home = UseAs.HOME;
        assertTrue(home.showsHomePage());
        assertTrue(home.showsHomeSettings());
        assertTrue(home.offersHomeStartIn());
        for (EditorCard card : EditorCard.values()) assertTrue(card.name(), UseAsCards.reaches(home, card));
        assertSame(EditorCard.HOME, UseAsCards.doorCard(home, EditorCard.HOME));
        assertSame(PageKind.HOME, home.editorPlace(PageKind.HOME));
        assertSame(PageKind.HOME, home.homePage());
    }

    @Test public void terminalOnlyTakesHomeAway() {
        UseAs terminal = UseAs.TERMINAL;
        assertFalse(terminal.showsHomePage());
        assertFalse(terminal.showsHomeSettings());
        assertFalse(terminal.offersHomeStartIn());
        assertSame(PageKind.TERMINAL, terminal.homePage());
    }

    @Test public void terminalOnlyRowIsTheTerminalAloneWindowOrNot() {
        assertEquals(Collections.singletonList(PageKind.TERMINAL), UseAs.TERMINAL.rowPages());
        assertEquals(PaneWallPolicy.rowPages(true), UseAs.HOME.rowPages());
    }

    @Test public void terminalOnlyReadsStartInHomeAsTheTerminal() {
        assertEquals(WallRow.START_IN_TERMINAL, UseAs.TERMINAL.startIn(WallRow.START_IN_HOME));
        assertEquals(WallRow.START_IN_TERMINAL, UseAs.TERMINAL.startIn(null));
        assertEquals(WallRow.START_IN_TERMINAL, UseAs.TERMINAL.startIn(WallRow.START_IN_TERMINAL));
        assertEquals(WallRow.START_IN_DISPLAY, UseAs.TERMINAL.startIn(WallRow.START_IN_DISPLAY));
        assertEquals(WallRow.START_IN_HOME, UseAs.HOME.startIn(WallRow.START_IN_HOME));
        assertEquals(WallRow.START_IN_HOME, UseAs.HOME.startIn("nonsense"));
    }

    @Test public void startInDropsHomeOnlyUnderTerminalOnly() {
        List<String> values = Arrays.asList(WallRow.START_IN_HOME, WallRow.START_IN_TERMINAL,
            WallRow.START_IN_DISPLAY);
        assertEquals(Arrays.asList(0, 1, 2), UseAs.HOME.startInChoices(values));
        assertEquals("Terminal · Display", Arrays.asList(1, 2),
            UseAs.TERMINAL.startInChoices(values));
        assertEquals(Collections.singletonList(1), UseAs.TERMINAL.startInChoices(
            Arrays.asList(WallRow.START_IN_HOME, WallRow.START_IN_TERMINAL)));
    }

    @Test public void terminalOnlyHasNoHomeCard() {
        UseAs terminal = UseAs.TERMINAL;
        assertFalse(UseAsCards.reaches(terminal, EditorCard.HOME));
        assertTrue("the editor at rest is always reachable", UseAsCards.reaches(terminal, null));
        for (EditorCard card : EditorCard.values()) {
            if (card != EditorCard.HOME) assertTrue(card.name(), UseAsCards.reaches(terminal, card));
        }
    }

    @Test public void terminalOnlyHomeDoorsOpenTheEditorAtRest() {
        UseAs terminal = UseAs.TERMINAL;
        assertNull(UseAsCards.doorCard(terminal, EditorCard.HOME));
        assertSame(EditorCard.TERMINAL, UseAsCards.doorCard(terminal, EditorCard.TERMINAL));
        assertSame(EditorCard.APPEARANCE, UseAsCards.doorCard(terminal, EditorCard.APPEARANCE));
        assertNull("never stands the editor on a Home page", terminal.editorPlace(PageKind.HOME));
        assertSame(PageKind.TERMINAL, terminal.editorPlace(PageKind.TERMINAL));
        assertEquals("settings-home hidden by terminal -> REST", terminal.doorLine("settings-home"));
    }

    @Test public void theTerminalCardGainsOnlyPagesUnderTerminalOnly() {
        // Edit pages' usual door is Home's card: under Terminal only it stands on the Terminal's.
        List<EditorRows.Spec> home = EditorRows.rows(EditorCard.TERMINAL,
            new EditorRows.Offers(true, true, true));
        List<EditorRows.Spec> terminal = EditorRows.rows(EditorCard.TERMINAL,
            new EditorRows.Offers(true, true, false));
        assertEquals(home.size() + 1, terminal.size());
        assertEquals(EditorRows.Row.LINK_PAGES, terminal.get(terminal.size() - 1).row);
    }

    @Test public void choosingTerminalOnlyOnHomeMovesTheWall() {
        assertTrue(UseAs.TERMINAL.leaves(PageKind.HOME));
        assertFalse(UseAs.TERMINAL.leaves(PageKind.TERMINAL));
        assertFalse(UseAs.HOME.leaves(PageKind.HOME));
        assertFalse(UseAs.TERMINAL.leaves(null));
    }
}
