package com.termux.app.tips;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FirstRunTipsTest {

    private static final class Fake implements FirstRunTips.Store, FirstRunTips.Surface {
        @Nullable String stored;
        boolean home = true;
        boolean busy;
        boolean slide = true;
        boolean fullScreen;
        boolean navigating;
        int soon;
        final List<String> calls = new ArrayList<>();

        @Nullable @Override public String read() { return stored; }
        @Override public void write(@NonNull String done) { stored = done; }
        @Override public boolean homeInFront() { return home; }
        @Override public boolean busy() { return busy; }
        @Override public boolean canSlide() { return slide; }
        @Override public boolean fullScreenInFront() { return fullScreen; }
        @Override public boolean navigating() { return navigating; }
        final List<Runnable> later = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        @Override public void postDelayed(@NonNull Runnable action, long delayMs) {
            later.add(action);
            delays.add(delayMs);
        }
        @Override public void refreshSoon() { soon++; }
        @Override public void show(@NonNull Tip tip) { up = true; calls.add("show " + tip.key); }
        boolean up;
        @Override public boolean showing() { return up; }
        @Override public void hide() { up = false; calls.add("hide"); }
    }

    @Test public void freshHomeShowsTheBarHoldFirst() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.refresh();
        assertEquals(Tip.BAR_HOLD, tips.shown());
        assertEquals("[show bar_hold]", fake.calls.toString());
    }

    @Test public void theGestureDoneSendsItAwayAndTheNextComes() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.refresh();
        tips.onGesture(Tip.BAR_HOLD);
        assertEquals(Tip.MARK_SLIDE, tips.shown());
        assertEquals("bar_hold", fake.stored);
    }

    @Test public void aGestureDoneBeforeItsCardCameKeepsItAway() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.refresh();
        tips.onGesture(Tip.HOME_HOLD);
        tips.onGesture(Tip.BAR_HOLD);
        tips.onGesture(Tip.MARK_SLIDE);
        assertNull(tips.shown());
        assertTrue(TipsDone.parse(fake.stored).has(Tip.HOME_HOLD));
        assertEquals("hide", fake.calls.get(fake.calls.size() - 1));
    }

    @Test public void dismissGoesForGoodAndStaysGoneOnANewScreen() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.refresh();
        tips.dismiss();
        FirstRunTips later = new FirstRunTips(fake, fake);
        later.refresh();
        assertEquals(Tip.MARK_SLIDE, later.shown());
        assertTrue(TipsDone.parse(fake.stored).has(Tip.BAR_HOLD));
    }

    @Test public void nothingShowsOffHomeOrOverABusyScreen() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        fake.home = false;
        tips.refresh();
        assertNull(tips.shown());
        fake.home = true;
        fake.busy = true;
        tips.refresh();
        assertNull(tips.shown());
        fake.busy = false;
        tips.refresh();
        assertEquals(Tip.BAR_HOLD, tips.shown());
    }

    @Test public void clearingTheStoreBringsTheHintsBack() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.onGesture(Tip.BAR_HOLD);
        tips.onGesture(Tip.MARK_SLIDE);
        tips.onGesture(Tip.HOME_HOLD);
        assertNull(tips.shown());
        fake.stored = "";
        tips.refresh();
        assertEquals(Tip.BAR_HOLD, tips.shown());
    }

    @Test public void aCardThatNeverCameUpIsShownAgain() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.refresh();
        fake.up = false;
        tips.refresh();
        assertEquals("[show bar_hold, show bar_hold]", fake.calls.toString());
    }

    @Test public void theSlideHintIsPassedOverWhenThereIsNothingToSlideBetween() {
        Fake fake = new Fake();
        fake.slide = false;
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.onGesture(Tip.BAR_HOLD);
        assertEquals(Tip.HOME_HOLD, tips.shown());
        assertFalse(TipsDone.parse(fake.stored).has(Tip.MARK_SLIDE));
        fake.slide = true;
        tips.onGesture(Tip.HOME_HOLD);
        assertEquals(Tip.MARK_SLIDE, tips.shown());
    }

    @Test public void aBusyScreenWithHintsLeftAsksToBeLookedAtAgain() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        fake.busy = true;
        tips.refresh();
        assertEquals(1, fake.soon);
        fake.stored = "bar_hold,mark_slide,home_hold";
        tips.refresh();
        assertEquals(1, fake.soon);
    }

    @Test public void unknownNamesAreDropped() {
        assertEquals("mark_slide", TipsDone.parse("nope, mark_slide,").format());
        assertEquals("", TipsDone.parse(null).format());
    }

    /** Round 3: full screen's hint, as the prototype, the first time only. */
    @Test public void fullScreensHintShowsTheFirstTimeAndIsGoneWhenItEnds() {
        Fake fake = new Fake();
        fake.home = false;
        FirstRunTips tips = new FirstRunTips(fake, fake);
        fake.fullScreen = true;
        tips.refresh();
        assertEquals(Tip.FULL_SCREEN, tips.shown());
        fake.fullScreen = false;
        tips.refresh();
        assertNull(tips.shown());
        assertTrue(TipsDone.parse(fake.stored).has(Tip.FULL_SCREEN));
        fake.fullScreen = true;
        tips.refresh();
        assertNull("shown once", tips.shown());
    }

    @Test public void navigationModesHintWinsOverHomesAndItsCrossSendsItAway() {
        Fake fake = new Fake();
        FirstRunTips tips = new FirstRunTips(fake, fake);
        tips.refresh();
        assertEquals(Tip.BAR_HOLD, tips.shown());
        fake.navigating = true;
        fake.busy = true;
        tips.refresh();
        assertEquals(Tip.NAVIGATION, tips.shown());
        tips.dismiss();
        assertTrue(TipsDone.parse(fake.stored).has(Tip.NAVIGATION));
        assertFalse("Home's own hints are not touched", TipsDone.parse(fake.stored).has(Tip.BAR_HOLD));
    }

    @Test public void theSituationsHintsAreNeverInHomesSequence() {
        TipsDone done = TipsDone.none().with(Tip.BAR_HOLD).with(Tip.MARK_SLIDE).with(Tip.HOME_HOLD);
        assertNull(done.next(true, false, true));
        assertFalse(done.anyLeft(true));
    }

    /** Round 4: hints stood for good; full screen's is the prototype's toast and goes after its time. */
    @Test public void fullScreensHintGoesAfterItsTimeAndNavigationsStaysWithTheMode() {
        Fake fake = new Fake();
        fake.home = false;
        FirstRunTips tips = new FirstRunTips(fake, fake);
        fake.fullScreen = true;
        tips.refresh();
        assertEquals(Tip.FULL_SCREEN, tips.shown());
        assertEquals(java.util.Collections.singletonList(FirstRunTips.FULL_SCREEN_HINT_MS), fake.delays);
        fake.later.get(0).run();
        assertNull("gone after its time, full screen or not", tips.shown());
        assertTrue(TipsDone.parse(fake.stored).has(Tip.FULL_SCREEN));

        fake.fullScreen = false;
        fake.navigating = true;
        tips.refresh();
        assertEquals(Tip.NAVIGATION, tips.shown());
        assertEquals("no timer for navigation's", 1, fake.later.size());
    }
}
