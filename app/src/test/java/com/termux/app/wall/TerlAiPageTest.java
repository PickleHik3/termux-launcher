package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Every way into the Assistant under Terl lands on the AI page, or offers to turn it on. */
public class TerlAiPageTest {

    private final List<String> calls = new ArrayList<>();
    private boolean on;
    private Runnable offered;

    private final TerlAiPage.Host host = new TerlAiPage.Host() {
        @Override public boolean isOn() { return on; }
        @Override public void turnOn() { calls.add("turnOn"); on = true; }
        @Override public void goToPage() { calls.add("goToPage"); }
        @Override public void offerToTurnOn(@NonNull Runnable turnOn) { calls.add("offer"); offered = turnOn; }
    };

    @Test public void withThePageOnItGoesThere() {
        on = true;
        TerlAiPage.open(host);
        assertEquals(java.util.Collections.singletonList("goToPage"), calls);
    }

    @Test public void withThePageOffItOffersToTurnItOnAndGoesOnlyWhenTheUserAgrees() {
        TerlAiPage.open(host);
        assertEquals(java.util.Collections.singletonList("offer"), calls);
        assertFalse(on);
        offered.run();
        assertTrue(on);
        assertEquals(java.util.Arrays.asList("offer", "turnOn", "goToPage"), calls);
    }

    @Test public void theTipIsDueOnceOnHomeUnderTerlWhileThePageIsOff() {
        assertTrue(TerlAiPage.nudgeDue(true, true, false, false));
        assertFalse("already shown", TerlAiPage.nudgeDue(true, true, false, true));
        assertFalse("already on", TerlAiPage.nudgeDue(true, true, true, false));
        assertFalse("on a Workspace", TerlAiPage.nudgeDue(true, false, false, false));
        assertFalse("v1", TerlAiPage.nudgeDue(false, true, false, false));
    }
}
