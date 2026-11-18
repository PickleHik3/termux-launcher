package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Page;
import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;
import com.termux.app.page.Rest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Terminal pages' front at the structure point: one page per window, a window
 * step is a front change, and the active pane takes the keys once on a Terminal front.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalPageFrontTest {

    private static final int TERMINAL_CELL = 5;
    private static final int HOME_CELL = 1;

    private static final class FakeHost implements TerminalPageHost {
        @Nullable TerminalPaneController.Window active;
        boolean keysWithTerminal;
        int handBacks;
        View paneHost;
        @Nullable @Override public TerminalPaneController.Window activeWindow() { return active; }
        @NonNull @Override public View paneHost() { return paneHost; }
        @Override public boolean keysWithTerminal() { return keysWithTerminal; }
        @Override public boolean handKeysToTerminal() { handBacks++; return true; }
        @Override public boolean backIsEscape() { return false; }
        @Override public boolean lowerKeyboard() { return false; }
    }

    /** Home's page: counts its fronts. */
    private static final class CountingHome implements Page {
        int on, off;
        @NonNull @Override public PageKind kind() { return PageKind.HOME; }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
        @NonNull @Override public Rest rest(@NonNull Rest.Inputs in) { throw new UnsupportedOperationException(); }
        @Override public void onFront() { on++; }
        @Override public void offFront() { off++; }
        @Override public boolean onBack() { return false; }
    }

    private FakeHost host;
    private TerminalPages terminalPages;
    private CountingHome home;
    private Pages pages;
    private int asks;
    private TerminalPaneController.Window w1;
    private TerminalPaneController.Window w2;

    @Before
    public void setUp() {
        host = new FakeHost();
        host.paneHost = new FrameLayout(Robolectric.buildActivity(android.app.Activity.class).get());
        terminalPages = new TerminalPages(host);
        home = new CountingHome();
        asks = 0;
        pages = new Pages(PageHost.NONE, () -> asks++);
        pages.put(HOME_CELL, home);
        pages.putStops(TERMINAL_CELL, terminalPages);
        w1 = new TerminalPaneController.Window(new TerminalPaneController.Leaf(null));
        w2 = new TerminalPaneController.Window(new TerminalPaneController.Leaf(null));
        host.active = w1;
    }

    private void land(int id, PageKind kind) {
        pages.landed(id, kind);
        pages.applyFront();
    }

    @Test
    public void terminalToHomeRunsTheWindowsOffFrontOnceAndNoTerminalOnFront() {
        land(TERMINAL_CELL, PageKind.TERMINAL);
        host.handBacks = 0;
        pages.committed(HOME_CELL, PageKind.HOME);
        // Home's landing: nothing of the window's page runs again.
        pages.landed(HOME_CELL, PageKind.HOME);
        pages.applyFront();
        assertEquals(1, home.on);
        assertEquals(0, host.handBacks);
    }

    @Test
    public void aWindowStepIsOneFrontChangeAndTheKeysGoToThePaneOnce() {
        land(TERMINAL_CELL, PageKind.TERMINAL);
        host.handBacks = 0;
        asks = 0;
        host.active = w2;
        pages.innerChanged();
        assertEquals(1, asks);
        pages.applyFront();
        assertSame(terminalPages.pageOf(w2), pages.front());
        assertEquals(1, host.handBacks);
        pages.applyFront();
        assertEquals(1, host.handBacks);
    }

    @Test
    public void aWindowStepWithTheKeysOnATerminalHandsNothing() {
        land(TERMINAL_CELL, PageKind.TERMINAL);
        host.handBacks = 0;
        host.keysWithTerminal = true;
        host.active = w2;
        pages.innerChanged();
        pages.applyFront();
        assertEquals(0, host.handBacks);
    }

    @Test
    public void aLandingOnTheTerminalHandsTheKeysOnce() {
        land(HOME_CELL, PageKind.HOME);
        host.handBacks = 0;
        pages.committed(TERMINAL_CELL, PageKind.TERMINAL);
        land(TERMINAL_CELL, PageKind.TERMINAL);
        assertEquals(1, host.handBacks);
    }

    @Test
    public void theSameWindowHasTheSamePage() {
        assertSame(terminalPages.pageOf(w1), terminalPages.pageOf(w1));
        assertSame(terminalPages.pageOf(w1), terminalPages.current());
    }
}
