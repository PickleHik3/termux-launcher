package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

/**
 * The one decision point for a new session's engine (TerminalSession.chooseEngine): the setting or
 * the property asks, the crash guard can veto, and a veto says why once.
 */
public class TerminalEngineChoiceTest {

    private final List<String> mWarnings = new ArrayList<>();
    private final List<String> mInfos = new ArrayList<>();
    private TerminalSession mSession;

    /** A client whose Terminal engine setting says {@code prefersGhostty}, recording its logs. */
    private TerminalSessionClient client(boolean prefersGhostty) {
        return (TerminalSessionClient) Proxy.newProxyInstance(TerminalSessionClient.class.getClassLoader(),
            new Class<?>[]{TerminalSessionClient.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "prefersGhosttyEngine":
                        return prefersGhostty;
                    case "logWarn":
                        mWarnings.add((String) args[1]);
                        return null;
                    case "logInfo":
                        mInfos.add((String) args[1]);
                        return null;
                    default:
                        return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                }
            });
    }

    private TerminalSession session(boolean prefersGhostty) {
        mSession = new TerminalSession("/bin/sh", null, new String[0], new String[0], 100, client(prefersGhostty));
        mSession.mEmulator = new TerminalEmulator(mSession, false, 20, 6, TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS,
            TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, 100, null);
        return mSession;
    }

    /** A guard that found a marker from a process that died with a Ghostty pane open (below API 30). */
    private static void installCrashedGuard() {
        GhosttyCrashGuardTest.FakeStore store = new GhosttyCrashGuardTest.FakeStore();
        store.marker = "4100 5";
        GhosttyCrashGuard.install(store, null, 5200, 1L, () -> 10L, message -> { });
        assertTrue(GhosttyCrashGuard.isDisabledAfterCrash());
    }

    private static boolean hostLibrary() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    @After
    public void tearDown() {
        if (mSession != null && mSession.mEmulator != null) mSession.mEmulator.releaseGhosttyCore();
        GhosttyCrashGuard.uninstallForTests();
    }

    @Test
    public void aCrashGuardVetoFallsBackToStandardAndSaysWhyOnce() {
        installCrashedGuard();
        TerminalSession session = session(true);
        session.chooseEngine(true);
        assertFalse("Standard after the veto", session.mEmulator.isGhosttyCoreActive());
        assertEquals(mWarnings.toString(), 1, mWarnings.size());
        assertTrue(mWarnings.get(0), mWarnings.get(0).contains("ghostty off after a crash"));
        assertTrue(mWarnings.get(0), mWarnings.get(0).contains("Ghostty pane open"));
        assertTrue(mInfos.isEmpty());
    }

    @Test
    public void standardAskedForIsChosenSilently() {
        installCrashedGuard();
        TerminalSession session = session(false);
        session.chooseEngine(false);
        assertFalse(session.mEmulator.isGhosttyCoreActive());
        assertTrue("nothing to explain", mWarnings.isEmpty() && mInfos.isEmpty());
    }

    @Test
    public void theSettingAloneStartsGhosttyWhenNothingVetoes() {
        if (!hostLibrary()) return;
        TerminalSession session = session(true);
        session.chooseEngine(false);
        assertTrue(session.mEmulator.isGhosttyCoreActive());
        assertTrue(mWarnings.toString(), mWarnings.isEmpty());
        assertEquals(1, mInfos.size());
    }

    @Test
    public void withoutTheLibraryTheRequestSaysItIsUnavailableOnce() {
        if (hostLibrary()) return;
        TerminalSession session = session(true);
        session.chooseEngine(true);
        assertFalse(session.mEmulator.isGhosttyCoreActive());
        assertEquals(mWarnings.toString(), 1, mWarnings.size());
        assertTrue(mWarnings.get(0), mWarnings.get(0).contains("ghostty unavailable"));
    }
}
