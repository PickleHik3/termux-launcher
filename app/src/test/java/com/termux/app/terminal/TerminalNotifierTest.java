package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.KittyNotification;
import com.termux.terminal.ProgramStatus;
import com.termux.terminal.ProgramStatusStore;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TerminalNotifierTest {

    private static final int WORKING = 1;
    private static final int DONE = 2;
    private static final int BLOCKED = 3;
    private static final int ERROR = 4;

    private final Object paneA = new Object();
    private final Object paneB = new Object();
    /** A second pane in paneA's window. */
    private final Object paneA2 = new Object();

    private boolean visible;
    private Object front;
    private int pgid = 100;
    private String programName = "claude";
    /** When true, name lookups wait in {@link #pending} until {@link #answer()}. */
    private boolean deferred;
    private final List<TerminalNotifier.NameCallback> pending = new ArrayList<>();
    private boolean canPost = true;
    private final List<TerminalNoticeContent> posted = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();
    private final List<Integer> remaining = new ArrayList<>();
    private TerminalNoticeRecords records;
    private TerminalNotifier notifier;

    @Before
    public void setUp() {
        records = new TerminalNoticeRecords();
        notifier = new TerminalNotifier(new TerminalNotifier.Env() {
            @Override public boolean isLauncherVisible() { return visible; }
            @Nullable @Override public Object frontPane() { return front; }
            @NonNull @Override public String windowKey(@NonNull Object pane) {
                return pane == paneA || pane == paneA2 ? "wa" : "wb";
            }
            @Nullable @Override public String windowLabel(@NonNull Object pane) {
                return pane == paneA ? "[1] build" : "[2] logs";
            }
            @NonNull @Override public String paneHandle(@NonNull Object pane) {
                return pane == paneA ? "ha" : "hb";
            }
            @Override public int foregroundProcessGroup(@NonNull Object pane) { return pgid; }
            @Override public void resolveProgramName(int id, @NonNull TerminalNotifier.NameCallback done) {
                if (deferred) pending.add(done);
                else done.onName(programName);
            }
        }, new TerminalNotifier.Poster() {
            @Override public boolean canPost() { return canPost; }
            @Override public void post(@NonNull TerminalNoticeContent content) { posted.add(content); }
            @Override public void cancel(@NonNull String windowKey, int left) {
                cancelled.add(windowKey);
                remaining.add(left);
            }
        }, records);
    }

    private void answer() {
        List<TerminalNotifier.NameCallback> run = new ArrayList<>(pending);
        pending.clear();
        for (TerminalNotifier.NameCallback c : run) c.onName(programName);
    }

    private static KittyNotification msg(String id, String title, String body, int urgency) {
        return KittyNotification.plain(id, title, body, urgency);
    }

    private static ProgramStatusStore store(int state, String id, String app, String title, String message) {
        ProgramStatusStore store = new ProgramStatusStore();
        store.apply(state, 0, -1, id, app, title, message);
        return store;
    }

    private void raise(ProgramStatusStore store, String id) {
        notifier.programStatus(paneA, Collections.singletonList(store.get(id)), store);
    }

    @Test
    public void postsWhenUnseen() {
        visible = false;
        assertTrue(notifier.kitty(paneA, msg("", "Build", "done", KittyNotification.URGENCY_NORMAL)));
        assertEquals(1, posted.size());
        TerminalNoticeContent c = posted.get(0);
        assertEquals(TerminalNoticeKind.MESSAGE, c.kind);
        assertEquals("Build", c.messageTitle);
        assertEquals("done", c.text);
        assertEquals("claude", c.name);
        assertEquals("[1] build", c.subText);
        assertSame(paneA, c.pane);
        assertEquals("ha", c.paneHandle);
        assertTrue(c.alert);
        assertEquals(0, c.replyPgid);
        assertNull(c.replyProgramName);
    }

    @Test
    public void criticalUrgencyIsUrgentMessage() {
        notifier.kitty(paneA, msg("", "x", "", KittyNotification.URGENCY_CRITICAL));
        assertEquals(TerminalNoticeKind.URGENT_MESSAGE, posted.get(0).kind);
    }

    @Test
    public void nothingWhenSeen() {
        visible = true;
        front = paneA;
        assertFalse(notifier.kitty(paneA, msg("", "x", "y", -1)));
        assertFalse(notifier.message(paneA, "x", "y"));
        notifier.bell(paneA);
        assertTrue(posted.isEmpty());
        assertEquals(0, records.size());
    }

    @Test
    public void terminalPageShowingPostsNothingEvenForAPaneNotInFront() {
        visible = true;
        front = paneB;
        assertFalse(notifier.message(paneA, "x", "y"));
        assertFalse(notifier.kitty(paneA, msg("", "x", "y", KittyNotification.URGENCY_CRITICAL)));
        notifier.bell(paneA);
        assertTrue(posted.isEmpty());
        assertEquals(0, records.size());
    }

    @Test
    public void visibleOnAnotherPagePosts() {
        // Home, the Assistant, the Display or a focused Home float: no pane is in front.
        visible = true;
        front = null;
        assertTrue(notifier.message(paneA, "x", "y"));
        assertEquals(1, posted.size());
    }

    @Test
    public void theTerminalPageShowingAWindowClearsItsNotificationWhicheverPaneSentIt() {
        notifier.bell(paneA2);
        assertEquals(1, posted.size());
        visible = true;
        front = paneA;
        notifier.frontMaybeChanged();
        assertEquals(java.util.Arrays.asList("wa"), cancelled);
        assertEquals(0, records.size());
        // The window was seen, so its next bell may notify again.
        visible = false;
        front = null;
        notifier.bell(paneA2);
        assertEquals(2, posted.size());
    }

    @Test
    public void emptyMessageIsNotPosted() {
        assertFalse(notifier.message(paneA, "  ", null));
        assertFalse(notifier.kitty(paneA, msg("", " ", " ", -1)));
        assertTrue(posted.isEmpty());
    }

    @Test
    public void staleGenerationIsNotPostedAfterAFrontChange() {
        deferred = true;
        notifier.message(paneA, "x", "y");
        assertEquals(1, pending.size());
        visible = true;
        front = paneA;
        notifier.frontMaybeChanged();
        assertEquals(java.util.Arrays.asList("wa"), cancelled);
        answer();
        assertTrue(posted.isEmpty());
    }

    @Test
    public void newerNoticeBeatsAnEarlierPendingOne() {
        deferred = true;
        notifier.message(paneA, "first", "");
        notifier.message(paneA, "second", "");
        answer();
        assertEquals(1, posted.size());
        assertEquals("second", posted.get(0).messageTitle);
    }

    @Test
    public void unknownProcessGroupPostsAtOnceWithTheWindowName() {
        pgid = 0;
        deferred = true;
        notifier.message(paneA, "x", "y");
        assertTrue(pending.isEmpty());
        assertEquals("[1] build", posted.get(0).name);
    }

    @Test
    public void blockedToWorkingCancels() {
        ProgramStatusStore store = store(BLOCKED, "job", "", "", "ok?");
        raise(store, "job");
        assertEquals(1, posted.size());
        assertEquals(TerminalNoticeKind.NEEDS_INPUT, posted.get(0).kind);
        store.apply(WORKING, 0, -1, "job", "", "", "");
        notifier.programStatus(paneA, Collections.emptyList(), store);
        assertEquals(java.util.Arrays.asList("wa"), cancelled);
        assertEquals(java.util.Arrays.asList(0), remaining);
        assertEquals(0, records.size());
    }

    @Test
    public void stillBlockedDoesNotCancel() {
        ProgramStatusStore store = store(BLOCKED, "job", "", "", "ok?");
        raise(store, "job");
        notifier.programStatus(paneA, Collections.emptyList(), store);
        assertTrue(cancelled.isEmpty());
    }

    @Test
    public void clearedStatusCancels() {
        ProgramStatusStore store = store(ERROR, "job", "", "", "boom");
        raise(store, "job");
        store.apply(ProgramStatusStore.STATE_CLEAR, 0, -1, "job", "", "", "");
        notifier.programStatus(paneA, Collections.emptyList(), store);
        assertEquals(java.util.Arrays.asList("wa"), cancelled);
    }

    @Test
    public void statusKindsMapAndOthersAreIgnored() {
        raise(store(DONE, "j", "", "", ""), "j");
        assertEquals(TerminalNoticeKind.FINISHED, posted.get(0).kind);
        ProgramStatusStore working = store(WORKING, "k", "", "", "");
        raise(working, "k");
        assertEquals(1, posted.size());
    }

    @Test
    public void closeByIdCancelsOnlyAMatchingMessage() {
        notifier.kitty(paneA, msg("build", "x", "y", -1));
        notifier.kittyClose(paneA, "other");
        notifier.kittyClose(paneA, "");
        assertTrue(cancelled.isEmpty());
        notifier.kittyClose(paneA, "build");
        assertEquals(java.util.Arrays.asList("wa"), cancelled);
    }

    @Test
    public void sessionGoneCancelsItsRecordsAndBell() {
        notifier.bell(paneA);
        notifier.message(paneB, "x", "y");
        notifier.paneGone(paneA);
        assertEquals(java.util.Arrays.asList("wa"), cancelled);
        assertFalse(records.bellSpent("wa"));
        assertEquals(1, records.size());
    }

    @Test
    public void cannotPostRecordsNothingAndReturnsFalse() {
        canPost = false;
        assertFalse(notifier.kitty(paneA, msg("", "x", "y", -1)));
        assertFalse(notifier.message(paneA, "x", "y"));
        notifier.bell(paneA);
        raise(store(BLOCKED, "j", "", "", "m"), "j");
        assertEquals(0, records.size());
        assertFalse(records.bellSpent("wa"));
        assertTrue(posted.isEmpty());
    }

    @Test
    public void lowerKindDoesNotStandIn() {
        raise(store(ERROR, "j", "", "", "boom"), "j");
        notifier.bell(paneA);
        notifier.message(paneA, "x", "y");
        assertEquals(1, posted.size());
        assertEquals(TerminalNoticeKind.ERROR, records.get("wa").kind);
    }

    @Test
    public void higherKindReplacesInPlace() {
        notifier.message(paneA, "x", "y");
        raise(store(BLOCKED, "j", "", "", "m"), "j");
        assertEquals(2, posted.size());
        assertEquals(TerminalNoticeKind.NEEDS_INPUT, records.get("wa").kind);
        assertEquals(1, records.size());
    }

    @Test
    public void bellPostsOnceUntilSeen() {
        notifier.bell(paneA);
        notifier.bell(paneA);
        assertEquals(1, posted.size());
        visible = true;
        front = paneA;
        notifier.frontMaybeChanged();
        visible = false;
        front = null;
        notifier.bell(paneA);
        assertEquals(2, posted.size());
    }

    @Test
    public void statusNamePrefersAppThenTitleThenProgramThenWindow() {
        raise(store(BLOCKED, "j", "app", "title", "m"), "j");
        assertEquals("app", posted.get(0).name);
        raise(store(BLOCKED, "j", "", "title", "m"), "j");
        assertEquals("title", posted.get(1).name);
        raise(store(BLOCKED, "j", "", "", "m"), "j");
        assertEquals("claude", posted.get(2).name);
        programName = null;
        raise(store(BLOCKED, "j", "", "", "m"), "j");
        assertEquals("[1] build", posted.get(3).name);
    }

    @Test
    public void statusAppResolvesFromAnAncestorRecord() {
        ProgramStatusStore store = new ProgramStatusStore();
        store.apply(WORKING, 0, -1, "root", "tool", "", "");
        store.apply(BLOCKED, 0, -1, "root/child", "", "", "m");
        raise(store, "root/child");
        assertEquals("tool", posted.get(0).name);
    }

    @Test
    public void statusTextIsOneLineAtMost200() {
        StringBuilder longText = new StringBuilder("line one\nline two ");
        for (int i = 0; i < 50; i++) longText.append("word ");
        raise(store(BLOCKED, "j", "", "", longText.toString()), "j");
        String text = posted.get(0).text;
        assertFalse(text.contains("\n"));
        assertTrue(text.codePointCount(0, text.length()) <= 200);
        assertTrue(text.startsWith("line one"));
    }

    @Test
    public void replyFieldsOnlyForNeedsInputAndBellWithAName() {
        raise(store(BLOCKED, "j", "", "", "m"), "j");
        assertEquals(100, posted.get(0).replyPgid);
        assertEquals("claude", posted.get(0).replyProgramName);

        notifier.paneGone(paneA);
        notifier.bell(paneA);
        assertEquals(TerminalNoticeKind.BELL, posted.get(1).kind);
        assertEquals(100, posted.get(1).replyPgid);

        notifier.paneGone(paneA);
        raise(store(ERROR, "e", "", "", "m"), "e");
        assertEquals(0, posted.get(2).replyPgid);
        assertNull(posted.get(2).replyProgramName);

        notifier.paneGone(paneA);
        notifier.message(paneA, "x", "y");
        assertEquals(0, posted.get(3).replyPgid);
    }

    @Test
    public void noReplyWithoutAProgramName() {
        programName = null;
        raise(store(BLOCKED, "j", "", "", "m"), "j");
        assertEquals(0, posted.get(0).replyPgid);
        assertNull(posted.get(0).replyProgramName);

        notifier.paneGone(paneA);
        programName = "";
        raise(store(BLOCKED, "j2", "", "", "m"), "j2");
        assertNull(posted.get(1).replyProgramName);

        notifier.paneGone(paneA);
        programName = "claude";
        pgid = 0;
        raise(store(BLOCKED, "j3", "", "", "m"), "j3");
        assertNull(posted.get(2).replyProgramName);
    }

    @Test
    public void sameKittyIdReplacesAnUrgentMessageWithAPlainOne() {
        notifier.kitty(paneA, msg("b", "x", "", KittyNotification.URGENCY_CRITICAL));
        notifier.kitty(paneA, msg("b", "y", "", KittyNotification.URGENCY_NORMAL));
        assertEquals(2, posted.size());
        assertEquals(TerminalNoticeKind.MESSAGE, records.get("wa").kind);
    }
}
