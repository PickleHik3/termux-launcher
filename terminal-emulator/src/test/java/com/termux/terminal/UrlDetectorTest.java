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
        assertTrue("row longer than the screen: " + text, text.length() <= mTerminal.mColumns);
        enterString(text + "\r\n");
        return this;
    }

    /** Pad {@code text} with blanks to exactly {@code width} cells. */
    private static String pad(String text, int width) {
        assertTrue("text wider than its cell span: " + text, text.length() <= width);
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    /** A row of a two-pane screen: {@code left}, a divider at {@code dividerColumn}, then {@code right}. */
    private static String split(int dividerColumn, String left, String right) {
        return pad(left, dividerColumn) + "│" + right;
    }

    /** Divider-only rows, so a divider spans enough rows to count. */
    private UrlDetectorTest dividerRows(int dividerColumn, int count) {
        for (int i = 0; i < count; i++) row(split(dividerColumn, "", ""));
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

    /**
     * A bordered pane: dividers at both edges, the address filling the pane up to the right border,
     * and the tool's own indentation on the continuation. Rewritten from the padded variant: the
     * old test relied on a padding cell before the border, which is no longer guessed at.
     */
    public void testBorderedPaneWithIndentedContinuationIsJoined() {
        String url = "https://example.com/aaaa/bbbb/cccc/dddd/ffffffgg/hhhh.tar.gz";
        String first = "https://example.com/aaaa/bbbb/cccc/dddd/ffffff";
        assertEquals(46, first.length());
        row("│" + first + "│")
            .row("│" + pad("     gg/hhhh.tar.gz and more", 46) + "│");
        for (int i = 0; i < 7; i++) row("│" + pad("", 46) + "│");
        assertEquals(url, urlAt(2, 0));
        assertEquals(url, urlAt(7, 1));
        assertNull(urlAt(0, 1));
        assertNull(urlAt(30, 1));
    }

    /** A pane padded by a cell before its border no longer reaches the window edge, so it is not joined. */
    public void testPaddingBeforeTheBorderIsNotGuessedAt() {
        row("│" + pad(" https://example.com/aaaa/bbbb/cccc/dddd/ff ", 46) + "│")
            .row("│" + pad("     gg/hhhh.tar.gz", 46) + "│");
        for (int i = 0; i < 7; i++) row("│" + pad("", 46) + "│");
        assertEquals("https://example.com/aaaa/bbbb/cccc/dddd/ff", urlAt(4, 0));
        assertNull(urlAt(8, 1));
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

    /** fish marks output that ends without a newline with ⏎; it opened as example.xn--com-9r3a. */
    public void testASymbolAfterTheHostIsNotPartOfIt() {
        row("https://example.com⏎");
        assertEquals("https://example.com", urlAt(5, 0));
        row("https://example.com/path⏎");
        assertEquals("https://example.com/path", urlAt(5, 1));
    }

    public void testAnInternationalHostIsStillOneAddress() {
        row("https://bücher.example/ok");
        assertEquals("https://bücher.example/ok", urlAt(5, 0));
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

    /**
     * herdr with its sidebar open: a divider separates the sidebar from the pane, every row of the
     * pane carries the sidebar's text to its left, and the address is wrapped by the tool over more
     * rows than the detector's context window.
     */
    public void testWrappedAddressBesideASidebarIsJoined() {
        String url = "https://example.com/d/aaaaaaaaaa/bbbbbbbbbb/cccccccccc/dddddddddd/eeeeeeeeee"
            + "/ffffffffff/gggggggggg/hhhhhhhhhh/iiiiiiiiii/jjjjjjjjjj/kkkkkkkkkk/end";
        String[] sidebar = {"machines", "  mac", "  omencachy", "new \u00b7 omen", "  agents"};
        int paneColumn = 14;
        int rows = sidebarRows(sidebar, url, paneColumn);
        assertTrue("the address must outrun the context window", rows > 4);
        for (int row = 0; row < rows; row++)
            assertEquals("row " + row, url, urlAt(paneColumn + 2, row));
    }

    /**
     * Lay a pane's text out at {@code paneColumn}, with the sidebar's own text to its left and a
     * divider between them. Blank divider rows follow so the divider is long enough to count.
     */
    private int sidebarRows(String[] sidebar, String text, int paneColumn) {
        int width = COLUMNS - paneColumn;
        int rows = (text.length() + width - 1) / width;
        for (int i = 0; i < rows; i++) {
            String pane = text.substring(i * width, Math.min(text.length(), (i + 1) * width));
            row(paneColumn > 0 ? split(paneColumn - 1, i < sidebar.length ? sidebar[i] : "", pane) : pane);
        }
        if (paneColumn > 0) dividerRows(paneColumn - 1, 9 - rows);
        return rows;
    }

    /**
     * A sidebar's own text is not another pane's address continuing: it must not be glued on.
     * Rewritten from the blank-count version: the sidebar is now told apart by its divider.
     */
    public void testASidebarsTextIsNotGluedOntoAnAddress() {
        row(split(13, "machines", "https://example.com/d/aaaaaaaaaa/b"))
            .row(split(13, "  mac", "bbbbbbbbb/end"));
        dividerRows(13, 7);
        assertEquals("https://example.com/d/aaaaaaaaaa/bbbbbbbbbb/end", urlAt(16, 0));
        assertEquals("https://example.com/d/aaaaaaaaaa/bbbbbbbbbb/end", urlAt(16, 1));
        assertNull("the sidebar's own word is not part of the address", urlAt(3, 1));
    }

    /** A full-width pane, no sidebar: an address wrapped past the context window is still whole. */
    public void testAnAddressWrappedPastTheContextWindowIsWholeFromEveryRow() {
        String url = "https://example.com/d/aaaaaaaaaa/bbbbbbbbbb/cccccccccc/dddddddddd/eeeeeeeeee"
            + "/ffffffffff/gggggggggg/hhhhhhhhhh/iiiiiiiiii/jjjjjjjjjj/kkkkkkkkkk/llllllllll"
            + "/mmmmmmmmmm/nnnnnnnnnn/oooooooooo/pppppppppp/qqqqqqqqqq/rrrrrrrrrr/end";
        int rows = sidebarRows(new String[0], url, 0);
        assertTrue("the address must outrun the context window", rows > 4);
        for (int row = 0; row < rows; row++)
            assertEquals("row " + row, url, urlAt(2, row));
    }

    /**
     * A pane's text column wanders by a column or two between an address's first row and its
     * continuations. The continuation still starts at the window's left edge, so it is joined.
     */
    public void testAContinuationIndentedDifferentlyFromTheAddressIsStillJoined() {
        for (int offset = 0; offset <= 2; offset++) {
            withTerminalSized(COLUMNS, 12);
            String url = "https://example.com/2/aaaaaaaaaa/bbbbbbbbbb/cc/end-2";
            int paneColumn = 14;
            int width = COLUMNS - paneColumn - offset;
            int rows = (url.length() + width - 1) / width;
            for (int i = 0; i < rows; i++) {
                String indent = i == 0 ? pad("", offset) : "";
                String pane = url.substring(i * width, Math.min(url.length(), (i + 1) * width));
                row(split(paneColumn - 1, i == 0 ? "  omen" : "  dev", indent + pane));
            }
            dividerRows(paneColumn - 1, 9 - rows);
            assertEquals("offset " + offset + ", first row", url, urlAt(paneColumn + offset + 2, 0));
            assertEquals("offset " + offset + ", last row", url, urlAt(paneColumn + 2, rows - 1));
        }
    }

    /**
     * The renderer underlines what {@code find} returns for the rows in view. An address whose
     * first row has scrolled above that window must still be found from the rows still on screen,
     * or it loses its underline until the user scrolls back to its start.
     */
    public void testAnAddressStartingAboveTheVisibleWindowIsStillFound() {
        String url = "https://example.com/3/aaaaaaaaaa/bbbbbbbbbb/cccccccccc/dddddddd/eeeeeeeeee"
            + "/ffffffffff/gggggggggg/hhhhhhhhhh/iiiiiiiiii/jjjjjjjjjj/kkkkkkkkkk/end-3";
        int paneColumn = 12;
        int rows = sidebarRows(new String[] {"  omen", "  dev", "  dev", "  dev", "  dev"}, url, paneColumn);
        assertTrue("the address must outrun the context window", rows > 4);
        TerminalBuffer screen = mTerminal.getScreen();
        for (int topRow = 0; topRow < rows; topRow++) {
            List<UrlDetector.UrlSpan> spans = UrlDetector.find(screen, topRow, mTerminal.mRows - 1);
            assertEquals("visible from row " + topRow, 1, spans.size());
            assertEquals("visible from row " + topRow, url, spans.get(0).url);
        }
        assertTrue("nothing to find once the address is wholly above the window",
            UrlDetector.find(screen, rows, mTerminal.mRows - 1).isEmpty());
    }

    /** One herdr row on a 110-column screen: sidebar, divider at 29, pane at 30..69, divider at 70, pane at 71..109. */
    private static String herdrRow(String sidebar, String pane1, String pane2) {
        return pad(sidebar, 29) + "\u2502" + pad(pane1, 40) + "\u2502" + pad(pane2, 39);
    }

    /** Real herdr 0.9.1 output: both panes' addresses are whole, and nothing from beside them is glued on. */
    public void testHerdrPanesAreJoinedEachWithinItsOwnWindow() {
        withTerminalSized(110, 12);
        String url1 = "https://github.com/herdrdev/herdr/blob/main/src/protocol/render_ansi.rs#L120";
        String url2 = "https://example.com/second/pane/long/path/goes/here/ok";
        // Row 0 is the top bar: its pane divider at column 70 is absent.
        row(pad(" spaces", 29) + "\u2502" + pad("   1      +", 80));
        row(herdrRow("", "https://github.com/herdrdev/herdr/blob/m", "https://example.com/second/pane/long/pa"));
        row(herdrRow(" \u00b7 termux-launcher", "ain/src/protocol/render_ansi.rs#L120", "th/goes/here/ok"));
        row(herdrRow("   dev up12", "[amalvajdan@amal-build termux-launcher]$", "[amalvajdan@amal-build termux-launcher]"));
        row(herdrRow("", "", "$"));
        for (int i = 0; i < 6; i++) row(herdrRow("", "", ""));
        enterString(herdrRow("", "", ""));

        for (int row = 1; row <= 2; row++) {
            assertEquals("pane 1, row " + row, url1, urlAt(35, row));
            assertEquals("pane 2, row " + row, url2, urlAt(75, row));
        }
        assertNull(urlAt(5, 2));
        assertNull(urlAt(20, 2));
        assertNull(urlAt(90, 2));
        List<String> found = urls();
        assertEquals(2, found.size());
        assertEquals(url1, found.get(0));
        assertEquals(url2, found.get(1));
        UrlDetector.UrlSpan span = UrlDetector.at(mTerminal.getScreen(), 35, 2);
        assertEquals(2, span.segmentCount());
        assertEquals(1, span.segmentRow(0));
        assertEquals(30, span.segmentStartColumn(0));
        assertEquals(70, span.segmentEndColumn(0));
        assertEquals(30, span.segmentStartColumn(1));
        assertEquals(30 + "ain/src/protocol/render_ansi.rs#L120".length(), span.segmentEndColumn(1));
    }

    /** One pane next to a sidebar whose row beside the continuation has text right up to the divider. */
    public void testSidebarTextBesideTheContinuationIsNotGluedOn() {
        String url = "https://example.com/aaaaaaaaaa/bbbbbbbbbb/end";
        row(split(14, "", url.substring(0, 33)))
            .row(split(14, "sidebar-words!", url.substring(33)));
        dividerRows(14, 7);
        assertEquals(url, urlAt(20, 0));
        assertEquals(url, urlAt(20, 1));
        assertNull(urlAt(3, 1));
        assertNull(urlAt(13, 1));
        assertEquals(1, urls().size());
    }

    /** A neighbour pane to the right: the address ends at its own window, not at the end of the line text. */
    public void testNeighbourPaneToTheRightIsNotGluedOn() {
        row(split(23, "https://example.com/abc", "$ ls -la"))
            .row(split(23, "def/ghi", "more words"));
        dividerRows(23, 7);
        String url = "https://example.com/abcdef/ghi";
        assertEquals(url, urlAt(5, 0));
        assertEquals(url, urlAt(2, 1));
        assertNull(urlAt(30, 1));
        assertEquals(1, urls().size());
    }

    /** A status row without the divider is one full-width window, so it does not join the split rows below. */
    public void testTopBarRowWithoutTheDividerIsNotJoinedToTheSplitRowBelow() {
        StringBuilder top = new StringBuilder("https://example.com/");
        while (top.length() < COLUMNS) top.append('a');
        row(top.toString())
            .row(split(23, "tail/of/url", "next pane"));
        dividerRows(23, 8);
        assertEquals(top.toString(), urlAt(5, 0));
        assertNull(urlAt(2, 1));
        assertEquals(1, urls().size());
    }

    /** Seven rows are too few to be a divider: the bar is blanked, the row is one window, nothing joins. */
    public void testADividerRunShorterThanTheThresholdIsNotADivider() {
        row(split(23, "https://example.com/abc", "x"))
            .row(split(23, "def/ghi", "y"));
        dividerRows(23, 5);
        assertEquals("https://example.com/abc", urlAt(5, 0));
        assertNull(urlAt(2, 1));
    }

    /** Claude Code's hanging indent inside a pane window: the continuation sits under the address. */
    public void testHangingIndentInsideAWindowIsJoined() {
        row(split(14, "agents", "  \u23BF  https://example.com/aaaa/bbb"))
            .row(split(14, "dev", "     bbbb/end ok"));
        dividerRows(14, 7);
        String url = "https://example.com/aaaa/bbbbbbb/end";
        assertEquals(url, urlAt(25, 0));
        assertEquals(url, urlAt(22, 1));
        assertNull(urlAt(16, 1));
        assertNull(urlAt(29, 1));
    }

    /**
     * A short address that stops short of its window's edge is complete: the row below is not
     * appended. Shortened from 24 cells when hand-wrapped rows began to be followed from that length.
     */
    public void testAShortAddressShortOfTheWindowEdgeIsNotJoined() {
        row(split(14, "", "https://example.com/aa"))
            .row(split(14, "", "bbbb/end"));
        dividerRows(14, 7);
        assertEquals("https://example.com/aa", urlAt(20, 0));
        assertNull(urlAt(16, 1));
    }

    private static final String BOX_HEAD = "http://amal-build.taild9c5dc.ts.net:4387/sess";
    private static final String BOX_TAIL = "ion/70faf933cfc4543e";

    /**
     * herdr drawing into a box with an inner margin on a wider screen: the address is cut several
     * columns short of the screen's edge and carried on under its own first column.
     */
    public void testAnAddressWrappedByHandInsideAPaddedBoxIsJoined() {
        withTerminalSized(80, 12);
        row("  Session ready:")
            .row("  " + BOX_HEAD)
            .row("  " + BOX_TAIL)
            .row("");
        String url = BOX_HEAD + BOX_TAIL;
        assertEquals(url, urlAt(4, 1));
        assertEquals(url, urlAt(3, 2));
        UrlDetector.UrlSpan span = UrlDetector.at(mTerminal.getScreen(), 3, 2);
        assertEquals(2, span.segmentCount());
        assertEquals(1, span.segmentRow(0));
        assertEquals(2, span.segmentStartColumn(0));
        assertEquals(2 + BOX_HEAD.length(), span.segmentEndColumn(0));
        assertEquals(2, span.segmentRow(1));
        assertEquals(2, span.segmentStartColumn(1));
        assertEquals(2 + BOX_TAIL.length(), span.segmentEndColumn(1));
        assertNull(urlAt(30, 2));
        assertEquals(1, urls().size());
    }

    /** The same box with its right border drawn: as a stray glyph on two rows, and as a divider. */
    public void testAHandWrappedAddressBesideTheBoxsRightBorderIsJoined() {
        for (int boxRows : new int[] {2, 9}) {
            withTerminalSized(80, 12);
            row(pad("  " + BOX_HEAD, 50) + "│")
                .row(pad("  " + BOX_TAIL, 50) + "│");
            for (int i = 2; i < boxRows; i++) row(pad("", 50) + "│");
            assertEquals(boxRows + " rows", BOX_HEAD + BOX_TAIL, urlAt(4, 0));
            assertEquals(boxRows + " rows", BOX_HEAD + BOX_TAIL, urlAt(3, 1));
            assertEquals(boxRows + " rows", 1, urls().size());
        }
    }

    public void testAShortAddressEndingASentenceIsNotJoinedToAnIndentedWord() {
        row("see https://example.com/z")
            .row("    done");
        assertEquals("https://example.com/z", urlAt(6, 0));
        assertNull(urlAt(5, 1));
    }

    /** A row below that starts at another column is other text, not the address carried on. */
    public void testAHandWrappedTailAtADifferentMarginIsNotJoined() {
        withTerminalSized(80, 12);
        row("  " + BOX_HEAD)
            .row("    " + BOX_TAIL);
        assertEquals(BOX_HEAD, urlAt(4, 0));
        assertNull(urlAt(6, 1));
    }

    /** The length guard, both ways: one cell under the minimum stays apart, the minimum joins. */
    public void testTheHandWrapMinimumLengthDecidesTheJoin() {
        String atMinimum = "https://example.com/aaaa";
        assertEquals(UrlDetector.MIN_HAND_WRAPPED_CELLS, atMinimum.length());
        String under = "https://example.com/aaa";
        row(under).row("bb/end");
        assertEquals(under, urlAt(3, 0));
        assertNull(urlAt(2, 1));

        withTerminalSized(COLUMNS, 12);
        row(atMinimum).row("bb/end");
        assertEquals(atMinimum + "bb/end", urlAt(3, 0));
        assertEquals(atMinimum + "bb/end", urlAt(2, 1));
    }

    /** A word after the tail makes the row below its own text, such as the next line of a log. */
    public void testAHandWrappedTailFollowedByMoreTextIsNotJoined() {
        withTerminalSized(80, 12);
        row("  " + BOX_HEAD)
            .row("  " + BOX_TAIL + " ok");
        assertEquals(BOX_HEAD, urlAt(4, 0));
        assertNull(urlAt(4, 1));
    }

    /** A narrow box wraps one address over more rows than the context: it is whole from every row in view. */
    public void testAnAddressHandWrappedPastTheContextWindowIsWholeFromEveryRow() {
        String url = "https://example.com/4/aaaaaaaaaa/bbbbbbbbbb/cccccccccc/dddddddddd/eeeeeeeeee"
            + "/ffffffffff/gggggggggg/hhhhhhhhhh/iiiiiiiiii/jjjjjjjjjj/kkkkkkkkkk/end-4";
        int width = 30;
        int rows = (url.length() + width - 1) / width;
        assertTrue("the address must outrun the context window", rows > 4);
        for (int i = 0; i < rows; i++) row("  " + url.substring(i * width, Math.min(url.length(), (i + 1) * width)));
        TerminalBuffer screen = mTerminal.getScreen();
        for (int topRow = 0; topRow < rows; topRow++) {
            List<UrlDetector.UrlSpan> spans = UrlDetector.find(screen, topRow, mTerminal.mRows - 1);
            assertEquals("visible from row " + topRow, 1, spans.size());
            assertEquals("visible from row " + topRow, url, spans.get(0).url);
            assertEquals("tapped on row " + topRow, url, urlAt(4, topRow));
        }
    }

    /** One cell short of a divider is not the edge: that allowance is for the screen's right edge only. */
    public void testOneCellShortOfADividerIsNotTheEdge() {
        row(split(23, "https://example.com/ab", "x"))
            .row(split(23, "cd/ef", "y"));
        dividerRows(23, 7);
        assertEquals("https://example.com/ab", urlAt(5, 0));
        assertNull(urlAt(2, 1));
    }
}
