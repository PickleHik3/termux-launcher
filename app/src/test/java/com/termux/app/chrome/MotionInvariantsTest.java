package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MotionInvariantsTest {

    private final MotionInvariants mInvariants = new MotionInvariants();
    private final List<String> mNames = new ArrayList<>();

    private void feed(long t, String event, String kv) {
        for (MotionInvariants.Violation v : mInvariants.onEvent(t, event, kv)) mNames.add(v.name);
    }

    private void crossingToSettle() {
        feed(1000, "crossing-begin", "revision=3");
        feed(1300, "wall-page-settled", "page=TERMINAL");
    }

    @Test
    public void aResizeAfterTheSettleViolates() {
        crossingToSettle();
        feed(1436, "pty-resize", "from=70x44 to=70x41 cause=layout returnHold=false");
        assertEquals(List.of("zero-resize-per-crossing"), mNames);
    }

    @Test
    public void aResizeDuringTheSlideViolates() {
        feed(1000, "crossing-begin", "revision=3");
        feed(1020, "pty-resize", "from=70x65 to=70x44 cause=layout returnHold=false");
        feed(1300, "wall-page-settled", "page=TERMINAL");
        assertEquals(List.of("zero-resize-per-crossing"), mNames);
    }

    @Test
    public void aCrossingIsReportedOnceHoweverManyResizesFollow() {
        feed(1000, "crossing-begin", "revision=3");
        feed(1020, "pty-resize", "from=70x65 to=70x44");
        feed(1100, "pty-resize", "from=70x44 to=70x41");
        crossingSettledOnly();
        feed(1436, "pty-resize", "from=70x41 to=70x40");
        assertEquals(List.of("zero-resize-per-crossing"), mNames);
    }

    private void crossingSettledOnly() {
        feed(1300, "wall-page-settled", "page=TERMINAL");
    }

    @Test
    public void aResizeLongAfterTheSettleDoesNotViolate() {
        crossingToSettle();
        feed(2600, "pty-resize", "from=70x44 to=70x41");
        assertTrue(mNames.toString(), mNames.isEmpty());
    }

    @Test
    public void aCrossingThatResizesNothingDoesNotViolate() {
        crossingToSettle();
        assertTrue(mNames.isEmpty());
    }

    @Test
    public void frostAfterSettleViolates() {
        crossingToSettle();
        feed(1400, "reflow-frost-start", "radiusPx=12");
        assertEquals(List.of("no-frost-at-landing"), mNames);
    }

    @Test
    public void frostLongAfterSettleDoesNot() {
        crossingToSettle();
        feed(1900, "reflow-frost-start", "radiusPx=12");
        assertTrue(mNames.isEmpty());
    }

    @Test
    public void stillKeptForAPageThatWentLiveViolates() {
        feed(1000, "crossing-begin", "revision=3");
        feed(1100, "still-dropped", "page=2");
        feed(1300, "wall-page-settled", "page=TERMINAL");
        feed(1400, "still-kept", "page=2 size=540x1200");
        assertEquals(List.of("no-still-after-live"), mNames);
    }

    @Test
    public void stillKeptForAnotherPageDoesNot() {
        feed(1000, "crossing-begin", "revision=3");
        feed(1100, "still-dropped", "page=2");
        feed(1300, "wall-page-settled", "page=TERMINAL");
        feed(1400, "still-kept", "page=5 size=540x1200");
        assertTrue(mNames.isEmpty());
    }

    @Test
    public void stillKeptDuringTheSlideClearsLive() {
        feed(1000, "crossing-begin", "revision=3");
        feed(1100, "still-dropped", "page=2");
        feed(1150, "still-kept", "page=2 size=1x1");
        feed(1300, "wall-page-settled", "page=TERMINAL");
        feed(1400, "still-kept", "page=2 size=1x1");
        assertTrue(mNames.isEmpty());
    }
}
