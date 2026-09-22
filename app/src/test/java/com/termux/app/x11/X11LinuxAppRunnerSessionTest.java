package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * What a whole desktop does that an app does not: only one has the display at a time and a second
 * asks before taking it (D2), the window manager comes back when one ends (D1), and a desktop is
 * never retried with {@code --no-sandbox}.
 *
 * <p>Drives {@link X11LinuxAppRunner#runAndWatch} and {@link X11LinuxAppRunner#run} directly,
 * exactly as {@link X11LinuxAppRunnerSandboxRetryTest} does, and delivers the exit code the way
 * {@link X11LinuxAppRunner.Host#runScript} promises it: as a plain argument, pushed once.
 *
 * <p>Nothing here asserts anything about a device. Whether the pid the script writes down really
 * is the desktop's, and whether a desktop that is told to go actually goes, are facts about a
 * phone no unit test can stand in for; what is tested is which script is run, in which order, and
 * what the user is asked.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11LinuxAppRunnerSessionTest {

    private static final String DISPLAY = ":1";
    private static final List<String> ENV = Collections.emptyList();
    /** What D1 runs when a desktop has finished with the display. */
    private static final String RESTART_WM = "export DISPLAY=:1\nexec openbox\n";

    private Context context;
    private FakeHost host;
    private X11LinuxAppRunner runner;

    private static LinuxAppCatalog.LinuxApp session(String file, String name, String exec) {
        return new LinuxAppCatalog.LinuxApp(ProotDistro.Container.PREFIX, file, name, exec, "", "",
            "", false, true, "");
    }

    private static LinuxAppCatalog.LinuxApp app(String id) {
        return new LinuxAppCatalog.LinuxApp(id, "Typora", "typora", "typora", "");
    }

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
                Context.MODE_PRIVATE)
            .edit().remove("x11_no_sandbox_apps_v1").commit();
        host = new FakeHost();
        runner = new X11LinuxAppRunner(context, host);
    }

    @Test public void aDesktopIsNeverRetriedWithTheSandboxFlag() {
        // A desktop refused because something already holds the display exits non-zero at once —
        // D7's exact shape, and --no-sandbox is a flag no desktop has ever heard of.
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV);
        host.listeners.get(0).onExit(1);

        assertEquals("no retry", 1, host.scripts.size());
        assertEquals(Collections.singletonList(
            context.getString(com.termux.R.string.termux_x11_session_failed)), host.notices);
        assertFalse("nothing to remember about a desktop", runner.shouldStartWithNoSandbox(xfce));
    }

    @Test public void theWindowManagerComesBackWhenTheDesktopEnds() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        idle(); // the desktop is up: the quick-fail window closed with nothing to say

        assertEquals(1, host.scripts.size());
        host.listeners.get(0).onExit(0); // the user logged out of the desktop

        assertEquals("the manager is started again", 2, host.scripts.size());
        assertEquals(RESTART_WM, host.scripts.get(1));
        assertTrue("and nothing is said about a desktop that simply ended", host.notices.isEmpty());
    }

    @Test public void aSecondDesktopAsksBeforeTakingTheDisplay() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        LinuxAppCatalog.LinuxApp lxqt = session("lxqt", "LXQt Desktop", "startlxqt");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV);
        idle();

        runner.run(lxqt);
        assertEquals("nothing was started behind the user's back", 1, host.scripts.size());
        assertEquals(Collections.singletonList("Stop Xfce Session and open LXQt Desktop instead?"),
            host.questions);

        // Saying no is the end of it.
        host.questions.clear();
        runner.run(lxqt);
        assertEquals(1, host.scripts.size());
    }

    @Test public void sayingYesStopsTheFirstDesktopAndThenStartsTheSecond() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        LinuxAppCatalog.LinuxApp lxqt = session("lxqt", "LXQt Desktop", "startlxqt");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        idle();

        // The second desktop's own launch reads the GPU profile off the main thread, which is not
        // what this is about: with no display up it stops at asking for one, which is as far as
        // this needs to see to know the take-over went through.
        host.displayRunning = false;
        host.answerYes = true;
        runner.run(lxqt);

        assertEquals("the running desktop is stopped first", 2, host.scripts.size());
        assertEquals(X11LinuxAppRunner.stopSessionScript(), host.scripts.get(1));
        assertEquals("nothing else has been started yet", 0, host.displayStarts);

        // The stopped desktop's own script reports its exit too. Either order is safe; this is the
        // one where it comes in first, before the stop script has finished waiting.
        host.listeners.get(0).onExit(143);
        assertEquals("the manager stays down for the desktop arriving", 2, host.scripts.size());

        host.listeners.get(1).onExit(0);
        assertEquals("and the second desktop is taken up", 1, host.displayStarts);
        for (String script : host.scripts) {
            assertFalse("the manager was never started again", script.equals(RESTART_WM));
        }
    }

    @Test public void tappingTheDesktopThatIsAlreadyRunningJustShowsIt() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV);
        idle();

        runner.run(xfce);
        assertTrue("nothing to ask", host.questions.isEmpty());
        assertEquals("and nothing started twice", 1, host.scripts.size());
        assertEquals("the display is simply brought into view", 1, host.shown);
    }

    @Test public void anOrdinaryAppIsUntouchedByAnyOfIt() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV);
        idle();

        // An app opens a window on the desktop like any other client: nothing is asked, and its
        // own quick-fail retry still works while a desktop is running.
        runner.runAndWatch(app("typora"), "exec typora\n", false, DISPLAY, ENV);
        host.listeners.get(1).onExit(133);

        assertTrue(host.questions.isEmpty());
        assertEquals("the app's retry is unaffected", 3, host.scripts.size());
        assertTrue(host.scripts.get(2).contains("--no-sandbox"));
    }

    @Test public void aDesktopThatNeverStartedIsNotTheRunningOne() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        LinuxAppCatalog.LinuxApp lxqt = session("lxqt", "LXQt Desktop", "startlxqt");
        host.startSucceeds = false;
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV);
        idle();

        host.startSucceeds = true;
        host.displayRunning = false; // stops short of the launch, which is not what this is about
        runner.run(lxqt);
        assertTrue("nothing is holding the display, so nothing is asked", host.questions.isEmpty());
        assertEquals("it is simply started", 1, host.displayStarts);
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(X11LinuxAppRunner.QUICK_FAIL_MS, TimeUnit.MILLISECONDS);
    }

    /** A Host that records what it was asked to run, to show and to ask. */
    private static final class FakeHost implements X11LinuxAppRunner.Host {
        final List<String> scripts = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        final List<String> questions = new ArrayList<>();
        final List<X11LinuxAppRunner.ScriptExitListener> listeners = new ArrayList<>();
        boolean startSucceeds = true;
        boolean answerYes;
        boolean displayRunning = true;
        int shown;
        int displayStarts;

        @Override public boolean isDisplayEnabled() { return true; }
        @Override public void turnOnDisplay() { }
        @Override public boolean isDisplayRunning() { return displayRunning; }
        @Override public void startDisplay() { displayStarts++; }
        @Override public void showDisplayPlace() { shown++; }
        @Override public void showNotice(@NonNull String message) { notices.add(message); }

        @Override public boolean runScript(@NonNull String script,
                                           @NonNull X11LinuxAppRunner.ScriptExitListener onExit) {
            scripts.add(script);
            listeners.add(onExit);
            return startSucceeds;
        }

        @Override public void askBeforeReplacingSession(@NonNull String message,
                                                        @NonNull Runnable onYes) {
            questions.add(message);
            if (answerYes) onYes.run();
        }
    }
}
