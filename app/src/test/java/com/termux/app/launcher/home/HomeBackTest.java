package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.WidgetPaneController;
import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;

import com.termux.app.wall.WidgetPaneFrame;

import org.junit.Test;

public class HomeBackTest {

    /**
     * Widgets are a concrete controller, so the widget answer is not faked here: it is null, and
     * the float's path (focused, not held up, keyboard lowered) is what these tests drive.
     */
    private static final class FakeHost implements HomePageHost {
        boolean floatFocused = true, held, keyboardUp;
        int lowered;

        @Nullable @Override public WidgetPaneController widgets() { return null; }
        @Nullable @Override public WidgetPaneFrame frame() { return null; }
        @Override public void batchBarRefresh(@NonNull Runnable work) { work.run(); }
        @Override public void releaseHomeFloatFocus() { }
        @Override public boolean homeFloatFocused() { return floatFocused; }
        @Override public boolean homeHoldsKeyboardUp() { return held; }
        @Override public boolean lowerKeyboard() {
            if (!keyboardUp) return false;
            lowered++;
            return true;
        }
        @Nullable @Override public PageKind committedKind() { return PageKind.HOME; }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
    }

    @Test
    public void aFocusedFloatWithTheKeyboardUpSpendsBack() {
        FakeHost host = new FakeHost();
        host.keyboardUp = true;
        assertTrue(new HomePage(1, host).onBack());
        assertEquals(1, host.lowered);
    }

    @Test
    public void homeHoldingTheKeyboardUpLeavesBackUnspent() {
        FakeHost host = new FakeHost();
        host.keyboardUp = true;
        host.held = true;
        assertFalse(new HomePage(1, host).onBack());
        assertEquals(0, host.lowered);
    }

    @Test
    public void noFocusedFloatLeavesTheKeyboardAlone() {
        FakeHost host = new FakeHost();
        host.keyboardUp = true;
        host.floatFocused = false;
        assertFalse(new HomePage(1, host).onBack());
        assertEquals(0, host.lowered);
    }

    @Test
    public void aKeyboardThatIsNotUpLeavesBackUnspent() {
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
