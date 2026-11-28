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
