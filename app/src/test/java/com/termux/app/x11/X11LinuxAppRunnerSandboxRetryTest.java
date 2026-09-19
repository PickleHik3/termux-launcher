package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * D7: a container app that dies at once without {@code --no-sandbox} is retried once with it, and
 * the outcome is remembered by {@link LinuxAppCatalog.LinuxApp#id} so a later tap goes straight to
 * the flag. Drives {@link X11LinuxAppRunner#runAndWatch} and
 * {@link X11LinuxAppRunner#shouldStartWithNoSandbox} directly — the seams left for testing this
 * without the real {@code EXECUTOR} background hop that {@link X11LinuxAppRunnerTest} does not
 * exercise either, since {@code runAndWatch} itself never leaves the main thread.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11LinuxAppRunnerSandboxRetryTest {

    private static final String DISPLAY = ":1";
    private static final List<String> ENV = Collections.emptyList();

    private Context context;
    private FakeHost host;
    private X11LinuxAppRunner runner;

    private static LinuxAppCatalog.LinuxApp app(String id) {
        return new LinuxAppCatalog.LinuxApp(id, "Typora", "typora", "typora", "");
    }

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
        preferences.edit().remove("x11_no_sandbox_apps_v1").commit();
        host = new FakeHost();
        runner = new X11LinuxAppRunner(context, host);
    }

    @Test public void aQuickUnsuccessfulExitRetriesOnceWithTheFlag() {
        LinuxAppCatalog.LinuxApp app = app("typora");
        host.nextExitCode = 133; // SIGABRT-ish, what the sandbox crash looks like as an exit code
        runner.runAndWatch(app, "exec typora\n", false, DISPLAY, ENV);
        idle();

        assertEquals("the plain command, then the flagged retry", 2, host.scripts.size());
        assertFalse(host.scripts.get(0).contains("--no-sandbox"));
        assertTrue(host.scripts.get(1).contains("--no-sandbox"));
    }

    @Test public void aSuccessfulLaunchIsNotRetriedAndTeachesNothing() {
        LinuxAppCatalog.LinuxApp app = app("typora");
        host.nextExitCode = null; // still running when the window checks in
        runner.runAndWatch(app, "exec typora\n", false, DISPLAY, ENV);
        idle();

        assertEquals("no retry", 1, host.scripts.size());
        assertFalse("never asked to remember a plain success",
            runner.shouldStartWithNoSandbox(app));
        assertTrue(host.notices.isEmpty());
    }

    @Test public void aSuccessfulFlaggedRetryIsRemembered() {
        LinuxAppCatalog.LinuxApp app = app("typora");
        host.nextExitCode = null; // the flag worked, so the process is still running
        runner.runAndWatch(app, "exec typora --no-sandbox\n", true, DISPLAY, ENV);
        idle();

        assertTrue("remembered for next time", runner.shouldStartWithNoSandbox(app));
    }

    @Test public void aRememberedAppStartsWithTheFlagOnTheVeryFirstAttempt() {
        LinuxAppCatalog.LinuxApp app = app("typora");
        host.nextExitCode = null;
        runner.runAndWatch(app, "exec typora --no-sandbox\n", true, DISPLAY, ENV);
        idle();

        // A second runner stands in for the app being tapped again later (a fresh instance, same
        // on-disk store) — the decision must not depend on anything kept only in memory.
        X11LinuxAppRunner reloaded = new X11LinuxAppRunner(context, new FakeHost());
        assertTrue(reloaded.shouldStartWithNoSandbox(app));
    }

    @Test public void whenTheFlagStopsHelpingTheMemoryIsForgottenAndTheUserIsTold() {
        LinuxAppCatalog.LinuxApp app = app("typora");
        // Already remembered from an earlier, successful run.
        new X11ElectronSandboxStore(context).remember(app.id);
        assertTrue(runner.shouldStartWithNoSandbox(app));

        host.nextExitCode = 133; // now even the flagged command dies at once
        runner.runAndWatch(app, "exec typora --no-sandbox\n", true, DISPLAY, ENV);
        idle();

        assertFalse("forgotten so the next tap gets a fresh plain attempt",
            runner.shouldStartWithNoSandbox(app));
        assertEquals(1, host.notices.size());
        assertEquals("no second retry attempted", 1, host.scripts.size());
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(X11LinuxAppRunner.QUICK_FAIL_MS, TimeUnit.MILLISECONDS);
    }

    /** A Host whose runScript is answered with a fixed exit code, recording every script it ran. */
    private static final class FakeHost implements X11LinuxAppRunner.Host {
        final List<String> scripts = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        @Nullable Integer nextExitCode;

        @Override public boolean isDisplayEnabled() { return true; }
        @Override public void turnOnDisplay() { }
        @Override public boolean isDisplayRunning() { return true; }
        @Override public void startDisplay() { }
        @Override public void showDisplayPlace() { }
        @Override public void showNotice(@NonNull String message) { notices.add(message); }

        @Override @Nullable public X11LinuxAppRunner.ScriptHandle runScript(@NonNull String script) {
            scripts.add(script);
            Integer exitCode = nextExitCode;
            return () -> exitCode;
        }
    }
}
