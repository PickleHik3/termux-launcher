package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorCard;
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
        for (EditorCard card : EditorCard.values()) assertTrue(card.name(), home.reaches(card));
        assertSame(EditorCard.HOME, home.doorCard(EditorCard.HOME));
        assertSame(PaneWallPage.WIDGETS, home.editorPlace(PaneWallPage.WIDGETS));
        assertSame(PaneWallPage.WIDGETS, home.homePage());
    }

    @Test public void terminalOnlyTakesHomeAway() {
        UseAs terminal = UseAs.TERMINAL;
        assertFalse(terminal.showsHomePage());
        assertFalse(terminal.showsHomeSettings());
        assertFalse(terminal.offersHomeStartIn());
        assertSame(PaneWallPage.TERMINAL, terminal.homePage());
    }

    @Test public void terminalOnlyRowIsTheWorkspaceAloneWindowOrNot() {
        assertEquals(Collections.singletonList(PaneWallPage.TERMINAL), UseAs.TERMINAL.rowPages(true));
        assertEquals(Collections.singletonList(PaneWallPage.TERMINAL), UseAs.TERMINAL.rowPages(false));
        assertEquals(PaneWallPolicy.rowPages(true), UseAs.HOME.rowPages(true));
        assertEquals(PaneWallPolicy.rowPages(false), UseAs.HOME.rowPages(false));
    }

    @Test public void terminalOnlyReadsStartInHomeAsTheWorkspace() {
        assertEquals(WallRow.START_IN_WORKSPACE, UseAs.TERMINAL.startIn(WallRow.START_IN_HOME));
        assertEquals(WallRow.START_IN_WORKSPACE, UseAs.TERMINAL.startIn(null));
        assertEquals(WallRow.START_IN_WORKSPACE, UseAs.TERMINAL.startIn(WallRow.START_IN_WORKSPACE));
        assertEquals(WallRow.START_IN_DISPLAY, UseAs.TERMINAL.startIn(WallRow.START_IN_DISPLAY));
        assertEquals(WallRow.START_IN_HOME, UseAs.HOME.startIn(WallRow.START_IN_HOME));
        assertEquals(WallRow.START_IN_HOME, UseAs.HOME.startIn("nonsense"));
    }

    @Test public void startInDropsHomeOnlyUnderTerminalOnly() {
        List<String> values = Arrays.asList(WallRow.START_IN_HOME, WallRow.START_IN_WORKSPACE,
            WallRow.START_IN_DISPLAY);
        assertEquals(Arrays.asList(0, 1, 2), UseAs.HOME.startInChoices(values));
        assertEquals("Terminal · Display", Arrays.asList(1, 2),
            UseAs.TERMINAL.startInChoices(values));
        assertEquals(Collections.singletonList(1), UseAs.TERMINAL.startInChoices(
            Arrays.asList(WallRow.START_IN_HOME, WallRow.START_IN_WORKSPACE)));
    }

    @Test public void terminalOnlyHasNoHomeCard() {
        UseAs terminal = UseAs.TERMINAL;
        assertFalse(terminal.reaches(EditorCard.HOME));
        assertTrue("the editor at rest is always reachable", terminal.reaches(null));
        for (EditorCard card : EditorCard.values()) {
            if (card != EditorCard.HOME) assertTrue(card.name(), terminal.reaches(card));
        }
    }

    @Test public void terminalOnlyHomeDoorsOpenTheEditorAtRest() {
        UseAs terminal = UseAs.TERMINAL;
        assertNull(terminal.doorCard(EditorCard.HOME));
        assertSame(EditorCard.WORKSPACE, terminal.doorCard(EditorCard.WORKSPACE));
        assertSame(EditorCard.APPEARANCE, terminal.doorCard(EditorCard.APPEARANCE));
        assertNull("never stands the editor on a Home page", terminal.editorPlace(PaneWallPage.WIDGETS));
        assertSame(PaneWallPage.TERMINAL, terminal.editorPlace(PaneWallPage.TERMINAL));
        assertEquals("settings-home hidden by terminal -> REST", terminal.doorLine("settings-home"));
    }

    @Test public void theWorkspaceCardIsTheSameUnderTerminalOnly() {
        // The Match row it lost under Terminal only retired with the halves (ADR 0015 §6).
        List<EditorRows.Spec> home = EditorRows.rows(EditorCard.WORKSPACE,
            new EditorRows.Offers(true, true, true));
        List<EditorRows.Spec> terminal = EditorRows.rows(EditorCard.WORKSPACE,
            new EditorRows.Offers(true, true, false));
        assertEquals(home.size(), terminal.size());
    }

    @Test public void choosingTerminalOnlyOnHomeMovesTheWall() {
        assertTrue(UseAs.TERMINAL.leaves(PaneWallPage.WIDGETS));
        assertFalse(UseAs.TERMINAL.leaves(PaneWallPage.TERMINAL));
        assertFalse(UseAs.HOME.leaves(PaneWallPage.WIDGETS));
        assertFalse(UseAs.TERMINAL.leaves(null));
    }
}
