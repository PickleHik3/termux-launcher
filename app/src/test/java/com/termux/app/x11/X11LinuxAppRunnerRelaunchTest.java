package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.junit.After;
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
 * An activity relaunched for a configuration change — a new wallpaper's Material You colours —
 * hands the runner from one {@link X11LinuxAppRunner.Host} to the next. Nothing on the display may
 * be stopped, restarted or forgotten on the way: no script runs because an activity went, the
 * desktop on the display is still the one the next activity knows of, and what was owed while no
 * activity was there runs once one is.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11LinuxAppRunnerRelaunchTest {

    private static final String DISPLAY = ":1";
    private static final List<String> ENV = Collections.emptyList();
    private static final String RESTART_WM = "export DISPLAY=:1\nexec openbox\n";

    private Context context;
    private FakeHost first;
    private FakeHost second;
    private X11LinuxAppRunner runner;

    private static LinuxAppCatalog.LinuxApp session(String file, String name, String exec) {
        return new LinuxAppCatalog.LinuxApp(ProotDistro.Container.PREFIX, file, name, exec, "", "",
            "", false, true, "");
    }

    private static LinuxAppCatalog.LinuxApp app(String id) {
        return new LinuxAppCatalog.LinuxApp(id, "Firefox", "firefox", "firefox", "");
    }

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
                Context.MODE_PRIVATE)
            .edit().remove("x11_no_sandbox_apps_v1").commit();
        X11LinuxAppRunner.resetSharedForTest();
        first = new FakeHost();
        second = new FakeHost();
        runner = X11LinuxAppRunner.shared(context);
        runner.attach(first);
    }

    @After public void tearDown() {
        X11LinuxAppRunner.resetSharedForTest();
    }

    @Test public void theProcessHasOneRunnerThatEveryActivityShares() {
        assertSame(runner, X11LinuxAppRunner.shared(context));
    }

    @Test public void aRelaunchRunsNoScriptAndKillsNothing() {
        LinuxAppCatalog.LinuxApp firefox = app("firefox");
        runner.runAndWatch(firefox, "exec firefox\n", false, DISPLAY, ENV);
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        assertEquals(2, first.scripts.size());

        relaunch();
        // Well past the quick-fail window, though short of the window manager's safety net, which
        // is the desktop's own business and is tested below.
        idleFor(X11LinuxAppRunner.QUICK_FAIL_MS);

        assertEquals("the going activity ran nothing more", 2, first.scripts.size());
        assertTrue("and the new one was handed nothing to stop or restart", second.scripts.isEmpty());
        assertTrue(first.notices.isEmpty());
        assertTrue(second.notices.isEmpty());
    }

    @Test public void theDesktopOnTheDisplayIsStillKnownAfterARelaunch() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        idleFor(X11LinuxAppRunner.QUICK_FAIL_MS);

        relaunch();
        runner.run(xfce);

        assertEquals("tapping it again only shows it", 1, second.shown);
        assertTrue("no second copy of it, and no window manager restart", second.scripts.isEmpty());
        assertTrue(second.questions.isEmpty());
    }

    @Test public void aSecondDesktopAfterARelaunchStillAsksFirst() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        idleFor(X11LinuxAppRunner.QUICK_FAIL_MS);

        relaunch();
        runner.run(session("lxqt", "LXQt", "startlxqt"));

        assertEquals(1, second.questions.size());
        assertTrue(second.scripts.isEmpty());
    }

    @Test public void aDesktopThatEndsMidRelaunchGetsItsWindowManagerBackFromTheNextActivity() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        idleFor(X11LinuxAppRunner.QUICK_FAIL_MS);

        runner.detach(first);
        first.listeners.get(0).onExit(0); // the desktop ends between one activity and the next
        assertEquals("nothing runs with no activity to run it in", 1, first.scripts.size());

        runner.attach(second);
        assertEquals(Collections.singletonList(RESTART_WM), second.scripts);
    }

    @Test public void whatIsOwedWaitsUntilTheNextActivityCanRunScripts() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        idleFor(X11LinuxAppRunner.QUICK_FAIL_MS);

        runner.detach(first);
        first.listeners.get(0).onExit(0);
        second.startSucceeds = false; // its service is not bound yet
        runner.attach(second);
        second.startSucceeds = true;
        second.scripts.clear();
        runner.onHostReady();

        assertEquals(Collections.singletonList(RESTART_WM), second.scripts);
        runner.onHostReady();
        assertEquals("and it runs once", 1, second.scripts.size());
    }

    @Test public void aGoingActivityThatWasAlreadyReplacedTakesNothingWithIt() {
        LinuxAppCatalog.LinuxApp xfce = session("xfce", "Xfce Session", "startxfce4");
        runner.runAndWatch(xfce, "exec startxfce4\n", false, DISPLAY, ENV, RESTART_WM);
        runner.attach(second); // the new activity attached before the old one's destroy
        runner.detach(first);

        assertTrue(runner.hasHost());
        runner.run(xfce);
        assertEquals(1, second.shown);
        assertEquals(0, first.shown);
    }

    @Test public void aDetachedRunnerIsTappedIntoNothing() {
        runner.detach(first);
        runner.run(app("firefox"));

        assertFalse(runner.hasHost());
        assertEquals(0, first.shown);
        assertTrue(first.scripts.isEmpty());
    }

    private void relaunch() {
        runner.detach(first);
        runner.attach(second);
    }

    private static void idleFor(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS);
    }

    private static final class FakeHost implements X11LinuxAppRunner.Host {
        final List<String> scripts = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        final List<String> questions = new ArrayList<>();
        final List<X11LinuxAppRunner.ScriptExitListener> listeners = new ArrayList<>();
        boolean startSucceeds = true;
        int shown;

        @Override public boolean isDisplayEnabled() { return true; }
        @Override public void turnOnDisplay() { }
        @Override public boolean isDisplayRunning() { return true; }
        @Override public void startDisplay() { }
        @Override public void showDisplayPlace() { shown++; }
        @Override public void showNotice(@NonNull String message) { notices.add(message); }

        @Override public boolean runScript(@NonNull String script,
                                           @NonNull X11LinuxAppRunner.ScriptExitListener onExit) {
            if (!startSucceeds) return false;
            scripts.add(script);
            listeners.add(onExit);
            return true;
        }

        @Override public void askBeforeReplacingSession(@NonNull String message,
                                                        @NonNull Runnable onYes) {
            questions.add(message);
        }
    }
}
