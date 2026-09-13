package com.termux.app.notice;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * How long the pill keeps a notice, and which notices are not kept by a clock at all.
 *
 * <p>The app used to say the same things on three surfaces with three unrelated timings. Folding
 * them onto one pill is only an improvement if the scale it lands on is the reading: these are the
 * numbers the whole app now agrees on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@LooperMode(LooperMode.Mode.LEGACY)
public class AppNoticeHoldTest {

    @Test
    public void theScaleRunsFromAGlanceToAReading() {
        assertEquals(1000L, AppNoticeItem.Hold.READOUT.ms);
        assertEquals(1600L, AppNoticeItem.Hold.CONFIRM.ms);
        assertEquals(2600L, AppNoticeItem.Hold.INFO.ms);
        assertEquals(3800L, AppNoticeItem.Hold.REFUSAL.ms);
        assertEquals(9000L, AppNoticeItem.Hold.UNDO.ms);
        // Strictly increasing: a kind that is worth more reading must never leave sooner.
        AppNoticeItem.Hold[] rising = {AppNoticeItem.Hold.READOUT, AppNoticeItem.Hold.CONFIRM,
            AppNoticeItem.Hold.INFO, AppNoticeItem.Hold.REFUSAL, AppNoticeItem.Hold.UNDO};
        for (int i = 1; i < rising.length; i++)
            assertTrue(rising[i - 1] + " outlives " + rising[i],
                rising[i - 1].ms < rising[i].ms);
    }

    /** The boolean the fifty-odd plain callers still pass maps onto two of the same kinds. */
    @Test
    public void theShortAndLongAliasesAreJustTwoOfTheKinds() {
        assertEquals(AppNoticeItem.Hold.INFO.ms, AppNoticeHostView.HOLD_SHORT_MS);
        assertEquals(AppNoticeItem.Hold.REFUSAL.ms, AppNoticeHostView.HOLD_LONG_MS);
        assertEquals(AppNoticeItem.Hold.UNDO.ms, AppNoticeHostView.HOLD_UNDO_MS);
    }

    @Test
    public void onlyStickyHasNoClock() {
        assertTrue(sticky().isSticky());
        for (AppNoticeItem.Hold hold : AppNoticeItem.Hold.values()) {
            if (hold == AppNoticeItem.Hold.STICKY) continue;
            assertFalse(hold + " has no hold", held(hold).isSticky());
        }
    }

    /** Each entry point picks its own kind's hold rather than a duration of its own. */
    @Test
    public void eachEntryPointCarriesItsKindsHold() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);

        AppNotice.readout(activity, "Mouse mode");
        assertEquals(AppNoticeItem.Hold.READOUT.ms, host.activeItem().durationMs);
        assertTrue(host.activeItem().fleeting);

        host.clear();
        AppNotice.confirm(activity, "⧉", "Copied");
        assertEquals(AppNoticeItem.Hold.CONFIRM.ms, host.activeItem().durationMs);
        assertEquals("⧉", host.activeItem().resolvedGlyph());

        host.clear();
        AppNotice.refusal(activity, "No session to split");
        assertEquals(AppNoticeItem.Hold.REFUSAL.ms, host.activeItem().durationMs);

        host.clear();
        AppNotice.held(activity, AppNoticeItem.Kind.INFO, "bash - exited", AppNoticeItem.Hold.INFO);
        assertEquals(AppNoticeItem.Hold.INFO.ms, host.activeItem().durationMs);
    }

    /**
     * A pending chord is the state of the keyboard, not news about it: it stands until the chord
     * resolves, and a further stroke swaps the words in place rather than replaying the entrance.
     */
    @Test
    public void aPendingChordStandsAndSwapsItsWordsInPlace() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);

        AppNotice.sticky(activity, "Waiting for key: Ctrl+Alt+Space  ›  …");
        assertNotNull(host.activeItem());
        assertTrue(host.activeItem().isSticky());
        assertEquals(View.VISIBLE, host.getVisibility());

        AppNotice.sticky(activity, "Waiting for key: Ctrl+Alt+Space  ›  P  ›  …");
        assertEquals("Waiting for key: Ctrl+Alt+Space  ›  P  ›  …",
            String.valueOf(host.activeItem().title));
        // Nothing queued behind it: the report replaced itself rather than lining up.
        assertEquals(View.GONE, host.getChildAt(3).getVisibility());
    }

    /** And it comes down when the chord does, not when a timer says so. */
    @Test
    public void aResolvedChordTakesItsReportDown() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        AppNotice.sticky(activity, "Key mode: pane");
        assertNotNull(host.activeItem());
        AppNotice.clearSticky(activity);
        // It stops holding the pill at once rather than when the fade finishes, so the read-out of
        // what the chord actually ran — which follows by a frame or two — gets a clean entrance.
        assertNull(host.activeItem());
    }

    /** A real notice takes the pill from a standing report at once rather than queueing. */
    @Test
    public void aRealNoticeOutranksAStandingReport() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        AppNotice.sticky(activity, "Waiting for key: Ctrl+Alt+Space  ›  …");
        AppNotice.refusal(activity, "Ctrl+Alt+R: no session");
        assertEquals("Ctrl+Alt+R: no session", String.valueOf(host.activeItem().title));
        assertFalse(host.activeItem().isSticky());

        // And clearing the chord afterwards must not drop the refusal that replaced it.
        AppNotice.clearSticky(activity);
        assertEquals("Ctrl+Alt+R: no session", String.valueOf(host.activeItem().title));
    }

    private static AppNoticeItem sticky() {
        return held(AppNoticeItem.Hold.STICKY);
    }

    private static AppNoticeItem held(AppNoticeItem.Hold hold) {
        return new AppNoticeItem(AppNoticeItem.Kind.INFO, "title", null, null, hold.ms);
    }
}
