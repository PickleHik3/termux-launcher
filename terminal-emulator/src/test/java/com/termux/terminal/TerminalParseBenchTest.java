package com.termux.terminal;

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parse bench on this machine. The Ghostty modes need the host library, which builds made
 * with {@code -Pghostty=true} provide; without it they report an error and only the Java mode runs.
 */
public class TerminalParseBenchTest extends TestCase {

    /** The same mix ghostty-core-bench.sh generates on the device: SGR, CJK and emoji, plain. */
    static byte[] transcript(int lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            int m = i % 4;
            if (m == 0) sb.append(String.format(Locale.US, "\033[1;3%dm%06d colour line\033[0m plain tail text here\n", i % 8, i));
            else if (m == 1) sb.append(String.format(Locale.US, "%06d 日本語 émoji 😀 mixed width ─┼─ box drawing\n", i));
            else sb.append(String.format(Locale.US, "%06d the quick brown fox jumps over the lazy dog 0123456789 abcdefghij\n", i));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":(\"[^\"]*\"|[^,}]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    public void testLineFeedsBecomeCarriageReturnLineFeeds() {
        byte[] out = TerminalParseBench.lfToCrLf("a\nb\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("a\r\nb\r\n", new String(out, StandardCharsets.UTF_8));
    }

    public void testEveryModeReportsEveryRun() {
        TerminalParseBench.Options options = new TerminalParseBench.Options();
        options.runs = 2;
        byte[] data = TerminalParseBench.lfToCrLf(transcript(3002));
        List<String> lines = new ArrayList<>();
        TerminalParseBench.run(data, options, lines::add);
        assertEquals(2 * TerminalParseBench.ALL_MODES.length, lines.size());

        boolean library = GhosttyNative.isAvailable();
        String javaTail = null;
        for (String line : lines) {
            String mode = field(line, "mode");
            assertNotNull(line, mode);
            boolean ghostty = !mode.equals("\"java\"");
            if (ghostty && !library) {
                assertNotNull(line, field(line, "error"));
                continue;
            }
            assertNull(line, field(line, "error"));
            assertTrue(line, Double.parseDouble(field(line, "wall_ms")) > 0);
            assertTrue(line, Double.parseDouble(field(line, "mb_per_s")) > 0);
            if (mode.equals("\"java\"")) javaTail = field(line, "tail");
            if (mode.equals("\"ghostty-mirror\"")) {
                // Both cores end on the same last line, and the mirror ran in turns.
                assertEquals(line, javaTail, field(line, "tail"));
                assertTrue(line, Integer.parseInt(field(line, "turns")) >= 1);
                // The copy is the screen's alone: no history row is copied while output arrives.
                assertTrue(line, Long.parseLong(field(line, "mirror_screen_rows")) > 0);
                assertEquals(line, "0", field(line, "mirror_history_rows"));
                assertNotNull(line, field(line, "mirror_sync_ms"));
                assertNotNull(line, field(line, "mirror_history_max_ms"));
                // History is read on demand afterwards, as much as both cores keep.
                assertEquals(line, field(line, "transcript_rows_used"), field(line, "scrollback_rows"));
                assertTrue(line, Integer.parseInt(field(line, "scrollback_rows")) > 0);
                assertNotNull(line, field(line, "scrollback_read_ms"));
                assertNull(GhosttyMirror.stats);
            }
            if (ghostty) assertNotNull(line, field(line, "resident_bytes"));
        }
        assertNotNull(javaTail);
        assertTrue(javaTail, javaTail.endsWith("003002 the quick brown fox jumps over the lazy dog 0123456789 abcdefghij\""));
    }
}
