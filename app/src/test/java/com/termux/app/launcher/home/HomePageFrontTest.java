package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Page;
import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;
import com.termux.app.page.Rest;

import org.junit.Before;
import org.junit.Test;

public class HomePageFrontTest {

    private static final int HOME1 = 1, HOME2 = 2, TERMINAL = 3;

    private static final class CountingHost implements HomePageHost {
        final Pages pages;
        int dismiss, leaving, shown;

        CountingHost(Pages pages) { this.pages = pages; }

        @Override public void dismissControls() { dismiss++; }
        @Override public void leaving() { leaving++; }
        @Override public void shown() { shown++; }
        @Nullable @Override public PageKind committedKind() { return pages.committedKind(); }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
    }

    /** A Terminal page that does nothing. */
    private static final class TerminalStub implements Page {
        @NonNull @Override public PageKind kind() { return PageKind.TERMINAL; }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
        @NonNull @Override public Rest rest(@NonNull Rest.Inputs in) { throw new UnsupportedOperationException(); }
        @Override public void onFront() { }
        @Override public void offFront() { }
        @Override public boolean onBack() { return false; }
    }

    private Pages pages;
    private CountingHost host;

    @Before
    public void setUp() {
        pages = new Pages(PageHost.NONE, () -> { });
        host = new CountingHost(pages);
        pages.put(HOME1, new HomePage(HOME1, host));
        pages.put(HOME2, new HomePage(HOME2, host));
        pages.put(TERMINAL, new TerminalStub());
        pages.landed(HOME1, PageKind.HOME);
        pages.applyFront();
        host.dismiss = 0;
        host.leaving = 0;
        host.shown = 0;
    }

    @Test
    public void homeToTerminalLeavesAtTheCommitAndNothingMoreAtTheLanding() {
        pages.committed(TERMINAL, PageKind.TERMINAL);
        assertEquals(1, host.dismiss);
        assertEquals(1, host.leaving);
        pages.landed(TERMINAL, PageKind.TERMINAL);
        pages.applyFront();
        assertEquals(1, host.dismiss);
        assertEquals(1, host.leaving);
        assertEquals(0, host.shown);
    }

    @Test
    public void homeToHomeDismissesOnceWithoutLeavingAndShowsAtTheLanding() {
        pages.committed(HOME2, PageKind.HOME);
        assertEquals(1, host.dismiss);
        assertEquals(0, host.leaving);
        assertEquals(0, host.shown);
        pages.landed(HOME2, PageKind.HOME);
        pages.applyFront();
        assertEquals(1, host.dismiss);
        assertEquals(0, host.leaving);
        assertEquals(1, host.shown);
    }

    @Test
    public void aCommitAwayAndBackBeforeTheLandingLeavesOnceAndShowsAgain() {
        pages.committed(TERMINAL, PageKind.TERMINAL);
        pages.committed(HOME1, PageKind.HOME);
        pages.landed(HOME1, PageKind.HOME);
        pages.applyFront();
        assertEquals(1, host.leaving);
        assertEquals(1, host.shown);
    }
}
