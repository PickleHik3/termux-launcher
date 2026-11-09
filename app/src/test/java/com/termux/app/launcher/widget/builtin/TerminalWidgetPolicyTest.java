package com.termux.app.launcher.widget.builtin;

import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Terminal widget's rules: which shell it shows, whose shells are widgets', when removal asks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWidgetPolicyTest {

    @Test public void nothingStartsBeforeTheShellsAreReady() {
        assertEquals(TerminalWidgetPolicy.Start.WAIT,
            TerminalWidgetPolicy.start(false, false, true, false));
        assertEquals(TerminalWidgetPolicy.Start.WAIT,
            TerminalWidgetPolicy.start(false, true, false, false));
    }

    @Test public void aRunningShellNoWindowShowsComesBackAsItWas() {
        assertEquals(TerminalWidgetPolicy.Start.REATTACH,
            TerminalWidgetPolicy.start(true, false, true, false));
    }

    @Test public void aGoneOrTakenShellIsReplacedByAFreshOne() {
        assertEquals("the process died with it", TerminalWidgetPolicy.Start.FRESH,
            TerminalWidgetPolicy.start(true, false, false, false));
        assertEquals("another window shows it", TerminalWidgetPolicy.Start.FRESH,
            TerminalWidgetPolicy.start(true, false, true, true));
    }

    @Test public void anEndedShellWaitsForATap() {
        assertEquals(TerminalWidgetPolicy.Start.ENDED,
            TerminalWidgetPolicy.start(true, true, false, false));
    }

    @Test public void aFreshShellStartsWhereTheLastOneWasWhileThatStillExists() {
        assertEquals("/data/x/home/src", TerminalWidgetPolicy.startCwd("/data/x/home/src", true));
        assertNull("removed since: the default", TerminalWidgetPolicy.startCwd("/gone", false));
        assertNull(TerminalWidgetPolicy.startCwd(null, true));
        assertNull(TerminalWidgetPolicy.startCwd("", true));
    }

    @Test public void theWidgetsStateIsKeptUnderStateKeys() {
        assertTrue(BuiltinWidgetView.isStateKey(TerminalWidgetPolicy.KEY_SHELL));
        assertTrue(BuiltinWidgetView.isStateKey(TerminalWidgetPolicy.KEY_CWD));
        assertTrue(BuiltinWidgetView.isStateKey(TerminalWidgetPolicy.KEY_ENDED));
        assertFalse("a setting is not state", BuiltinWidgetView.isStateKey(BuiltinWidgetGlass.CONFIG_KEY));
    }

    @Test public void stateChangesAreNoNewSettings() {
        Bundle a = new Bundle();
        a.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
        a.putString(TerminalWidgetPolicy.KEY_SHELL, "1");
        Bundle b = new Bundle();
        b.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
        b.putString(TerminalWidgetPolicy.KEY_SHELL, "2");
        b.putString(TerminalWidgetPolicy.KEY_CWD, "/tmp");
        assertTrue(BuiltinWidgetView.sameConfig(a, b));
        b.remove(BuiltinWidgetGlass.CONFIG_KEY);
        assertFalse("the Glass switch is a setting", BuiltinWidgetView.sameConfig(a, b));
    }

    @Test public void anEndedRecordReadsAsEnded() {
        Bundle config = new Bundle();
        assertFalse(TerminalWidgetPolicy.isEnded(config));
        assertFalse(TerminalWidgetPolicy.isEnded(null));
        config.putString(TerminalWidgetPolicy.KEY_ENDED, TerminalWidgetPolicy.ENDED);
        assertTrue(TerminalWidgetPolicy.isEnded(config));
    }

    @Test public void theShellsWidgetsNameAreTheirsEvenOffScreen() {
        Map<Integer, Bundle> widgets = new HashMap<>();
        Bundle one = new Bundle();
        one.putString(TerminalWidgetPolicy.KEY_SHELL, "h1");
        widgets.put(1, one);
        Bundle ended = new Bundle();
        ended.putString(TerminalWidgetPolicy.KEY_ENDED, TerminalWidgetPolicy.ENDED);
        widgets.put(2, ended);
        widgets.put(3, null);
        Set<String> claimed = TerminalWidgetPolicy.claimedHandles(widgets);
        assertEquals(Collections.singleton("h1"), claimed);

        assertTrue(TerminalWidgetPolicy.isWidgetShell(false, "h1", claimed));
        assertTrue("shown by a widget now", TerminalWidgetPolicy.isWidgetShell(true, "h9", claimed));
        assertFalse("a Workspace shell", TerminalWidgetPolicy.isWidgetShell(false, "h9", claimed));
        assertFalse(TerminalWidgetPolicy.isWidgetShell(false, null, claimed));
    }

    @Test public void onlyWidgetsGoneFromHomeTakeTheirShellsWithThem() {
        assertEquals(Arrays.asList(4, 9),
            TerminalWidgetPolicy.gone(Arrays.asList(1, 4, 9), new HashSet<>(Arrays.asList(1, 2))));
        assertTrue(TerminalWidgetPolicy.gone(Arrays.asList(1, 2),
            new HashSet<>(Arrays.asList(1, 2, 3))).isEmpty());
    }

    @Test public void removalAsksOnlyWhileSomethingIsKnownToRun() {
        assertTrue(TerminalWidgetPolicy.removalAsks(false));
        assertFalse("an idle shell goes at once", TerminalWidgetPolicy.removalAsks(true));
        assertFalse("unknown does not ask", TerminalWidgetPolicy.removalAsks(null));
    }
}
