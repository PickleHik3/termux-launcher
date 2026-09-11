package com.termux.terminal;

import java.util.ArrayList;
import java.util.List;

/** {@link UrlDetector} on a 48-column screen: the phone's width, where wrapping is a daily sight. */
public class UrlDetectorTest extends TerminalTestCase {

    private static final int COLUMNS = 48;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        withTerminalSized(COLUMNS, 12);
    }

    /** Type one screen row and move to the next without the emulator wrapping anything. */
    private UrlDetectorTest row(String text) {
        assertTrue("row longer than the screen: " + text, text.length() <= COLUMNS);
        enterString(text + "\r\n");
        return this;
    }

    private List<String> urls() {
        List<String> found = new ArrayList<>();
        TerminalBuffer screen = mTerminal.getScreen();
        for (UrlDetector.UrlSpan span : UrlDetector.find(screen, -screen.getActiveTranscriptRows(), mTerminal.mRows - 1))
            found.add(span.url);
        return found;
    }

    private String urlAt(int column, int row) {
        UrlDetector.UrlSpan span = UrlDetector.at(mTerminal.getScreen(), column, row);
        return span == null ? null : span.url;
    }

    public void testEmulatorWrappedUrlIsOneAddress() {
        String url = "https://github.com/microsoft/terminal/blob/main/src/cascadia/TerminalApp/Tab.xaml";
        enterString("see " + url + " now");
        assertEquals(url, urlAt(10, 0));
        assertEquals(url, urlAt(3, 1));
        UrlDetector.UrlSpan span = UrlDetector.at(mTerminal.getScreen(), 3, 1);
        assertEquals(2, span.segmentCount());
        assertEquals(4, span.segmentStartColumn(0));
        assertEquals(COLUMNS, span.segmentEndColumn(0));
        assertEquals(0, span.segmentStartColumn(1));
        assertEquals(url.length() - (COLUMNS - 4), span.segmentEndColumn(1));
        assertNull(urlAt(1, 0));
        assertNull(urlAt(span.segmentEndColumn(1) + 1, 1));
    }

    /** Claude Code inside a full-width herdr pane: the tool wraps at the edge and indents the rest. */
    public void testIndentedContinuationAfterAFullRowIsJoined() {
        row("  ⎿  https://github.com/microsoft/terminal/blob/")
            .row("     main/src/cascadia/TerminalApp/Tab.xaml ok");
        String url = "https://github.com/microsoft/terminal/blob/main/src/cascadia/TerminalApp/Tab.xaml";
        assertEquals(url, urlAt(10, 0));
        assertEquals(url, urlAt(6, 1));
        assertNull(urlAt(2, 1));
        assertNull(urlAt(44, 1));
        assertEquals(1, urls().size());
    }

    /** A TUI that keeps one cell of padding at the edge is still wrapping at its edge. */
    public void testContinuationAfterARowOneCellShortOfTheEdgeIsJoined() {
        row("⏺ https://example.com/downloads/release/v1.2/ar")
            .row("  m64/app.tar.gz");
        assertEquals(COLUMNS - 1, "⏺ https://example.com/downloads/release/v1.2/ar".length());
        assertEquals("https://example.com/downloads/release/v1.2/arm64/app.tar.gz", urlAt(5, 0));
        assertEquals("https://example.com/downloads/release/v1.2/arm64/app.tar.gz", urlAt(4, 1));
    }

    /** tmux or a desktop herdr split: border, one cell of padding, and the tool's own indentation. */
    public void testBorderedPaneWithIndentedContinuationIsJoined() {
        row("│ https://example.com/aaaa/bbbb/cccc/dddd/ff │")
            .row("│     gg/hhhh.tar.gz and more                │");
        assertEquals("https://example.com/aaaa/bbbb/cccc/dddd/ffgg/hhhh.tar.gz", urlAt(2, 0));
        assertEquals("https://example.com/aaaa/bbbb/cccc/dddd/ffgg/hhhh.tar.gz", urlAt(7, 1));
        assertNull(urlAt(0, 1));
    }

    public void testAnAddressThatStopsShortOfTheEdgeIsNotExtended() {
        row("see https://example.com/a")
            .row("  next line here");
        assertEquals("https://example.com/a", urlAt(6, 0));
        assertNull(urlAt(3, 1));
    }

    public void testTrailingPunctuationIsNotPartOfTheAddress() {
        row("(see https://example.com/x).")
            .row("https://en.wikipedia.org/wiki/Foo_(bar), yes")
            .row("\"https://example.com/q?a=1;\"");
        assertEquals("https://example.com/x", urlAt(8, 0));
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", urlAt(8, 1));
        assertEquals("https://example.com/q?a=1", urlAt(8, 2));
        assertNull(urlAt(26, 0));
    }

    public void testANewAddressOnTheNextRowIsNotGluedOn() {
        row("first https://example.com/aaaa/bbbb/cccc/dddd/ee")
            .row("https://other.example/x");
        assertEquals("https://example.com/aaaa/bbbb/cccc/dddd/ee", urlAt(10, 0));
        assertEquals("https://other.example/x", urlAt(3, 1));
        assertEquals(2, urls().size());
    }

    public void testABorderGlyphNextToAnAddressIsNotPartOfIt() {
        row("│https://example.com│");
        assertEquals("https://example.com", urlAt(5, 0));
        assertNull(urlAt(0, 0));
    }

    public void testFindReturnsOnlyAddressesTouchingTheRange() {
        row("https://one.example")
            .row("plain")
            .row("https://two.example");
        TerminalBuffer screen = mTerminal.getScreen();
        List<UrlDetector.UrlSpan> found = UrlDetector.find(screen, 2, 2);
        assertEquals(1, found.size());
        assertEquals("https://two.example", found.get(0).url);
        assertEquals(2, urls().size());
    }

    public void testAddressesInTheTranscriptAreFound() {
        row("https://scrolled.example/away");
        for (int i = 0; i < 14; i++) row("filler " + i);
        TerminalBuffer screen = mTerminal.getScreen();
        assertTrue(screen.getActiveTranscriptRows() > 0);
        List<String> found = urls();
        assertEquals(1, found.size());
        assertEquals("https://scrolled.example/away", found.get(0));
        assertEquals("https://scrolled.example/away", urlAt(3, -screen.getActiveTranscriptRows()));
    }
}
