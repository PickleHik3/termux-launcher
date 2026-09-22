package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

/**
 * Output arriving at a terminal the wall is not showing must not repaint it.
 *
 * <p>The place the user is on is not the activity's visibility: the launcher is fully in the
 * foreground on its Widgets place while a shell floods a terminal a whole page away. Everything but
 * the drawing keeps running — the activity is still told about the output — and the pane is drawn
 * exactly once when the terminal can be seen again, however many bursts it missed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalScreenUpdateDeferralTest {

    /**
     * The client with its one painting call counted per view. {@link TerminalView} is final, so a
     * counting view is not an option; the client's own seam is.
     */
    private static final class CountingClient extends TermuxTerminalSessionActivityClient {
        final Map<TerminalView, Integer> updates = new LinkedHashMap<>();

        CountingClient(@NonNull Context context, @NonNull TerminalHost host) {
            super(context, host);
        }

        /** When set, {@link #paneCanBeSeen} answers this instead of asking the view hierarchy. */
        Boolean paneVisibleOverride;

        @Override
        void drawScreen(@NonNull TerminalView view) {
            updates.merge(view, 1, Integer::sum);
        }

        @Override
        boolean paneCanBeSeen(@NonNull TerminalView view) {
            return paneVisibleOverride != null ? paneVisibleOverride : super.paneCanBeSeen(view);
        }

        int updatesFor(@NonNull TerminalView view) {
            return updates.getOrDefault(view, 0);
        }
    }

    private FakeTerminalHost host;
    private CountingClient client;
    private TerminalView view;
    private TerminalSession session;

    @Before
    public void setUp() throws IOException {
        Context context = FakeTerminalHost.testContext();
        host = new FakeTerminalHost(context, FakeTerminalHost.testProperties());
        view = new TerminalView(context, null);
        session = session();
        host.currentSession = session;
        host.focusedView = view;
        client = new CountingClient(context, host);
    }

    private static TerminalSession session() {
        // Inert: nothing is started until the emulator is initialized, which no test here does.
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], null, null);
    }

    private void drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void outputWhileAnotherPlaceIsShowingNeverRedrawsTheTerminal() {
        host.terminalPlaceOnScreen = false;

        for (int i = 0; i < 5; i++) client.onTextChanged(session);
        drainMainLooper();

        assertEquals(0, client.updatesFor(view));
        // The activity still hears every burst: monitor-activity, the window chips and the agent
        // rules are not deferred, only the painting is.
        assertEquals(5, host.shellActivity.size());
        assertSame(session, host.shellActivity.get(0));
    }

    @Test
    public void theTerminalComingBackDrawsEachDeferredPaneExactlyOnce() {
        host.terminalPlaceOnScreen = false;
        for (int i = 0; i < 5; i++) client.onTextChanged(session);
        drainMainLooper();

        host.terminalPlaceOnScreen = true;
        client.onTerminalPlaceMayBeVisible();
        drainMainLooper();

        assertEquals(1, client.updatesFor(view));

        // The wall keeps moving and keeps telling us so; nothing is owed, so nothing is drawn.
        client.onTerminalPlaceMayBeVisible();
        client.onTerminalPlaceMayBeVisible();
        drainMainLooper();
        assertEquals(1, client.updatesFor(view));
    }

    @Test
    public void aReturnWithNothingDeferredDrawsNothing() {
        host.terminalPlaceOnScreen = true;

        client.onTerminalPlaceMayBeVisible();
        drainMainLooper();

        assertEquals(0, client.updatesFor(view));
    }

    @Test
    public void outputWhileTheTerminalIsShowingStillCoalescesToOneRedraw() {
        host.terminalPlaceOnScreen = true;

        for (int i = 0; i < 5; i++) client.onTextChanged(session);
        drainMainLooper();

        assertEquals(1, client.updatesFor(view));

        // A later burst is its own coalesced redraw, exactly as before.
        client.onTextChanged(session);
        drainMainLooper();
        assertEquals(2, client.updatesFor(view));
    }

    @Test
    public void aPostedRedrawThatFindsTheWallGoneIsHeldUntilTheTerminalReturns() {
        host.terminalPlaceOnScreen = true;
        client.onTextChanged(session);
        // The wall left between the post and the frame it was posted for.
        host.terminalPlaceOnScreen = false;
        drainMainLooper();

        assertEquals(0, client.updatesFor(view));

        host.terminalPlaceOnScreen = true;
        client.onTerminalPlaceMayBeVisible();
        drainMainLooper();
        assertEquals(1, client.updatesFor(view));
    }

    @Test
    public void everySplitPaneIsDeferredAndReturnedOnItsOwn() {
        Context context = FakeTerminalHost.testContext();
        TerminalSession second = session();
        TerminalView secondView = new TerminalView(context, null);
        host.sessionViews.put(session, view);
        host.sessionViews.put(second, secondView);
        host.terminalPlaceOnScreen = false;

        for (int i = 0; i < 4; i++) {
            client.onTextChanged(session);
            client.onTextChanged(second);
        }
        drainMainLooper();
        assertEquals(0, client.updatesFor(view));
        assertEquals(0, client.updatesFor(secondView));

        host.terminalPlaceOnScreen = true;
        client.onTerminalPlaceMayBeVisible();
        drainMainLooper();

        assertEquals(1, client.updatesFor(view));
        assertEquals(1, client.updatesFor(secondView));
    }

    @Test
    public void aStoppedActivityDropsWhatWasDeferredRatherThanDrawingItLater() {
        host.terminalPlaceOnScreen = false;
        client.onTextChanged(session);
        drainMainLooper();

        client.onStop();
        host.terminalPlaceOnScreen = true;
        client.onTerminalPlaceMayBeVisible();
        drainMainLooper();

        // onStart draws the focused view itself, so a held redraw would be a second one.
        assertEquals(0, client.updatesFor(view));
    }

    /**
     * The wall's page is bookkeeping, and when it disagrees with what the user is looking at the
     * pane must still be painted. Believing the wall alone is what strands the terminal: output is
     * filed for a redraw only a later wall movement delivers, so the screen sits frozen until
     * something jogs it — opening the keyboard is what people find.
     */
    @Test
    public void aPaneTheHierarchySaysIsShowingIsDrawnEvenWhenTheWallDisagrees() {
        host.terminalPlaceOnScreen = false;
        client.paneVisibleOverride = Boolean.TRUE;

        client.onTextChanged(session);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("a visible pane is painted whatever the wall's page says",
            1, client.updatesFor(view));
    }

    /** With both in agreement that nothing is showing, the saved repaints are still saved. */
    @Test
    public void aPaneNeitherTheWallNorTheHierarchyShowsIsStillDeferred() {
        host.terminalPlaceOnScreen = false;
        client.paneVisibleOverride = Boolean.FALSE;

        client.onTextChanged(session);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("nothing is painted for a pane nobody can see", 0, client.updatesFor(view));

        client.paneVisibleOverride = Boolean.TRUE;
        host.terminalPlaceOnScreen = true;
        client.onTerminalPlaceMayBeVisible();

        assertEquals("and the pane owed a frame gets exactly one on return",
            1, client.updatesFor(view));
    }
}
