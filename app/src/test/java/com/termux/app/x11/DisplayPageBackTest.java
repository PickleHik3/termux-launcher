package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Back on a Display page through the chain's tail: a layer open over it is the
 * layer's; a keyboard the page raised goes down; a running display gets Back for its app; a
 * stopped one with nothing raised leaves Back to the chain's default, Home page 1.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DisplayPageBackTest {

    private DisplayPageFrontTest.FakeHost host;
    private Pages pages;
    private int homeDefaults;

    @Before
    public void setUp() {
        host = new DisplayPageFrontTest.FakeHost();
        DisplayPages display = new DisplayPages(host);
        pages = new Pages(PageHost.NONE, () -> { });
        pages.put(5, display.page(5));
        pages.setHomeDefault(() -> {
            homeDefaults++;
            return true;
        });
        host.committed = PageKind.DISPLAY;
        pages.landed(5, PageKind.DISPLAY);
        pages.applyFront();
    }

    @Test
    public void aLayerOpenOverThePageIsTheLayers() {
        host.layerOpen = true;
        assertFalse(pages.front().onBack());
        assertEquals(0, host.link.backs);
        assertEquals(0, host.keyboardsLowered);
    }

    @Test
    public void aKeyboardThePageRaisedGoesDownFirst() {
        host.link.textFocus = DisplayTextFocusPolicy.State.AUTO_OPEN;
        assertTrue(pages.onBack());
        assertEquals(1, host.keyboardsLowered);
        assertEquals(0, host.link.backs);
        assertEquals(0, homeDefaults);
    }

    @Test
    public void theKeyboardInTheFrameGoesDownEvenWithTheDisplayStopped() {
        host.running = false;
        host.content = DisplayFrameContentPolicy.Content.KEYBOARD;
        assertTrue(pages.onBack());
        assertEquals(1, host.keyboardsLowered);
    }

    @Test
    public void aRunningDisplayGetsBackForItsApp() {
        assertTrue(pages.onBack());
        assertEquals(1, host.link.backs);
        assertEquals(0, homeDefaults);
    }

    @Test
    public void aStoppedDisplayWithNothingRaisedGoesToHomePageOne() {
        host.running = false;
        assertTrue(pages.onBack());
        assertEquals(1, homeDefaults);
        assertEquals(0, host.link.backs);
    }
}
