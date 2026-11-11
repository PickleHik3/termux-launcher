package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OSC 7501 program status on the real libghostty-vt: the support query, what the library validates
 * and drops, and what reaches the record store and the session. Row numbers are those of the
 * embedder checklist. Runs only in builds made with {@code -Pghostty=true}; elsewhere every test
 * returns at once.
 */
public class GhosttyProgramStatusTest extends TerminalTestCase {

    private static final String REPLY_BODY =
        "7501;?:states=idle,working,done,blocked,error:kinds=permission,question,auth";

    private boolean ghostty() {
        if (System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) == null) return false;
        withTerminalSized(20, 5);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), mTerminal.useGhosttyCore());
        return true;
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String report(String body) {
        return "\033]7501;" + body + "\033\\";
    }

    private ProgramStatusStore store() {
        return mTerminal.getProgramStatus();
    }

    public void testRows1To4QueryReplyWithSt() {
        if (!ghostty()) return;
        enterString("\033]7501;?\033\\");
        assertEquals("\033]" + REPLY_BODY + "\033\\", mOutput.getOutputAndClear());
        assertEquals(0, store().size());
    }

    public void testRow3QueryReplyWithBelUsesBel() {
        if (!ghostty()) return;
        enterString("\033]7501;?\007");
        assertEquals("\033]" + REPLY_BODY + "\007", mOutput.getOutputAndClear());
    }

    public void testRow5ReportsProduceNoOutput() {
        if (!ghostty()) return;
        enterString(report("state=blocked:kind=permission:id=a:title=" + b64("secret") + ":msg=" + b64("hidden")));
        enterString(report("state=working:progress=5"));
        enterString(report("state=clear"));
        assertEquals("", mOutput.getOutputAndClear());
    }

    public void testRows6And7PairsAndWhitespace() {
        if (!ghostty()) return;
        enterString(report(" state = working : id = job : progress = 42 "));
        ProgramStatus r = store().get("job");
        assertNotNull(r);
        assertEquals(ProgramStatus.State.WORKING, r.state);
        assertEquals(42, r.progress);
    }

    public void testRows8And9MalformedPairSkippedUnknownKeyIgnored() {
        if (!ghostty()) return;
        enterString(report("state=working:bogus:novalue=:unknownkey=zzz:id=a"));
        ProgramStatus r = store().get("a");
        assertNotNull(r);
        assertEquals(ProgramStatus.State.WORKING, r.state);
    }

    public void testRow10LastValueWins() {
        if (!ghostty()) return;
        enterString(report("state=working:state=done:id=a:id=b"));
        assertNull(store().get("a"));
        assertEquals(ProgramStatus.State.DONE, store().get("b").state);
    }

    public void testRow11MissingOrUnknownStateIsDiscarded() {
        if (!ghostty()) return;
        enterString(report("id=a:progress=5"));
        enterString(report("state=bogus:id=b"));
        enterString(report("state=WORKING:id=c"));
        enterString(report("state=:id=d"));
        assertEquals(0, store().size());
        assertEquals(0, mOutput.programStatusChanges.size());
    }

    public void testRows12And14BadBase64IsDiscarded() {
        if (!ghostty()) return;
        // A value with a byte outside the value charset is a skipped pair, not a discard: the report stands.
        enterString(report("state=working:id=skipped:title=!!!notbase64"));
        assertNotNull(store().get("skipped"));
        assertEquals("", store().get("skipped").title);
        store().clearAll();
        enterString(report("state=working:id=a:title=abcde"));
        enterString(report("state=working:id=b:msg=" + b64("fine") + ":title=a-b_"));
        assertEquals(0, store().size());
    }

    public void testRow13ControlCharacterInTitleIsDiscarded() {
        if (!ghostty()) return;
        enterString(report("state=working:id=a:title=" + b64("bad\u0007title")));
        enterString(report("state=working:id=b:msg=" + b64("bad\u009Bmsg")));
        enterString(report("state=working:id=c:title=" + b64("bad\u007Ftitle")));
        assertEquals(0, store().size());
    }

    public void testRow14PaddingIsOptional() {
        if (!ghostty()) return;
        enterString(report("state=done:id=a:title=" + b64("ab") + ":msg=" + b64("abc").replace("=", "")));
        ProgramStatus r = store().get("a");
        assertNotNull(r);
        assertEquals("ab", r.title);
        assertEquals("abc", r.message);
    }

    public void testRow16BadIdIsDiscardedNotRoot() {
        if (!ghostty()) return;
        enterString(report("state=working:id=bad id!"));
        enterString(report("state=working:id=a//b"));
        enterString(report("state=working:id="));
        assertEquals(0, store().size());
    }

    public void testRow16EmptyIdDoesNotClearEverything() {
        if (!ghostty()) return;
        enterString(report("state=working:id=a"));
        enterString(report("state=clear:id="));
        assertEquals(1, store().size());
    }

    public void testRow22KindOnlyForBlocked() {
        if (!ghostty()) return;
        enterString(report("state=blocked:kind=question:id=a"));
        enterString(report("state=working:kind=question:id=b"));
        enterString(report("state=blocked:kind=bogus:id=c"));
        assertEquals(ProgramStatus.Kind.QUESTION, store().get("a").kind);
        assertEquals(ProgramStatus.Kind.NONE, store().get("b").kind);
        assertEquals(ProgramStatus.Kind.NONE, store().get("c").kind);
        enterString(report("state=blocked:kind=permission:id=d"));
        enterString(report("state=blocked:kind=auth:id=e"));
        assertEquals(ProgramStatus.Kind.PERMISSION, store().get("d").kind);
        assertEquals(ProgramStatus.Kind.AUTH, store().get("e").kind);
    }

    public void testRow23ProgressOnlyForWorkingAndBlocked() {
        if (!ghostty()) return;
        enterString(report("state=working:progress=40:id=a"));
        enterString(report("state=blocked:progress=7:id=b"));
        enterString(report("state=done:progress=40:id=c"));
        enterString(report("state=idle:progress=40:id=d"));
        enterString(report("state=error:progress=40:id=e"));
        enterString(report("state=working:id=f"));
        enterString(report("state=working:progress=101:id=g"));
        enterString(report("state=working:progress=-1:id=h"));
        assertEquals(40, store().get("a").progress);
        assertEquals(7, store().get("b").progress);
        assertEquals(-1, store().get("c").progress);
        assertEquals(-1, store().get("d").progress);
        assertEquals(-1, store().get("e").progress);
        assertEquals(-1, store().get("f").progress);
        assertEquals(-1, store().get("g").progress);
        assertEquals(-1, store().get("h").progress);
    }

    public void testRows17To19And21ThroughTheLibrary() {
        if (!ghostty()) return;
        enterString(report("state=working:id=build:app=make"));
        enterString(report("state=blocked:id=build/x"));
        enterString(report("state=working:id=build2"));
        assertEquals("make", store().resolvedApp(store().get("build/x")));
        enterString(report("state=clear:id=build"));
        assertNull(store().get("build"));
        assertNull(store().get("build/x"));
        assertNotNull(store().get("build2"));
    }

    public void testRow20ClearWithoutIdThroughTheLibrary() {
        if (!ghostty()) return;
        enterString(report("state=working"));
        enterString(report("state=done:id=a"));
        enterString(report("state=clear"));
        assertEquals(0, store().size());
    }

    public void testRow25AlternateScreenKeepsRecords() {
        if (!ghostty()) return;
        enterString(report("state=working:id=a"));
        enterString("\033[?1049h");
        assertEquals(1, store().size());
        enterString(report("state=done:id=b"));
        enterString("\033[?1049l");
        assertEquals(2, store().size());
    }

    public void testRow26FullResetClearsAll() {
        if (!ghostty()) return;
        enterString(report("state=working:id=a"));
        enterString(report("state=done"));
        assertEquals(2, store().size());
        enterString("\033c");
        assertEquals(0, store().size());
    }

    public void testRow27SoftResetKeepsRecords() {
        if (!ghostty()) return;
        enterString(report("state=working:id=a"));
        enterString("\033[!p");
        assertEquals(1, store().size());
    }

    public void testRow29PromptStartThroughTheLibraryDropsWorkingAndBlocked() {
        if (!ghostty()) return;
        enterString(report("state=working:id=w"));
        enterString(report("state=blocked:id=b"));
        enterString(report("state=done:id=d"));
        enterString(report("state=error:id=e"));
        enterString(report("state=idle:id=i"));
        mOutput.programStatusChanges.clear();
        enterString("\033]133;A\007");
        assertNull(store().get("w"));
        assertNull(store().get("b"));
        assertEquals(3, store().size());
        assertEquals("one signal for the prompt, nothing raised", 1, mOutput.programStatusChanges.size());
        assertTrue(mOutput.programStatusChanges.get(0).isEmpty());
        enterString("\033]133;A\007");
        assertEquals("a prompt that changes nothing signals nothing", 1, mOutput.programStatusChanges.size());
    }

    public void testRow39StringsSurviveAfterTheWriteReturns() {
        if (!ghostty()) return;
        String title = "Tést 😀 中";
        String message = "Méssage Ж 😀";
        enterString(report("state=blocked:kind=auth:id=deep/er:app=my.app:progress=9:title=" + b64(title)
            + ":msg=" + b64(message)));
        for (int i = 0; i < 50; i++) enterString("churn " + i + "\r\n" + report("state=idle:id=n" + i));
        ProgramStatus r = store().get("deep/er");
        assertEquals("deep/er", r.id);
        assertEquals("my.app", r.app);
        assertEquals(title, r.title);
        assertEquals(message, r.message);
        assertEquals(9, r.progress);
        assertEquals(ProgramStatus.Kind.AUTH, r.kind);
    }

    public void testEmptyTitleAndMessageAreEmptyStrings() {
        if (!ghostty()) return;
        enterString(report("state=idle:id=a:title=:msg="));
        ProgramStatus r = store().get("a");
        assertEquals("", r.title);
        assertEquals("", r.message);
    }

    public void testInvisibleFormattingIsStrippedFromDecodedText() {
        if (!ghostty()) return;
        enterString(report("state=done:id=a:title=" + b64("ad‮min​") + ":msg=" + b64("﻿ok")));
        assertEquals("admin", store().get("a").title);
        assertEquals("ok", store().get("a").message);
    }

    public void testOneSignalPerWriteTurnWithRaisedRecords() {
        if (!ghostty()) return;
        enterString(report("state=working:id=a")
            + report("state=blocked:kind=permission:id=b")
            + report("state=done:id=c")
            + report("state=error:id=c")
            + report("state=blocked:id=e")
            + report("state=idle:id=e")
            + report("state=idle:id=f"));
        assertEquals("one call for seven reports", 1, mOutput.programStatusChanges.size());
        List<ProgramStatus> raised = mOutput.programStatusChanges.get(0);
        Set<String> ids = new HashSet<>();
        for (ProgramStatus r : raised) ids.add(r.id + ":" + r.state);
        assertEquals(2, raised.size());
        assertTrue(ids.toString(), ids.contains("b:BLOCKED"));
        assertTrue(ids.toString(), ids.contains("c:ERROR"));
    }

    public void testSignalOnlyWhenSomethingChanged() {
        if (!ghostty()) return;
        enterString("hello\r\n");
        enterString(report("state=working:id=a"));
        assertEquals(1, mOutput.programStatusChanges.size());
        assertTrue("only the picture changed", mOutput.programStatusChanges.get(0).isEmpty());
        enterString(report("state=clear:id=zzz"));
        assertEquals("a clear of nothing changes nothing", 1, mOutput.programStatusChanges.size());
        enterString(report("state=clear:id=a"));
        assertEquals(2, mOutput.programStatusChanges.size());
    }

    public void testRaisedRecordClearedInTheSameTurnIsNotRaised() {
        if (!ghostty()) return;
        enterString(report("state=blocked:id=a") + report("state=clear:id=a"));
        assertEquals(1, mOutput.programStatusChanges.size());
        assertTrue(mOutput.programStatusChanges.get(0).isEmpty());
    }

    public void testMarkSeenSignalsOnceAndDropsDoneAndError() {
        if (!ghostty()) return;
        enterString(report("state=done:id=a") + report("state=error:id=b") + report("state=working:id=c"));
        mOutput.programStatusChanges.clear();
        mTerminal.markProgramStatusSeen();
        assertEquals(1, mOutput.programStatusChanges.size());
        assertTrue(mOutput.programStatusChanges.get(0).isEmpty());
        assertEquals(1, store().size());
        mTerminal.markProgramStatusSeen();
        assertEquals(1, mOutput.programStatusChanges.size());
    }

    public void testRow28ProcessExitDropsWorkingAndBlocked() {
        if (!ghostty()) return;
        enterString(report("state=working:id=w") + report("state=blocked:id=b") + report("state=done:id=d"));
        mOutput.programStatusChanges.clear();
        mTerminal.onProgramStatusProcessExit();
        assertEquals(1, store().size());
        assertNotNull(store().get("d"));
        assertEquals(1, mOutput.programStatusChanges.size());
        assertTrue(mOutput.programStatusChanges.get(0).isEmpty());
    }
}
