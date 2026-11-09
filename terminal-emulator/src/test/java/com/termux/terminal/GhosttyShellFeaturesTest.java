package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * OSC 8 links, OSC 133 prompt marks, OSC 9;4 progress, OSC 22 pointer shapes, OSC 9/99/777
 * notifications and the OSC 66 fallback on the Ghostty core, each fed to both cores and compared
 * through the getters the view and the app read. Runs only with the host library
 * ({@code -Pghostty=true}); elsewhere every test returns at once.
 */
public class GhosttyShellFeaturesTest extends TerminalTestCase {

    private static boolean libraryBuilt() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    private static final class Pane {
        final MockTerminalOutput output = new MockTerminalOutput();
        final TerminalEmulator emulator;

        Pane(int columns, int rows, int transcriptRows, boolean ghostty) {
            emulator = new TerminalEmulator(output, false, columns, rows, INITIAL_CELL_WIDTH_PIXELS,
                INITIAL_CELL_HEIGHT_PIXELS, transcriptRows, null);
            if (ghostty) assertTrue("library failed to load: " + GhosttyNative.loadError(), emulator.useGhosttyCore());
        }

        Pane feed(String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            emulator.append(bytes, bytes.length);
            return this;
        }

        /** The same bytes, one append per byte, so every sequence and code point is split. */
        Pane feedByteByByte(String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) emulator.append(new byte[]{b}, 1);
            return this;
        }

