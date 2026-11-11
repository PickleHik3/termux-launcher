package com.termux.terminal;

import junit.framework.TestCase;

/** The OSC 7501 record store, without a terminal. Row numbers are those of the embedder checklist. */
public class ProgramStatusStoreTest extends TestCase {

    private static final int IDLE = 0, WORKING = 1, DONE = 2, BLOCKED = 3, ERROR = 4, CLEAR = 5;
    private static final int NONE = 0, PERMISSION = 1;

    private final ProgramStatusStore mStore = new ProgramStatusStore();

    private boolean put(int state, String id) {
        return mStore.apply(state, NONE, -1, id, "", "", "");
    }

    private boolean put(int state, String id, String app) {
        return mStore.apply(state, NONE, -1, id, app, "", "");
    }

    public void testRow17ReportReplacesTheRecordWhole() {
        mStore.apply(BLOCKED, PERMISSION, 40, "job", "tool", "Title", "Message");
        mStore.apply(WORKING, NONE, -1, "job", "", "", "");
        assertEquals(1, mStore.size());
        ProgramStatus r = mStore.get("job");
        assertEquals(ProgramStatus.State.WORKING, r.state);
        assertEquals(ProgramStatus.Kind.NONE, r.kind);
        assertEquals(-1, r.progress);
        assertEquals("", r.app);
        assertEquals("", r.title);
        assertEquals("", r.message);
    }

    public void testRow17UnknownStateIsIgnored() {
        assertFalse(put(9, "x"));
        assertFalse(put(-1, "x"));
        assertEquals(0, mStore.size());
    }

    public void testRow18RootAndChildrenCoexist() {
        put(IDLE, "");
        put(BLOCKED, "child");
        assertEquals(2, mStore.size());
        assertEquals(ProgramStatus.State.IDLE, mStore.get("").state);
        assertEquals(ProgramStatus.State.BLOCKED, mStore.get("child").state);
    }

    public void testRow19ClearRemovesSubtreeBySegment() {
        put(WORKING, "build");
        put(WORKING, "build/x");
        put(WORKING, "build/x/y");
        put(WORKING, "build2");
        put(WORKING, "other");
        assertTrue(put(CLEAR, "build"));
        assertNull(mStore.get("build"));
        assertNull(mStore.get("build/x"));
        assertNull(mStore.get("build/x/y"));
        assertNotNull("build2 is a different segment", mStore.get("build2"));
        assertNotNull(mStore.get("other"));
        assertEquals(2, mStore.size());
    }

    public void testRow19ClearOfMissingIdChangesNothing() {
        put(WORKING, "a");
        assertFalse(put(CLEAR, "b"));
        assertEquals(1, mStore.size());
    }

    public void testRow20ClearWithoutIdRemovesEverything() {
        put(WORKING, "");
        put(DONE, "a");
        put(ERROR, "a/b");
        assertTrue(put(CLEAR, ""));
        assertEquals(0, mStore.size());
        assertFalse("nothing left to clear", put(CLEAR, ""));
    }

    public void testRow21AppInheritsFromNearestAncestor() {
        put(IDLE, "", "rootapp");
        put(IDLE, "a", "mid");
        put(IDLE, "a/b");
        put(IDLE, "a/b/c");
        assertEquals("mid", mStore.resolvedApp(mStore.get("a/b/c")));
        assertEquals("mid", mStore.resolvedApp(mStore.get("a/b")));
    }

    public void testRow21RootIsAncestorOfEveryId() {
        put(IDLE, "", "rootapp");
        put(IDLE, "x/y");
        assertEquals("rootapp", mStore.resolvedApp(mStore.get("x/y")));
    }

    public void testRow21OwnAppWins() {
        put(IDLE, "", "rootapp");
        put(IDLE, "x", "own");
        assertEquals("own", mStore.resolvedApp(mStore.get("x")));
    }

    public void testRow21NoAncestorWithAppGivesEmpty() {
        put(IDLE, "x/y");
        put(IDLE, "x");
        assertEquals("", mStore.resolvedApp(mStore.get("x/y")));
        assertEquals("", mStore.resolvedApp(null));
    }

    public void testRow21MissingParentRecordIsSkipped() {
        put(IDLE, "a", "top");
        put(IDLE, "a/b/c");
        assertEquals("top", mStore.resolvedApp(mStore.get("a/b/c")));
    }

    private void fillOneOfEach() {
        put(IDLE, "idle");
        put(WORKING, "working");
        put(BLOCKED, "blocked");
        put(DONE, "done");
        put(ERROR, "error");
    }

    public void testRow28And29ExitDropsWorkingAndBlocked() {
        fillOneOfEach();
        assertTrue(mStore.onProcessExit());
        assertNull(mStore.get("working"));
        assertNull(mStore.get("blocked"));
        assertNotNull(mStore.get("idle"));
        assertNotNull(mStore.get("done"));
        assertNotNull(mStore.get("error"));
        assertFalse("nothing more to drop", mStore.onProcessExit());
    }

    public void testRow29PromptDropsWorkingAndBlocked() {
        fillOneOfEach();
        assertTrue(mStore.onPromptStart());
        assertEquals(3, mStore.size());
        assertNull(mStore.get("working"));
        assertNull(mStore.get("blocked"));
        assertFalse(mStore.onPromptStart());
    }

