package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalPaneController;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingDesktopStore;
import com.termux.app.tiling.TilingHostedIds;
import com.termux.app.tiling.TilingInput;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.TilingWorkspace;
import com.termux.app.tiling.WindowSpec;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tiling tracer end to end (#418) with a real pane controller and real views: Home first with
 * Workspace 1's terminal ready below it, moves between places and windows, a new terminal as the
 * column after the focused one, and a restart that brings every window back with its shell.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TilingDesktopTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Activity activity;
    private FrameLayout root;
    private FakeHost host;
    private TilingDesktopStore store;
    private final List<TilingDesktop> started = new ArrayList<>();

    @Before public void setUp() {
        android.provider.Settings.Global.putFloat(
            org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new FrameLayout(activity);
        activity.setContentView(root);
        host = new FakeHost();
        store = new TilingDesktopStore(new File(folder.getRoot(), "tiling.json"));
        TilingDesktop.forgetProcessCopy();
    }

    @After public void tearDown() {
        for (TilingDesktop desktop : started) desktop.onDestroy();
        TilingDesktop.forgetProcessCopy();
    }

    @Test public void theFirstStartIsHomeWithWorkspaceOnesTerminalReadyBelow() {
        TilingDesktop desktop = start();

        assertFalse(desktop.isWorkspaceInFront());
        assertEquals(Place.home(0), desktop.stack().current());
        assertEquals(1, desktop.stack().workspace(1).windows().size());
        assertTrue("an empty Workspace waits below", desktop.stack().workspace(2).isEmpty());
        assertEquals("Workspace 1 is Home's neighbour, so its shell is ready", 1, host.created.size());
        assertNotNull(host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)));
        assertNull("Home keeps the keyboard", host.panes.focusedHomeFloat());
        assertEquals("nothing of it shows on Home", View.INVISIBLE, desktop.view().getVisibility());
    }

    @Test public void downIsWorkspaceOneWhoseTerminalTakesTheKeyboard() {
        TilingDesktop desktop = start();

        desktop.run(TilingInput.Command.DOWN);
        idle();

        assertTrue(desktop.isWorkspaceInFront());
        assertEquals(Place.workspace(1), desktop.stack().current());
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)), host.panes.focusedHomeFloat());
        assertEquals(View.VISIBLE, desktop.view().getVisibility());
        assertTrue("its shell is drawn", shown(desktop.view(), host.created.get(0)));
    }

    @Test public void upFromWorkspaceOneIsHomeAndTheKeyboardGoesBack() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();

        desktop.run(TilingInput.Command.UP);
        idle();

        assertFalse(desktop.isWorkspaceInFront());
        assertNull(host.panes.focusedHomeFloat());
        assertEquals(View.INVISIBLE, desktop.view().getVisibility());
    }

    @Test public void theHomePressGoesHome() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();

        desktop.home();
        idle();

        assertEquals(Place.home(0), desktop.stack().current());
    }

    @Test public void aNewTerminalOpensAsTheColumnAfterTheFocusedOne() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.LEFT);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        TilingWorkspace one = desktop.stack().workspace(1);
        assertEquals(3, one.groups().size());
        assertEquals(1, one.groups().get(0).active().id());
        assertEquals(3, one.groups().get(1).active().id());
        assertEquals(2, one.groups().get(2).active().id());
        assertEquals(3, desktop.stack().focusedWindowId());
        assertEquals(3, host.created.size());
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(3)), host.panes.focusedHomeFloat());
    }

    @Test public void sidewaysMovesBetweenTheWorkspacesWindows() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);

        desktop.run(TilingInput.Command.LEFT);
        assertEquals(1, desktop.stack().focusedWindowId());
        desktop.run(TilingInput.Command.RIGHT);
        assertEquals(2, desktop.stack().focusedWindowId());
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(2)), host.panes.focusedHomeFloat());
    }

    @Test public void fromHomeANewTerminalOpensTheWaitingWorkspace() {
        TilingDesktop desktop = start();

        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        assertEquals(Place.workspace(2), desktop.stack().current());
        assertEquals(3, desktop.stack().workspaceCount());
    }

    @Test public void aShellThatEndsClosesItsWindow() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);

        assertFalse("a widget's id is not the desktop's", desktop.onHostedShellEnded(42));
        assertTrue(desktop.onHostedShellEnded(TilingHostedIds.hostedId(2)));

        assertNull(desktop.stack().window(2));
        assertEquals(1, desktop.stack().focusedWindowId());
        assertFalse(desktop.claimsShell(host.created.get(1)));
    }

    @Test public void aRestartBringsBackWorkspacesColumnsAndFocusAndReattachesTheShells() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.run(TilingInput.Command.NEW_TERMINAL);
        first.run(TilingInput.Command.LEFT);
        TerminalSession focusedShell = host.created.get(0);
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();

        assertEquals("no shell started again", 2, host.created.size());
        assertEquals(Place.workspace(1), second.stack().current());
        assertEquals(2, second.stack().workspace(1).groups().size());
        assertEquals(1, second.stack().focusedWindowId());
        TerminalPaneController.Window focused = host.panes.focusedHomeFloat();
        assertNotNull(focused);
        assertSame(focusedShell, host.panes.windowActiveSession(focused));
    }

    @Test public void afterTheShellsDiedEachWindowOpensANewOneInItsDirectory() throws Exception {
        TilingStack stack = TilingStack.firstStart(1, "/saved/one");
        stack.focusWindow(1);
        stack.open(WindowSpec.shell("/saved/two"));
        Map<Long, String> shells = new HashMap<>();
        shells.put(1L, "gone-1");
        shells.put(2L, "gone-2");
        store.save(TilingDesktopStore.encode(stack, shells));

        TilingDesktop desktop = start();

        assertEquals(Place.workspace(1), desktop.stack().current());
        assertEquals(2, host.created.size());
        assertTrue(host.cwds.contains("/saved/one"));
        assertTrue(host.cwds.contains("/saved/two"));
        assertTrue(desktop.claimsShell(host.created.get(0)));
    }

    @Test public void onlyThePlaceInFrontAndItsNeighboursHoldViews() throws Exception {
        TilingStack stack = TilingStack.firstStart(1, null);
        for (int number = 2; number <= 4; number++) stack.openIn(Place.workspace(number), WindowSpec.shell(null), false);
        stack.goTo(Place.workspace(4));
        store.save(TilingDesktopStore.encode(stack, new HashMap<>()));

        TilingDesktop desktop = start();

        assertNull("Workspace 1 is two places up", host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)));
        assertNull(host.panes.hostedFloatFor(TilingHostedIds.hostedId(2)));
        assertNotNull(host.panes.hostedFloatFor(TilingHostedIds.hostedId(3)));
        assertNotNull(host.panes.hostedFloatFor(TilingHostedIds.hostedId(4)));

        desktop.run(TilingInput.Command.UP);
        desktop.run(TilingInput.Command.UP);
        idle();

        assertNotNull(host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)));
        assertNull("parked: its view goes", host.panes.hostedFloatFor(TilingHostedIds.hostedId(4)));
        TerminalSession parked = host.live.get(desktop.handleOf(4));
        assertNotNull("its shell keeps running", parked);
        assertTrue("and stays the desktop's", desktop.claimsShell(parked));
    }

    // --- helpers ---

    @NonNull private TilingDesktop start() {
        host.panes = new TerminalPaneController(new SilentPaneHost(), new FrameLayout(activity),
            LayoutInflater.from(activity));
        TilingDesktop desktop = new TilingDesktop(activity, host, store, true, Runnable::run);
        started.add(desktop);
        root.addView(desktop.view(), new FrameLayout.LayoutParams(1000, 2000));
        idle();
        host.ready = true;
        desktop.onServiceConnected();
        desktop.onShellsReady();
        idle();
        return desktop;
    }

    /** The activity goes and comes back: the views and the pane controller go, the shells stay. */
    private void restartActivity(@NonNull TilingDesktop desktop) {
        desktop.onDestroy();
        started.remove(desktop);
        root.removeView(desktop.view());
        host.ready = false;
    }

    private static boolean shown(@NonNull View view, @NonNull TerminalSession shell) {
        if (view instanceof TerminalView) return ((TerminalView) view).getCurrentSession() == shell;
        if (!(view instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) if (shown(group.getChildAt(i), shell)) return true;
        return false;
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @NonNull private static TerminalSession newShell() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }

    private static final class FakeHost implements TilingDesktop.Host {
        TerminalPaneController panes;
        final Map<String, TerminalSession> live = new HashMap<>();
        final List<TerminalSession> created = new ArrayList<>();
        final List<String> cwds = new ArrayList<>();
        final List<TerminalSession> killed = new ArrayList<>();
        boolean ready;

        @Override @Nullable public TerminalPaneController panes() { return panes; }
        @Override public boolean shellsReady() { return ready; }
        @Override @Nullable public TerminalSession liveShell(@NonNull String handle) { return live.get(handle); }
        @Override @Nullable public TerminalSession createShell(@Nullable String cwd) {
            TerminalSession shell = newShell();
            created.add(shell);
            cwds.add(cwd);
            live.put(shell.mHandle, shell);
            return shell;
        }
        @Override public void killShell(@NonNull TerminalSession shell) {
            killed.add(shell);
            live.remove(shell.mHandle);
        }
        @Override public boolean reducedMotion() { return true; }
    }

    private static final class SilentPaneHost implements TerminalPaneController.Host {
        @Override public TerminalSession createShell(String cwd) { return newShell(); }
        @Override public void configurePaneView(TerminalView view) { }
        @Override public void removeShell(TerminalSession session) { }
        @Override public void onActivePaneChanged() { }
        @Override public void onTreesChanged() { }
        @Override public String defaultCwd() { return "/"; }
    }
}
