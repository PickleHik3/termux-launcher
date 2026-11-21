package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;

import org.junit.Before;
import org.junit.Test;

/** Back on a terminal with nothing raised: lowers its keyboard, else goes to Home page 1. */
public class TerminalPageBackTest {

    private static final class FakeHost implements TerminalPageHost {
        boolean escape;
        boolean keyboardUp;
        int lowered;
        @Nullable TerminalPaneController.Window active;
        @Nullable @Override public TerminalPaneController.Window activeWindow() { return active; }
        @NonNull @Override public View paneHost() { throw new UnsupportedOperationException(); }
        @Override public boolean activePaneHoldsKeys() { return true; }
        @Override public boolean handKeysToTerminal() { return true; }
        @Override public boolean backIsEscape() { return escape; }
        @Override public boolean lowerKeyboard() {
            if (!keyboardUp) return false;
            keyboardUp = false;
            lowered++;
            return true;
        }
    }

    private FakeHost host;
    private TerminalPage page;
    private Pages pages;
    private int homes;

    @Before
    public void setUp() {
        host = new FakeHost();
        page = new TerminalPage(host);
        homes = 0;
        pages = new Pages(PageHost.NONE, () -> { });
        pages.setHomeDefault(() -> { homes++; return true; });
        pages.put(5, page);
        pages.landed(5, PageKind.TERMINAL);
    }

    @Test
    public void backMappedToEscapeIsSpentAndTheKeyboardIsUntouched() {
        host.escape = true;
        host.keyboardUp = true;
        assertTrue(page.onBack());
        assertEquals(0, host.lowered);
        assertTrue(host.keyboardUp);
    }

    @Test
    public void backWithTheKeyboardUpLowersItOnce() {
        host.keyboardUp = true;
        assertTrue(page.onBack());
        assertEquals(1, host.lowered);
    }

    @Test
    public void backWithTheKeyboardDownGoesToHomePageOne() {
        assertFalse(page.onBack());
        assertTrue(pages.onBack());
        assertEquals(1, homes);
    }

    @Test
    public void theSecondBackAfterTheKeyboardWentDownGoesHome() {
        host.keyboardUp = true;
        assertTrue(pages.onBack());
        assertEquals(0, homes);
        assertTrue(pages.onBack());
        assertEquals(1, homes);
        assertEquals(1, host.lowered);
    }

    /**
     * The state seen on pong before this page existed (Back did nothing): the Terminal cell's
     * window resolved through the stops, keyboard up, Back not mapped, nothing raised. Four presses
     * through the chain: the first lowers the keyboard, the second goes to Home page 1.
     */
    @Test
    public void backOnAWindowResolvedThroughTheStopsLowersThenGoesHome() {
        Pages chain = new Pages(PageHost.NONE, () -> { });
        int[] home = {0};
        chain.setHomeDefault(() -> { home[0]++; return true; });
        TerminalPages windows = new TerminalPages(host);
        chain.putStops(7, windows);
        host.active = new TerminalPaneController.Window(new TerminalPaneController.Leaf(null));
        chain.landed(7, PageKind.TERMINAL);
        chain.applyFront();
        host.keyboardUp = true;
        assertTrue(chain.onBack());
        assertEquals(1, host.lowered);
        assertEquals(0, home[0]);
        assertTrue(chain.onBack());
        assertEquals(1, home[0]);
        assertEquals(1, host.lowered);
    }
}
