package com.termux.app.statusbar;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackgroundProcessModelTest {

    private static final long GRACE = BackgroundProcessModel.SHOW_DELAY_MS;

    @Test
    public void retainsStartOrderUpdatesNativeTitleAndKeepsOtherSessions() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(10, 101, "codex", "Thinking")), 1000);
        model.update(Arrays.asList(snapshot(10, 101, "codex", "requires attention"),
            snapshot(20, 202, "gradle", "Build")), 2000);

        long settled = 2000 + GRACE;
        List<BackgroundProcessModel.Entry> all = model.visibleEntries(-1L, settled);
        assertEquals(Arrays.asList(10, 20), Arrays.asList(all.get(0).shellPid, all.get(1).shellPid));
        assertEquals(1000, all.get(0).startedAtMs);
        assertEquals("requires attention", all.get(0).displayText());
        // Session 2 is focused, so both session-1 rows stay up.
        assertEquals(2, model.visibleEntries(2L, settled).size());
    }

    @Test
    public void foregroundReturnSessionExitAndPidReplacementRemoveOldRows() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(10, 101, "sleep", "sleep")), 1000);
        model.update(Collections.singletonList(snapshot(10, 102, "make", "make")), 2000);
        long settled = 2000 + GRACE;
        assertEquals(1, model.visibleEntries(-1L, settled).size());
        assertEquals(102, model.visibleEntries(-1L, settled).get(0).foregroundPid);
        assertEquals(2000, model.visibleEntries(-1L, settled).get(0).startedAtMs);

        model.update(Collections.singletonList(
            new BackgroundProcessModel.Snapshot(1L, 10, -1, null, "shell", false)), 3000);
        assertEquals(0, model.visibleEntries(-1L, 3000 + GRACE).size());

        model.update(Collections.singletonList(snapshot(20, 201, "nvim", "nvim")), 4000);
        model.update(Collections.emptyList(), 5000);
        assertEquals(0, model.visibleEntries(-1L, 5000 + GRACE).size());
    }

    /** A window's own rc-file startup work must not flash a row before it finishes. */
    @Test
    public void aForegroundGoneWithinTheGraceNeverBecomesVisible() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(30, 301, "fastfetch", "fastfetch")), 1000);
        assertEquals(0, model.visibleEntries(-1L, 1000).size());
        assertEquals(0, model.visibleEntries(-1L, 1000 + GRACE - 1).size());

        model.update(Collections.emptyList(), 1000 + GRACE - 1);
        assertEquals(0, model.visibleEntries(-1L, 9000).size());
    }

    @Test
    public void aSurvivingForegroundBecomesVisibleAndIsAnnouncedByMsUntilNextVisible() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(40, 401, "pacman", "pacman -Syu")), 1000);
        assertEquals(GRACE, model.msUntilNextVisible(-1L, 1000));
        assertEquals(1L, model.msUntilNextVisible(-1L, 1000 + GRACE - 1));

        List<BackgroundProcessModel.Entry> visible = model.visibleEntries(-1L, 1000 + GRACE);
        assertEquals(1, visible.size());
        assertEquals("pacman -Syu", visible.get(0).displayText());
        assertEquals(-1L, model.msUntilNextVisible(-1L, 1000 + GRACE));
    }

    @Test
    public void keyIsStableAcrossUpdatesAndDistinguishesForegroundReplacement() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(50, 501, "make", "make")), 1000);
        long first = model.visibleEntries(-1L, 1000 + GRACE).get(0).key;
        model.update(Collections.singletonList(snapshot(50, 501, "make", "make: linking")), 2000);
        assertEquals(first, model.visibleEntries(-1L, 2000 + GRACE).get(0).key);

        model.update(Collections.singletonList(snapshot(50, 502, "ld", "ld")), 3000);
        assertTrue(first != model.visibleEntries(-1L, 3000 + GRACE).get(0).key);
    }

    /**
     * The scoping rule: a job in the session you are in never raises a row, however many windows or
     * panes you move through, and leaving the session is what puts it up.
     */
    @Test
    public void rowsAreRaisedPerSessionNotPerPane() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        // Two windows of session 1 both working, plus one in session 2.
        model.update(Arrays.asList(
            snapshot(1L, 10, 101, "pacman", "pacman -Syu"),
            snapshot(1L, 11, 111, "codex", "codex"),
            snapshot(2L, 20, 201, "gradle", "gradle")), 1000);
        long settled = 1000 + GRACE;

        // Inside session 1: its own two jobs stay silent, the other session's job shows.
        List<BackgroundProcessModel.Entry> inOne = model.visibleEntries(1L, settled);
        assertEquals(1, inOne.size());
        assertEquals(20, inOne.get(0).shellPid);

        // Move to session 2: now session 1's two jobs are the ones off screen.
        List<BackgroundProcessModel.Entry> inTwo = model.visibleEntries(2L, settled);
        assertEquals(Arrays.asList(10, 11),
            Arrays.asList(inTwo.get(0).shellPid, inTwo.get(1).shellPid));
    }

    /** A new session's own startup work must not raise rows against the session that spawned it. */
    @Test
    public void theGraceAppliesPerSessionScopedEntry() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(2L, 20, 201, "fish", "fish")), 1000);
        // Still inside session 1, the new session's rc work is too young to show...
        assertEquals(0, model.visibleEntries(1L, 1000 + GRACE - 1).size());
        assertEquals(GRACE - 1, model.msUntilNextVisible(1L, 1000 + 1));
        // ...and once the user is in session 2 it is their own foreground, so it never shows at all.
        assertEquals(0, model.visibleEntries(2L, 9000).size());
        assertEquals(-1L, model.msUntilNextVisible(2L, 9000));
    }

    /** Session 1 by default; the focused session in these tests is 0 unless stated. */
    private static BackgroundProcessModel.Snapshot snapshot(int shell, int foreground,
                                                            String process, String title) {
        return snapshot(1L, shell, foreground, process, title);
    }

    private static BackgroundProcessModel.Snapshot snapshot(long session, int shell, int foreground,
                                                            String process, String title) {
        return new BackgroundProcessModel.Snapshot(session, shell, foreground, process, title, true);
    }
}