        String row(int y) {
            TerminalContent content = emulator.getContent();
            return content.text(0, y, content.columns(), y, false, false);
        }
    }

    private static Pane[] both(int columns, int rows, int transcriptRows) {
        return new Pane[]{new Pane(columns, rows, transcriptRows, false), new Pane(columns, rows, transcriptRows, true)};
    }

    private static String link(String uri, String text) {
        return "\033]8;;" + uri + "\033\\" + text + "\033]8;;\033\\";
    }

    /** Every readable row's text and every cell's link URI the same on both panes. */
    private static void assertSameLinks(Pane java, Pane ghostty) {
        TerminalContent j = java.emulator.getContent(), g = ghostty.emulator.getContent();
        assertEquals("history rows", j.historyRows(), g.historyRows());
        for (int y = -j.historyRows(); y < j.screenRows(); y++) {
            assertEquals("row " + y, java.row(y), ghostty.row(y));
            for (int x = 0; x < j.columns(); x++)
                assertEquals("link at row " + y + " column " + x, java.emulator.getHyperlinkUriAt(y, x),
                    ghostty.emulator.getHyperlinkUriAt(y, x));
        }
    }

    public void testLinksMatchOnScreenAndInHistory() {
        if (!libraryBuilt()) return;
        Pane[] panes = both(20, 4, 50);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 12; i++)
            out.append(String.format(Locale.US, "%02d ", i)).append(link("https://example.com/" + i, "page " + i))
                .append(" end\r\n");
        // A link that wraps onto the next row, and one written over a styled background.
        out.append(link("https://example.com/long", "a link long enough to wrap")).append("\r\n");
        out.append("\033[44m").append(link("file:///sdcard/x.txt", "x.txt")).append("\033[0m");
        for (Pane pane : panes) pane.feed(out.toString());
        assertSameLinks(panes[0], panes[1]);

        Pane ghostty = panes[1];
        int top = -ghostty.emulator.getContent().historyRows();
        assertEquals("https://example.com/0", ghostty.emulator.getHyperlinkUriAt(top, 3));
        // One link, one id: every cell of a link carries the same id, as the renderer underlines it whole.
        TerminalRow first = ghostty.emulator.getContent().row(top);
        int id = first.getHyperlinkId(3);
        assertTrue(id != TerminalHyperlinks.NO_LINK);
        for (int x = 3; x < 9; x++) assertEquals(id, first.getHyperlinkId(x));
        assertEquals(TerminalHyperlinks.NO_LINK, first.getHyperlinkId(10));
    }

    public void testLinkPoolStaysBoundedOnTheGhosttyCore() {
        if (!libraryBuilt()) return;
        Pane ghostty = new Pane(30, 5, 20, true);
        int links = TerminalHyperlinks.MAX_LINKS + 500;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < links; i++) {
            out.append(link("https://example.com/" + i, "l" + i)).append("\r\n");
            if (out.length() > 64 * 1024) {
                ghostty.feed(out.toString());
                out.setLength(0);
            }
        }
        ghostty.feed(out.toString());
        assertTrue(ghostty.emulator.getHyperlinks().size() <= TerminalHyperlinks.MAX_LINKS);
        // The newest links still resolve, on screen and in the history read on demand.
        TerminalContent content = ghostty.emulator.getContent();
        for (int y = -content.historyRows(); y < content.screenRows() - 1; y++) {
            String text = ghostty.row(y).trim();
            assertTrue("row " + y + ": " + text, text.startsWith("l"));
            assertEquals("https://example.com/" + text.substring(1), ghostty.emulator.getHyperlinkUriAt(y, 0));
        }
    }

    /**
     * A shell session: prompt, typed command, output, exit status. With {@code ownRows} the prompt,
     * the command and the output each start their own row; otherwise the command is typed on the
     * prompt's row, as most shells do.
     */
    private static String command(int i, int exitCode, int outputLines, boolean ownRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("\033]133;A\007$ ").append(ownRows ? "\r\n" : "").append("\033]133;B\007cmd").append(i)
            .append("\r\n\033]133;C\007");
        for (int line = 0; line < outputLines; line++) sb.append("out").append(i).append('.').append(line).append("\r\n");
        sb.append("\033]133;D;").append(exitCode).append('\007');
        return sb.toString();
    }

    public void testPromptRowsAndExitCodesMatchIntoHistory() {
        if (!libraryBuilt()) return;
        Pane[] panes = both(16, 5, 100);
        for (Pane pane : panes) {
            assertFalse(pane.emulator.hasShellIntegration());
            for (int i = 0; i < 6; i++) pane.feed(command(i, i, 3, true));
            pane.feed("\033]133;A\007$ \r\n\033]133;B\007sleep\r\n\033]133;C\007");
        }
        Pane java = panes[0], ghostty = panes[1];
        assertTrue(ghostty.emulator.hasShellIntegration());
        assertTrue(ghostty.emulator.isShellIntegrationCommandRunning());
        assertEquals(java.emulator.isShellIntegrationCommandRunning(), ghostty.emulator.isShellIntegrationCommandRunning());
        assertEquals(5, ghostty.emulator.getLastCommandExitCode());

        // Walk every prompt from the bottom up and back down: same rows on both cores, into history.
        List<Integer> up = new ArrayList<>(), javaUp = new ArrayList<>();
        for (int row = ghostty.emulator.findPromptRow(5, true); row != Integer.MIN_VALUE; row = ghostty.emulator.findPromptRow(row, true))
            up.add(row);
        for (int row = java.emulator.findPromptRow(5, true); row != Integer.MIN_VALUE; row = java.emulator.findPromptRow(row, true))
            javaUp.add(row);
        assertEquals(javaUp, up);
        assertEquals(7, up.size());
        assertTrue("the oldest prompt is in history", up.get(up.size() - 1) < -20);
        for (int row : up) assertEquals("$", ghostty.row(row).trim());
        List<Integer> down = new ArrayList<>(), javaDown = new ArrayList<>();
        int top = -ghostty.emulator.getContent().historyRows() - 1;
        for (int row = ghostty.emulator.findPromptRow(top, false); row != Integer.MIN_VALUE; row = ghostty.emulator.findPromptRow(row, false))
            down.add(row);
        for (int row = java.emulator.findPromptRow(top, false); row != Integer.MIN_VALUE; row = java.emulator.findPromptRow(row, false))
            javaDown.add(row);
        assertEquals(javaDown, down);

        for (Pane pane : panes) pane.feed("done\r\n\033]133;D;0\007");
        assertFalse(ghostty.emulator.isShellIntegrationCommandRunning());
        assertEquals(java.emulator.getLastCommandExitCode(), ghostty.emulator.getLastCommandExitCode());
        assertEquals(0, ghostty.emulator.getLastCommandExitCode());
        for (Pane pane : panes) pane.feed("\033]133;D\007");
        assertEquals(TerminalEmulator.COMMAND_EXIT_CODE_UNKNOWN, ghostty.emulator.getLastCommandExitCode());
        assertEquals(java.emulator.getLastCommandExitCode(), ghostty.emulator.getLastCommandExitCode());
    }

    /**
     * Most shells type the command on the prompt's row. The Java core keeps one mark per row and the
     * last one wins (OSC 133 B overwrites A), so it finds no prompt there; Ghostty flags the row as a
     * prompt whatever follows on it, so jumps land on every prompt.
     */
    public void testPromptJumpsFindOneLinePromptsOnTheGhosttyCore() {
        if (!libraryBuilt()) return;
        Pane[] panes = both(16, 5, 100);
        for (Pane pane : panes) for (int i = 0; i < 6; i++) pane.feed(command(i, 0, 3, false));
        assertEquals(Integer.MIN_VALUE, panes[0].emulator.findPromptRow(5, true));
        Pane ghostty = panes[1];
        List<Integer> up = new ArrayList<>();
        for (int row = ghostty.emulator.findPromptRow(5, true); row != Integer.MIN_VALUE; row = ghostty.emulator.findPromptRow(row, true))
            up.add(row);
        assertEquals(6, up.size());
        for (int i = 0; i < up.size(); i++)
            assertEquals("$ cmd" + (5 - i), ghostty.row(up.get(i)).trim());
    }

    public void testProgressReportsMatch() {
        if (!libraryBuilt()) return;
        Pane[] panes = both(20, 4, 20);
        String[] steps = {
            "\033]9;4;1;40\007", "\033]9;4;1\007", "\033]9;4;3\007", "\033]9;4;2;70\033\\", "\033]9;4;4;150\007",
            "\033]9;4;0\007", "\033]9;4;1;10\007\033]9;4;1;20\007\033]9;4;1;30\007",
            "\033]9;4;1;55\007\033]9;4;3\007\033]9;4;1\007",
        };
        for (String step : steps) {
            for (Pane pane : panes) pane.feed(step);
            String at = "after " + step.replace("\033", "ESC").replace("\007", "BEL");
            assertEquals(at, panes[0].emulator.getProgressState(), panes[1].emulator.getProgressState());
            assertEquals(at, panes[0].emulator.getProgressValue(), panes[1].emulator.getProgressValue());
        }
        assertEquals(TerminalEmulator.PROGRESS_STATE_NORMAL, panes[1].emulator.getProgressState());
        assertEquals(55, panes[1].emulator.getProgressValue());
        // A finished command takes its progress with it.
        for (Pane pane : panes) pane.feed("\033]133;D;0\007");
        assertEquals(TerminalEmulator.PROGRESS_STATE_NONE, panes[1].emulator.getProgressState());
    }

    public void testPointerShapesMatch() {
        if (!libraryBuilt()) return;
        Pane[] panes = both(20, 4, 20);
        for (String shape : new String[]{"pointer", "wait", "crosshair", "text", "not-allowed", "default"}) {
            for (Pane pane : panes) pane.feed("\033]22;" + shape + "\033\\");
            assertEquals(shape, panes[0].emulator.getPointerShape(), panes[1].emulator.getPointerShape());
        }
        for (Pane pane : panes) pane.feed("\033]22;wait\007").emulator.reset();
        assertNull(panes[1].emulator.getPointerShape());
        assertEquals(panes[0].output.pointerShapes, panes[1].output.pointerShapes);
    }

    public void testNotificationsReachTheSameCallbacks() {
        if (!libraryBuilt()) return;
        Pane[] panes = both(20, 4, 20);
        for (Pane pane : panes) {
            pane.feed("\033]9;Build done\007");
            pane.feed("\033]777;notify;Title;The body\033\\");
            pane.feed("\033]99;i=1:d=0:p=title;Build finished\033\\");
            pane.feed("\033]99;i=1:d=1:p=body;42 tests passed\033\\");
        }
        assertEquals(2, panes[1].output.notifications.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(panes[0].output.notifications.get(i)[0], panes[1].output.notifications.get(i)[0]);
            assertEquals(panes[0].output.notifications.get(i)[1], panes[1].output.notifications.get(i)[1]);
        }
        assertEquals(1, panes[1].output.kittyNotifications.size());
        KittyNotification shown = panes[1].output.kittyNotifications.get(0);
        assertEquals("Build finished", shown.getTitle());
        assertEquals("42 tests passed", shown.getBody());
        assertEquals(panes[0].output.kittyNotifications.get(0).getTitle(), shown.getTitle());
        assertEquals(panes[0].output.kittyNotifications.get(0).getBody(), shown.getBody());
        // Nothing of either was drawn.
        assertEquals(panes[0].row(0), panes[1].row(0));
    }

    public void testNotificationQueryAnswersInOrderWithOtherReplies() {
        if (!libraryBuilt()) return;
        Pane[] panes = both(20, 4, 20);
        for (Pane pane : panes) {
            pane.output.getOutputAndClear();
            pane.feed("\033[5n\033]99;i=q1:p=?;\033\\\033[5n");
        }
        String java = panes[0].output.getOutputAndClear(), ghostty = panes[1].output.getOutputAndClear();
        assertTrue(ghostty, ghostty.startsWith("\033[0n\033]99;i=q1:p=?;"));
        assertTrue(ghostty, ghostty.endsWith("\033[0n"));
        assertEquals(java, ghostty);
    }

    public void testSizedTextIsWrittenAtNormalSize() {
        if (!libraryBuilt()) return;
        Pane ghostty = new Pane(30, 4, 20, true);
        ghostty.feed("\033]66;s=2;Hello\007 world\r\n");
        ghostty.feed("\033]66;w=3:n=1:d=2;Hi\033\\!\r\n");
        // Control bytes in the text are not run.
        ghostty.feed("\033]66;s=2;a\bb\rc\007\r\n");
        assertEquals("Hello world", ghostty.row(0).trim());
        assertEquals("Hi!", ghostty.row(1).trim());
        assertEquals("abc", ghostty.row(2).trim());
    }

    public void testSizedTextProbeStillSeesNoSupport() {
        if (!libraryBuilt()) return;
        // The probe kitty documents: CR, one sized space, then a cursor position report. A terminal
        // with text sizing answers column 3; one without, column 1.
        Pane[] panes = both(30, 4, 20);
        for (Pane pane : panes) {
            pane.output.getOutputAndClear();
            pane.feed("\r\033]66;w=2; \007\033[6n\r\033]66;s=2; \033\\\033[6n");
        }
        assertEquals("\033[1;3R\033[1;3R", panes[0].output.getOutputAndClear());
        assertEquals("\033[1;1R\033[1;1R", panes[1].output.getOutputAndClear());
        assertEquals("", panes[1].row(0).trim());
    }

    private static String kitty(String control, String payload) {
        return "\033_G" + control + (payload == null ? "" : ";" + payload) + "\033\\";
    }

    /**
     * Replies from both of the write loop's interceptors and from Ghostty itself, interleaved: two
     * status reports around a kitty query, a kitty notification query, then another kitty query and
     * a status report. Each answer must come out where its sequence was.
     */
    private static final String MIXED_REPLIES = "\033[5n" + kitty("i=31,s=1,v=1,a=q,t=d,f=24", "AAAA") + "\033[5n"
        + "\033]99;i=q2:p=?;\033\\" + kitty("a=q,i=32,s=1,v=1,f=32", "/wAA/w==") + "\033[5n";

    /**
     * Sequences split anywhere, code points split anywhere, strings cancelled by CAN or SUB, kitty
     * queries, a kitty transmit and place and a synchronized-output hold: byte-by-byte feeding must
     * leave exactly what one write leaves, with the replies in the same order.
     */
    public void testSplitWritesLeaveWhatOneWriteLeaves() {
        if (!libraryBuilt()) return;
        String stream = "plain é 日本 😀\r\n"
            + link("https://example.com/split", "linked") + " after\r\n"
            + "\033]99;i=s:d=0:p=title;Split\033\\\033]99;i=s:d=1:p=body;notification\007"
            + "\033]66;s=2;sized é\033\\ tail\r\n"
            + "\033]99;i=c;cancelled\030kept\r\n"
            + "\033]66;s=2;dropped\032also\r\n"
            + "\033]2;a title\007\033[1;31mred\033[0m \033]9;4;1;33\007"
            + "\033]133;A\007$ \033]133;B\007ls\r\n\033]133;C\007\033]133;D;2\007"
            + "\033]66;s=2;one\033\\\033]66;s=2;two\007three\r\n"
            + "\033]9;ping\007\033]22;pointer\007\033[5n"
            + MIXED_REPLIES
            + "\033[?2026h" + link("https://example.com/held", "H") + "\033[?2026l"
            + kitty("a=t,i=7,f=32,s=1,v=1,q=2", "/wAA/w==") + kitty("a=p,i=7,p=1,q=2", null)
            + kitty("a=T,i=8,f=32,s=1,v=1,q=2", "AP8A/w==") + "\033[5n";
        Pane whole = new Pane(24, 9, 50, true);
        Pane split = new Pane(24, 9, 50, true);
        whole.feed(stream);
        split.feedByteByByte(stream);
        assertFalse(whole.emulator.isScreenUpdateHeld());
        assertFalse(split.emulator.isScreenUpdateHeld());

        // The interleaved replies, in the order the Java core gives them, inside both outputs.
        Pane java = new Pane(24, 9, 50, false);
        java.output.getOutputAndClear();
        java.feed(MIXED_REPLIES);
        String mixed = java.output.getOutputAndClear();
        assertTrue(mixed, mixed.startsWith("\033[0n\033_Gi=31;OK\033\\\033[0n\033]99;i=q2:p=?;"));
        assertTrue(mixed, mixed.endsWith("\033_Gi=32;OK\033\\\033[0n"));

        // Image placements: the a=p of image 7 and the a=T of image 8, the same on both.
        int[] wholePlacements = new int[16 * GhosttyNative.KP_INTS];
        int[] splitPlacements = new int[16 * GhosttyNative.KP_INTS];
        int placed = whole.emulator.ghosttyCore().kittyPlacements(wholePlacements);
        assertEquals(2, placed);
        assertEquals(placed, split.emulator.ghosttyCore().kittyPlacements(splitPlacements));
        assertTrue(Arrays.equals(wholePlacements, splitPlacements));
        // What the hold kept back is there once it ends, link and all.
        int held = whole.row(8).indexOf('H');
        assertTrue(whole.row(8), held >= 0);
        assertEquals("https://example.com/held", whole.emulator.getHyperlinkUriAt(8, held));

        TerminalContent w = whole.emulator.getContent(), s = split.emulator.getContent();
        assertEquals(w.historyRows(), s.historyRows());
        for (int y = -w.historyRows(); y < w.screenRows(); y++) {
            assertEquals("row " + y, whole.row(y), split.row(y));
            for (int x = 0; x < w.columns(); x++) {
                assertEquals(w.row(y).getStyle(x), s.row(y).getStyle(x));
                assertEquals(whole.emulator.getHyperlinkUriAt(y, x), split.emulator.getHyperlinkUriAt(y, x));
            }
        }
        assertEquals(whole.emulator.getCursorRow(), split.emulator.getCursorRow());
        assertEquals(whole.emulator.getCursorCol(), split.emulator.getCursorCol());
        String wholeReplies = whole.output.getOutputAndClear();
        assertEquals(wholeReplies, split.output.getOutputAndClear());
        assertTrue(wholeReplies, wholeReplies.contains(mixed));
        assertTrue(wholeReplies, wholeReplies.endsWith(mixed + "\033[0n"));
        assertEquals(whole.emulator.getTitle(), split.emulator.getTitle());
        assertEquals(whole.emulator.getProgressValue(), split.emulator.getProgressValue());
        assertEquals(2, split.emulator.getLastCommandExitCode());
        assertEquals("pointer", split.emulator.getPointerShape());
        assertEquals(1, split.output.kittyNotifications.size());
        assertEquals("Split", split.output.kittyNotifications.get(0).getTitle());
        assertEquals(whole.output.kittyNotifications.size(), split.output.kittyNotifications.size());
        assertEquals(1, split.output.notifications.size());
        assertEquals(whole.output.notifications.size(), split.output.notifications.size());

        // What one write leaves, row by row: cancelled strings drop their text, kept ones do not.
        StringBuilder rows = new StringBuilder();
        for (int y = -w.historyRows(); y < w.screenRows(); y++) rows.append(y).append(": ").append(whole.row(y)).append('\n');
        assertEquals(rows.toString(), "plain é 日本 😀", whole.row(0).trim());
        assertEquals("linked after", whole.row(1).trim());
        assertEquals("https://example.com/split", whole.emulator.getHyperlinkUriAt(1, 0));
        assertEquals("sized é tail", whole.row(2).trim());
        assertEquals("kept", whole.row(3).trim());
        assertEquals("also", whole.row(4).trim());
        // Ghostty starts a prompt on a fresh line (OSC 133 A), where the Java core stays put.
        assertEquals("red", whole.row(5).trim());
        assertEquals("$ ls", whole.row(6).trim());
        assertEquals("onetwothree", whole.row(7).trim());
    }

    /** With nothing to sniff, the sniffer's writes leave the screen the Java core leaves. */
    public void testUnsniffedOutputMatchesTheJavaCoreWhenSplit() {
        if (!libraryBuilt()) return;
        String stream = "abc \033[1mbold\033[0m é 日本\r\n\033]2;t\007\033[2;3Hx\033[K\r\nline ǘ\r\n";
        Pane java = new Pane(20, 4, 20, false).feed(stream);
        Pane split = new Pane(20, 4, 20, true).feedByteByByte(stream);
        for (int y = 0; y < 4; y++) assertEquals("row " + y, java.row(y), split.row(y));
        assertEquals(java.emulator.getCursorRow(), split.emulator.getCursorRow());
        assertEquals(java.emulator.getCursorCol(), split.emulator.getCursorCol());
    }
}
