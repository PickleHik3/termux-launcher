package com.termux.terminal;

import junit.framework.TestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Which folder a session says it is in. The shell's own OSC 7 report wins over the /proc reading,
 * but only while it still names a folder that exists — a new pane opens here.
 */
public class SessionWorkingDirectoryTest extends TestCase {

    /** A client that does nothing: the session needs one to hand its callbacks to. */
    private static final TerminalSessionClient SILENT_CLIENT = new TerminalSessionClient() {
        @Override public void onTextChanged(TerminalSession changedSession) {}
        @Override public void onTitleChanged(TerminalSession changedSession) {}
        @Override public void onSessionFinished(TerminalSession finishedSession) {}
        @Override public void onCopyTextToClipboard(TerminalSession session, String text) {}
        @Override public void onPasteTextFromClipboard(TerminalSession session) {}
        @Override public void onBell(TerminalSession session) {}
        @Override public void onColorsChanged(TerminalSession session) {}
        @Override public void onTerminalCursorStateChange(boolean state) {}
        @Override public void setTerminalShellPid(TerminalSession session, int pid) {}
        @Override public void logError(String tag, String message) {}
        @Override public void logWarn(String tag, String message) {}
        @Override public void logInfo(String tag, String message) {}
        @Override public void logDebug(String tag, String message) {}
        @Override public void logVerbose(String tag, String message) {}
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) {}
        @Override public void logStackTrace(String tag, Exception e) {}
        @Override public Integer getTerminalCursorStyle() { return null; }
    };

    private TerminalSession sessionWithEmulator() {
        TerminalSession session = new TerminalSession("/bin/sh", null, new String[0], new String[0], null, SILENT_CLIENT);
        session.mEmulator = new TerminalEmulator(session, false, 10, 4,
            TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS, TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, 8, null);
        return session;
    }

    private void report(TerminalSession session, String uri) {
        byte[] bytes = ("\033]7;" + uri + "\007").getBytes(StandardCharsets.UTF_8);
        session.mEmulator.append(bytes, bytes.length);
    }

    public void testWithoutAReportThereIsNothingToPrefer() {
        TerminalSession session = sessionWithEmulator();
        assertNull(session.getReportedWorkingDirectory());
        // No shell is running in a test, so the /proc reading has nothing to say either.
        assertNull(session.getCwd());
    }

    public void testAReportedDirectoryIsWhereTheSessionIs() {
        TerminalSession session = sessionWithEmulator();
        File existing = new File(System.getProperty("java.io.tmpdir"));
        report(session, "file://" + existing.getAbsolutePath());

        assertEquals(existing.getAbsolutePath(), session.getReportedWorkingDirectory());
        assertEquals(existing.getAbsolutePath(), session.getCwd());
    }

    public void testAReportedDirectoryThatIsGoneIsNotUsed() {
        TerminalSession session = sessionWithEmulator();
        report(session, "file:///no/such/folder/anywhere");

        assertEquals("/no/such/folder/anywhere", session.getReportedWorkingDirectory());
        assertNull("A path that is not there falls back", session.getCwd());
    }
}
