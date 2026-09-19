package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
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
 * the fix for the idle-shell refocus bug: a tap that finds its tagged pane must not blindly focus
 * it. It may only do that when {@link LinuxTerminalAppRunner.ForegroundState} confirms the app is
 * still running there; an idle or unknown reading re-runs the command in the same pane instead of
 * opening a duplicate.
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

    @Test public void aSecondTapConfirmedStillRunningFocusesTheSamePaneRatherThanOpeningAnother()
            throws IOException {
        LinuxAppCatalog.LinuxApp app = terminalApp();

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> null));
        assertEquals("the user's own shell plus the app's new pane",
            2, host.controller.shellsOf(host.window).size());
        String openedId = host.controller.getActiveSession().mHandle;

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> Boolean.FALSE));
        assertEquals("confirmed running: no duplicate pane",
            2, host.controller.shellsOf(host.window).size());
        assertEquals("confirmed running: the existing pane is focused",
            openedId, host.controller.getActiveSession().mHandle);
    }

    @Test public void anIdleOrUnknownReadingReusesThePaneInsteadOfFocusingItAsIsOrDuplicatingIt()
            throws IOException {
        LinuxAppCatalog.LinuxApp app = terminalApp();

        assertTrue(LinuxTerminalAppRunner.run(app, pid -> null));
        assertEquals(2, host.controller.shellsOf(host.window).size());
        String openedId = host.controller.getActiveSession().mHandle;

        // This is the bug the coordinator caught: the app has already exited, so the pane's
        // foreground is confirmed idle. The old behaviour just focused it, leaving the user
        // staring at a bare prompt. The fix must re-run the command in that same pane — not open
        // a fresh one (that would be a duplicate) and not silently focus the idle one as if the
        // tap had done something.
        assertTrue(LinuxTerminalAppRunner.run(app, pid -> Boolean.TRUE));
        assertEquals("idle: the tagged pane is reused, not duplicated",
            2, host.controller.shellsOf(host.window).size());
        assertEquals("idle: the same pane is the one now focused",
            openedId, host.controller.getActiveSession().mHandle);

        // Unknown (no privileged backend, or no cached reading yet) must behave the same way as
        // idle, never as "confirmed running" — see LinuxTerminalAppRunner's class doc.
        assertTrue(LinuxTerminalAppRunner.run(app, pid -> null));
        assertEquals("unknown: still reused, still no duplicate",
            2, host.controller.shellsOf(host.window).size());
        assertEquals(openedId, host.controller.getActiveSession().mHandle);
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
            return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
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
