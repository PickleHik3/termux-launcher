package com.termux.shared.termux.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TermuxUrlUtilsTest {

    private static List<String> urls(String text) {
        return new ArrayList<String>() {{
            for (CharSequence url : TermuxUrlUtils.extractUrls(text)) add(url.toString());
        }};
    }

    /**
     * A multiplexer pane draws its rows with the cursor, so a URL wrapped inside it reaches the
     * emulator as two rows with no wrap flag between them. The whole address is offered too.
     */
    @Test
    public void aUrlCutAtAPaneBorderIsJoinedAcrossTheRows() {
        String text = "\u2502 see https://github.com/microsoft/terminal/blob/main/src/cascadia/Ter \u2502\n"
            + "\u2502 minalApp/TabHeaderControl.xaml and more                             \u2502";
        List<String> found = urls(text);
        assertTrue(found.toString(), found.contains(
            "https://github.com/microsoft/terminal/blob/main/src/cascadia/TerminalApp/TabHeaderControl.xaml"));
        // Each row's own fragment stays available: the join is a guess, not a verdict.
        assertEquals("https://github.com/microsoft/terminal/blob/main/src/cascadia/Ter", found.get(0));
    }

    @Test
    public void aUrlThatStopsShortOfTheBorderIsLeftAlone() {
        String text = "\u2502 see https://example.com/a       \u2502\n"
            + "\u2502 Next sentence here              \u2502";
        assertEquals(1, urls(text).size());
        assertEquals("https://example.com/a", urls(text).get(0));
    }

    @Test
    public void aScrollbarColumnCountsAsABorderToo() {
        String text = "curl https://example.com/very/long/pa\u2590\n"
            + "th/file.tar.gz -o out                \u2590";
        assertTrue(urls(text).contains("https://example.com/very/long/path/file.tar.gz"));
    }

    @Test
    public void plainTextIsUntouched() {
        String text = "one https://example.com/a\ntwo https://example.com/b";
        assertEquals(text, TermuxUrlUtils.joinLinesCutAtBorder(text));
        assertEquals(2, urls(text).size());
    }

    @Test
    public void aCutThatSpansThreeRowsChains() {
        String text = "\u2502https://example.com/aaaa\u2502\n\u2502bbbb/cccc\u2502\n\u2502dddd rest\u2502";
        assertTrue(urls(text).contains("https://example.com/aaaabbbb/ccccdddd"));
    }
}
