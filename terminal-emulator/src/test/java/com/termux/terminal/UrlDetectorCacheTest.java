package com.termux.terminal;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

/**
 * {@link UrlDetector.Cache}: the underlines are asked for every frame, and while a command streams
 * output every frame's text differs. The cache keeps each group of joined lines' answer and only
 * matches the groups whose text changed — and the answer must be exactly the uncached one.
 */
public class UrlDetectorCacheTest {

    private static final int COLUMNS = 40;
    private static final int ROWS = 10;

    private final TerminalEmulator mTerminal = new TerminalEmulator(new TerminalTestCase.MockTerminalOutput(),
        false, COLUMNS, ROWS, TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS,
        TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, 200, null);

    private final UrlDetector.Cache mCache = new UrlDetector.Cache();

    private void enter(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        mTerminal.append(bytes, bytes.length);
    }

    /** The visible screen through the cache, checked against a pass with no cache at all. */
    private List<UrlDetector.UrlSpan> visible() {
        TerminalBuffer screen = mTerminal.getScreen();
        List<UrlDetector.UrlSpan> cached = UrlDetector.find(screen, 0, ROWS - 1, mCache);
        List<UrlDetector.UrlSpan> fresh = UrlDetector.find(screen, 0, ROWS - 1);
        assertEquals(fresh.toString(), cached.toString());
        return cached;
    }

    private void streamLines(int from, int count) {
        for (int i = from; i < from + count; i++) enter("log " + i + " https://example.com/" + i + "\r\n");
    }

    @Test
    public void streamingANewLineAtTheBottomRescansOnlyThatLine() {
        streamLines(0, 20);
        visible();

        streamLines(20, 1);
        List<UrlDetector.UrlSpan> spans = visible();

        assertEquals(1, mCache.mScannedGroups);
        assertEquals(1, mCache.mScannedLines);
        assertEquals("https://example.com/20", spans.get(spans.size() - 1).url);
    }

    @Test
    public void anUnchangedScreenRescansNothing() {
        streamLines(0, 20);
        visible();
        visible();
        assertEquals(0, mCache.mScannedGroups);
        assertEquals(0, mCache.mScannedLines);
    }

    @Test
    public void aRowJoinedToTheOneAboveRescansItsWholeGroup() {
        streamLines(0, 20);
        // A program wrapping by hand: the first row runs to the edge, the second carries on.
        String url = "https://example.com/aaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbb/end";
        enter(url.substring(0, COLUMNS) + "\r\n" + url.substring(COLUMNS) + "\r\n");
        visible();

        // Change only the continuation row: both rows are matched again, as one group.
        enter("\033[2A\033[2K" + url.substring(0, COLUMNS) + "\r\n\033[2K" + url.substring(COLUMNS, url.length() - 3) + "xyz\r\n");
        List<UrlDetector.UrlSpan> spans = visible();

        assertEquals(1, mCache.mScannedGroups);
        assertEquals(2, mCache.mScannedLines);
        assertEquals(url.substring(0, url.length() - 3) + "xyz", spans.get(spans.size() - 1).url);
    }

    @Test
    public void aHandWrappedPairIsOneGroupKeptOnScrollAndRescannedWhenItsTailChanges() {
        for (int i = 0; i < 20; i++) enter("log " + i + "\r\n");
        // A box with an inner margin: the address stops short of the edge and carries on beneath.
        String head = "https://example.com/aaaa/bbbb/cccc";
        enter("  " + head + "\r\n  dddd/end\r\n");
        List<UrlDetector.UrlSpan> spans = visible();
        assertEquals(head + "dddd/end", spans.get(spans.size() - 1).url);

        // Scroll by one row: every group, the joined pair included, keeps its answer.
        enter("\r\n");
        spans = visible();
        assertEquals(0, mCache.mScannedGroups);
        UrlDetector.UrlSpan span = spans.get(spans.size() - 1);
        assertEquals(head + "dddd/end", span.url);
        assertEquals(2, span.segmentCount());
        assertEquals(ROWS - 4, span.segmentRow(0));
        assertEquals(ROWS - 3, span.segmentRow(1));

        // Change only the tail row: both rows are matched again, as one group.
        enter("\033[2A\033[2K  dddd/xyz\r\n\r\n");
        spans = visible();
        assertEquals(1, mCache.mScannedGroups);
        assertEquals(2, mCache.mScannedLines);
        assertEquals(head + "dddd/xyz", spans.get(spans.size() - 1).url);
    }

    @Test
    public void emulatorWrappedPanesAndSidebarsMatchTheUncachedAnswerAsTheyScroll() {
        String wrapped = "see https://github.com/microsoft/terminal/blob/main/src/cascadia/TerminalApp/Tab.xaml now";
        for (int i = 0; i < 30; i++) {
            if (i % 7 == 0) enter(wrapped + "\r\n");
            else if (i % 3 == 0) enter(pad("https://a.example/" + i, 14) + "│" + "https://b.example/" + i + "\r\n");
            else enter(pad("", 14) + "│" + "plain text " + i + "\r\n");
            visible();
        }
    }

    private static String pad(String text, int width) {
        StringBuilder sb = new StringBuilder(text.length() > width ? text.substring(0, width) : text);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }
}
