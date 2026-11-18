package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The Display pages' front and leave at the structure point (#272 slice 3): Terminal to Display
 * attaches and focuses the one view once; Display to Terminal lets it go and hands the keys back
 * with no retry; Display to Display keeps the view where it is.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DisplayPageFrontTest {

    static final class FakeLink implements DisplayPages.Link {
        int attaches, detaches, entered, left, backs;
        @Nullable DisplayTextFocusPolicy.State textFocus;
        @Override public void attachView(@NonNull View view) { attaches++; }
        @Override public void detachView() { detaches++; }
        @Override public void placeEntered(boolean keyboardRaisedOnEnter) { entered++; }
        @Override public void placeLeft() { left++; }
        @Override public void sendBack() { backs++; }
        @Nullable @Override public DisplayTextFocusPolicy.State textFocus() { return textFocus; }
    }

    static final class FocusCountingView extends View {
        int focusAsks;
        FocusCountingView() { super(RuntimeEnvironment.getApplication()); }
        @Override public boolean requestFocus(int direction, @Nullable Rect previouslyFocusedRect) {
            focusAsks++;
            return super.requestFocus(direction, previouslyFocusedRect);
        }
    }

    static class FakeHost implements DisplayPageHost {
        final FocusCountingView view = new FocusCountingView();
        final FakeLink link = new FakeLink();
        @Nullable PageKind committed;
        boolean atRest = true;
        boolean showing = true;
        boolean layerOpen;
        boolean running = true;
        DisplayFrameContentPolicy.Content content = DisplayFrameContentPolicy.Content.PAD;
        int handBacks, keyboardsLowered, routeSyncs, systemKeyboardsHidden;
        @Nullable @Override public View displayView() { return view; }
        @Nullable @Override public DisplayPages.Link link() { return link; }
        @Override public boolean isRunning() { return running; }
        @Nullable @Override public PageKind committedKind() { return committed; }
        @Override public boolean wallAtRest() { return atRest; }
        @Override public boolean isDisplayShowing() { return showing; }
        @Override public boolean isLayerOpen() { return layerOpen; }
        @Override public boolean wantsKeyboardOnEnter() { return false; }
        @Override public void refreshEmptyState() { }
        @Override public void restoreTypingClaim() { }
        @Override public void syncKeyboardRoute() { routeSyncs++; }
        @Override public void hideSystemKeyboard() { systemKeyboardsHidden++; }
        @Override public boolean handKeysToTerminal() { handBacks++; return true; }
        @NonNull @Override public DisplayFrameContentPolicy.Content frameContent() { return content; }
        @Override public void lowerKeyboard() { keyboardsLowered++; }
    }

    private FakeHost host;
    private DisplayPages display;
    private Pages pages;

    @Before
    public void setUp() {
        host = new FakeHost();
        display = new DisplayPages(host);
        pages = new Pages(PageHost.NONE, () -> { });
        pages.put(5, display.page(5));
        pages.put(6, display.page(6));
    }

    private void land(int id, PageKind kind) {
        host.committed = kind;
        pages.landed(id, kind);
        pages.applyFront();
    }

    @Test
    public void terminalToDisplayAttachesAndFocusesOnce() {
        land(1, PageKind.TERMINAL);
        land(5, PageKind.DISPLAY);
        assertEquals(1, host.link.attaches);
        assertEquals(1, host.link.entered);
        assertEquals(1, host.view.focusAsks);
        assertTrue(host.view.isFocusable());
        // The structure point again with nothing changed does nothing.
        pages.applyFront();
        land(5, PageKind.DISPLAY);
        assertEquals(1, host.link.attaches);
        assertEquals(1, host.view.focusAsks);
    }

    @Test
    public void displayToTerminalReleasesOnceAndHandsTheKeysBackWithNoRetry() {
        land(5, PageKind.DISPLAY);
        host.atRest = false;
        host.committed = PageKind.TERMINAL;
        pages.committed(1, PageKind.TERMINAL);
        assertEquals(1, host.link.left);
        assertFalse("the view can no longer take the keys", host.view.isFocusable());
        int handBacks = host.handBacks;
        assertTrue(handBacks >= 1);
        assertEquals("kept attached under the still while the page slides out", 0,
            host.link.detaches);
        // Later layouts ask nothing again: there is no retry.
        host.view.getViewTreeObserver().dispatchOnGlobalLayout();
        host.atRest = true;
        host.showing = false;
        land(1, PageKind.TERMINAL);
        assertEquals(handBacks, host.handBacks);
        assertEquals(1, host.link.left);
        display.detachIfOwed();
        assertEquals("the rest lets the view go, once", 1, host.link.detaches);
        display.detachIfOwed();
        assertEquals(1, host.link.detaches);
    }

    @Test
    public void aLeaveAtRestLetsTheViewGoAtOnce() {
        land(5, PageKind.DISPLAY);
        land(1, PageKind.TERMINAL);
        assertEquals(1, host.link.detaches);
        assertEquals(1, host.systemKeyboardsHidden);
    }

    @Test
    public void displayToDisplayKeepsTheOneView() {
        land(5, PageKind.DISPLAY);
        host.committed = PageKind.DISPLAY;
        pages.committed(6, PageKind.DISPLAY);
        land(6, PageKind.DISPLAY);
        assertEquals(0, host.link.left);
        assertEquals(0, host.link.detaches);
        assertEquals(0, host.handBacks);
        assertTrue(host.view.isFocusable());
    }
}
