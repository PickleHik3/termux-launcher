package com.termux.app.terminal;

import com.termux.app.terminal.TerminalNoticePolicy.Action;
import com.termux.app.terminal.TerminalNoticePolicy.Decision;
import com.termux.terminal.KittyNotification;
import com.termux.terminal.KittyNotifications;

import org.junit.Test;

import static com.termux.app.terminal.TerminalNoticeKind.BELL;
import static com.termux.app.terminal.TerminalNoticeKind.ERROR;
import static com.termux.app.terminal.TerminalNoticeKind.FINISHED;
import static com.termux.app.terminal.TerminalNoticeKind.MESSAGE;
import static com.termux.app.terminal.TerminalNoticeKind.NEEDS_INPUT;
import static com.termux.app.terminal.TerminalNoticeKind.URGENT_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** When a terminal event is worth putting in the shade. */
public class TerminalNoticePolicyTest {

    private static final Object PANE = new Object();

    private static String occasionOf(String metadataAndPayload) {
        KittyNotification[] built = new KittyNotification[1];
        new KittyNotifications().handle(metadataAndPayload, new KittyNotifications.Handler() {
            @Override
            public void show(KittyNotification notification) {
                built[0] = notification;
            }

            @Override
            public void close(String id) {
            }

            @Override
            public void write(String escapeSequence) {
            }
        });
        assertNotNull(built[0]);
        return built[0].getOccasion();
    }

    private static TerminalNoticeRecords.Record record(TerminalNoticeKind kind, String messageId) {
        return new TerminalNoticeRecords().put(kind, PANE, "w", messageId, null, null);
    }

    private static Decision decide(TerminalNoticeKind kind, boolean visible, boolean front,
                                   TerminalNoticeRecords.Record current, String id) {
        return TerminalNoticePolicy.decide(kind, visible, front, false, current, id, false);
    }

    // --- occasionAllows (moved from ShellNotifications.shouldShow) ---

    @Test
    public void plainMessageIsAlwaysShown() {
        String occasion = occasionOf(";Build finished");
        assertEquals(KittyNotification.OCCASION_ALWAYS, occasion);
        assertTrue(TerminalNoticePolicy.occasionAllows(occasion, true, true));
        assertTrue(TerminalNoticePolicy.occasionAllows(occasion, false, false));
    }

    @Test
    public void unfocusedMessageIsHeldBackOnlyWhileTheTerminalPageIsShowing() {
        String occasion = occasionOf("o=unfocused;Build finished");
        assertFalse(TerminalNoticePolicy.occasionAllows(occasion, true, true));
        assertTrue(TerminalNoticePolicy.occasionAllows(occasion, true, false));
        assertTrue(TerminalNoticePolicy.occasionAllows(occasion, false, true));
    }

    @Test
    public void invisibleMessageWaitsForTheLauncherToBePutAway() {
        String occasion = occasionOf("o=invisible;Build finished");
        assertFalse(TerminalNoticePolicy.occasionAllows(occasion, true, true));
        assertFalse(TerminalNoticePolicy.occasionAllows(occasion, true, false));
        assertTrue(TerminalNoticePolicy.occasionAllows(occasion, false, false));
    }

    @Test
    public void anUnknownOccasionFallsBackToShowingIt() {
        String occasion = occasionOf("o=whenever;Build finished");
        assertEquals(KittyNotification.OCCASION_ALWAYS, occasion);
        assertTrue(TerminalNoticePolicy.occasionAllows(occasion, true, true));
        assertTrue(TerminalNoticePolicy.occasionAllows(null, true, true));
    }

    // --- decide ---

    @Test
    public void seenNeverPosts() {
        for (TerminalNoticeKind kind : TerminalNoticeKind.values())
            assertEquals(Action.NOTHING, decide(kind, true, true, null, "").action);
    }

    @Test
    public void unseenPostsWithAlert() {
        boolean[][] unseen = {{false, false}, {false, true}, {true, false}};
        for (boolean[] s : unseen) {
            Decision d = decide(MESSAGE, s[0], s[1], null, "");
            assertEquals(Action.POST, d.action);
            assertTrue(d.alert);
        }
    }

    @Test
    public void invisibleOccasionNarrowsToLauncherAway() {
        assertEquals(Action.NOTHING,
            TerminalNoticePolicy.decide(MESSAGE, true, false, true, null, "", false).action);
        assertEquals(Action.POST,
            TerminalNoticePolicy.decide(MESSAGE, false, false, true, null, "", false).action);
    }

    @Test
    public void alwaysOccasionDoesNotWidenTheUnseenRule() {
        // "always" is not an input: what is seen stays silent whatever the program asked.
        assertEquals(Action.NOTHING,
            TerminalNoticePolicy.decide(URGENT_MESSAGE, true, true, false, null, "x", false).action);
        assertFalse(TerminalNoticePolicy.occasionAllows(KittyNotification.OCCASION_INVISIBLE, true, false));
    }

    @Test
    public void higherOrEqualKindReplacesAndLowerIsIgnored() {
        TerminalNoticeRecords.Record up = record(ERROR, "");
        assertEquals(Action.REPLACE, decide(NEEDS_INPUT, false, false, up, "").action);
        assertEquals(Action.REPLACE, decide(ERROR, false, false, up, "").action);
        assertEquals(Action.NOTHING, decide(FINISHED, false, false, up, "").action);
        assertEquals(Action.NOTHING, decide(BELL, false, false, up, "").action);
    }

    @Test
    public void sameMessageIdReplacesAHigherKind() {
        TerminalNoticeRecords.Record up = record(URGENT_MESSAGE, "build");
        assertEquals(Action.REPLACE, decide(MESSAGE, false, false, up, "build").action);
        assertEquals(Action.NOTHING, decide(MESSAGE, false, false, up, "other").action);
        assertEquals(Action.NOTHING, decide(MESSAGE, false, false, up, "").action);
    }

    @Test
    public void bellNotifiesOnceUntilSeen() {
        assertEquals(Action.POST,
            TerminalNoticePolicy.decide(BELL, false, false, false, null, "", false).action);
        assertEquals(Action.NOTHING,
            TerminalNoticePolicy.decide(BELL, false, false, false, null, "", true).action);
    }

    @Test
    public void kindOrderAndReply() {
        assertTrue(NEEDS_INPUT.outranks(BELL));
        assertFalse(ERROR.outranks(ERROR));
        assertTrue(NEEDS_INPUT.takesReply());
        assertTrue(BELL.takesReply());
        assertFalse(MESSAGE.takesReply());
        assertFalse(ERROR.takesReply());
    }
}
