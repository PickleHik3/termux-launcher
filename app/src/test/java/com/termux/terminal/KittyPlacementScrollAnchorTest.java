package com.termux.terminal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A kitty graphics display command captures the cursor row when it arrives, advances the cursor
 * over the image's rows immediately (scrolling if the cursor sits near the bottom), and places the
 * image only after an asynchronous decode. The captured row is a screen row: it must follow the
 * content it was captured against, or the image lands below its anchor with blank lines above it —
 * exactly the height of the image, once its own placement scrolls a second time to fit.
 */
@RunWith(RobolectricTestRunner.class)
public class KittyPlacementScrollAnchorTest {

    /** Captures placement runnables so the async decode can be drained deterministically. */
    private static final class RecordingOutput extends TerminalOutput {
        final BlockingQueue<Runnable> posted = new ArrayBlockingQueue<>(8);
        final StringBuilder written = new StringBuilder();

        @Override public void write(byte[] data, int offset, int count) {
            written.append(new String(data, offset, count, StandardCharsets.UTF_8));
        }
        @Override public void titleChanged(String oldTitle, String newTitle) { }
        @Override public void onCopyTextToClipboard(String text) { }
        @Override public void onPasteTextFromClipboard() { }
        @Override public void onBell() { }
        @Override public void onColorsChanged() { }
        @Override public void postTerminalUpdate(Runnable update) { posted.add(update); }
    }

    private static final TerminalSessionClient CLIENT = new TerminalSessionClient() {
        @Override public void onTextChanged(TerminalSession changedSession) { }
        @Override public void onTitleChanged(TerminalSession changedSession) { }
        @Override public void onSessionFinished(TerminalSession finishedSession) { }
        @Override public void onCopyTextToClipboard(TerminalSession session, String text) { }
        @Override public void onPasteTextFromClipboard(TerminalSession session) { }
        @Override public void onBell(TerminalSession session) { }
        @Override public void onColorsChanged(TerminalSession session) { }
        @Override public void onTerminalCursorStateChange(boolean state) { }
        @Override public void setTerminalShellPid(TerminalSession session, int pid) { }
        @Override public Integer getTerminalCursorStyle() { return null; }
        @Override public void logError(String tag, String message) { }
        @Override public void logWarn(String tag, String message) { }
        @Override public void logInfo(String tag, String message) { }
        @Override public void logDebug(String tag, String message) { }
        @Override public void logVerbose(String tag, String message) { }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { }
        @Override public void logStackTrace(String tag, Exception e) { }
    };

    private static void enter(TerminalEmulator emulator, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    @Test
    public void placementFollowsTheScrollItsOwnCursorAdvanceCaused() throws Exception {
        RecordingOutput output = new RecordingOutput();
        // 10x5 cells of 10x10 px each.
        TerminalEmulator emulator = new TerminalEmulator(output, true, 10, 5, 10, 10, 50, CLIENT);

        // Fill every row so the cursor ends on the bottom row, column 0 — an interactive prompt.
        enter(emulator, "0\r\n1\r\n2\r\n3\r\n4\r");
        assertEquals(4, emulator.getCursorRow());
        long scrollsBefore = emulator.scrollEventCount();

        // One a=T raw RGB image, 10x30 px = 1 column x 3 rows at the current cell size. The cursor
        // advance runs synchronously and must scroll twice; the placement lands after the decode.
        byte[] pixels = new byte[10 * 30 * 3];
        String payload = Base64.getEncoder().encodeToString(pixels);
        enter(emulator, "\033_Gi=77,a=T,f=24,s=10,v=30,q=2;" + payload + "\033\\");
        assertEquals("the advance scrolls exactly the rows the image needs past the bottom",
            2, emulator.scrollEventCount() - scrollsBefore);

        Runnable placement = output.posted.poll(5, TimeUnit.SECONDS);
        assertNotNull("decode never posted its placement", placement);
        placement.run();

        // The anchor followed the scroll: the image's first row overwrites the line the cursor was
        // on ("4", now at row 2), and placing it caused no second scroll — so no blank gap opens
        // between the last text line and the image.
        assertEquals("placement must not scroll a second time",
            2, emulator.scrollEventCount() - scrollsBefore);
        assertEquals("2", emulator.getScreen().getSelectedText(0, 0, 0, 0));
        assertEquals("3", emulator.getScreen().getSelectedText(0, 1, 0, 1));
        assertEquals(Character.valueOf('+'), emulator.getChar(0, 2));
        assertEquals(Character.valueOf('+'), emulator.getChar(0, 3));
        assertEquals(Character.valueOf('+'), emulator.getChar(0, 4));
    }
}
