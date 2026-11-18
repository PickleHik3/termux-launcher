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
        @Nullable @Override public TerminalPaneController.Window activeWindow() { return null; }
        @NonNull @Override public View paneHost() { throw new UnsupportedOperationException(); }
        @Override public boolean keysWithTerminal() { return true; }
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
}
