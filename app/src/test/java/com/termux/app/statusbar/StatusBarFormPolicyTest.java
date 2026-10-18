package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.statusbar.StatusBarFormPolicy.Form;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The three forms of the status bar and the rules between them, as arithmetic. */
public class StatusBarFormPolicyTest {

    private static final float EPS = 1e-3f;

    @Test public void theFormsStepOneAtATimeAndStopAtTheEnds() {
        assertEquals(Form.COMPACT, Form.CAPSULE.step(1));
        assertEquals(Form.EXPANDED, Form.COMPACT.step(1));
        assertEquals(Form.EXPANDED, Form.EXPANDED.step(1));
        assertEquals(Form.COMPACT, Form.EXPANDED.step(-1));
        assertEquals(Form.CAPSULE, Form.CAPSULE.step(-1));
        // The capsule rests compact in the launcher's two-form memory.
        assertEquals(TopStatusBarState.COMPACT, Form.CAPSULE.state());
        assertEquals(TopStatusBarState.EXPANDED, Form.EXPANDED.state());
    }

    @Test public void onlyARowHasACapsule() {
        assertTrue(StatusBarFormPolicy.capsuleAllowed(Edge.TOP));
        assertTrue(StatusBarFormPolicy.capsuleAllowed(Edge.BOTTOM));
        assertFalse(StatusBarFormPolicy.capsuleAllowed(Edge.LEFT));
        assertFalse(StatusBarFormPolicy.capsuleAllowed(Edge.RIGHT));
        assertEquals(Form.CAPSULE, StatusBarFormPolicy.lowest(true));
        assertEquals(Form.COMPACT, StatusBarFormPolicy.lowest(false));
        assertEquals(Form.COMPACT, StatusBarFormPolicy.highest(false));
    }

    @Test public void aSlowReleaseLandsOnTheNearestForm() {
        // 599 dp/s is under the 600 dp/s line: where the finger let go decides.
        assertEquals(Form.EXPANDED, StatusBarFormPolicy.release(Form.CAPSULE, Form.EXPANDED,
            599f, Form.CAPSULE, Form.EXPANDED));
        assertEquals(Form.CAPSULE, StatusBarFormPolicy.release(Form.EXPANDED, Form.CAPSULE,
            -599f, Form.CAPSULE, Form.EXPANDED));
        assertEquals(Form.COMPACT, StatusBarFormPolicy.release(Form.COMPACT, Form.COMPACT,
            0f, Form.CAPSULE, Form.EXPANDED));
        // Held to the forms the bar has: on Home the bar never expands.
        assertEquals(Form.COMPACT, StatusBarFormPolicy.release(Form.CAPSULE, Form.EXPANDED,
            0f, Form.CAPSULE, Form.COMPACT));
        // A NaN speed (no movement sampled) is a slow release.
        assertEquals(Form.EXPANDED, StatusBarFormPolicy.release(Form.COMPACT, Form.EXPANDED,
            Float.NaN, Form.CAPSULE, Form.EXPANDED));
    }

    @Test public void aQuickReleaseStepsOneFormFromWhereItStarted() {
        // 600 dp/s or faster: one step, wherever the finger got to.
        assertEquals(Form.COMPACT, StatusBarFormPolicy.release(Form.CAPSULE, Form.EXPANDED,
            600f, Form.CAPSULE, Form.EXPANDED));
        assertEquals(Form.EXPANDED, StatusBarFormPolicy.release(Form.COMPACT, Form.COMPACT,
            1500f, Form.CAPSULE, Form.EXPANDED));
        assertEquals(Form.CAPSULE, StatusBarFormPolicy.release(Form.COMPACT, Form.COMPACT,
            -600f, Form.CAPSULE, Form.EXPANDED));
        assertEquals(Form.COMPACT, StatusBarFormPolicy.release(Form.EXPANDED, Form.CAPSULE,
            -3000f, Form.CAPSULE, Form.EXPANDED));
        // At an end there is no step: a column bar has no capsule to step down to.
        assertEquals(Form.COMPACT, StatusBarFormPolicy.release(Form.COMPACT, Form.COMPACT,
            -2000f, Form.COMPACT, Form.EXPANDED));
        assertTrue(StatusBarFormPolicy.isQuick(-600f));
        assertFalse(StatusBarFormPolicy.isQuick(599.9f));
    }

    @Test public void theBarFoldsToTheCapsuleAfterFourQuietSeconds() {
        StatusBarFormPolicy.Idle early = idle(Form.COMPACT, 1000L, 0L, 0L, 3500L);
        assertFalse(early.fold);
        assertEquals(1500L, early.delayMs);
        StatusBarFormPolicy.Idle due = idle(Form.COMPACT, 1000L, 0L, 0L, 5000L);
        assertTrue(due.fold);
        // An expanded bar folds the whole way to the capsule too.
        assertTrue(idle(Form.EXPANDED, 0L, 0L, 0L, 4000L).fold);
        // A notification arriving or a window change restarts the quiet stretch.
        StatusBarFormPolicy.Idle afterNotification = idle(Form.COMPACT, 0L, 3000L, 0L, 5000L);
        assertFalse(afterNotification.fold);
        assertEquals(2000L, afterNotification.delayMs);
        StatusBarFormPolicy.Idle afterWindow = idle(Form.COMPACT, 0L, 0L, 4500L, 5000L);
        assertFalse(afterWindow.fold);
        assertEquals(3500L, afterWindow.delayMs);
    }

