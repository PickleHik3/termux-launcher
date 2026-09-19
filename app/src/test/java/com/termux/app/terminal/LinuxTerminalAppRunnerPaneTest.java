package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.LinuxTerminalAppRunner;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * D5's terminal-app runner against a real {@link TerminalActionDispatcher} and pane controller —
 * the three-way reuse rule ({@code FOCUS}/{@code RERUN_IN_PLACE}/{@code OPEN_FRESH}) end to end.
 * The case that matters most: a pane confirmed still running must never have anything written
 * into it — that is the destructive bug the coordinator caught (a naive "not confirmed idle, so
 * re-run" rule would type the app's own command, as keystrokes, into whatever the pane turns out
 * to be running). {@link TerminalSession#getLastWriteUptimeMs()} is the observation point: it is
 * nonzero only once {@link TerminalSession#write} has actually been called.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxTerminalAppRunnerPaneTest {

    private final TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
    private PaneHost host;

    @Before public void attach() throws IOException {
        AgentPaneRegistry.getInstance().clear();
        host = new PaneHost();
        dispatcher.attach(host);
    }

    @After public void detach() {
        dispatcher.detach(host);
        AgentPaneRegistry.getInstance().clear();
    }

    private static LinuxAppCatalog.LinuxApp terminalApp() throws IOException {
        File dir = Files.createTempDirectory("linux-terminal-app-runner-test").toFile();
        File file = new File(dir, "htop.desktop");
        Files.write(file.toPath(),
            "[Desktop Entry]\nType=Application\nName=htop\nExec=htop\nTerminal=true\n"
                .getBytes(StandardCharsets.UTF_8));
        List<LinuxAppCatalog.LinuxApp> apps =
            LinuxAppCatalog.scan(LinuxAppCatalog.prefixRoots(Collections.singletonList(dir)));
        return apps.get(0);
    }

    /**
     * The case that matters most (the coordinator's own words): an existing pane confirmed still
     * running must never be written into. Only focus reaches it.
     */
    @Test public void aConfirmedRunningPaneIsFocusedAndNeverWrittenInto() throws IOException {
        LinuxAppCatalog.LinuxApp app = terminalApp();

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> null));
        assertEquals("the user's own shell plus the app's new pane",
            2, host.controller.shellsOf(host.window).size());
        String openedId = host.controller.getActiveSession().mHandle;
        TerminalSession opened = host.findPaneById(openedId);
        assertEquals("nothing written yet", 0L, opened.getLastWriteUptimeMs());

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> Boolean.FALSE));
        assertEquals("confirmed running: no duplicate pane",
            2, host.controller.shellsOf(host.window).size());
        assertEquals("confirmed running: the existing pane is focused",
            openedId, host.controller.getActiveSession().mHandle);
        assertEquals("confirmed running: its input is never touched",
            0L, opened.getLastWriteUptimeMs());
    }

    /** Confirmed idle (the app already finished): safe to re-run in the same pane. */
    @Test public void aConfirmedIdlePaneIsReRunInPlaceRatherThanDuplicated() throws IOException {
        LinuxAppCatalog.LinuxApp app = terminalApp();

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> null));
        assertEquals(2, host.controller.shellsOf(host.window).size());
        String openedId = host.controller.getActiveSession().mHandle;
        TerminalSession opened = host.findPaneById(openedId);

        // This is the first bug the coordinator caught: the app already exited and the pane is
        // sitting at a bare prompt (idle=true). Focusing it alone would silently "do nothing"
        // from the user's side, so the fix re-runs the command in that same pane instead — not a
        // fresh one (that would be a duplicate for no reason, since this pane is provably safe).
        assertTrue(LinuxTerminalAppRunner.run(app, pid -> Boolean.TRUE));
        assertEquals("idle: the tagged pane is reused, not duplicated",
            2, host.controller.shellsOf(host.window).size());
        assertEquals("idle: the same pane is the one now focused",
            openedId, host.controller.getActiveSession().mHandle);
        assertTrue("idle: the command was actually re-run (something was written)",
            opened.getLastWriteUptimeMs() > 0L);
    }

    /**
     * Unknown (no privileged backend and, before this fix, the common case for most installs):
     * neither focus-only (the first bug) nor write-in-place (the second, destructive bug the
     * coordinator caught) is safe, so a fresh pane is opened instead and the existing one is left
     * completely alone.
     */
    @Test public void anUnknownReadingOpensAFreshPaneAndNeverTouchesTheExistingOne() throws IOException {
        LinuxAppCatalog.LinuxApp app = terminalApp();

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> null));
        assertEquals(2, host.controller.shellsOf(host.window).size());
        String openedId = host.controller.getActiveSession().mHandle;
        TerminalSession opened = host.findPaneById(openedId);

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> null));
        assertEquals("unknown: a fresh pane is opened rather than reused",
            3, host.controller.shellsOf(host.window).size());
        assertNotEquals("unknown: the fresh pane is not the existing one",
            openedId, host.controller.getActiveSession().mHandle);
        assertEquals("unknown: the existing pane's input is never touched",
            0L, opened.getLastWriteUptimeMs());
    }

    /** A host whose pane surface is a real controller with one window and the user's own shell. */
    private static final class PaneHost extends FakeTerminalHost {
        final TerminalPaneController controller;
        final TerminalPaneController.Window window;

        PaneHost() throws IOException {
            super(FakeTerminalHost.testContext(), FakeTerminalHost.testProperties());
            Context context = RuntimeEnvironment.getApplication();
            controller = new TerminalPaneController(new TerminalPaneController.Host() {
                @Override public TerminalSession createShell(String cwd) { return shell(); }
                @Override public void configurePaneView(TerminalView view) {}
                @Override public void removeShell(TerminalSession session) {}
                @Override public void onActivePaneChanged() {}
                @Override public void onTreesChanged() {}
                @Override public String defaultCwd() { return "/"; }
            }, new FrameLayout(context), LayoutInflater.from(context));
            window = controller.newWindow(shell());
            controller.showWindow(window);
        }

        private static TerminalSession shell() {
            TerminalSession session =
                new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
            // A real subprocess is forked underneath, and this test cannot control its timing —
            // it may already have exited by the time a later assertion runs. Pin a stable fake
            // pid so isRunning()/write() behave deterministically instead of racing a real
            // process, the same technique TerminalActionDispatcherPaneTest uses in the opposite
            // direction (forcing -1) to simulate a pane that has stopped running.
            org.robolectric.util.ReflectionHelpers.setField(session, "mShellPid", 12345);
            return session;
        }

        @Override @Nullable public TerminalSession currentSession() {
            return controller.getActiveSession();
        }

        @Override @Nullable public TerminalPaneController paneController() {
            return controller;
        }

        @Override @Nullable public TerminalSession openCommandPane(@NonNull List<String> command,
                                                                    @Nullable String cwd,
                                                                    @Nullable String title,
                                                                    boolean focus) {
            TerminalSession session = shell();
            session.mSessionName = title;
            return controller.addPane(session, focus) ? session : null;
        }

        @Override @NonNull public List<TerminalPaneController.Window> currentSessionWindows() {
            return Collections.singletonList(window);
        }

        @Override @Nullable public TerminalSession findPaneById(@NonNull String id) {
            for (TerminalSession shell : controller.shellsOf(window)) {
                if (id.equals(shell.mHandle)) return shell;
            }
            return null;
        }

        @Override public boolean activateSessionInPanes(TerminalSession session) {
            controller.focusSession(session);
            return true;
        }

        @Override @Nullable public String activePaneLayoutPolicy() {
            return controller.activeLayoutPolicy();
        }
    }
}
