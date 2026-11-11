package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Clipboard reads on the real libghostty-vt: OSC 52 {@code ?} and OSC 5522 reads are held until the
 * app answers, and the answer is the bytes the library writes. Runs only in builds made with
 * {@code -Pghostty=true}; elsewhere every test returns at once.
 */
public class GhosttyClipboardReadTest extends TerminalTestCase {

    private boolean ghostty() {
        if (System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) == null) return false;
        withTerminalSized(20, 5);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), mTerminal.useGhosttyCore());
        mOutput.captureClipboardReads = true;
        return true;
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public void testOsc52ReadWithStIsHeldUntilAllowed() {
        if (!ghostty()) return;
        enterString("\033]52;c;?\033\\");
        assertEquals("", mOutput.getOutputAndClear());
        assertEquals(1, mOutput.clipboardReadRequests.size());
        ClipboardReadRequest request = mOutput.clipboardReadRequests.get(0);
        assertEquals("52;c;?", request.getRaw());
        assertFalse(request.isListingOnly());
        assertFalse(request.isAnswered());
        request.allow("hi");
        assertTrue(request.isAnswered());
        assertEquals("\033]52;c;aGk=\033\\", mOutput.getOutputAndClear());
        request.allow("again");
        request.deny();
        assertEquals("", mOutput.getOutputAndClear());
    }

    public void testOsc52ReadWithBelAllowAndDeny() {
        if (!ghostty()) return;
        enterString("\033]52;c;?\007");
        assertEquals("", mOutput.getOutputAndClear());
        mOutput.clipboardReadRequests.get(0).allow("hi");
        assertEquals("\033]52;c;aGk=\007", mOutput.getOutputAndClear());

        enterString("\033]52;c;?\033\\");
        mOutput.clipboardReadRequests.get(1).deny();
        // What the library writes for a denied read: an empty clipboard.
        assertEquals("\033]52;c;\033\\", mOutput.getOutputAndClear());
    }

    public void testOsc5522ReadAllowAndDeny() {
        if (!ghostty()) return;
        enterString("\033]5522;type=read;" + b64("text/plain") + "\033\\");
        assertEquals("", mOutput.getOutputAndClear());
        assertEquals(1, mOutput.clipboardReadRequests.size());
        ClipboardReadRequest request = mOutput.clipboardReadRequests.get(0);
        assertTrue(request.isKittyProtocol());
        assertFalse(request.isListingOnly());
        assertFalse(request.isPasted());
        request.allow("hi");
        assertEquals("\033]5522;type=read:status=OK\033\\"
            + "\033]5522;type=read:status=DATA:mime=" + b64("text/plain") + ";" + b64("hi") + "\033\\"
            + "\033]5522;type=read:status=DONE\033\\", mOutput.getOutputAndClear());

        enterString("\033]5522;type=read;" + b64("text/plain") + "\033\\");
        mOutput.clipboardReadRequests.get(1).deny();
        assertEquals("\033]5522;type=read:status=EPERM\033\\", mOutput.getOutputAndClear());
    }

    public void testListingOnly5522ReadIsFlagged() {
        if (!ghostty()) return;
        enterString("\033]5522;type=read;" + b64(".") + "\033\\");
        assertEquals(1, mOutput.clipboardReadRequests.size());
        assertTrue(mOutput.clipboardReadRequests.get(0).isListingOnly());
        assertEquals("", mOutput.getOutputAndClear());
    }

    public void testDa1AdvertisesClipboardAndMode5522IsSettable() {
        if (!ghostty()) return;
        enterString("\033[c");
        String da1 = mOutput.getOutputAndClear();
        assertTrue(da1, da1.equals("\033[?62;22;52c") || da1.equals("\033[?62;4;22;52c"));
        enterString("\033[?5522$p");
        assertEquals("\033[?5522;2$y", mOutput.getOutputAndClear());
    }

    public void testPasteEventPasswordMarksTheFollowUpReadAsPasted() {
        if (!ghostty()) return;
        mTerminal.sendPasteEvent(Arrays.asList("text/plain"));
        String out = mOutput.getOutputAndClear();
        String first = out.split("\033\\\\")[0];
        String pw = first.substring(first.indexOf("pw=") + 3);
        String name = b64("Paste event");

        // The password without a name is an ordinary read and keeps the password.
        enterString("\033]5522;type=read:pw=" + pw + ";" + b64("text/plain") + "\033\\");
        enterString("\033]5522;type=read:pw=" + pw + ":name=" + name + ";" + b64("text/plain") + "\033\\");
        // Used up.
        enterString("\033]5522;type=read:pw=" + pw + ":name=" + name + ";" + b64("text/plain") + "\033\\");
        assertEquals(3, mOutput.clipboardReadRequests.size());
        assertFalse(mOutput.clipboardReadRequests.get(0).isPasted());
        assertTrue(mOutput.clipboardReadRequests.get(1).isPasted());
        assertFalse(mOutput.clipboardReadRequests.get(2).isPasted());
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    // A read request over the sniffer's 16 KiB cap is refused in the protocol's own form, with the
    // request's terminator, and never reaches Java.
    // A whitespace-separated MIME list of about {@code bytes} bytes, base64 as the protocol wants.
    private static String mimeListB64(int bytes) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < bytes) sb.append("text/plain ");
        return b64(sb.toString());
    }

    // OSC 52 read grammar is "52;<one selection letter>;?", so a read cannot be longer than the
    // cap; a long selection string is not a read to the library (it drops the sequence with no
    // callback and no reply). Pinned: nothing reaches Java.
    public void testOsc52WithAnOver16KibSelectionIsNotAReadAndReachesNobody() {
        if (!ghostty()) return;
        enterString("\033]52;" + repeat('c', 20000) + ";?\033\\");
        enterString("\033]52;" + repeat('c', 20000) + ";?\007");
        assertEquals(0, mOutput.clipboardReadRequests.size());
    }

    public void testOsc5522ReadWithAMimeListUnderTheCapReachesJava() {
        if (!ghostty()) return;
        enterString("\033]5522;type=read;" + mimeListB64(3000) + "\033\\");
        assertEquals(1, mOutput.clipboardReadRequests.size());
        assertEquals("", mOutput.getOutputAndClear());
    }

    public void testOsc5522ReadWithAMimeListOver16KibGetsEpermWithSt() {
        if (!ghostty()) return;
        enterString("\033]5522;type=read;" + mimeListB64(20000) + "\033\\");
        assertEquals(0, mOutput.clipboardReadRequests.size());
        assertEquals("\033]5522;type=read:status=EPERM\033\\", mOutput.getOutputAndClear());
    }

    public void testOsc5522ReadWithAMimeListOver16KibGetsEpermWithBel() {
        if (!ghostty()) return;
        enterString("\033]5522;type=read;" + mimeListB64(20000) + "\007");
        assertEquals(0, mOutput.clipboardReadRequests.size());
        assertEquals("\033]5522;type=read:status=EPERM\007", mOutput.getOutputAndClear());
    }

    public void testOsc5522ReadWithAnIdOver16KibKeepsTheIdInTheEperm() {
        if (!ghostty()) return;
        enterString("\033]5522;type=read:id=abc;" + mimeListB64(20000) + "\033\\");
        assertEquals(0, mOutput.clipboardReadRequests.size());
        assertEquals("\033]5522;type=read:status=EPERM:id=abc\033\\", mOutput.getOutputAndClear());
    }

    public void testOtherRepliesKeepTheirOrderAndAreNotHeldBack() {
        if (!ghostty()) return;
        enterString("\033[5n\033]52;c;?\033\\\033[6n");
        // DSR 5 and DSR 6 answer at once, in order; the read waits.
        assertEquals("\033[0n\033[1;1R", mOutput.getOutputAndClear());
        assertEquals(1, mOutput.clipboardReadRequests.size());
        // A query after the read, in a later write, is answered while the read is still pending.
        enterString("\033[5n");
        assertEquals("\033[0n", mOutput.getOutputAndClear());
        assertFalse(mOutput.clipboardReadRequests.get(0).isAnswered());
    }
}
