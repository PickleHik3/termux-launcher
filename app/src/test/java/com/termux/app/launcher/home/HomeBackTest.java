package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;

import org.junit.Test;

public class HomeBackTest {

    private static final class FakeHost implements HomePageHost {
        boolean widgetSpends, floatSpends;
        int widgetAsked, floatAsked;

        @Override public void dismissControls() { }
        @Override public void leaving() { }
        @Override public void shown() { }
        @Nullable @Override public PageKind committedKind() { return PageKind.HOME; }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
        @Override public boolean widgetBack() { widgetAsked++; return widgetSpends; }
        @Override public boolean lowerFloatKeyboard() { floatAsked++; return floatSpends; }
    }

    @Test
    public void aWidgetEditOrPickerSpendsBackBeforeTheFloatIsAsked() {
        FakeHost host = new FakeHost();
        host.widgetSpends = true;
        assertTrue(new HomePage(1, host).onBack());
        assertEquals(0, host.floatAsked);
    }

    @Test
    public void aFocusedFloatWithTheKeyboardUpSpendsBack() {
        FakeHost host = new FakeHost();
        host.floatSpends = true;
        assertTrue(new HomePage(1, host).onBack());
        assertEquals(1, host.widgetAsked);
    }

    @Test
    public void nothingToDoLeavesBackUnspent() {
        assertFalse(new HomePage(1, new FakeHost()).onBack());
    }

    @Test
    public void homePageTwoWithNothingToDoGoesToHomePageOne() {
        Pages pages = new Pages(PageHost.NONE, () -> { });
        pages.put(1, new HomePage(1, new FakeHost()));
        pages.put(2, new HomePage(2, new FakeHost()));
        int[] calls = new int[1];
        pages.setHomeDefault(() -> { calls[0]++; return true; });
        pages.landed(2, PageKind.HOME);
        assertTrue(pages.onBack());
        assertEquals(1, calls[0]);
    }

    @Test
    public void homePageOneWithNothingToDoStillAsksTheDefault() {
        Pages pages = new Pages(PageHost.NONE, () -> { });
        pages.put(1, new HomePage(1, new FakeHost()));
        int[] calls = new int[1];
        pages.setHomeDefault(() -> { calls[0]++; return false; });
        pages.landed(1, PageKind.HOME);
        assertFalse(pages.onBack());
        assertEquals(1, calls[0]);
    }
}