    @Test public void nothingFoldsABarThatIsHeld() {
        long now = 60_000L;
        // A finger down, a card open, another surface holding the bar.
        for (int hold = 0; hold < 3; hold++) {
            StatusBarFormPolicy.Idle held = StatusBarFormPolicy.idle(Form.COMPACT, true, 0L, 0L,
                0L, now, hold == 0, hold == 1, hold == 2, false);
            assertFalse("hold " + hold, held.fold);
            assertTrue("hold " + hold, held.held);
            assertEquals("looks again half a second later", 500L, held.delayMs);
        }
        // Touch exploration: never, and no timer.
        StatusBarFormPolicy.Idle reader = StatusBarFormPolicy.idle(Form.COMPACT, true, 0L, 0L,
            0L, now, false, false, false, true);
        assertFalse(reader.fold);
        assertEquals(-1L, reader.delayMs);
        // The capsule, and a bar that cannot be one, have nothing to fold and no timer.
        StatusBarFormPolicy.Idle capsule = idle(Form.CAPSULE, 0L, 0L, 0L, now);
        assertFalse(capsule.fold);
        assertEquals(-1L, capsule.delayMs);
        StatusBarFormPolicy.Idle column = StatusBarFormPolicy.idle(Form.COMPACT, false, 0L, 0L,
            0L, now, false, false, false, false);
        assertFalse(column.fold);
        assertEquals(-1L, column.delayMs);
    }

    @Test public void onlyANewOrNewerNotificationIsAnArrival() {
        Set<String> ab = new HashSet<>(Arrays.asList("a", "b"));
        Set<String> a = Collections.singleton("a");
        assertTrue(StatusBarFormPolicy.notificationArrived(a, 100L, ab, 100L));
        assertTrue(StatusBarFormPolicy.notificationArrived(ab, 100L, ab, 101L));
        assertFalse("a dismissal is not an arrival",
            StatusBarFormPolicy.notificationArrived(ab, 100L, a, 100L));
        assertFalse(StatusBarFormPolicy.notificationArrived(ab, 100L, ab, 100L));
    }

    @Test public void minimalModeStartsAsTheCapsule() {
        assertEquals(Form.CAPSULE, StatusBarFormPolicy.defaultForm(true,
            TopStatusBarState.EXPANDED, true));
        assertEquals(Form.EXPANDED, StatusBarFormPolicy.defaultForm(false,
            TopStatusBarState.EXPANDED, true));
        assertEquals(Form.COMPACT, StatusBarFormPolicy.defaultForm(true,
            TopStatusBarState.COMPACT, false));
        assertEquals(Form.CAPSULE, StatusBarFormPolicy.formOnMinimalChange(true, Form.EXPANDED,
            true));
        assertEquals(Form.COMPACT, StatusBarFormPolicy.formOnMinimalChange(false, Form.CAPSULE,
            true));
        assertEquals(Form.EXPANDED, StatusBarFormPolicy.formOnMinimalChange(false,
            Form.EXPANDED, true));
    }

    @Test public void theCapsuleShowsTheHouseOrTheWindowGlyphAndUpToThreeDots() {
        StatusBarFormPolicy.Content home = StatusBarFormPolicy.content(true, " zsh", 1, 0);
        assertEquals(StatusBarFormPolicy.HOUSE_GLYPH, home.glyph);
        assertTrue(home.home);
        assertEquals(1, home.dots);

        StatusBarFormPolicy.Content window = StatusBarFormPolicy.content(false, " zsh", 2,
            3);
        assertEquals("", window.glyph);
        assertEquals(3, window.dots);
        assertEquals(5, window.count);

        assertEquals(StatusBarFormPolicy.WINDOW_GLYPH,
            StatusBarFormPolicy.content(false, "Display", 0, 0).glyph);
        assertEquals(StatusBarFormPolicy.WINDOW_GLYPH,
            StatusBarFormPolicy.content(false, null, 0, 0).glyph);
        assertEquals(0, StatusBarFormPolicy.content(false, null, -2, 0).dots);

        assertEquals("", StatusBarFormPolicy.leadingGlyph(""));
        assertNull(StatusBarFormPolicy.leadingGlyph("zsh"));
        assertNull(StatusBarFormPolicy.leadingGlyph(""));
    }

    @Test public void theCapsuleIsAsWideAsItsContentAndThirtySixDpTall() {
        // density 2: padding 24 + 24, glyph 30, gap 16, clock 80 = 174; with two dots
        // gap 16 + 10 + 6 + 10 = 42 more.
        assertEquals(174, StatusBarFormPolicy.capsuleWidthPx(2f, 30f, 80f, 0, 1000));
        assertEquals(216, StatusBarFormPolicy.capsuleWidthPx(2f, 30f, 80f, 2, 1000));
        assertEquals(150, StatusBarFormPolicy.capsuleWidthPx(2f, 30f, 80f, 2, 150));
        assertEquals(72, StatusBarFormPolicy.capsuleHeightPx(2f, 200));
        // A thinner band holds a thinner capsule, still a pill.
        assertEquals(64, StatusBarFormPolicy.capsuleHeightPx(2f, 64));
        assertEquals(36f, StatusBarFormPolicy.capsuleRadiusPx(2f, 72), EPS);
        assertEquals(32f, StatusBarFormPolicy.capsuleRadiusPx(2f, 64), EPS);
    }

    private static StatusBarFormPolicy.Idle idle(Form form, long touch, long notification,
                                                 long window, long now) {
        return StatusBarFormPolicy.idle(form, true, touch, notification, window, now, false,
            false, false, false);
    }
}
