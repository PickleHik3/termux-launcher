package com.termux.app.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The per-app D7 memory: which container apps need {@code --no-sandbox} from the start. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11ElectronSandboxStoreTest {
    private static final String KEY = "x11_no_sandbox_apps_v1";
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
        preferences.edit().remove(KEY).commit();
    }

    @Test public void unknownAppIsNotRememberedYet() {
        X11ElectronSandboxStore store = new X11ElectronSandboxStore(context);
        assertFalse(store.needsNoSandbox("distro:debian:typora"));
    }

    @Test public void aRememberedAppReadsBackAsNeedingTheFlag() {
        X11ElectronSandboxStore store = new X11ElectronSandboxStore(context);
        store.remember("distro:debian:typora");
        assertTrue(store.needsNoSandbox("distro:debian:typora"));
        // Another app is untouched.
        assertFalse(store.needsNoSandbox("distro:debian:xterm"));
    }

    @Test public void theMemorySurvivesAFreshStoreInstance() {
        new X11ElectronSandboxStore(context).remember("distro:debian:typora");
        // A restart drops every in-memory cache; a new instance is all a reload has.
        X11ElectronSandboxStore reloaded = new X11ElectronSandboxStore(context);
        assertTrue(reloaded.needsNoSandbox("distro:debian:typora"));
    }

    @Test public void forgettingDropsIt() {
        X11ElectronSandboxStore store = new X11ElectronSandboxStore(context);
        store.remember("distro:debian:typora");
        store.forget("distro:debian:typora");
        assertFalse(store.needsNoSandbox("distro:debian:typora"));
        // Surviving a restart too: forgetting persists just like remembering does.
        assertFalse(new X11ElectronSandboxStore(context).needsNoSandbox("distro:debian:typora"));
    }

    @Test public void forgettingAnAppThatWasNeverRememberedIsHarmless() {
        X11ElectronSandboxStore store = new X11ElectronSandboxStore(context);
        store.forget("distro:debian:typora");
        assertFalse(store.needsNoSandbox("distro:debian:typora"));
    }
}
