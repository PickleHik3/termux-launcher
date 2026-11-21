package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;

import org.junit.Test;

public class HomeKeyboardCoverTest {

    private static final int K = 600;

    @Test
    public void onlyHomeStandsStill() {
        assertFalse(HomeKeyboardCover.liftsRows(PageKind.HOME));
        for (PageKind page : PageKind.values()) {
            if (page != PageKind.HOME) assertTrue(page.name(), HomeKeyboardCover.liftsRows(page));
        }
    }

    @Test
    public void anAlwaysOnHomeKeepsRidingTheKeyboard() {
        assertTrue(HomeKeyboardCover.liftsRows(PageKind.HOME, true));
        assertFalse(HomeKeyboardCover.liftsRows(PageKind.HOME, false));
        for (PageKind page : PageKind.values()) {
            if (page != PageKind.HOME) {
                assertTrue(page.name(), HomeKeyboardCover.liftsRows(page, true));
                assertTrue(page.name(), HomeKeyboardCover.liftsRows(page, false));
            }
        }
    }

    @Test
    public void aLiftingSideIsNeverDropped() {
        assertEquals(0f, HomeKeyboardCover.rowsDropPx(true, K, 0f), 0f);
        assertEquals(0f, HomeKeyboardCover.imeStandPx(true, 300, 40, 10f), 0f);
    }

    @Test
    public void dropIsWhatTheShareDoesNotAlreadyCarry() {
        assertEquals(K, HomeKeyboardCover.rowsDropPx(false, K, 0f), 0f);
        assertEquals(K, HomeKeyboardCover.rowsDropPx(false, K, -5f), 0f);
        assertEquals(0f, HomeKeyboardCover.rowsDropPx(false, K, K), 0f);
        assertEquals(0f, HomeKeyboardCover.rowsDropPx(false, K, K + 10f), 0f);
    }

    @Test
    public void homeRowsStandAtTheirKeyboardDownRectAtEveryReveal() {
        for (float r : new float[] {0f, .3f, 1f}) {
            float share = StackScene.keyboardTranslationPx(r, K);
            float home = share + HomeKeyboardCover.rowsDropPx(false, K, share);
            float terminal = share + HomeKeyboardCover.rowsDropPx(true, K, share);
            assertEquals("home at " + r, K, home, .001f);
            assertEquals("terminal at " + r, share, terminal, .001f);
        }
    }

    @Test
    public void sharedRowsBlendByHomesShare() {
        assertEquals(0f, HomeKeyboardCover.sharedDropPx(0f, 400f), 0f);
        assertEquals(200f, HomeKeyboardCover.sharedDropPx(.5f, 400f), 0f);
        assertEquals(400f, HomeKeyboardCover.sharedDropPx(1f, 400f), 0f);
        assertEquals(400f, HomeKeyboardCover.sharedDropPx(2f, 400f), 0f);
        assertEquals(0f, HomeKeyboardCover.sharedDropPx(.5f, -10f), 0f);
    }

    @Test
    public void imeStandIsEverythingThatRaisesTheContainer() {
        assertEquals(350f, HomeKeyboardCover.imeStandPx(false, 300, 40, 10f), 0f);
        assertEquals(0f, HomeKeyboardCover.imeStandPx(false, 0, 0, 0f), 0f);
        assertEquals(0f, HomeKeyboardCover.imeStandPx(false, 0, 0, -20f), 0f);
    }

    @Test
    public void coverCutIsThePartUnderTheKeyboardsTop() {
        assertEquals(30, HomeKeyboardCover.coverCutPx(1000f, 970f));
        assertEquals(0, HomeKeyboardCover.coverCutPx(900f, 970f));
        assertEquals(30, HomeKeyboardCover.coverCutPx(1000.4f, 970f));
    }
}
