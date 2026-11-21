package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClipboardReadGrantsTest {

    private SharedPreferences store;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("clipboard-read-grants-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
    }

    private ClipboardReadGrants grants() {
        return new ClipboardReadGrants(new TermuxAppSharedPreferences(context, store, null));
    }

    @Test
    public void allowingOnceStoresNothing() {
        ClipboardReadGrants grants = grants();
        Object session = new Object();
        // "Allow once" never reaches the store: nothing to call, nothing granted.
        assertFalse(grants.isSessionGranted(session, "vim"));
        assertFalse(grants.isAlwaysGranted("vim"));
    }

    @Test
    public void aSessionGrantAppliesToThatSessionAndProgramOnly() {
        ClipboardReadGrants grants = grants();
        Object session = new Object();
        Object other = new Object();
        grants.allowForSession(session, "vim");
        assertTrue(grants.isSessionGranted(session, "vim"));
        assertFalse(grants.isSessionGranted(session, "cat"));
        assertFalse(grants.isSessionGranted(other, "vim"));
        assertFalse(grants.isSessionGranted(session, null));
        assertFalse(grants.isAlwaysGranted("vim"));
    }

    @Test
    public void aSessionGrantIsGoneAfterTheSessionIsForgotten() {
        ClipboardReadGrants grants = grants();
        Object session = new Object();
        grants.allowForSession(session, "vim");
        grants.forgetSession(session);
        assertFalse(grants.isSessionGranted(session, "vim"));
    }

    @Test
    public void anAlwaysGrantSurvivesANewStoreOverTheSamePreferences() {
        grants().allowAlways("claude");
        ClipboardReadGrants again = grants();
        assertTrue(again.isAlwaysGranted("claude"));
        assertFalse(again.isAlwaysGranted("vim"));
        assertFalse(again.isAlwaysGranted(null));
    }

    @Test
    public void resetClearsAlwaysButNotSessionGrants() {
        ClipboardReadGrants grants = grants();
        Object session = new Object();
        grants.allowAlways("claude");
        grants.allowForSession(session, "vim");
        grants.resetAlways();
        assertFalse(grants.isAlwaysGranted("claude"));
        assertFalse(grants().isAlwaysGranted("claude"));
        assertTrue(grants.isSessionGranted(session, "vim"));
    }
}
