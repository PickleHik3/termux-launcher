package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertArrayEquals;
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
import com.termux.app.tiling.SavedWorkspace;
import com.termux.app.tiling.SavedWorkspaceStore;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.TilingDesktopStore;
import com.termux.app.tiling.TilingHostedIds;
import com.termux.app.tiling.TilingInput;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.TileGroup;
import com.termux.app.tiling.TilingWorkspace;
import com.termux.app.tiling.WorkspaceLayout;
import com.termux.app.tiling.WindowKind;
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
import java.util.Arrays;
import java.util.Collections;
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
        SavedWorkspaces.forgetProcessState();
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

    @Test public void aWorkspaceHasNoGroundOfItsOwnAndHomeLeavesAsItComesIn() {
        TilingDesktop desktop = start();
        assertFalse("Home stands at rest", host.homeAway);
        assertEquals(0f, host.homeTravel, 0f);

        desktop.run(TilingInput.Command.DOWN);
        idle();

        android.graphics.drawable.Drawable ground = ((TilingDesktopView) desktop.view()).pageAt(1).getBackground();
        assertTrue("the wallpaper shows between and around the windows", ground == null
            || (ground instanceof android.graphics.drawable.ColorDrawable
                && ((android.graphics.drawable.ColorDrawable) ground).getAlpha() == 0));
        assertTrue("Home is wholly away behind a Workspace", host.homeAway);

        desktop.run(TilingInput.Command.UP);
        idle();

        assertFalse(host.homeAway);
        assertEquals(0f, host.homeTravel, 0f);
    }

    /** Round 3: launcherctl's pane record read a tile as floating; the desktop says what it is. */
    @Test public void aTilesShellIsNoFloatAndAFloatsIs() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        TerminalSession shell = host.created.get(0);
        assertEquals(Boolean.FALSE, desktop.shellWindowFloats(shell));
        desktop.toggleFrontFloat();
        idle();
        assertEquals(Boolean.TRUE, desktop.shellWindowFloats(shell));
        assertNull("a shell no window shows", desktop.shellWindowFloats(newShell()));
    }

    /** Round 4: Home's Terminal on the bar opened a Workspace a tap; it goes to the terminal that is open. */
    @Test public void fromHomeTheTerminalGoesToTheOneOpenAndOpensNothing() {
        TilingDesktop desktop = start();
        int windows = desktop.stack().workspace(1).windows().size();
        int workspaces = desktop.stack().workspaceCount();

        assertTrue(desktop.hasTerminal());
        assertTrue(desktop.showTerminal());
        idle();
        assertTrue(desktop.showTerminal());
        idle();

        assertEquals(Place.workspace(1), desktop.stack().current());
        assertEquals(windows, desktop.stack().workspace(1).windows().size());
        assertEquals(workspaces, desktop.stack().workspaceCount());
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

    // ---- every door to a terminal opens a window in the stack (terl-integration-1) -----------------

    @Test public void aCommandTerminalFromHomeOpensInTheWaitingWorkspaceAndGoesThere() {
        TilingDesktop desktop = start();

        TerminalSession shell = desktop.openCommandTerminal(java.util.Arrays.asList("sh", "-c", "top"),
            "/tmp/here", "top", true, false);
        idle();

        assertTrue(desktop.isWorkspaceInFront());
        assertEquals(Place.workspace(2), desktop.stack().current());
        assertEquals(1, desktop.stack().workspace(2).windows().size());
        assertNotNull("the window's shell answers", shell);
        assertSame(host.created.get(host.created.size() - 1), shell);
        assertEquals("/tmp/here", host.cwds.get(host.cwds.size() - 1));
        assertEquals("the window closes with its command", java.util.Collections.singletonList(false),
            host.commandStays);
        assertEquals("its shell is the window's", shell.mHandle,
            desktop.windowShell(desktop.stack().focusedWindowId()).mHandle);
    }

    @Test public void aCommandTerminalOnAWorkspaceOpensAfterTheFocusedWindow() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();

        TerminalSession shell = desktop.openCommandTerminal(java.util.Arrays.asList("htop"), null, null, true, false);
        idle();

        assertEquals(Place.workspace(1), desktop.stack().current());
        TilingWorkspace one = desktop.stack().workspace(1);
        assertEquals(2, one.groups().size());
        assertEquals(desktop.stack().focusedWindowId(), one.groups().get(1).active().id());
        assertNotNull(shell);
    }

    @Test public void aCommandTerminalWithoutFocusOpensBehindAndTheFocusStays() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        long before = desktop.stack().focusedWindowId();

        TerminalSession shell = desktop.openCommandTerminal(java.util.Arrays.asList("true"), null, null, false, false);
        idle();

        assertNotNull("its command runs at once", shell);
        assertEquals(Place.workspace(1), desktop.stack().current());
        assertEquals("the focus stays", before, desktop.stack().focusedWindowId());
        assertEquals(2, desktop.stack().workspace(1).windows().size());
    }

    @Test public void aCommandTerminalWithoutFocusFromHomeStaysOnHome() {
        TilingDesktop desktop = start();

        assertNotNull(desktop.openCommandTerminal(java.util.Arrays.asList("true"), null, null, false, false));
        idle();

        assertFalse(desktop.isWorkspaceInFront());
        assertEquals("it waits in the waiting Workspace", 1, desktop.stack().workspace(2).windows().size());
    }

    @Test public void aRefusedCommandTerminalInsertsNothing() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        host.refuseCommands = true;
        int windows = desktop.windows().size();

        assertNull(desktop.openCommandTerminal(java.util.Arrays.asList("top"), null, null, true, false));
        idle();

        assertEquals("no tile is left behind", windows, desktop.windows().size());
        assertEquals(Place.workspace(1), desktop.stack().current());
    }

    @Test public void newFloatHereOnHomeIsAFloatOfTheHomePageInFront() {
        TilingDesktop desktop = start();

        desktop.newFloatHere();
        idle();

        assertFalse("Home stays in front", desktop.isWorkspaceInFront());
        long id = desktop.stack().focusedWindowId();
        assertEquals(Place.home(0), desktop.placeOfWindow(id));
        assertTrue(desktop.stack().window(id).isFloat());
    }

    @Test public void newFloatHereOnAWorkspaceFloatsOverItsTiles() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();

        desktop.newFloatHere();
        idle();

        long id = desktop.stack().focusedWindowId();
        assertEquals(Place.workspace(1), desktop.placeOfWindow(id));
        assertTrue(desktop.stack().window(id).isFloat());
    }

    @Test public void aNewTerminalHereFromHomeLandsOnTheWaitingWorkspace() {
        TilingDesktop desktop = start();

        desktop.newTerminalHere();
        idle();

        assertEquals(Place.workspace(2), desktop.stack().current());
        assertEquals(View.VISIBLE, desktop.view().getVisibility());
    }

    // ---- Overview's actions (#430) ---------------------------------------------------------------

    @Test public void theSnapshotIsACopyWithTheDesktopsIds() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        com.termux.app.tiling.TilingStack copy = desktop.snapshot();
        copy.close(2);

        assertEquals("the desktop keeps both", 2, desktop.stack().workspace(1).windows().size());
        assertEquals(Place.workspace(1), copy.current());
        assertEquals(1, copy.workspace(1).windows().get(0).id());
    }

    @Test public void theSnapshotCarriesEachDwindleTreeAsLaidOutAndShellsHaveTitles() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        desktop.setFrontLayout(com.termux.app.tiling.WorkspaceLayout.DWINDLE);
        idle();

        com.termux.app.tiling.TilingStack copy = desktop.snapshot();

        com.termux.app.tiling.DwindleTree tree =
            com.termux.app.tiling.DwindleTree.decode(copy.workspace(1).layoutState());
        assertNotNull("the tree as laid out, before any save", tree);
        assertEquals(2, tree.tiles().size());
        assertEquals(host.created.get(0).getTitle(), desktop.shellTitle(1));
        assertNull("no such window", desktop.shellTitle(99));
    }

    @Test public void aWindowMovedToTheWaitingWorkspaceStartsANewOneAndTheFrontStays() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        int[] told = {0};
        desktop.setFrontListener(() -> told[0]++);

        assertTrue(desktop.moveWindow(2, Place.workspace(2)));
        idle();

        assertEquals(Place.workspace(2), desktop.stack().placeOf(2));
        assertEquals(Place.workspace(1), desktop.front());
        assertEquals(3, desktop.stack().workspaceCount());
        assertTrue("the bar hears of it", told[0] > 0);
        assertFalse("it is there already", desktop.moveWindow(2, Place.workspace(2)));
    }

    @Test public void movingTheLastWindowOutOfTheWorkspaceInFrontKeepsAPlaceInFront() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        desktop.moveWindow(1, Place.workspace(2));
        idle();

        assertEquals(Place.workspace(1), desktop.stack().placeOf(1));
        assertEquals(2, desktop.stack().workspaceCount());
    }

    @Test public void aWindowJoinsAnotherAsATabAndGoToChangesThePlace() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        assertTrue(desktop.joinTab(2, 1));
        idle();
        assertEquals(1, desktop.stack().workspace(1).groups().size());
        assertEquals(com.termux.app.tiling.TileGroup.Mode.TABS,
            desktop.stack().workspace(1).groups().get(0).mode());

        assertTrue(desktop.goTo(Place.home(0)));
        idle();
        assertFalse(desktop.isWorkspaceInFront());
        assertFalse("no Workspace 9", desktop.goTo(Place.workspace(9)));
    }

    @Test public void theFrontReadsHomeThenTheWorkspacesWindowsAndTellsItsListener() {
        TilingDesktop desktop = start();
        int[] told = {0};
        desktop.setFrontListener(() -> told[0]++);
        assertEquals(Place.home(0), desktop.front());
        assertTrue(desktop.frontWindows().isEmpty());
        assertNull(desktop.frontMainWindow());
        assertEquals(TilingStack.NO_WINDOW, desktop.frontFocusedWindowId());

        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        assertTrue(told[0] >= 2);
        assertEquals(Place.workspace(1), desktop.front());
        assertEquals(2, desktop.frontWindows().size());
        assertEquals(1, desktop.frontWindows().get(0).id());
        assertEquals(2, desktop.frontFocusedWindowId());
        assertNotNull(desktop.frontMainWindow());
    }

    @Test public void focusingAWindowByItsMarkGoesToItsPlace() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);

        assertTrue(desktop.focusWindow(1));
        assertEquals(1, desktop.frontFocusedWindowId());
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)), host.panes.focusedHomeFloat());

        desktop.home();
        assertTrue("from Home it goes down to the window", desktop.focusWindow(2));
        assertEquals(Place.workspace(1), desktop.front());
        assertEquals(2, desktop.frontFocusedWindowId());
        assertFalse(desktop.focusWindow(77));
    }

    @Test public void splitStacksANewTerminalBelowTheFocusedOne() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);

        desktop.split();
        idle();

        TilingWorkspace one = desktop.stack().workspace(1);
        assertEquals("one column holds both", 1, one.groups().size());
        assertEquals(2, one.groups().get(0).windows().size());
        assertEquals(2, desktop.frontFocusedWindowId());
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

    /**
     * An emptied Workspace above the one in front goes and renumbers it: the view follows to the
     * new level, so the keyboard never goes to a window on a page off screen.
     */
    @Test public void aShellEndingAboveRenumbersTheFrontAndTheViewFollows() throws Exception {
        TilingStack stack = TilingStack.firstStart(1, null);
        stack.openIn(Place.workspace(2), WindowSpec.shell(null), false);
        stack.goTo(Place.workspace(2));
        store.save(TilingDesktopStore.encode(stack, new HashMap<>()));
        TilingDesktop desktop = start();
        TilingDesktopView view = (TilingDesktopView) desktop.view();
        assertEquals(2f, view.position(), 0f);

        assertTrue(desktop.onHostedShellEnded(TilingHostedIds.hostedId(1)));
        idle();

        assertEquals(Place.workspace(1), desktop.stack().current());
        assertEquals(1f, view.position(), 0f);
        TerminalPaneController.Window front = host.panes.hostedFloatFor(TilingHostedIds.hostedId(2));
        assertSame(front, host.panes.focusedHomeFloat());
        assertTrue(shown(view, host.panes.windowActiveSession(front)));
        View page = (View) view.frameOf(2).getParent().getParent().getParent();
        assertEquals("the front window's page is on screen", View.VISIBLE, page.getVisibility());
        assertEquals(0f, page.getTranslationY(), 0f);
    }

    /** A parked window's shell that ends closes its window, as a shown one's does. */
    @Test public void aParkedShellThatEndsClosesItsWindow() throws Exception {
        TilingStack stack = TilingStack.firstStart(1, null);
        for (int number = 2; number <= 3; number++) stack.openIn(Place.workspace(number), WindowSpec.shell(null), false);
        stack.goTo(Place.workspace(3));
        store.save(TilingDesktopStore.encode(stack, new HashMap<>()));
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.UP);
        desktop.run(TilingInput.Command.UP);
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.DOWN);
        idle();
        assertNull("window 1 is parked", host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)));
        TerminalSession parked = host.live.get(desktop.handleOf(1));
        assertNotNull(parked);

        assertFalse("a shown window's shell is v1's finish path's",
            desktop.onShellFinished(host.live.get(desktop.handleOf(3))));
        assertTrue(desktop.onShellFinished(parked));
        idle();

        assertNull(desktop.stack().window(1));
        assertFalse(desktop.claimsShell(parked));
        assertEquals("Workspace 3 became 2", Place.workspace(2), desktop.stack().current());
        assertEquals(2f, ((TilingDesktopView) desktop.view()).position(), 0f);
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(3)), host.panes.focusedHomeFloat());
        assertFalse("a shell no window has", desktop.onShellFinished(newShell()));
    }

    @Test public void theLocalApisCloseTakesAShellWindowOutOfTheStackAtOnce() {
        // Review of round 2: launcherctl window close must close the window itself, asking nothing,
        // whether or not its shell has a process yet.
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        TerminalSession shell = host.live.get(desktop.handleOf(2));
        host.busy.add(shell);

        assertTrue(desktop.closeShellWindowNow(2));

        assertNull(desktop.stack().window(2));
        assertTrue("nothing asked", host.asked.isEmpty());
        assertEquals(java.util.Collections.singletonList(shell), host.killed);
        assertNull(host.panes.hostedFloatFor(TilingHostedIds.hostedId(2)));
        assertFalse("no such window", desktop.closeShellWindowNow(2));
    }

    /** Close asks first while the shell runs a program, as v1's pane close does; idle closes at once. */
    @Test public void closeAsksWhileAProgramRunsAndClosesAnIdleShellAtOnce() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        TerminalSession running = host.live.get(desktop.handleOf(2));
        host.busy.add(running);

        desktop.run(TilingInput.Command.CLOSE);
        assertEquals(1, host.asked.size());
        assertNotNull("nothing closes before the answer", desktop.stack().window(2));
        assertTrue(host.killed.isEmpty());
        host.asked.get(0).run();
        assertNull(desktop.stack().window(2));
        assertEquals(java.util.Collections.singletonList(running), host.killed);
        assertNull(host.panes.hostedFloatFor(TilingHostedIds.hostedId(2)));

        desktop.run(TilingInput.Command.CLOSE);
        assertEquals("idle: no question", 1, host.asked.size());
        assertNull(desktop.stack().window(1));
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

    /** Round 4: full screen came back after a force-stop, and with it no bar; a fresh start leaves it. */
    @Test public void aFreshStartNeverComesBackFullScreenButARecreateDoes() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        first.setFrontFullScreen(true);
        first.onPause();
        restartActivity(first);

        TilingDesktop recreated = start();
        assertTrue("a recreate in the same process keeps it", recreated.frontFullScreen());
        recreated.onPause();
        restartActivity(recreated);
        TilingDesktop.forgetProcessCopy();

        TilingDesktop fresh = start();
        assertFalse(fresh.frontFullScreen());
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

    // --- a held mark's window menu (#425) ---

    /** Workspace 1 in front with its shell, then Firefox after it, focused. */
    @NonNull private TilingDesktop withFirefox() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", "Firefox", false);
        idle();
        return desktop;
    }

    @Test public void aMinimisedLinuxAppIsParkedKeepsItsWindowAndATapOnItsMarkShowsIt() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);

        assertTrue(desktop.setWindowMinimised(firefox, true));
        idle();

        assertNull("parked: its view goes", host.linux.views.get(70L).getParent());
        assertEquals("focus goes to the shell", 1, desktop.frontFocusedWindowId());
        assertEquals(2, desktop.frontWindows().size());
        assertTrue(desktop.frontWindows().get(1).isMinimised());

        assertTrue(desktop.focusWindow(firefox));
        idle();
        assertFalse(desktop.stack().window(firefox).isMinimised());
        assertSame(frame(desktop, firefox), host.linux.views.get(70L).getParent());
    }

    @Test public void theOnlyWindowMinimisedInFocusIsShownByATapOnItsMark() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        desktop.moveWindow(firefox, Place.workspace(2));
        desktop.goTo(Place.workspace(2));
        idle();
        desktop.setWindowMinimised(firefox, true);
        idle();
        assertEquals(firefox, desktop.frontFocusedWindowId());

        assertTrue(desktop.focusWindow(firefox));
        idle();

        assertFalse(desktop.stack().window(firefox).isMinimised());
    }

    @Test public void switchingToDwindleLeavesAMinimisedWindowMinimised() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        desktop.moveWindow(firefox, Place.workspace(2));
        desktop.goTo(Place.workspace(2));
        desktop.setWindowMinimised(firefox, true);
        idle();

        assertTrue(desktop.setFrontLayout(com.termux.app.tiling.WorkspaceLayout.DWINDLE));

        assertTrue(desktop.stack().window(firefox).isMinimised());
    }

    @Test public void aGapDragBesideAMinimisedWindowResizes() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.setWindowMinimised(firefox, true);
        desktop.focusWindow(1);
        idle();
        int before = bounds(frame(desktop, 1))[2];

        desktop.onGapDragBegin(0);
        desktop.onGapDrag(0, -300f);
        idle();

        assertTrue("the shell's column narrowed", bounds(frame(desktop, 1))[2] < before);
    }

    @Test public void aRestoredWindowComesBackAtTheWidthItHad() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        desktop.reorderWindow(firefox, 1);
        desktop.onGapDragBegin(0);
        desktop.onGapDrag(0, -300f);
        desktop.onGapDragEnd();
        idle();
        int width = bounds(frame(desktop, firefox))[2];
        assertTrue("narrower than the room", width < 900);
        desktop.focusWindow(firefox);
        idle();
        desktop.setWindowMinimised(firefox, true);
        idle();

        desktop.focusWindow(firefox);
        idle();

        assertEquals(width, bounds(frame(desktop, firefox))[2]);
    }

    @Test public void aMaximisedWindowInFocusFillsTheRoomAndTheOthersPark() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        int[] before = bounds(frame(desktop, firefox));

        assertTrue(desktop.toggleWindowMaximised(firefox));
        idle();

        int[] after = bounds(frame(desktop, firefox));
        assertTrue("it is as wide as the room or wider than before", after[2] >= before[2]);
        assertNull("the shell is not shown", host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)));

        assertTrue(desktop.focusWindow(1));
        idle();
        assertNotNull("in focus, the shell stands in its tile again", host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)));
        assertTrue("and Firefox stays maximised for when it is back in focus", desktop.stack().window(firefox).isMaximised());
    }

    @Test public void aWindowReordersOntoAnotherMarksSlot() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);

        assertTrue(desktop.reorderWindow(firefox, 1));
        assertEquals(firefox, desktop.frontWindows().get(0).id());
        assertTrue(desktop.reorderWindow(firefox, 1));
        assertEquals(firefox, desktop.frontWindows().get(1).id());
        assertFalse("onto itself, nothing", desktop.reorderWindow(firefox, firefox));
    }

    @Test public void aDwindleWorkspaceReordersThroughItsTiles() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        desktop.setFrontLayout(com.termux.app.tiling.WorkspaceLayout.DWINDLE);
        idle();
        int[] shellRoom = bounds(frame(desktop, 1));

        assertTrue(desktop.reorderWindow(firefox, 1));
        idle();

        assertArrayEquals(shellRoom, bounds(frame(desktop, firefox)));
    }

    @Test public void aStackedWindowIsTakenOutAsATileOfItsOwn() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        assertTrue(desktop.stackWindow(firefox, 1));
        assertEquals(1, desktop.stack().workspace(1).groups().size());

        assertTrue(desktop.separateWindow(firefox));

        assertEquals(2, desktop.stack().workspace(1).groups().size());
        assertFalse(desktop.separateWindow(firefox));
    }

    @Test public void scaleIsTheLinuxAppsOwn() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);

        assertTrue(desktop.windowTakesScale(firefox));
        assertTrue(desktop.showWindowScale(firefox));
        assertEquals(Arrays.asList("scale 70"), host.linux.tools);
        assertFalse("a shell has none", desktop.windowTakesScale(1));
    }

    @Test public void closeFromTheMenuGoesToItsWindowFirst() {
        TilingDesktop desktop = withFirefox();
        long firefox = desktop.windowOfLinux(70);
        desktop.focusWindow(1);

        desktop.closeWindowAsked(firefox);

        assertEquals("the app is asked to close", Arrays.asList(70L), host.linux.asked);
    }

    // --- the other kinds (#419) ---

    @Test public void aLinuxAppOpensAsAWindowAfterTheFocusedOneAndTakesTheKeyboard() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.LEFT);

        desktop.linuxWindowOpened(70, "firefox", "Firefox", false);
        idle();

        TilingWorkspace one = desktop.stack().workspace(1);
        long id = desktop.windowOfLinux(70);
        assertEquals("after the focused window", id, one.groups().get(1).active().id());
        assertEquals(WindowKind.LINUX_APP, desktop.frontKind());
        assertSame("its content is in its frame", ((TilingDesktopView) desktop.view()).frameOf(id),
            host.linux.views.get(70L).getParent());
        assertEquals(Long.valueOf(70), host.linux.focused);
        assertNull("the shell gave the keyboard up", host.panes.focusedHomeFloat());
    }

    @Test public void focusMovingToAShellTakesTheKeyboardFromTheLinuxApp() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", null, false);

        desktop.run(TilingInput.Command.LEFT);

        assertEquals(WindowKind.SHELL, desktop.frontKind());
        assertNull(host.linux.focused);
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(1)), host.panes.focusedHomeFloat());
    }

    @Test public void fromHomeALinuxAppOpensTheWaitingWorkspace() {
        TilingDesktop desktop = start();

        desktop.linuxWindowOpened(70, "firefox", null, false);

        assertEquals(Place.workspace(2), desktop.stack().current());
        assertEquals(WindowKind.LINUX_APP, desktop.frontKind());
    }

    @Test public void theDisplayAndTheAssistantOpenOnceAndAreGoneToAfter() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);

        desktop.openDisplay();
        assertEquals(WindowKind.DISPLAY, desktop.frontKind());
        assertTrue(host.display.attached);
        desktop.openAssistant();
        assertEquals(WindowKind.ASSISTANT, desktop.frontKind());
        assertTrue(host.assistant.focusedNow);
        assertFalse(host.display.focusedNow);
        desktop.run(TilingInput.Command.LEFT);
        desktop.run(TilingInput.Command.LEFT);

        desktop.openDisplay();

        assertEquals(WindowKind.DISPLAY, desktop.frontKind());
        assertEquals("no second Display", 3, desktop.stack().workspace(1).windows().size());
    }

    /** The control bar's Mouse mode and Keyboard reach the window in front's own content (#424). */
    @Test public void theBarsMouseModeAndKeyboardGoToTheFocusedWindowsContent() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        assertFalse("a shell's keyboard is the launcher's", desktop.toggleFrontKeyboard());
        assertFalse(desktop.toggleFrontMouseMode());

        desktop.linuxWindowOpened(70, "firefox", null, false);
        idle();
        assertTrue(desktop.toggleFrontKeyboard());
        assertTrue(desktop.toggleFrontMouseMode());
        assertEquals(Arrays.asList("keyboard 70", "mouse 70"), host.linux.tools);

        desktop.openDisplay();
        idle();
        host.display.hasMouseMode = false;
        assertFalse("a kind with no mouse mode changes nothing", desktop.toggleFrontMouseMode());
        assertTrue(desktop.toggleFrontKeyboard());
        assertEquals(Arrays.asList("mouse 0", "keyboard 0"), host.display.tools);
        assertTrue("the Linux app was not asked again", host.linux.tools.size() == 2);

        desktop.home();
        assertFalse("nothing on Home", desktop.toggleFrontKeyboard());
        assertFalse(desktop.toggleFrontMouseMode());
    }

    @Test public void closingTheDisplayClosesItsWindowAndParksItsView() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.openDisplay();

        desktop.run(TilingInput.Command.CLOSE);

        assertNull(desktop.stack().firstOfKind(WindowKind.DISPLAY));
        assertFalse(host.display.attached);
        assertEquals(WindowKind.SHELL, desktop.frontKind());
    }

    @Test public void closingALinuxWindowAsksTheAppAndClosesWhenItsWindowGoes() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", null, false);
        long id = desktop.windowOfLinux(70);

        desktop.run(TilingInput.Command.CLOSE);
        assertEquals(java.util.Collections.singletonList(70L), host.linux.asked);
        assertNotNull("it waits for the app", desktop.stack().window(id));

        desktop.linuxWindowGone(70);
        assertNull(desktop.stack().window(id));
        assertNull(host.linux.views.get(70L).getParent());
    }

    @Test public void aParkedLinuxWindowDropsItsViewAndComesBackWhenLive() throws Exception {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", null, false);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        assertEquals(Place.workspace(3), desktop.stack().current());
        assertNull("two places down: parked", host.linux.views.get(70L).getParent());

        desktop.run(TilingInput.Command.UP);
        idle();

        assertNotNull(host.linux.views.get(70L).getParent());
    }

    @Test public void afterARestartRunningLinuxAppsSlotBackAndGoneOnesClose() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.linuxWindowOpened(70, "firefox", null, false);
        first.linuxWindowOpened(71, "gimp", null, false);
        first.run(TilingInput.Command.NEW_TERMINAL);
        long firefox = first.windowOfLinux(70);
        long gimp = first.windowOfLinux(71);
        first.onPause();
        restartActivity(first);
        host.linux = new FakeContent();

        TilingDesktop second = start();
        assertNull("waiting for the replay", host.linux.views.get(70L));
        second.linuxWindowOpened(70, "firefox", null, true);
        second.linuxReplayEnded();
        idle();

        assertEquals(firefox, second.windowOfLinux(70));
        assertNull("gimp ended while the launcher was away", second.stack().window(gimp));
        assertSame(((TilingDesktopView) second.view()).frameOf(firefox), host.linux.views.get(70L).getParent());
        assertEquals("the place in front stays", Place.workspace(1), second.stack().current());
    }

    // ---- saved Workspaces (#431) ------------------------------------------------------------------

    @Test public void aSavedWorkspaceLoadsAsANewOneWithItsShellsInTheirDirectories() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        long first = desktop.frontFocusedWindowId();
        desktop.stack().setWorkingDirectory(first, "/proj");
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        long second = desktop.frontFocusedWindowId();
        desktop.stack().setWorkingDirectory(second, "/proj/sub");

        SavedWorkspace saved = desktop.frontAsSaved();
        assertNotNull(saved);
        assertEquals(2, saved.windowCount());
        assertTrue(desktop.nameWorkspaceOf(first, "dev", saved.id()));
        assertTrue(desktop.holdsSaved(saved, "dev"));
        host.cwds.clear();

        assertTrue(desktop.loadSaved(saved, "dev copy", true));
        idle();

        assertEquals(Place.workspace(2), desktop.stack().current());
        TilingWorkspace loaded = desktop.stack().workspace(2);
        assertEquals("dev copy", loaded.name());
        assertEquals(2, loaded.windows().size());
        assertTrue(desktop.stack().workspace(3).isEmpty());
        assertTrue("its shells start in their directories as they are drawn",
            host.cwds.containsAll(Arrays.asList("/proj", "/proj/sub")));
        assertEquals("Workspace 1 is untouched", 2, desktop.stack().workspace(1).windows().size());
    }

    @Test public void homeAndAnEmptyWorkspaceHaveNothingToSave() {
        TilingDesktop desktop = start();
        assertNull(desktop.frontAsSaved());
        desktop.goTo(Place.workspace(2));
        assertNull(desktop.frontAsSaved());
    }

    @Test public void aLoadAskedForQuietlyKeepsThePlaceInFront() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        SavedWorkspace saved = desktop.frontAsSaved();
        desktop.run(TilingInput.Command.UP);
        idle();

        assertTrue(desktop.loadSaved(saved, "dev", false));
        idle();

        assertEquals(Place.home(0), desktop.stack().current());
        assertEquals("dev", desktop.stack().workspace(2).name());
    }

    @Test public void aLoadedLinuxAppIsLaunchedAgainAndItsWindowGoesBackInPlace() {
        TilingDesktop desktop = start();
        long waiting = loadWebWorkspace(desktop);
        int windows = desktop.stack().workspace(2).windows().size();

        host.saved.answer.launched(waiting, "firefox.desktop");
        host.saved.keys.put(90L, "firefox.desktop");
        host.saved.started.add(90L);
        desktop.linuxWindowOpened(90, "firefox", "Firefox", false);
        idle();

        assertEquals(waiting, desktop.windowOfLinux(90));
        assertEquals("no new window", windows, desktop.stack().workspace(2).windows().size());
        assertSame(((TilingDesktopView) desktop.view()).frameOf(waiting), host.linux.views.get(90L).getParent());
    }

    @Test public void aWindowTheUserOpensIsNeverClaimedByAWaitingTile() {
        TilingDesktop desktop = start();
        long waiting = loadWebWorkspace(desktop);
        host.saved.answer.launched(waiting, "firefox.desktop");
        host.saved.keys.put(95L, "firefox.desktop");

        desktop.linuxWindowOpened(95, "firefox", "Firefox", false);
        idle();

        long opened = desktop.windowOfLinux(95);
        assertTrue(opened != TilingStack.NO_WINDOW);
        assertTrue("a window of its own", opened != waiting);
        assertNotNull("the tile still waits for its own app", desktop.stack().window(waiting));
        assertEquals(desktop.stack().placeOf(opened), desktop.stack().current());
    }

    @Test public void aLinuxAppThatIsNotFoundClosesItsTileAtOnce() {
        TilingDesktop desktop = start();
        long waiting = loadWebWorkspace(desktop);

        host.saved.answer.notFound(waiting);
        idle();

        assertNull(desktop.stack().window(waiting));
        assertEquals(1, desktop.stack().workspace(2).windows().size());
        assertEquals(WindowKind.SHELL, desktop.stack().workspace(2).windows().get(0).kind());
    }

    @Test public void aLinuxAppThatNeverOpensClosesItsWindowAtTheBackstop() {
        TilingDesktop desktop = start();
        long waiting = loadWebWorkspace(desktop);

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(TilingDesktop.LINUX_RELAUNCH_BACKSTOP_MS + 1));

        assertNull(desktop.stack().window(waiting));
        desktop.linuxWindowOpened(91, "firefox", null, false);
        assertTrue("a late window is a new one", desktop.windowOfLinux(91) != TilingStack.NO_WINDOW);
    }

    @Test public void aTileStillWaitingKeepsWaitingThroughARecreate() {
        TilingDesktop first = start();
        long waiting = loadWebWorkspace(first);
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();
        second.linuxReplayEnded();
        idle();
        assertNotNull("not closed as gone by the replay", second.stack().window(waiting));

        host.saved.answer.launched(waiting, "firefox.desktop");
        host.saved.keys.put(90L, "firefox.desktop");
        host.saved.started.add(90L);
        second.linuxWindowOpened(90, "firefox", null, false);
        idle();

        assertEquals("the answer reached the desktop alive now", waiting, second.windowOfLinux(90));
    }

    @Test public void renamingASavedWorkspaceRenamesItsWorkspaceAndItIsKnownByItsId() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        long first = desktop.frontFocusedWindowId();
        SavedWorkspace saved = desktop.frontAsSaved();
        SavedWorkspace other = desktop.frontAsSaved();
        assertFalse(desktop.holdsSaved(saved, "dev"));

        assertTrue(desktop.nameWorkspaceOf(first, "dev", saved.id()));
        assertTrue(desktop.holdsSaved(saved, "dev"));
        assertFalse("the same name is not the same save", desktop.holdsSaved(other, "dev"));

        desktop.savedRenamed(saved.id(), "dev", "work");
        assertEquals("work", desktop.stack().workspace(1).name());
        assertTrue(desktop.holdsSaved(saved, "work"));
        desktop.stack().renameWorkspace(1, "mine");
        desktop.savedRenamed(saved.id(), "work", "other");
        assertEquals("a name the user gave stays", "mine", desktop.stack().workspace(1).name());
    }

    @Test public void openAtStartLoadsOnceOntoTheDesktopAliveWhenTheFilesAreRead() {
        SavedWorkspaceStore files = new SavedWorkspaceStore(new File(folder.getRoot(), "saved"));
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        idle();
        files.save("dev", first.frontAsSaved().withOpenAtStart(true), false);
        List<Runnable> queued = new ArrayList<>();
        SavedWorkspaces.forgetProcessState();
        SavedWorkspaces firstSaved = new SavedWorkspaces(activity, first, files, () -> { }, queued::add, Runnable::run);
        firstSaved.openAtStart(first.startedCold());
        // The activity is recreated before the files are read.
        first.onPause();
        restartActivity(first);
        firstSaved.release();
        TilingDesktop second = start();
        assertFalse(second.startedCold());
        SavedWorkspaces secondSaved = new SavedWorkspaces(activity, second, files, () -> { }, queued::add, Runnable::run);
        secondSaved.openAtStart(second.startedCold());

        for (Runnable task : new ArrayList<>(queued)) task.run();
        idle();

        int dev = 0;
        for (int n = 1; n <= second.stack().workspaceCount(); n++) {
            if ("dev".equals(second.stack().workspace(n).name())) dev++;
        }
        assertEquals(1, dev);
        secondSaved.openAtStart(false);
        assertEquals("once per process", 2, queued.size());
        SavedWorkspaces.forgetProcessState();
    }

    /** Workspace 1 with its terminal and a Linux app, saved and loaded as Workspace 2; its Linux tile's id. */
    private long loadWebWorkspace(@NonNull TilingDesktop desktop) {
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", "Firefox", false);
        idle();
        host.saved.keys.put(70L, "firefox.desktop");
        SavedWorkspace saved = desktop.frontAsSaved();
        assertTrue(desktop.loadSaved(saved, "web", true));
        idle();
        long waiting = desktop.stack().workspace(2).windows().get(1).id();
        assertEquals(WindowKind.LINUX_APP, desktop.stack().window(waiting).kind());
        assertEquals(Collections.singletonList(waiting + " firefox.desktop firefox"), host.saved.relaunched);
        return waiting;
    }

    @Test public void onlyANewProcessIsAColdStart() {
        TilingDesktop first = start();
        assertTrue(first.startedCold());
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();

        assertFalse("an activity recreated in the same process", second.startedCold());
    }

    @Test public void withNoLinuxHostEverySavedLinuxWindowCloses() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.linuxWindowOpened(70, "firefox", null, false);
        long firefox = first.windowOfLinux(70);
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();
        assertNotNull(second.stack().window(firefox));
        second.linuxReplayEnded();

        assertNull(second.stack().window(firefox));
    }

    @Test public void aReplayedAppNoSavedWindowTakesJoinsWithoutMovingThePlace() {
        TilingDesktop desktop = start();
        desktop.linuxWindowOpened(80, "foot", null, true);
        desktop.linuxReplayEnded();

        assertEquals(Place.home(0), desktop.stack().current());
        assertNotNull(desktop.stack().window(desktop.windowOfLinux(80)));
    }

    @Test public void theHostStoppingClosesItsWindowsButNotTheWaitingOnes() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.linuxWindowOpened(70, "firefox", null, false);
        long firefox = first.windowOfLinux(70);
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();
        second.linuxWindowsGone();
        assertNotNull("still waiting", second.stack().window(firefox));
        second.linuxWindowOpened(70, "firefox", null, true);
        second.linuxReplayEnded();
        second.linuxWindowsGone();

        assertNull(second.stack().window(firefox));
    }

    @Test public void aTouchOnAWindowsBodyGoesToItsContent() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", null, false);
        idle();
        View content = host.linux.views.get(70L);
        final int[] touches = {0};
        content.setOnTouchListener((v, e) -> {
            touches[0]++;
            return true;
        });
        int[] at = new int[2];
        int[] origin = new int[2];
        content.getLocationInWindow(at);
        root.getLocationInWindow(origin);
        assertTrue("laid out", content.getWidth() > 0);
        float x = at[0] - origin[0] + content.getWidth() / 2f;
        float y = at[1] - origin[1] + content.getHeight() / 2f;
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent move = android.view.MotionEvent.obtain(now, now + 16,
            android.view.MotionEvent.ACTION_MOVE, x - 300, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 32,
            android.view.MotionEvent.ACTION_UP, x - 300, y, 0);
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(move);
        root.dispatchTouchEvent(up);

        assertEquals("down, move and up all reach the app", 3, touches[0]);
        assertEquals("nothing changes place", desktop.windowOfLinux(70), desktop.stack().focusedWindowId());
        assertEquals(Place.workspace(1), desktop.stack().current());
    }

    // --- review fixes (#419) ---

    @Test public void aWaitingLinuxWindowClosesAtOnce() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.linuxWindowOpened(70, "firefox", null, false);
        long firefox = first.windowOfLinux(70);
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();
        assertEquals(firefox, second.stack().focusedWindowId());
        second.run(TilingInput.Command.CLOSE);

        assertNull("nothing to ask: it closes", second.stack().window(firefox));
        assertTrue(host.linux.asked.isEmpty());
    }

    @Test public void anAppIdAndNameLearntLaterAreSavedWithTheWindow() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.linuxWindowOpened(70, "", "Linux app", false);
        long id = first.windowOfLinux(70);

        first.linuxWindowChanged(70, "org.gnome.Nautilus", "Files");
        first.onPause();
        restartActivity(first);
        TilingDesktop second = start();

        assertEquals("org.gnome.Nautilus", second.stack().window(id).appId());
        assertEquals("Files", second.stack().window(id).name());
        second.linuxWindowOpened(91, "org.gnome.Nautilus", null, true);
        second.linuxReplayEnded();
        assertEquals("matched by the app id it learnt", id, second.windowOfLinux(91));
    }

    @Test public void savedLinuxWindowsStopWaitingWhenNoReplayComes() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.linuxWindowOpened(70, "firefox", null, false);
        long firefox = first.windowOfLinux(70);
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(TilingDesktop.LINUX_REPLAY_BACKSTOP_MS - 100));
        assertNotNull("still waiting", second.stack().window(firefox));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));

        assertNull("the backstop closed it", second.stack().window(firefox));
        second.linuxWindowOpened(70, "firefox", null, true);
        assertTrue("a late replay opens it as a new window",
            second.windowOfLinux(70) != TilingStack.NO_WINDOW);
    }

    @Test public void aPressOnAWindowsOuterPixelReachesItAndTheAirBesideItHolds() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", null, false);
        idle();
        View content = host.linux.views.get(70L);
        final int[] touches = {0};
        content.setOnTouchListener((v, e) -> {
            touches[0]++;
            return true;
        });
        int[] at = new int[2];
        int[] origin = new int[2];
        content.getLocationInWindow(at);
        root.getLocationInWindow(origin);
        float left = at[0] - origin[0];
        float right = left + content.getWidth();
        float y = at[1] - origin[1] + content.getHeight() / 2f;

        tap(left + 0.5f, y);
        tap(right - 0.5f, y);
        assertEquals("both outer pixels reach the app", 4, touches[0]);

        tap(left - 1f, y);
        assertEquals("the air beside it is the hold's", 4, touches[0]);
    }

    // --- navigation mode (#433) ---

    @Test public void inNavigationModeWindowsTakeNoTouchAndATapFocusesAndLeaves() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.linuxWindowOpened(70, "firefox", null, false);
        idle();
        View content = host.linux.views.get(70L);
        final int[] touches = {0};
        content.setOnTouchListener((v, e) -> {
            touches[0]++;
            return true;
        });
        float[] at = centreOf(content);

        desktop.toggleNavigation();
        idle();
        assertTrue(desktop.isNavigating());
        tap(at[0], at[1]);
        idle();

        assertEquals("the app got nothing", 0, touches[0]);
        assertFalse("the tap left the mode", desktop.isNavigating());
        assertEquals(desktop.windowOfLinux(70), desktop.frontFocusedWindowId());
        tap(at[0], at[1]);
        assertEquals("out of the mode the app has its touches again", 2, touches[0]);
    }

    /** Round 3: the mode shows on the windows (dimmed, white rims), not as a scrim over the screen. */
    @Test public void navigationModeDressesEveryWindowAndTakesItOffAgain() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        long id = desktop.stack().workspace(1).windows().get(0).id();
        TilingDesktopView view = (TilingDesktopView) desktop.view();

        desktop.toggleNavigation();
        idle();
        assertTrue(view.frameOf(id).wearsNavigationLook());

        desktop.toggleNavigation();
        idle();
        assertFalse(view.frameOf(id).wearsNavigationLook());
    }

    @Test public void onHomeTheModeTakesHomesTouchesAndTheHomePressLeavesIt() {
        TilingDesktop desktop = start();

        desktop.toggleNavigation();
        idle();
        assertEquals("the stack shows over Home", View.VISIBLE, desktop.view().getVisibility());

        desktop.home();
        idle();
        assertFalse(desktop.isNavigating());
        assertEquals(View.INVISIBLE, desktop.view().getVisibility());
        assertFalse("Back finds nothing left", desktop.leaveNavigation());
    }

    @Test public void aSlideSidewaysSettlesOnTheNearestWindowAndStaysInTheMode() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        assertEquals(2, desktop.frontFocusedWindowId());

        desktop.toggleNavigation();
        idle();
        // The finger pulls the strip right: window 1, the column before, comes under the middle.
        drag(300f, 1000f, 700f, 0f);
        idle();

        assertEquals(1, desktop.frontFocusedWindowId());
        assertTrue(desktop.isNavigating());
    }

    @Test public void aSlideDownFromWorkspaceOneSettlesOnHome() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();

        desktop.toggleNavigation();
        idle();
        drag(500f, 300f, 0f, 1500f);
        idle();

        assertFalse(desktop.isWorkspaceInFront());
        assertTrue(desktop.isNavigating());
    }

    @Test public void aWindowCarriedOntoAnothersTopHalfTakesItsTopUnderDwindle() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.setFrontLayout(WorkspaceLayout.DWINDLE);
        idle();
        assertTrue("window 1 over window 2", top(frame(desktop, 1)) < top(frame(desktop, 2)));
        float[] from = centreOf(frame(desktop, 2));
        float[] one = centreOf(frame(desktop, 1));
        float onTop = one[1] - frame(desktop, 1).getHeight() * 0.4f;

        desktop.toggleNavigation();
        idle();
        carry(from[0], from[1], one[0], onTop);

        assertTrue("window 2 above window 1 now", top(frame(desktop, 2)) < top(frame(desktop, 1)));
        assertEquals(2, desktop.stack().workspace(1).groups().size());
        assertEquals(2, desktop.frontFocusedWindowId());
    }

    @Test public void aWindowCarriedOntoAnothersCentreJoinsItAsATab() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.setFrontLayout(WorkspaceLayout.DWINDLE);
        idle();
        float[] from = centreOf(frame(desktop, 2));
        float[] one = centreOf(frame(desktop, 1));

        desktop.toggleNavigation();
        idle();
        carry(from[0], from[1], one[0], one[1]);

        assertEquals(1, desktop.stack().workspace(1).groups().size());
        assertEquals(TileGroup.Mode.TABS, desktop.stack().workspace(1).groups().get(0).mode());
    }

    @Test public void aDropBeforeTheFirstColumnOrIntoAnEmptyWorkspaceMovesTheWindowThere() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        float[] span = {0f, 0f, 1000f, 2000f};
        List<com.termux.app.tiling.navigation.NavigationDrop.Tile> columns = Arrays.asList(
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(1, 0, 0f, 0f, 1000f, 2000f),
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(2, 1, 1020f, 0f, 2020f, 2000f),
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(3, 2, 2040f, 0f, 3040f, 2000f));

        assertTrue(desktop.dropWindow(3, 1,
            com.termux.app.tiling.navigation.NavigationDrop.at(columns, false, 3, 5f, 500f, 30f, span)));
        idle();
        assertEquals(3, desktop.frontWindows().get(0).id());
        assertEquals(1, desktop.frontWindows().get(1).id());

        assertTrue(desktop.dropWindow(2, 2, com.termux.app.tiling.navigation.NavigationDrop.at(
            Collections.<com.termux.app.tiling.navigation.NavigationDrop.Tile>emptyList(), false, 2, 5f, 5f, 30f, span)));
        idle();
        assertEquals(Place.workspace(2), desktop.stack().placeOf(2));
    }

    @Test public void aWindowStackedAboveAnotherByItsTopHalfUnderScrolling() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        float[] span = {0f, 0f, 1000f, 2000f};
        List<com.termux.app.tiling.navigation.NavigationDrop.Tile> columns = Arrays.asList(
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(1, 0, 0f, 0f, 1000f, 2000f),
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(2, 1, 1020f, 0f, 2020f, 2000f));

        assertTrue(desktop.dropWindow(2, 1,
            com.termux.app.tiling.navigation.NavigationDrop.at(columns, false, 2, 500f, 100f, 30f, span)));
        idle();
        List<com.termux.app.tiling.TilingWindow> column = desktop.stack().workspace(1).groups().get(0).windows();
        assertEquals(2, column.get(0).id());
        assertEquals(1, column.get(1).id());
    }

    @Test public void theTopOfAStackCarriedIntoTheGapBesideItGetsAColumnOfItsOwn() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        assertTrue(desktop.stack().stack(2, 1));
        idle();
        assertEquals(2, desktop.stack().workspace(1).groups().size());
        float[] span = {0f, 0f, 1000f, 2000f};
        List<com.termux.app.tiling.navigation.NavigationDrop.Tile> columns = Arrays.asList(
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(1, 0, 0f, 0f, 1000f, 990f),
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(2, 0, 0f, 1010f, 1000f, 2000f),
            new com.termux.app.tiling.navigation.NavigationDrop.Tile(3, 1, 1020f, 0f, 2020f, 2000f));

        assertTrue(desktop.dropWindow(1, 1,
            com.termux.app.tiling.navigation.NavigationDrop.at(columns, false, 1, 1010f, 500f, 30f, span)));
        idle();
        List<com.termux.app.tiling.TileGroup> groups = desktop.stack().workspace(1).groups();
        assertEquals("window 1 left the stack for a column of its own", 3, groups.size());
        assertEquals(2, groups.get(0).windows().get(0).id());
        assertEquals(1, groups.get(1).windows().get(0).id());
        assertEquals(3, groups.get(2).windows().get(0).id());
    }

    @Test public void aWindowHeldAtTheBottomEdgeIsCarriedDownToTheNextWorkspace() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        float[] from = centreOf(frame(desktop, 2));

        desktop.toggleNavigation();
        idle();
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, from[0], from[1], 0);
        root.dispatchTouchEvent(down);
        down.recycle();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 50L));
        long later = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent move = android.view.MotionEvent.obtain(now, later,
            android.view.MotionEvent.ACTION_MOVE, from[0], 1995f, 0);
        root.dispatchTouchEvent(move);
        move.recycle();
        // Held at the edge: after its beat the view keeps moving down through the places.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(3000L));
        assertTrue("the view moved on", view(desktop).position() > 1.5f);
        long end = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, end,
            android.view.MotionEvent.ACTION_UP, from[0], 1995f, 0);
        root.dispatchTouchEvent(up);
        up.recycle();
        idle();

        assertEquals("dropped into the waiting Workspace", Place.workspace(2), desktop.stack().placeOf(2));
        assertEquals(Place.workspace(2), desktop.front());
    }

    @Test public void aTapThatStopsASettleFindsTheWindowWhereItIsDrawn() {
        host.motion = true;
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        desktop.toggleNavigation();
        idle();
        float height = view(desktop).getHeight();

        // Slid most of the way to Home and let go: the view settles on Home, still sliding.
        drag(500f, 300f, 0f, height * 0.6f);
        assertTrue("the settle is still on its way: " + view(desktop).position() + " moving=" + view(desktop).isMoving(),
            view(desktop).position() > 0.1f);
        // Workspace 1's window is still drawn on the lower part of the screen: a tap there takes it.
        tap(500f, height * 0.85f);
        idle();

        assertFalse("the tap took a window and left the mode", desktop.isNavigating());
        assertEquals(Place.workspace(1), desktop.front());
        assertEquals(1, desktop.frontFocusedWindowId());
    }

    @Test public void aLongPressOnAFloatPicksNothingUpAndATapTakesTheFloat() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FLOAT));
        desktop.stack().focusWindow(1);
        idle();
        float[] onFloat = centreOf(floatFrame(desktop, 2));

        desktop.toggleNavigation();
        idle();
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, onFloat[0], onFloat[1], 0);
        root.dispatchTouchEvent(down);
        down.recycle();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 50L));
        assertEquals("the tile under the Float is not picked up", 1f, frame(desktop, 1).getAlpha(), 0.001f);
        // Nothing picked up, the lift where it landed is a tap: on the Float, not the tile.
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(),
            android.view.MotionEvent.ACTION_UP, onFloat[0], onFloat[1], 0);
        root.dispatchTouchEvent(up);
        up.recycle();
        idle();
        assertFalse(desktop.isNavigating());
        assertEquals("the Float over the tile took the tap", 2, desktop.stack().focusedWindowId());
    }

    @Test public void overlappingFloatsGiveTheTapToTheOneDrawnOnTop() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FLOAT));
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FLOAT));
        desktop.stack().focusWindow(1);
        idle();
        TilingDesktopView.FloatFrame a = floatFrame(desktop, 2);
        TilingDesktopView.FloatFrame b = floatFrame(desktop, 3);
        assertSame(a.getParent(), b.getParent());
        android.view.ViewGroup layer = (android.view.ViewGroup) a.getParent();
        long onTop = layer.indexOfChild(b) > layer.indexOfChild(a) ? 3 : 2;
        int[] pa = bounds(a);
        int[] pb = bounds(b);
        // A point both hold.
        float x = (Math.max(pa[0], pb[0]) + Math.min(pa[0] + pa[2], pb[0] + pb[2])) / 2f;
        float y = (Math.max(pa[1], pb[1]) + Math.min(pa[1] + pa[3], pb[1] + pb[3])) / 2f;
        assertTrue("the two Floats overlap", x > Math.max(pa[0], pb[0]) && y > Math.max(pa[1], pb[1]));
        int[] origin = new int[2];
        int[] layerAt = new int[2];
        root.getLocationInWindow(origin);
        layer.getLocationInWindow(layerAt);

        desktop.toggleNavigation();
        idle();
        tap(x + layerAt[0] - origin[0], y + layerAt[1] - origin[1]);
        idle();
        assertFalse(desktop.isNavigating());
        assertEquals(onTop, desktop.stack().focusedWindowId());
    }

    @Test public void aTapOnAHomeFloatTakesItAndLeavesTheMode() {
        host.homeLayer = new FrameLayout(activity);
        root.addView(host.homeLayer, new FrameLayout.LayoutParams(1000, 2000));
        host.homeIds = new ArrayList<>(Arrays.asList(11, 22));
        TilingDesktop desktop = start();
        long id = desktop.stack().openIn(Place.home(0), WindowSpec.shell(null), true);
        desktop.onRoomChanged();
        idle();
        float[] at = centreOf(floatFrame(desktop, id));

        desktop.toggleNavigation();
        idle();
        tap(at[0], at[1]);
        idle();

        assertFalse("the tap left the mode", desktop.isNavigating());
        assertEquals(id, desktop.stack().focusedWindowId());
    }

    @Test public void aWindowPickedUpInTheEdgeBandPansWithoutMoving() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        float[] mid = centreOf(frame(desktop, 2));
        float x = mid[0];
        float y = mid[1] + frame(desktop, 2).getHeight() / 2f - 4f;

        desktop.toggleNavigation();
        idle();
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        root.dispatchTouchEvent(down);
        down.recycle();
        // Picked up and held still, no move at all.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(3000L));
        assertTrue("the view moved on", view(desktop).position() > 1.5f);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(),
            android.view.MotionEvent.ACTION_UP, x, y, 0);
        root.dispatchTouchEvent(up);
        up.recycle();
        idle();
        assertEquals(Place.workspace(2), desktop.stack().placeOf(2));
    }

    @Test public void aDropLandsWhereTheFingerLiftsNotWhereItLastMoved() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.setFrontLayout(WorkspaceLayout.DWINDLE);
        idle();
        float[] from = centreOf(frame(desktop, 2));
        float[] one = centreOf(frame(desktop, 1));
        float onTop = one[1] - frame(desktop, 1).getHeight() * 0.4f;

        desktop.toggleNavigation();
        idle();
        // The last move is on window 1's top half; the lift, in its centre.
        carry(from[0], from[1], one[0], onTop, one[0], one[1]);

        assertEquals("joined as a tab", 1, desktop.stack().workspace(1).groups().size());
        assertEquals(TileGroup.Mode.TABS, desktop.stack().workspace(1).groups().get(0).mode());
    }

    /** A long press at ({@code x}, {@code y}), moved last to ({@code moveX}, {@code moveY}), let go at ({@code upX}, {@code upY}). */
    private void carry(float x, float y, float moveX, float moveY, float upX, float upY) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        root.dispatchTouchEvent(down);
        down.recycle();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 50L));
        long later = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent move = android.view.MotionEvent.obtain(now, later,
            android.view.MotionEvent.ACTION_MOVE, moveX, moveY, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, later + 16,
            android.view.MotionEvent.ACTION_UP, upX, upY, 0);
        root.dispatchTouchEvent(move);
        root.dispatchTouchEvent(up);
        move.recycle();
        up.recycle();
        idle();
    }

    /** A long press at ({@code x}, {@code y}) in navigation mode, carried to ({@code toX}, {@code toY}) and let go. */
    private void carry(float x, float y, float toX, float toY) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        root.dispatchTouchEvent(down);
        down.recycle();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 50L));
        long later = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent move = android.view.MotionEvent.obtain(now, later,
            android.view.MotionEvent.ACTION_MOVE, toX, toY, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, later + 16,
            android.view.MotionEvent.ACTION_UP, toX, toY, 0);
        root.dispatchTouchEvent(move);
        root.dispatchTouchEvent(up);
        move.recycle();
        up.recycle();
        idle();
    }

    /** {@code view}'s middle in the root's coordinates. */
    @NonNull private float[] centreOf(@NonNull View view) {
        int[] at = new int[2];
        int[] origin = new int[2];
        view.getLocationInWindow(at);
        root.getLocationInWindow(origin);
        return new float[] {at[0] - origin[0] + view.getWidth() / 2f, at[1] - origin[1] + view.getHeight() / 2f};
    }

    private void tap(float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 16,
            android.view.MotionEvent.ACTION_UP, x, y, 0);
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    // --- full screen (#426) ---

    @Test public void aFullScreenWindowTakesTheWholeRoomAndTheOthersWait() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        View view = desktop.view();
        long focused = desktop.stack().focusedWindowId();
        int[] before = bounds(frame(desktop, focused));
        assertTrue("air round a tiled window", before[2] < view.getWidth());
        assertNotNull(frameOrNull(desktop, 1));

        assertTrue(desktop.setFrontFullScreen(true));
        idle();

        assertTrue(desktop.frontFullScreen());
        int[] full = bounds(frame(desktop, focused));
        assertEquals(view.getWidth(), full[2]);
        assertEquals(view.getHeight(), full[3]);
        assertEquals(0, full[0]);
        assertEquals(0, full[1]);
        assertNull(frameOrNull(desktop, 1));

        assertTrue(desktop.setFrontFullScreen(false));
        idle();
        assertFalse(desktop.frontFullScreen());
        assertEquals(before[2], bounds(frame(desktop, focused))[2]);
        assertNotNull(frameOrNull(desktop, 1));
    }

    @Test public void aFullScreenWindowIsResizedInALayoutPassOfItsOwnNotInsideTheDesktops() {
        // Round 2: entering full screen rendered the windows from inside the layout pass that grew
        // the desktop (onSizeChanged); the window kept the room it had and stopped where the bar had
        // stood. The grown room is rendered after that pass and laid out in the next one.
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        long focused = desktop.stack().focusedWindowId();
        View view = desktop.view();
        assertTrue(desktop.setFrontFullScreen(true));
        idle();
        int before = bounds(frame(desktop, focused))[3];

        // The bar goes down to its handle: the desktop's layout pass grows it over the bar's room.
        view.setLayoutParams(new FrameLayout.LayoutParams(1000, 2240));
        View top = root.getRootView();
        top.measure(View.MeasureSpec.makeMeasureSpec(top.getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(top.getHeight(), View.MeasureSpec.EXACTLY));
        top.layout(top.getLeft(), top.getTop(), top.getRight(), top.getBottom());
        assertEquals(2240, view.getHeight());
        assertEquals("no window layout written inside the desktop's layout pass", before,
            bounds(frame(desktop, focused))[3]);

        idle();
        assertEquals("down to the handle", view.getHeight(), bounds(frame(desktop, focused))[3]);
    }

    @Test public void aTerminalResizedWhileHiddenUnderAHoldIsSizedWhenShownAgain() {
        // Review of round 2 (landscape): a window laid out at the rotated width while the grid was
        // held, and hidden when the hold ended, lost that size; shown again at the same pixel size
        // its shell kept the old columns. The desktop sizes its shown terminals after each render.
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        TerminalSession shell = host.live.get(desktop.handleOf(1));
        TerminalView view = host.panes.getViewForSession(shell);
        assertNotNull(view);
        int portrait = view.getWidth();
        List<Integer> sizedAt = new ArrayList<>();
        view.setSizeUpdateObserverForTests(v -> {
            if (v.isShown()) sizedAt.add(v.getWidth());
        });

        // A rotation: the grid is held while the desktop widens; the window is hidden (Home in
        // front) when the hold ends, so the size it was laid out at is dropped.
        host.panes.beginHostSurfaceResize();
        desktop.view().setLayoutParams(new FrameLayout.LayoutParams(1800, 1000));
        idle();
        desktop.run(TilingInput.Command.UP);
        idle();
        host.panes.finishHostSurfaceResizeKeepingBottom();
        idle();
        int wide = view.getWidth();
        assertTrue("laid out wider: " + wide + " vs " + portrait, wide > portrait);
        sizedAt.clear();

        desktop.run(TilingInput.Command.DOWN);
        idle();
        assertTrue("shown again, its grid is sized at the width it stands at: " + sizedAt,
            sizedAt.contains(wide));
    }

    @Test public void aPortraitWorkspaceWiderThanTallStillOpensPortraitWidths() {
        // Review of round 2: the keyboard can leave a portrait Workspace wider than tall; its windows
        // still open at a half and its menu offers portrait widths.
        TilingDesktop desktop = start();
        desktop.view().setLayoutParams(new FrameLayout.LayoutParams(1000, 600));
        idle();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        assertFalse(desktop.landscapeNow());
        int[] first = bounds(frame(desktop, 1));
        int[] second = bounds(frame(desktop, 2));
        assertEquals("two halves side by side", first[2], second[2]);
    }

    /** Review of round 3: the menu's 1/3 did nothing in portrait; it sets the column to a third. */
    @Test public void aThirdFromTheMenuSetsAPortraitColumnToAThird() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        assertFalse(desktop.landscapeNow());
        int[] half = bounds(frame(desktop, 1));

        assertTrue(desktop.setWindowWidth(1, 1f / 3f));
        idle();

        int[] third = bounds(frame(desktop, 1));
        assertTrue("narrower than the half it was: " + third[2] + " vs " + half[2], third[2] < half[2]);
        float gap = TilingDesktopView.GAP_DP * desktop.view().getResources().getDisplayMetrics().density;
        float full = (half[2] + gap) * 2f - gap;
        assertEquals(((full + gap) / 3f) - gap, third[2], 2f);
    }

    @Test public void theFullScreenToolTogglesAndHomeHasNone() {
        TilingDesktop desktop = start();
        assertFalse(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN));
        assertFalse(desktop.setFrontFullScreen(true));
        desktop.run(TilingInput.Command.DOWN);
        idle();

        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN));
        assertTrue(desktop.frontFullScreen());
        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN));
        assertFalse(desktop.frontFullScreen());
    }

    @Test public void renamingAWorkspaceReDrawsAndIsSaved() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        int[] told = {0};
        desktop.setFrontListener(() -> told[0]++);

        assertTrue(desktop.renameWorkspace(1, "Mail"));
        idle();

        assertEquals("Mail", desktop.stack().workspace(1).name());
        assertTrue("the front is told, so the bar draws it again", told[0] > 0);
        desktop.onPause();
        TilingDesktopStore.Saved saved = store.load();
        assertNotNull(saved);
        assertEquals("Mail", saved.stack.workspace(1).name());
        assertFalse("no such Workspace", desktop.renameWorkspace(99, "x"));
    }

    @Test public void fullScreenIsSavedAndComesBackAfterARestart() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        desktop.setFrontFullScreen(true);
        desktop.onPause();
        TilingDesktopStore.Saved saved = store.load();
        assertNotNull(saved);
        assertNotNull(saved.stack.fullScreenWindow(saved.stack.current().workspaceNumber()));
    }

    @Test public void everyPageStandsInItsOwnRoomAcrossAFullScreenAndATiledWorkspace() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.UP);
        idle();
        TilingDesktopView view = (TilingDesktopView) desktop.view();
        int[] normal = view.pageAt(1).viewportBounds();
        assertTrue("air round a tiled page", normal[0] > 0 && normal[2] < view.getWidth());

        // Workspace 1 full screen, Workspace 2 (a neighbour in the crossing) tiled.
        assertTrue(desktop.setFrontFullScreen(true));
        idle();
        int[] full = view.pageAt(1).viewportBounds();
        assertEquals(0, full[0]);
        assertEquals(view.getWidth(), full[2]);
        assertArrayEquals(normal, view.pageAt(2).viewportBounds());

        // Stepping to the tiled Workspace: the full-screen one stays a neighbour in its own room.
        desktop.run(TilingInput.Command.DOWN);
        idle();
        assertEquals(view.getWidth(), view.pageAt(1).viewportBounds()[2]);
        assertArrayEquals(normal, view.pageAt(2).viewportBounds());

        // The tiled one in front goes full screen too; a neighbour that is itself full screen is laid out so.
        assertTrue(desktop.setFrontFullScreen(true));
        idle();
        assertEquals(view.getWidth(), view.pageAt(2).viewportBounds()[2]);
        assertEquals(view.getWidth(), view.pageAt(1).viewportBounds()[2]);
        assertEquals(view.getWidth(), bounds(frame(desktop, desktop.stack().focusedWindowId()))[2]);
        desktop.run(TilingInput.Command.UP);
        idle();
        assertEquals(view.getWidth(), bounds(frame(desktop, desktop.stack().focusedWindowId()))[2]);
    }

    @Test public void theFrontListenerHearsOfFullScreen() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();
        final boolean[] seen = {false};
        desktop.setFrontListener(() -> seen[0] = desktop.frontFullScreen());
        desktop.setFrontFullScreen(true);
        idle();
        assertTrue(seen[0]);
    }

    // --- Dwindle (#420) ---

    @Test public void theLayoutSwitchesToDwindleAndBackAndIsSavedWithTheWorkspace() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WORKSPACE_LAYOUT_TOGGLE));
        idle();

        assertEquals(WorkspaceLayout.DWINDLE, desktop.frontLayout());
        // The windows split the room in their order: 1 above, 2 and 3 side by side below.
        TilingDesktopView.WindowFrame one = frame(desktop, 1);
        TilingDesktopView.WindowFrame two = frame(desktop, 2);
        TilingDesktopView.WindowFrame three = frame(desktop, 3);
        // The room is a little taller than wide, so each split stacks: 1, then 2 over 3 below.
        assertTrue(bounds(one)[1] + bounds(one)[3] <= bounds(two)[1]);
        assertTrue(bounds(two)[1] + bounds(two)[3] <= bounds(three)[1]);
        assertEquals(bounds(two)[3], bounds(three)[3]);
        desktop.onPause();
        TilingDesktopStore.Saved saved = store.load();
        assertNotNull(saved);
        assertEquals(WorkspaceLayout.DWINDLE, saved.stack.workspace(1).layout());

        int dwindleHeight = bounds(three)[3];
        assertTrue(desktop.setFrontLayout(WorkspaceLayout.SCROLLING));
        idle();
        assertEquals(WorkspaceLayout.SCROLLING, desktop.frontLayout());
        assertTrue("each tile a full-height column again", bounds(frame(desktop, 3))[3] > 2 * dwindleHeight);
        assertFalse(desktop.setFrontLayout(WorkspaceLayout.SCROLLING));
    }

    @Test public void aWindowTooSmallToSplitJoinsAsATabWhoseTabShowsIt() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.setFrontLayout(WorkspaceLayout.DWINDLE);
        TileGroup tabbed = null;
        for (int i = 0; i < 12 && tabbed == null; i++) {
            desktop.run(TilingInput.Command.NEW_TERMINAL);
            idle();
            for (TileGroup group : desktop.stack().workspace(1).groups()) {
                if (group.mode() == TileGroup.Mode.TABS) tabbed = group;
            }
        }
        assertNotNull("a split too small became a tab", tabbed);
        long shown = tabbed.active().id();
        long other = tabbed.windows().get(0).id();
        assertEquals(desktop.stack().focusedWindowId(), shown);
        assertNotNull(frame(desktop, shown));
        assertNull("the tab not shown is parked", frameOrNull(desktop, other));

        TilingTabStrip strip = find(desktop.view(), TilingTabStrip.class);
        assertNotNull(strip);
        assertEquals(tabbed.windows().size(), strip.row().getChildCount());
        strip.row().getChildAt(0).performClick();
        idle();

        assertEquals(other, desktop.stack().focusedWindowId());
        assertNotNull(frame(desktop, other));
        assertNull(frameOrNull(desktop, shown));
    }

    @Test public void successiveLinuxAppsUnderDwindleFallBackToTabsAtTheirMinimum() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.setFrontLayout(WorkspaceLayout.DWINDLE);
        TileGroup tabbed = null;
        for (int i = 0; i < 12 && tabbed == null; i++) {
            desktop.linuxWindowOpened(70 + i, "app" + i, null, false);
            idle();
            for (TileGroup group : desktop.stack().workspace(1).groups()) {
                if (group.mode() == TileGroup.Mode.TABS) tabbed = group;
            }
        }
        assertNotNull("an app split under its minimum became a tab", tabbed);
        float density = activity.getResources().getDisplayMetrics().density;
        for (TileGroup group : desktop.stack().workspace(1).groups()) {
            TilingDesktopView.WindowFrame frame = frame(desktop, group.active().id());
            int[] b = bounds(frame);
            assertTrue("no tile under the default minimum", b[2] >= TilingDesktop.DEFAULT_MIN_WIDTH_DP * density - 1);
        }
    }

    @Test public void dwindlesSplitsAndTabsSurviveARestart() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.setFrontLayout(WorkspaceLayout.DWINDLE);
        first.run(TilingInput.Command.NEW_TERMINAL);
        first.run(TilingInput.Command.NEW_TERMINAL);
        assertTrue(first.stack().joinTab(3, 2));
        first.onRoomChanged();
        idle();
        int[] twoTabs = bounds(frame(first, 3));
        int[] oneBounds = bounds(frame(first, 1));
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();

        TilingWorkspace one = second.stack().workspace(1);
        assertEquals(WorkspaceLayout.DWINDLE, one.layout());
        assertEquals(2, one.groups().size());
        assertEquals(TileGroup.Mode.TABS, one.groups().get(1).mode());
        assertEquals(3, one.groups().get(1).active().id());
        assertEquals(Arrays.toString(oneBounds), Arrays.toString(bounds(frame(second, 1))));
        assertEquals(Arrays.toString(twoTabs), Arrays.toString(bounds(frame(second, 3))));
        assertNotNull(find(second.view(), TilingTabStrip.class));
    }

    // --- Floats and Pin (#421) ---

    @Test public void floatingTheFocusedWindowLiftsItAboveItsWorkspacesTilesAndKeepsTheKeyboard() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();

        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FLOAT));
        idle();

        assertTrue(desktop.stack().window(2).isFloat());
        assertEquals("floating never moves the focus", 2, desktop.stack().focusedWindowId());
        TilingDesktopView.FloatFrame floating = floatFrame(desktop, 2);
        assertTrue("on its Workspace's page, over the tiles", pageOf(floating) != null);
        assertFalse(floating.getParent() == view(desktop).pinnedLayer());
        assertTrue(shown(floating, host.created.get(1)));
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(2)), host.panes.focusedHomeFloat());
        assertNotNull("a Float is given a box where a new one opens", desktop.stack().window(2).floatBox());
        assertEquals("the tile left has the Workspace's whole width",
            bounds(frame(desktop, 1))[2], (int) Math.round(view(desktop).getWidth()
                - 2 * TilingDesktopView.EDGE_DP * activity.getResources().getDisplayMetrics().density));

        assertTrue(desktop.toggleFrontFloat());
        idle();
        assertFalse(desktop.stack().window(2).isFloat());
        assertNull(view(desktop).floatFrameOf(2));
        assertTrue("a tile again", shown(frame(desktop, 2), host.created.get(1)));
    }

    @Test public void pinShowsTheFloatOnEveryWorkspaceAndUnpinReturnsItToItsOwn() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        assertTrue(desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_PIN));
        idle();
        assertTrue(desktop.stack().window(2).isPinned());
        assertSame("one frame, over every page", view(desktop).pinnedLayer(), floatFrame(desktop, 2).getParent());

        // Two Workspaces down, Workspace 1 is no longer live: its tile parks, its pinned Float shows.
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.DOWN);
        idle();
        assertEquals(Place.workspace(3), desktop.front());
        assertNull(frameOrNull(desktop, 1));
        assertSame(view(desktop).pinnedLayer(), floatFrame(desktop, 2).getParent());
        assertTrue(shown(floatFrame(desktop, 2), host.created.get(1)));
        assertEquals("still Workspace 1's", Place.workspace(1), desktop.stack().placeOf(2));

        desktop.run(TilingInput.Command.UP);
        desktop.run(TilingInput.Command.UP);
        idle();
        assertEquals(2, desktop.frontFocusedWindowId());
        assertTrue(desktop.toggleFrontPin());
        idle();
        assertFalse(desktop.stack().window(2).isPinned());
        assertNotNull("back on its own page", pageOf(floatFrame(desktop, 2)));
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.DOWN);
        idle();
        assertNull("not shown on another Workspace", frameOrNull(desktop, 2));
    }

    @Test public void onHomeThePinnedFloatsLeaveWithTheWorkspaces() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        desktop.toggleFrontPin();

        desktop.home();
        idle();

        assertNull("pinned Floats show on Workspaces, not Home", view(desktop).floatFrameOf(2));
        assertNull(host.panes.focusedHomeFloat());
    }

    @Test public void aPressOnAPinnedFloatOfAnotherWorkspaceBringsItIntoThisOne() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        desktop.toggleFrontPin();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        assertEquals(3, desktop.frontFocusedWindowId());

        desktop.onWindowPressed(2);
        idle();

        assertEquals(Place.workspace(2), desktop.stack().placeOf(2));
        assertEquals(2, desktop.frontFocusedWindowId());
        assertTrue(desktop.stack().window(2).isPinned());
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(2)), host.panes.focusedHomeFloat());
    }

    @Test public void floatsTheirBoxesAndPinsSurviveARestart() {
        TilingDesktop first = start();
        first.run(TilingInput.Command.DOWN);
        first.run(TilingInput.Command.NEW_TERMINAL);
        first.run(TilingInput.Command.NEW_TERMINAL);
        first.toggleFrontFloat();
        first.toggleFrontPin();
        first.focusWindow(2);
        first.toggleFrontFloat();
        com.termux.app.tiling.FloatGeometry.Box box = new com.termux.app.tiling.FloatGeometry.Box(20f, 30f, 400f, 300f);
        first.onFloatBoxChanged(2, box);
        idle();
        first.onPause();
        restartActivity(first);

        TilingDesktop second = start();

        assertTrue(second.stack().window(2).isFloat());
        assertFalse(second.stack().window(2).isPinned());
        assertEquals(box, second.stack().window(2).floatBox());
        assertTrue(second.stack().window(3).isFloat());
        assertTrue(second.stack().window(3).isPinned());
        assertNotNull(second.stack().window(3).floatBox());
        assertSame(view(second).pinnedLayer(), floatFrame(second, 3).getParent());
        assertNotNull(pageOf(floatFrame(second, 2)));
        float density = activity.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(400f * density), floatFrame(second, 2).getLayoutParams().width);
    }

    @Test public void aDragOnTheNameStripMovesTheFloatAndACornerDragSizesIt() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        desktop.onFloatBoxChanged(2, new com.termux.app.tiling.FloatGeometry.Box(20f, 40f, 300f, 300f));
        idle();
        TilingDesktopView.FloatFrame floating = floatFrame(desktop, 2);
        float density = activity.getResources().getDisplayMetrics().density;
        float x = left(floating) + 40f * density;
        float y = top(floating) + 6f * density;

        drag(x, y, 60f * density, 50f * density);
        idle();

        com.termux.app.tiling.FloatGeometry.Box moved = desktop.stack().window(2).floatBox();
        assertEquals(80f, moved.left, 0.6f);
        assertEquals(90f, moved.top, 0.6f);
        assertEquals(300f, moved.width, 0.01f);

        floating = floatFrame(desktop, 2);
        assertEquals("the move's offset went with the new margins", 0f, floating.getTranslationX(), 0.01f);
        float cornerX = left(floating) + floating.getWidth() - 8f * density;
        float cornerY = top(floating) + floating.getHeight() - 8f * density;
        drag(cornerX, cornerY, 40f * density, 30f * density);
        idle();

        com.termux.app.tiling.FloatGeometry.Box sized = desktop.stack().window(2).floatBox();
        assertEquals(80f, sized.left, 0.6f);
        assertEquals(340f, sized.width, 0.6f);
        assertEquals(330f, sized.height, 0.6f);
        assertEquals(Math.round(340f * density), floatFrame(desktop, 2).getLayoutParams().width);
    }

    /**
     * Review of round 3: Overview's Home page card drove the desktop before the wall, which owns
     * Home's page; the desktop read the wall's old page back. The desktop follows the wall: going
     * to a Home page lands where the wall stands, so the wall must move first.
     */
    @Test public void goingToAHomePageFollowsTheWallsPage() {
        host.homeIds = new ArrayList<>(Arrays.asList(11, 22));
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        idle();

        // The wall has moved first (the activity's order): the desktop lands on that page.
        host.homePage = 1;
        assertTrue(desktop.goTo(Place.home(1)));
        idle();
        assertEquals(Place.home(1), desktop.front());

        // With the wall left behind, the desktop reads its old page back: the order matters.
        desktop.run(TilingInput.Command.DOWN);
        idle();
        host.homePage = 0;
        desktop.goTo(Place.home(1));
        idle();
        assertEquals(Place.home(0), desktop.front());
    }

    @Test public void aHomePagesFloatStandsInHomesFloatLayerOnItsPageOnly() {
        host.homeLayer = new FrameLayout(activity);
        root.addView(host.homeLayer, new FrameLayout.LayoutParams(1000, 2000));
        host.homeIds = new ArrayList<>(Arrays.asList(11, 22));
        TilingDesktop desktop = start();
        assertEquals(2, desktop.stack().homePageCount());
        long id = desktop.stack().openIn(Place.home(0), WindowSpec.shell(null), true);
        desktop.onRoomChanged();
        idle();

        TilingDesktopView.FloatFrame floating = floatFrame(desktop, id);
        assertSame(host.homeLayer, floating.getParent());
        assertTrue(shown(floating, host.created.get(host.created.size() - 1)));
        desktop.onWindowPressed(id);
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(id)), host.panes.focusedHomeFloat());
        assertFalse("Home's windows are all Floats", desktop.toggleFrontFloat());

        host.homePage = 1;
        desktop.onHomePagesChanged();
        idle();
        assertEquals(Place.home(1), desktop.front());
        assertNull("another page's Float is not shown", view(desktop).floatFrameOf(id));
        assertNull(host.panes.focusedHomeFloat());

        host.homePage = 0;
        desktop.onHomePagesChanged();
        desktop.onWindowPressed(id);
        assertTrue(desktop.toggleFrontPin());
        desktop.run(TilingInput.Command.DOWN);
        idle();
        assertSame("pinned, it stands over the Workspace", view(desktop).pinnedLayer(),
            floatFrame(desktop, id).getParent());
        assertEquals(Place.home(0), desktop.stack().placeOf(id));
    }

    @Test public void aDragOnAFloatsSideOrBottomEdgeMovesItToo() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        desktop.onFloatBoxChanged(2, new com.termux.app.tiling.FloatGeometry.Box(20f, 40f, 300f, 300f));
        idle();
        float density = activity.getResources().getDisplayMetrics().density;
        TilingDesktopView.FloatFrame floating = floatFrame(desktop, 2);

        drag(left(floating) + 4f * density, top(floating) + 150f * density, 30f * density, 0f);
        idle();
        assertEquals("the left edge", 50f, desktop.stack().window(2).floatBox().left, 0.6f);

        floating = floatFrame(desktop, 2);
        drag(left(floating) + 150f * density, top(floating) + floating.getHeight() - 4f * density, 0f, 20f * density);
        idle();
        assertEquals("the bottom edge", 60f, desktop.stack().window(2).floatBox().top, 0.6f);
        assertEquals(300f, desktop.stack().window(2).floatBox().width, 0.01f);
    }

    @Test public void aPressOnAFloatsBodyNeverReachesTheTileBeneath() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        desktop.onFloatBoxChanged(2, new com.termux.app.tiling.FloatGeometry.Box(20f, 40f, 300f, 300f));
        idle();
        TilingDesktopView.FloatFrame floating = floatFrame(desktop, 2);
        desktop.focusWindow(1);
        idle();
        floating = floatFrame(desktop, 2);
        // The content holds nothing that takes a touch.
        floating.mContent.removeAllViews();
        float density = activity.getResources().getDisplayMetrics().density;

        tap(left(floating) + 150f * density, top(floating) + 150f * density);
        idle();

        assertEquals(2, desktop.frontFocusedWindowId());
    }

    @Test public void aFloatRisesWholeOverTheKeyboardNameStripAndAll() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        idle();

        TilingDesktopView.FloatFrame floating = floatFrame(desktop, 2);
        assertSame(floating, floating.mContent.liftTarget());
        assertSame("a tile lifts itself", frame(desktop, 1), frame(desktop, 1).liftTarget());
    }

    @Test public void floatAndPinSayWhenTheyChangedNothing() {
        TilingDesktop desktop = start();
        String float_ = com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_FLOAT;
        String pin = com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_PIN;

        assertFalse("Home has nothing to float", desktop.onTool(float_));
        desktop.run(TilingInput.Command.DOWN);
        assertFalse("a tile has no pin", desktop.onTool(pin));
        assertTrue(desktop.onTool(float_));
        assertTrue(desktop.onTool(pin));
    }

    @Test public void thePinnedFloatsSlideAwayWithTheWorkspacesThenParkOnHome() {
        host.motion = true;
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.toggleFrontFloat();
        desktop.toggleFrontPin();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(view(desktop).floatFrameOf(2));

        // The move to Home begins: the pinned layer is still there, sliding with Workspace 1.
        desktop.home();
        assertTrue(view(desktop).isMoving());
        assertSame(view(desktop).pinnedLayer(), floatFrame(desktop, 2).getParent());

        org.robolectric.shadows.ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(view(desktop).isMoving());
        assertNull("at rest on Home it parks", view(desktop).floatFrameOf(2));
    }

    @Test public void aRemovedOrMovedHomePageKeepsEveryOtherPagesFloats() {
        host.homeLayer = new FrameLayout(activity);
        root.addView(host.homeLayer, new FrameLayout.LayoutParams(1000, 2000));
        host.homeIds = new ArrayList<>(Arrays.asList(11, 22, 33));
        TilingDesktop desktop = start();
        long onB = desktop.stack().openIn(Place.home(1), WindowSpec.shell(null), true);
        long onC = desktop.stack().openIn(Place.home(2), WindowSpec.shell(null), true);

        host.homeIds = new ArrayList<>(Arrays.asList(22, 33));
        desktop.onHomePagesChanged();
        assertEquals(Place.home(0), desktop.stack().placeOf(onB));
        assertEquals(Place.home(1), desktop.stack().placeOf(onC));

        // A move tells no one: the next pass follows it.
        host.homeIds = new ArrayList<>(Arrays.asList(33, 22));
        desktop.onRoomChanged();
        idle();
        assertEquals(Place.home(1), desktop.stack().placeOf(onB));
        assertEquals(Place.home(0), desktop.stack().placeOf(onC));
        assertSame("the page in front shows its own Float", host.homeLayer, floatFrame(desktop, onC).getParent());
        assertNull(view(desktop).floatFrameOf(onB));
    }

    @Test public void aFloatTappedAwayFromKeepsNoFocusAcrossAPageTurn() {
        host.homeLayer = new FrameLayout(activity);
        root.addView(host.homeLayer, new FrameLayout.LayoutParams(1000, 2000));
        host.homeIds = new ArrayList<>(Arrays.asList(11, 22));
        TilingDesktop desktop = start();
        long id = desktop.stack().openIn(Place.home(0), WindowSpec.shell(null), true);
        desktop.onRoomChanged();
        idle();
        desktop.onWindowPressed(id);
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(id)), host.panes.focusedHomeFloat());

        // A page turned away and back gives the focus back, as v1's floats have it.
        host.homePage = 1;
        desktop.onHomePagesChanged();
        host.homePage = 0;
        desktop.onHomePagesChanged();
        idle();
        assertNotNull(host.panes.focusedHomeFloat());
        assertSame(host.panes.hostedFloatFor(TilingHostedIds.hostedId(id)), host.panes.focusedHomeFloat());

        // A tap beside the Floats lets it go (v1 releases, then tells the desktop): it stays gone.
        host.panes.releaseHomeFloatFocus();
        desktop.onTapBesideHomeFloats();
        host.homePage = 1;
        desktop.onHomePagesChanged();
        host.homePage = 0;
        desktop.onHomePagesChanged();
        desktop.onRoomChanged();
        idle();
        assertNotNull(view(desktop).floatFrameOf(id));
        assertNull(host.panes.focusedHomeFloat());
        assertEquals(TilingStack.NO_WINDOW, desktop.stack().focusedWindowId());
    }

    @Test public void withoutHomeNoFrameStaysInHomesFloatLayer() {
        host.homeLayer = new FrameLayout(activity);
        root.addView(host.homeLayer, new FrameLayout.LayoutParams(1000, 2000));
        host.homeIds = new ArrayList<>(Arrays.asList(11));
        TilingDesktop desktop = start();
        long id = desktop.stack().openIn(Place.home(0), WindowSpec.shell(null), true);
        desktop.onRoomChanged();
        idle();
        assertEquals(1, host.homeLayer.getChildCount());

        desktop.setHasHome(false);
        idle();

        assertEquals(0, host.homeLayer.getChildCount());
        assertTrue(desktop.stack().window(id).isFloat());
        assertFalse(desktop.stack().placeOf(id).isHome());
    }

    private static int left(@NonNull View view) {
        return ((FrameLayout.LayoutParams) view.getLayoutParams()).leftMargin;
    }

    private static int top(@NonNull View view) {
        return ((FrameLayout.LayoutParams) view.getLayoutParams()).topMargin;
    }

    private void drag(float x, float y, float dx, float dy) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent half = android.view.MotionEvent.obtain(now, now + 16,
            android.view.MotionEvent.ACTION_MOVE, x + dx / 2f, y + dy / 2f, 0);
        android.view.MotionEvent move = android.view.MotionEvent.obtain(now, now + 32,
            android.view.MotionEvent.ACTION_MOVE, x + dx, y + dy, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 48,
            android.view.MotionEvent.ACTION_UP, x + dx, y + dy, 0);
        for (android.view.MotionEvent event : new android.view.MotionEvent[] {down, half, move, up}) {
            root.dispatchTouchEvent(event);
            event.recycle();
        }
    }

    @NonNull private static TilingDesktopView view(@NonNull TilingDesktop desktop) {
        return (TilingDesktopView) desktop.view();
    }

    @NonNull private static TilingDesktopView.FloatFrame floatFrame(@NonNull TilingDesktop desktop, long id) {
        TilingDesktopView.FloatFrame frame = view(desktop).floatFrameOf(id);
        assertNotNull("window " + id + " is a Float on screen", frame);
        return frame;
    }

    @Nullable private static TilingDesktopView.Page pageOf(@NonNull View view) {
        for (android.view.ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof TilingDesktopView.Page) return (TilingDesktopView.Page) parent;
        }
        return null;
    }

    @NonNull private static TilingDesktopView.WindowFrame frame(@NonNull TilingDesktop desktop, long id) {
        TilingDesktopView.WindowFrame frame = frameOrNull(desktop, id);
        assertNotNull("window " + id + " has a frame", frame);
        return frame;
    }

    @Nullable private static TilingDesktopView.WindowFrame frameOrNull(@NonNull TilingDesktop desktop, long id) {
        return ((TilingDesktopView) desktop.view()).frameOf(id);
    }

    @NonNull private static int[] bounds(@NonNull View view) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        return new int[] {params.leftMargin, params.topMargin, params.width, params.height};
    }

    @Nullable private static <T extends View> T find(@NonNull View view, @NonNull Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = find(group.getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    // --- helpers ---

    @Test public void useAsTerminalDropsHomeAndHomeScreenBringsItBack() {
        TilingDesktop desktop = start();
        long floating = desktop.stack().openIn(Place.home(0), WindowSpec.assistant(), true);
        assertTrue(desktop.hasHome());
        assertEquals(Place.home(0), desktop.stack().current());

        desktop.setHasHome(false);
        idle();
        assertFalse(desktop.hasHome());
        assertEquals(Place.workspace(1), desktop.stack().current());
        Place waiting = desktop.stack().placeOf(floating);
        assertFalse(waiting.isHome());
        assertTrue(desktop.stack().window(floating).isFloat());
        assertEquals(View.VISIBLE, desktop.view().getVisibility());
        assertTrue("without Home nothing of v1 stands behind the Workspaces", host.homeAway);

        desktop.setHasHome(true);
        idle();
        assertTrue(desktop.hasHome());
        assertEquals(Place.workspace(1), desktop.stack().current());
        assertEquals(waiting, desktop.stack().placeOf(floating));
    }

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

    private final class FakeHost implements TilingDesktop.Host {
        TerminalPaneController panes;
        final Map<String, TerminalSession> live = new HashMap<>();
        final List<TerminalSession> created = new ArrayList<>();
        final List<String> cwds = new ArrayList<>();
        final List<TerminalSession> killed = new ArrayList<>();
        boolean ready;
        @Nullable FrameLayout homeLayer;
        List<Integer> homeIds = new ArrayList<>();
        boolean motion;
        int homePage;
        float homeTravel;
        boolean homeAway;

        @Override public void moveHome(float travel) {
            homeTravel = travel;
            homeAway = travel >= 1f;
        }

        @Override @Nullable public android.view.ViewGroup homeFloatLayer() { return homeLayer; }
        @Override @NonNull public List<Integer> homePageIds() { return homeIds; }
        @Override public int homePageInFront() { return homePage; }

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
        /** Command shells asked for, with whether each stays behind its command; refused while {@link #refuseCommands}. */
        final List<Boolean> commandStays = new ArrayList<>();
        boolean refuseCommands;
        @Override @Nullable public TerminalSession createCommandShell(@NonNull List<String> command,
                                                                      @Nullable String cwd, @Nullable String title,
                                                                      boolean stayBehind) {
            if (refuseCommands) return null;
            commandStays.add(stayBehind);
            return createShell(cwd);
        }
        @Override public void killShell(@NonNull TerminalSession shell) {
            killed.add(shell);
            live.remove(shell.mHandle);
        }
        @Override public boolean reducedMotion() { return !motion; }
        FakeContent linux = new FakeContent();
        final FakeContent display = new FakeContent();
        final FakeContent assistant = new FakeContent();
        @Override @Nullable public TilingWindowContent content(@NonNull WindowKind kind) {
            switch (kind) {
                case LINUX_APP: return linux;
                case DISPLAY: return display;
                case ASSISTANT: return assistant;
                default: return null;
            }
        }
        /** Shells that run a program: closing one waits in {@link #asked} for an answer. */
        final List<TerminalSession> busy = new ArrayList<>();
        final List<Runnable> asked = new ArrayList<>();
        @Override public void confirmClose(@NonNull TerminalSession shell, @NonNull Runnable close) {
            if (busy.contains(shell)) asked.add(close);
            else close.run();
        }
        final FakeSavedApps saved = new FakeSavedApps();
        @Override @Nullable public TilingDesktop.SavedLinuxApps savedLinuxApps() { return saved; }
    }

    /** The Linux side of saved Workspaces: launch keys by host window, relaunches asked and answered by hand. */
    private static final class FakeSavedApps implements TilingDesktop.SavedLinuxApps {
        final Map<Long, String> keys = new HashMap<>();
        final List<String> relaunched = new ArrayList<>();
        /** Host windows the relaunch started: only these are claimed. */
        final java.util.Set<Long> started = new java.util.HashSet<>();
        @Nullable TilingDesktop.RelaunchAnswer answer;
        @Override @Nullable public String launchKey(long linuxId) { return keys.get(linuxId); }
        @Override public void relaunch(@NonNull List<SavedWorkspace.Relaunch> apps,
                                       @NonNull TilingDesktop.RelaunchAnswer answer) {
            for (SavedWorkspace.Relaunch app : apps) relaunched.add(app.windowId + " " + app.key + " " + app.appId);
            this.answer = answer;
        }
        @Override @Nullable public String claimRelaunched(long linuxId) {
            return started.remove(linuxId) ? keys.get(linuxId) : null;
        }
    }

    /** A kind's content: one plain view per content id, parked and focused as asked. */
    private final class FakeContent implements TilingWindowContent {
        final Map<Long, View> views = new HashMap<>();
        final List<Long> asked = new ArrayList<>();
        @Nullable Long focused;
        boolean attached;
        boolean focusedNow;

        @Override public boolean attach(long windowId, long contentId, @NonNull android.view.ViewGroup frame) {
            View view = views.get(contentId);
            if (view == null) {
                view = new View(activity);
                views.put(contentId, view);
            }
            TilingWindowContent.place(view, frame);
            attached = true;
            return true;
        }
        @Override public void park(long windowId, long contentId) {
            TilingWindowContent.unplace(views.get(contentId));
            attached = false;
        }
        @Override public void focus(long windowId, long contentId, boolean on) {
            focused = on ? contentId : null;
            focusedNow = on;
        }
        @Override public boolean close(long windowId, long contentId) {
            if (contentId == 0L) return true;
            asked.add(contentId);
            return false;
        }
        final List<String> tools = new ArrayList<>();
        boolean hasMouseMode = true;
        @Override public boolean toggleMouseMode(long windowId, long contentId) {
            tools.add("mouse " + contentId);
            return hasMouseMode;
        }
        @Override public boolean toggleKeyboard(long windowId, long contentId) {
            tools.add("keyboard " + contentId);
            return true;
        }
        boolean scales = true;
        @Override public boolean takesScale(long windowId, long contentId) { return scales; }
        @Override public boolean showScale(long windowId, long contentId) {
            tools.add("scale " + contentId);
            return scales;
        }
    }

    private static final class SilentPaneHost implements TerminalPaneController.Host {
        @Override public TerminalSession createShell(String cwd) { return newShell(); }
        @Override public void configurePaneView(TerminalView view) { }
        @Override public void removeShell(TerminalSession session) { }
        @Override public void onActivePaneChanged() { }
        @Override public void onTreesChanged() { }
        @Override public String defaultCwd() { return "/"; }
    }

    // ---- the leader's tools (#439) ---------------------------------------------------------------

    @Test public void goingToAWorkspaceByNumber() {
        TilingDesktop desktop = start();
        assertTrue(desktop.onTool("workspace.go.1"));
        idle();
        assertEquals(Place.workspace(1), desktop.stack().current());
        assertTrue(desktop.onTool("workspace.go.2"));
        idle();
        assertEquals(Place.workspace(2), desktop.stack().current());
        assertFalse("one that is not there is declined", desktop.onTool("workspace.go.9"));
        assertEquals(Place.workspace(2), desktop.stack().current());
    }

    @Test public void sendingTheFocusedWindowToAWorkspaceByNumber() {
        TilingDesktop desktop = start();
        desktop.onTool("workspace.go.1");
        idle();
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        long window = desktop.stack().focusedWindowId();
        assertTrue(desktop.onTool("window.send.2"));
        idle();
        assertEquals(Place.workspace(2), desktop.stack().placeOf(window));
        assertEquals("the view stays", Place.workspace(1), desktop.stack().current());
    }

    @Test public void movingTheFocusedWindowDownFollowsIt() {
        TilingDesktop desktop = start();
        desktop.onTool("workspace.go.1");
        idle();
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        long window = desktop.stack().focusedWindowId();
        assertTrue(desktop.onTool("window.move_down"));
        idle();
        assertEquals(desktop.stack().placeOf(window), desktop.stack().current());
        assertEquals(window, desktop.stack().focusedWindowId());
        assertEquals(Place.workspace(2), desktop.stack().current());
    }

    @Test public void movingLeftReordersAndTabJoinsTheNeighbour() {
        TilingDesktop desktop = start();
        desktop.onTool("workspace.go.1");
        idle();
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        long moved = desktop.stack().focusedWindowId();
        java.util.List<TilingWindow> before = desktop.stack().workspace(1).tiledWindows();
        assertEquals(moved, before.get(before.size() - 1).id());
        assertTrue(desktop.onTool("window.move_left"));
        java.util.List<TilingWindow> after = desktop.stack().workspace(1).tiledWindows();
        assertEquals(moved, after.get(after.size() - 2).id());
        int groups = desktop.stack().workspace(1).groups().size();
        assertTrue(desktop.onTool("window.tab"));
        assertEquals(groups - 1, desktop.stack().workspace(1).groups().size());
    }

    @Test public void movingLeftUnderDwindleSwapsTheTilesOnScreen() {
        TilingDesktop desktop = start();
        desktop.run(TilingInput.Command.DOWN);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        desktop.onTool(com.termux.launcherctl.LauncherToolRegistry.TOOL_WORKSPACE_LAYOUT_TOGGLE);
        idle();
        assertEquals(WorkspaceLayout.DWINDLE, desktop.frontLayout());
        long last = desktop.stack().focusedWindowId();
        long middle = desktop.stack().workspace(1).tiledWindows().get(1).id();
        assertTrue("three is below two", bounds(frame(desktop, last))[1] > bounds(frame(desktop, middle))[1]);

        assertTrue(desktop.onTool("window.move_left"));
        idle();

        assertTrue("three now stands where two did",
            bounds(frame(desktop, last))[1] < bounds(frame(desktop, middle))[1]);
    }

    @Test public void theTabGroupKeyLeavesTheGroupItIsIn() {
        TilingDesktop desktop = start();
        desktop.onTool("workspace.go.1");
        idle();
        desktop.run(TilingInput.Command.NEW_TERMINAL);
        idle();
        int groups = desktop.stack().workspace(1).groups().size();
        assertTrue(desktop.onTool("window.tab"));
        assertEquals(groups - 1, desktop.stack().workspace(1).groups().size());
        assertTrue(desktop.onTool("window.tab"));
        assertEquals("a second press takes it out again", groups,
            desktop.stack().workspace(1).groups().size());
    }
}
