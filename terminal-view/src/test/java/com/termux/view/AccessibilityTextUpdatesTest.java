package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * A screen reader hears that the terminal's text changed — once per burst of output, not once per
 * screen update — and nothing builds the screen's text for services that never read it.
 */
public class AccessibilityTextUpdatesTest {

    private final List<Runnable> mPosted = new ArrayList<>();
    private final List<Long> mDelays = new ArrayList<>();
    private int mSent;

    private final AccessibilityTextUpdates mUpdates = new AccessibilityTextUpdates(new AccessibilityTextUpdates.Host() {
        @Override
        public void postDelayed(Runnable action, long delayMs) {
            mPosted.add(action);
            mDelays.add(delayMs);
        }

        @Override
        public void removeCallbacks(Runnable action) {
            mPosted.remove(action);
        }

        @Override
        public void sendTextChanged() {
            mSent++;
        }
    });

    private void runPosted() {
        List<Runnable> due = new ArrayList<>(mPosted);
        mPosted.clear();
        for (Runnable action : due) action.run();
    }

    @Test
    public void onlyATouchExplorationReaderWantsTheText() {
        assertTrue(AccessibilityTextUpdates.readsText(true, true));
        assertFalse("A password manager or automation service", AccessibilityTextUpdates.readsText(true, false));
        assertFalse(AccessibilityTextUpdates.readsText(false, false));
    }

    @Test
    public void aServiceThatDoesNotReadGetsNothingScheduled() {
        mUpdates.setServiceState(true, false);
        for (int i = 0; i < 10; i++) mUpdates.onScreenUpdated();
        assertTrue(mPosted.isEmpty());
        assertEquals(0, mSent);
    }

    @Test
    public void aBurstOfUpdatesBecomesOneTrailingEvent() {
        mUpdates.setServiceState(true, true);
        for (int i = 0; i < 50; i++) mUpdates.onScreenUpdated();

        assertEquals(1, mPosted.size());
        assertEquals(AccessibilityTextUpdates.THROTTLE_MS, (long) mDelays.get(0));
        assertEquals("Nothing is sent before the window closes", 0, mSent);

        runPosted();
        assertEquals(1, mSent);

        // The next burst gets its own event: TalkBack users keep hearing updates.
        mUpdates.onScreenUpdated();
        runPosted();
        assertEquals(2, mSent);
    }

    @Test
    public void turningTheReaderOnStartsEventsAndTurningItOffDropsAPendingOne() {
        mUpdates.setServiceState(false, false);
        mUpdates.onScreenUpdated();
        assertTrue(mPosted.isEmpty());

        mUpdates.setServiceState(true, true);
        mUpdates.onScreenUpdated();
        assertEquals(1, mPosted.size());

        mUpdates.setServiceState(true, false);
        assertTrue(mPosted.isEmpty());
        assertEquals(0, mSent);
    }

    @Test
    public void cancelDropsThePendingEventAndAllowsTheNext() {
        mUpdates.setServiceState(true, true);
        mUpdates.onScreenUpdated();
        mUpdates.cancel();
        assertTrue(mPosted.isEmpty());

        mUpdates.onScreenUpdated();
        assertEquals(1, mPosted.size());
    }
}
