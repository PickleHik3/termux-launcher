package com.termux.terminal;

/**
 * OSC 8 semantic hyperlinks.
 *
 * @see <a href="https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3cb5feda">the specification</a>
 */
public class HyperlinkTest extends TerminalTestCase {

    private static final String URI = "https://example.com/a";

    private String open(String params, String uri) {
        return "\033]8;" + params + ";" + uri + "\033\\";
    }

    private String close() {
        return open("", "");
    }

    public void testLinkedCellsCarryTheUri() {
        withTerminalSized(6, 2).enterString(open("", URI) + "AB" + close() + "C");
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 1));
        assertNull(mTerminal.getHyperlinkUriAt(0, 2));
        assertEquals("AB", mTerminal.getScreen().getSelectedText(0, 0, 1, 0));
    }

    public void testBellTerminatorIsAccepted() {
        withTerminalSized(4, 2).enterString("\033]8;;" + URI + "\007A");
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 0));
    }

    public void testTheLinkStaysOpenUntilClosed() {
        withTerminalSized(4, 3).enterString(open("", URI) + "A\r\nB");
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals(URI, mTerminal.getHyperlinkUriAt(1, 0));
    }

    /** Two runs sharing an id are one pool entry, which is what makes long links cheap. */
    public void testSameIdAndUriIsInternedOnce() {
        withTerminalSized(6, 2).enterString(open("id=x", URI) + "A" + close() + "B" + open("id=x", URI) + "C");
        assertEquals(1, mTerminal.getHyperlinks().size());
        assertEquals(mTerminal.getScreen().getHyperlinkIdAt(0, 0), mTerminal.getScreen().getHyperlinkIdAt(0, 2));
    }

    public void testDifferentUrisAreDistinctLinks() {
        withTerminalSized(6, 2).enterString(open("", URI) + "A" + open("", "https://example.com/b") + "B");
        assertEquals(2, mTerminal.getHyperlinks().size());
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals("https://example.com/b", mTerminal.getHyperlinkUriAt(0, 1));
    }

    public void testUnknownParametersAreIgnored() {
        withTerminalSized(4, 2).enterString(open("id=x:bogus=1", URI) + "A");
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 0));
    }

    /** A URI is required to be percent encoded, so a control character in one means it is not one. */
    public void testControlCharacterInUriIsRejected() {
        withTerminalSized(4, 2).enterString("\033]8;;https://example.com/\001x\033\\A");
        assertNull(mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals(0, mTerminal.getHyperlinks().size());
    }

    public void testMalformedPayloadWithoutSeparatorClosesTheLink() {
        withTerminalSized(4, 2).enterString(open("", URI) + "A\033]8;nonsense\033\\B");
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 0));
        assertNull(mTerminal.getHyperlinkUriAt(0, 1));
    }

    public void testOverlongUriIsRejected() {
        StringBuilder longUri = new StringBuilder("https://example.com/");
        while (longUri.length() <= TerminalHyperlinks.MAX_URI_LENGTH) longUri.append('a');
        withTerminalSized(4, 2).enterString(open("", longUri.toString()) + "A");
        assertNull(mTerminal.getHyperlinkUriAt(0, 0));
    }

    public void testLinkSurvivesReflow() {
        withTerminalSized(4, 4).enterString(open("", URI) + "ABCDEF" + close());
        resize(3, 4);
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals(URI, mTerminal.getHyperlinkUriAt(1, 0));
    }

    public void testLinkSurvivesScrollIntoHistory() {
        withTerminalSized(3, 2).enterString(open("", URI) + "A" + close() + "\r\nB\r\nC");
        assertEquals(URI, mTerminal.getHyperlinkUriAt(-1, 0));
    }

    public void testEraseDropsTheLink() {
        withTerminalSized(4, 2).enterString(open("", URI) + "AB" + close() + "\033[H\033[2J");
        assertNull(mTerminal.getHyperlinkUriAt(0, 0));
    }

    public void testOverwritingALinkedCellDropsItsLink() {
        withTerminalSized(4, 2).enterString(open("", URI) + "AB" + close() + "\033[HX");
        assertNull(mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals(URI, mTerminal.getHyperlinkUriAt(0, 1));
    }

    public void testResetClearsThePool() {
        withTerminalSized(4, 2).enterString(open("", URI) + "A");
        assertEquals(1, mTerminal.getHyperlinks().size());
        mTerminal.reset();
        assertEquals(0, mTerminal.getHyperlinks().size());
    }

    public void testOutOfRangeQueriesReturnNull() {
        withTerminalSized(4, 2).enterString(open("", URI) + "A");
        assertNull(mTerminal.getHyperlinkUriAt(0, -1));
        assertNull(mTerminal.getHyperlinkUriAt(0, 4));
        assertNull(mTerminal.getHyperlinkUriAt(2, 0));
        assertNull(mTerminal.getHyperlinkUriAt(-5, 0));
    }

    /** A full pool degrades to plain text rather than growing without bound. */
    public void testPoolIsBounded() {
        TerminalHyperlinks pool = new TerminalHyperlinks();
        for (int i = 0; i < TerminalHyperlinks.MAX_LINKS; i++) {
            assertTrue("link " + i, pool.intern("", "https://example.com/" + i) != TerminalHyperlinks.NO_LINK);
        }
        assertEquals(TerminalHyperlinks.NO_LINK, pool.intern("", "https://example.com/overflow"));
        // An already interned link still resolves after the pool is full.
        assertEquals("https://example.com/0", pool.getUri(pool.intern("", "https://example.com/0")));
    }

    public void testEmptyUriIsNotInterned() {
        TerminalHyperlinks pool = new TerminalHyperlinks();
        assertEquals(TerminalHyperlinks.NO_LINK, pool.intern("", ""));
        assertEquals(TerminalHyperlinks.NO_LINK, pool.intern("id=x", null));
        assertNull(pool.getUri(TerminalHyperlinks.NO_LINK));
        assertNull(pool.getUri(7));
    }
}