    public void testRow30IdleIsKeptAtPromptAndExit() {
        put(IDLE, "idle");
        assertFalse(mStore.onPromptStart());
        assertFalse(mStore.onProcessExit());
        assertNotNull(mStore.get("idle"));
    }

    public void testRow31DoneAndErrorSurviveExitAndPromptUntilSeen() {
        put(DONE, "done");
        put(ERROR, "error");
        mStore.onPromptStart();
        mStore.onProcessExit();
        assertEquals(2, mStore.size());
        assertTrue(mStore.markSeen());
        assertEquals(0, mStore.size());
        assertFalse(mStore.markSeen());
    }

    public void testMarkSeenKeepsIdleWorkingBlocked() {
        fillOneOfEach();
        assertTrue(mStore.markSeen());
        assertEquals(3, mStore.size());
        assertNull(mStore.get("done"));
        assertNull(mStore.get("error"));
    }

    public void testRow32NothingInTheApiTakesTime() {
        // No timer, clock or TTL exists: records stay until a clear, a replacement, a lifetime event or the cap.
        put(WORKING, "w");
        put(BLOCKED, "b");
        assertEquals(2, mStore.size());
        for (java.lang.reflect.Method m : ProgramStatusStore.class.getDeclaredMethods()) {
            for (Class<?> p : m.getParameterTypes()) {
                if (p == long.class || p == java.time.Duration.class) fail("time parameter in " + m);
            }
        }
    }

    public void testRow33CapEvictsTheLeastRecentlyUpdated() {
        for (int i = 0; i < 257; i++) put(IDLE, "r" + i);
        assertEquals(256, mStore.size());
        assertNull("the first record was the oldest", mStore.get("r0"));
        assertNotNull(mStore.get("r1"));
        assertNotNull(mStore.get("r256"));
        assertTrue(ProgramStatusStore.MAX_RECORDS >= 64);
    }

    public void testRow33UpdateRefreshesAge() {
        for (int i = 0; i < 256; i++) put(IDLE, "r" + i);
        put(WORKING, "r0");
        put(IDLE, "extra");
        assertNotNull("r0 was updated, so r1 is now the oldest", mStore.get("r0"));
        assertNull(mStore.get("r1"));
        assertEquals(256, mStore.size());
    }

    public void testRow33ReplacingAtTheCapEvictsNothing() {
        for (int i = 0; i < 256; i++) put(IDLE, "r" + i);
        put(DONE, "r100");
        assertEquals(256, mStore.size());
        assertNotNull(mStore.get("r0"));
    }

    public void testRow33ClearsDoNotCountAsUpdates() {
        put(IDLE, "a");
        put(IDLE, "b");
        long before = mStore.get("b").updateSeq;
        put(CLEAR, "zzz");
        put(IDLE, "c");
        assertEquals(before + 1, mStore.get("c").updateSeq);
    }

    public void testRow35InvisibleFormattingIsStripped() {
        int[] cps = {0x061C, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
            0x2060, 0x2061, 0x2062, 0x2063, 0x2064, 0x2066, 0x2067, 0x2068, 0x2069, 0x206A, 0x206F,
            0xFEFF, 0xFFF9, 0xFFFA, 0xFFFB, 0x00AD, 0x2028, 0x2029, 0xE0041, 0xE007F};
        for (int cp : cps) {
            String bad = new String(Character.toChars(cp));
            mStore.apply(WORKING, NONE, -1, "x", "a" + bad + "b", "t" + bad + "i" + bad, bad + "m" + bad + "g");
            ProgramStatus r = mStore.get("x");
            assertEquals("app U+" + Integer.toHexString(cp), "ab", r.app);
            assertEquals("title U+" + Integer.toHexString(cp), "ti", r.title);
            assertEquals("message U+" + Integer.toHexString(cp), "mg", r.message);
        }
    }

    public void testRow35SurrogatePairsAndPlainTextSurvive() {
        String text = "ok 😀 é 中";
        mStore.apply(DONE, NONE, -1, "x", "", text, text + "‮");
        assertEquals(text, mStore.get("x").title);
        assertEquals(text, mStore.get("x").message);
    }

    public void testMostUrgentOrder() {
        assertNull(mStore.mostUrgent());
        put(IDLE, "idle");
        assertEquals("idle", mStore.mostUrgent().id);
        put(DONE, "done");
        assertEquals("done", mStore.mostUrgent().id);
        put(WORKING, "working");
        assertEquals("working", mStore.mostUrgent().id);
        put(ERROR, "error");
        assertEquals("error", mStore.mostUrgent().id);
        put(BLOCKED, "blocked");
        assertEquals("blocked", mStore.mostUrgent().id);
    }

    public void testMostUrgentTieGoesToTheLatestUpdate() {
        put(WORKING, "a");
        put(WORKING, "b");
        assertEquals("b", mStore.mostUrgent().id);
        put(WORKING, "a");
        assertEquals("a", mStore.mostUrgent().id);
    }

    public void testRecordsSnapshotIsUnmodifiable() {
        put(IDLE, "a");
        try {
            mStore.records().clear();
            fail();
        } catch (UnsupportedOperationException expected) {
        }
        assertEquals(1, mStore.records().size());
        mStore.clearAll();
        assertEquals(0, mStore.size());
    }
}
