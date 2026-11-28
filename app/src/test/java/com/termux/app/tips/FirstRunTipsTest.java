package com.termux.app.tips;

import static org.junit.Assert.assertEquals;
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
        final List<String> calls = new ArrayList<>();

        @Nullable @Override public String read() { return stored; }
        @Override public void write(@NonNull String done) { stored = done; }
        @Override public boolean homeInFront() { return home; }
        @Override public boolean busy() { return busy; }
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

    @Test public void unknownNamesAreDropped() {
        assertEquals("mark_slide", TipsDone.parse("nope, mark_slide,").format());
        assertEquals("", TipsDone.parse(null).format());
    }
}
