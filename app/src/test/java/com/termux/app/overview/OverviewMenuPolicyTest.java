package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.overview.OverviewMenuPolicy.Row;
import com.termux.app.page.PageKind;
import com.termux.app.wall.PageOverviewPolicy.Removal;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class OverviewMenuPolicyTest {

    @Test public void aHomePageOffersSetHomeRenameRemove() {
        assertEquals(Arrays.asList(Row.SET_HOME, Row.RENAME, Row.REMOVE),
            OverviewMenuPolicy.rows(PageKind.HOME, false, 2, false));
    }

    @Test public void theHomePageIsNotOfferedSetAsHome() {
        assertEquals(Arrays.asList(Row.RENAME, Row.REMOVE),
            OverviewMenuPolicy.rows(PageKind.HOME, true, 2, false));
    }

    @Test public void theLastHomePageCannotBeRemoved() {
        assertEquals(Collections.singletonList(Row.RENAME),
            OverviewMenuPolicy.rows(PageKind.HOME, true, 1, false));
        assertEquals(Removal.REFUSED, OverviewMenuPolicy.removal(PageKind.HOME, 1, 3));
    }

    @Test public void aWorkspaceWindowOffersNewSessionRenameAndClose() {
        assertEquals(Arrays.asList(Row.NEW_SESSION, Row.SAVE, Row.RENAME, Row.CLOSE),
            OverviewMenuPolicy.rows(PageKind.TERMINAL, false, 2, true));
    }

    @Test public void anEmptyTerminalPageHasNoMenu() {
        assertTrue(OverviewMenuPolicy.rows(PageKind.TERMINAL, false, 2, false).isEmpty());
    }

    @Test public void theAssistantOffersRenameAndCloseNeverSetHome() {
        assertEquals(Arrays.asList(Row.RENAME, Row.CLOSE),
            OverviewMenuPolicy.rows(PageKind.ASSISTANT, false, 2, false));
        assertEquals(Arrays.asList(Row.RENAME, Row.CLOSE),
            OverviewMenuPolicy.rows(PageKind.ASSISTANT, true, 1, false));
    }

    @Test public void linuxAndDisplayCardsHaveNoMenu() {
        assertTrue(OverviewMenuPolicy.rows(PageKind.LINUX, false, 2, true).isEmpty());
        assertTrue(OverviewMenuPolicy.rows(PageKind.DISPLAY, false, 2, true).isEmpty());
        assertEquals(Removal.REFUSED, OverviewMenuPolicy.removal(PageKind.LINUX, 2, 1));
    }

    @Test public void aHomePageWithThingsAsksForUndoAnEmptyOneGoesAtOnce() {
        assertEquals(Removal.WITH_UNDO, OverviewMenuPolicy.removal(PageKind.HOME, 2, 4));
        assertEquals(Removal.AT_ONCE, OverviewMenuPolicy.removal(PageKind.HOME, 2, 0));
    }

    @Test public void aWindowAsksForUndoWhileItHoldsShells() {
        assertEquals(Removal.WITH_UNDO, OverviewMenuPolicy.removal(PageKind.TERMINAL, 2, 1));
        assertEquals(Removal.AT_ONCE, OverviewMenuPolicy.removal(PageKind.TERMINAL, 2, 0));
    }

    @Test public void theAssistantAlwaysAsksForUndo() {
        assertEquals(Removal.WITH_UNDO, OverviewMenuPolicy.removal(PageKind.ASSISTANT, 2, 0));
    }

    @Test public void cardsAfterARemovedOneMoveUp() {
        assertEquals(2, OverviewMenuPolicy.indexAfterRemoval(3, 1));
        assertEquals(1, OverviewMenuPolicy.indexAfterRemoval(1, 3));
        assertEquals(3, OverviewMenuPolicy.indexAfterRemoval(3, -1));
        assertEquals(1, OverviewMenuPolicy.indexAfterRemoval(1, 1));
    }

    @Test public void aSavedCardOffersOpenInOnlyWithAWorkspaceInFocus() {
        assertEquals(Arrays.asList(OverviewMenuPolicy.SavedRow.OPEN_IN_FOCUS,
            OverviewMenuPolicy.SavedRow.RENAME, OverviewMenuPolicy.SavedRow.DELETE),
            OverviewMenuPolicy.savedRows(true));
        assertEquals(Arrays.asList(OverviewMenuPolicy.SavedRow.RENAME,
            OverviewMenuPolicy.SavedRow.DELETE), OverviewMenuPolicy.savedRows(false));
    }

    @Test public void aSessionCardOffersSaveRenameAndClose() {
        assertEquals(Arrays.asList(Row.SAVE, Row.RENAME, Row.CLOSE),
            OverviewMenuPolicy.sessionRows());
    }
}
