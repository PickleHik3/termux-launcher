package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageBarModel.Action;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OverviewModelTest {

    /** Ids 1.. in order from a kind string: H Home, T Terminal, A Assistant. */
    private static PageBarModel.Input wall(String kinds, String... windows) {
        PageBarModel.Input in = new PageBarModel.Input();
        List<PageBarModel.Page> pages = new ArrayList<>();
        for (int i = 0; i < kinds.length(); i++) {
            PageKind kind = kinds.charAt(i) == 'H' ? PageKind.HOME
                : kinds.charAt(i) == 'T' ? PageKind.TERMINAL : PageKind.ASSISTANT;
            pages.add(new PageBarModel.Page(i + 1, kind, null));
        }
        in.pages = pages;
        in.windowNames = Arrays.asList(windows);
        in.currentWindow = windows.length == 0 ? -1 : 0;
        return in;
    }

    private static OverviewModel model(PageBarModel.Input in) {
        return OverviewModel.of(PageBarModel.build(in));
    }

    @Test public void homePagesThenEachWindowThenAssistant() {
        OverviewModel m = model(wall("HHTA", "one", "two"));
        List<OverviewModel.Card> cards = m.cards();
        assertEquals(5, cards.size());
        assertEquals(PageKind.HOME, cards.get(0).kind);
        assertEquals(PageKind.HOME, cards.get(1).kind);
        assertEquals(PageKind.TERMINAL, cards.get(2).kind);
        assertEquals(PageKind.TERMINAL, cards.get(3).kind);
        assertEquals(PageKind.ASSISTANT, cards.get(4).kind);
    }

    @Test public void windowsKeepTheirOwnNames() {
        OverviewModel m = model(wall("HT", "build", "logs"));
        assertEquals("build", m.cards().get(1).label);
        assertEquals("logs", m.cards().get(2).label);
    }

    @Test public void originIsThePageTheWallIsOn() {
        PageBarModel.Input in = wall("HHT", "a", "b");
        in.currentPageId = 2;
        OverviewModel m = model(in);
        assertEquals(1, m.originIndex());
        assertTrue(m.cards().get(1).origin);
        assertFalse(m.cards().get(0).origin);
    }

    @Test public void originIsTheWindowInFront() {
        PageBarModel.Input in = wall("HT", "a", "b");
        in.currentPageId = 2;
        in.currentWindow = 1;
        assertEquals(2, model(in).originIndex());
    }

    @Test public void originIsTheCurrentPageNotTheWindowToReturnTo() {
        PageBarModel.Input in = wall("TH", "a", "b");
        in.currentPageId = 2;
        in.currentWindow = 1;
        assertEquals(2, model(in).originIndex());
    }

    @Test public void originFallsBackToTheWindowToReturnTo() {
        PageBarModel.Input in = wall("HT", "a");
        in.currentPageId = -1;
        in.currentWindow = 0;
        assertEquals(1, model(in).originIndex());
    }

    @Test public void originFallsBackToTheFirstCard() {
        PageBarModel.Input in = wall("HH");
        in.currentPageId = -1;
        assertEquals(0, model(in).originIndex());
    }

    @Test public void aWallWithNoCardsHasNoOrigin() {
        OverviewModel m = model(wall(""));
        assertEquals(0, m.cards().size());
        assertEquals(-1, m.originIndex());
    }

    @Test public void aTapOnTheOriginOnlyCloses() {
        PageBarModel.Input in = wall("HH");
        in.currentPageId = 1;
        OverviewModel m = model(in);
        assertEquals(Action.CLOSE, m.cards().get(0).target.action);
        assertEquals(Action.GO_PAGE, m.cards().get(1).target.action);
        assertEquals(2, m.cards().get(1).target.pageId);
    }

    @Test public void aWindowNotInFrontIsGoneToByItsIndex() {
        PageBarModel.Input in = wall("HT", "a", "b");
        in.currentPageId = 1;
        OverviewModel m = model(in);
        assertEquals(Action.GO_TERMINAL, m.cards().get(2).target.action);
        assertEquals(1, m.cards().get(2).target.index);
    }

    @Test public void aTerminalPageWithNoWindowIsOneCard() {
        OverviewModel m = model(wall("HT"));
        assertEquals(2, m.cards().size());
        assertEquals(PageKind.TERMINAL, m.cards().get(1).kind);
    }
}
