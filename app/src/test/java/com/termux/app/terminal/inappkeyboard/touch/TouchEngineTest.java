package com.termux.app.terminal.inappkeyboard.touch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.termux.app.terminal.inappkeyboard.touch.TapModelTest.BACKSPACE;
import static com.termux.app.terminal.inappkeyboard.touch.TapModelTest.ESC;
import static com.termux.app.terminal.inappkeyboard.touch.TapModelTest.SPACE;

import juloo.keyboard2.TapGeometry;

import org.junit.Before;
import org.junit.Test;

public class TouchEngineTest {

    /** Row 0: nine characters 0..8 in 0..1.2; row 1: backspace, space bar (1..8), Esc. */
    private final TapGeometry g = TapModelTest.spaceGrid();
    private static final int B = 4;
    private static final int V = 3;
    private static final int N = 5;

    private TouchEngine engine;
    private GridMemory grid;
    /** Spaced so no key ever meets the per-minute cap. */
    private long now;

    @Before
    public void setUp() {
        engine = new TouchEngine();
        engine.onSwitch(true);
        grid = new GridMemory(g.keyCount);
        now = 1_000_000L;
    }

    private void tap(int key, float x, float y) {
        now += 3_000L;
        engine.onRelease(grid, g, Tap.plain(key, x, y, now));
    }

    private void tapCentre(int key) {
        tap(key, g.centerX(key), g.centerY(key));
    }

    /** Hit {@code hit} at a point, Backspace, then {@code meant}, quickly. */
    private void takeBack(int hit, float x, float y, int meant) {
        now += 3_000L;
        engine.onRelease(grid, g, Tap.plain(hit, x, y, now));
        now += 300L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += 300L;
        engine.onRelease(grid, g, Tap.plain(meant, g.centerX(meant), g.centerY(meant), now));
    }

    private void finishWarmUp() {
        while (engine.gate().state() == LearningGate.STATE_LEARNING)
            tapCentre(0);
    }

    @Test
    public void aimingAtSpaceAndLandingOnBBecomesASpaceOnceLearned() {
        float lowB = 1.05f; // b's bottom edge is 1.2
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 6; j++)
                tap(SPACE, 4.5f, 1.6f);
            for (int j = 0; j < 3; j++)
                tapCentre(B);
            takeBack(i % 2 == 0 ? B : V, i % 2 == 0 ? 4.5f : 3.5f, lowB, SPACE);
        }
        assertTrue(engine.gate().isCorrecting());
        // The lower part of b, and of v, now types a space.
        assertEquals(SPACE, engine.resolve(grid, g, B, 4.5f, 1.1f));
        assertEquals(SPACE, engine.resolve(grid, g, V, 3.5f, 1.1f));
        // b's centre is still b, and so is its upper half.
        assertEquals(B, engine.resolve(grid, g, B, 4.5f, 0.6f));
        assertEquals(B, engine.resolve(grid, g, B, 4.5f, 0.3f));
    }

    @Test
    public void warmUpLearnsButNeverCorrects() {
        for (int i = 0; i < 300; i++)
            takeBack(B, 4.5f, 1.1f, SPACE);
        assertEquals(LearningGate.STATE_LEARNING, engine.gate().state());
        assertTrue(grid.model().biasY(g, SPACE) < -0.3f);
        assertEquals(B, engine.resolve(grid, g, B, 4.5f, 1.15f));
    }

    @Test
    public void aTakeBackFilesTheMissAsMeantForTheRetypedKeyAtWeightThree() {
        takeBack(B, 4.5f, 1.1f, SPACE);
        engine.interrupt();
        // b learned nothing from the miss; space learned it three times over.
        assertEquals(0f, grid.model().counts()[B], 0f);
        assertEquals(TouchEngine.TAKE_BACK_WEIGHT + 1f, grid.model().counts()[SPACE], 0f);
        assertEquals(1, grid.log().takeBackCount(SPACE));
        assertEquals(1, engine.gate().warmUpTakeBacks());
    }

    @Test
    public void twoBackspacesAreNotATakeBackAndTheErasedTapIsDropped() {
        tap(B, 4.5f, 1.1f);
        now += 200L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += 200L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += 200L;
        engine.onRelease(grid, g, Tap.plain(SPACE, 4.5f, 1.8f, now));
        engine.interrupt();
        assertEquals(0f, grid.model().counts()[B], 0f);
        assertEquals(1f, grid.model().counts()[SPACE], 0f);
        assertEquals(0, engine.gate().warmUpTakeBacks());
    }

    @Test
    public void aSlowTakeBackIsNotOne() {
        tap(B, 4.5f, 1.1f);
        now += 1_000L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += TouchEngine.TAKE_BACK_WINDOW_MS + 100L;
        engine.onRelease(grid, g, Tap.plain(SPACE, 4.5f, 1.8f, now));
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(0f, grid.model().counts()[SPACE], 0f);
        engine.interrupt();
        // The erased miss is never filed; the space typed after the pause is.
        assertEquals(0f, grid.model().counts()[B], 0f);
        assertEquals(1f, grid.model().counts()[SPACE], 0f);
    }

    @Test
    public void eachStepOfATakeBackMayTakeUpToTheWindow() {
        tap(B, 4.5f, 1.1f);
        now += 1_500L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += 1_500L;
        engine.onRelease(grid, g, Tap.plain(SPACE, 4.5f, 1.8f, now));
        assertEquals(1, engine.gate().warmUpTakeBacks());
    }

    // Round 2: a miss noticed a few keys later.

    private static final int L = 0;
    private static final int S = 1;
    private static final int T = 2;
    private static final int A = 7;

    /** A plain tap at the centre of {@code key}, a fifth of a second after the last keystroke. */
    private void quick(int key) {
        quickAt(key, g.centerX(key), g.centerY(key));
    }

    private void quickAt(int key, float x, float y) {
        now += 200L;
        engine.onRelease(grid, g, Tap.plain(key, x, y, now));
    }

    private void backspaces(int times) {
        for (int i = 0; i < times; i++) {
            now += 200L;
            engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        }
    }

    /** {@code l s b s t a}, the {@code b} low on its key: a space aimed too high. */
    private void typeMissThenThreeMore() {
        quick(L);
        quick(S);
        quickAt(B, 4.5f, 1.1f);
        quick(S);
        quick(T);
        quick(A);
    }

    @Test
    public void aMissNoticedThreeKeysLaterIsATakeBackIntoSpace() {
        typeMissThenThreeMore();
        backspaces(4);
        quick(SPACE);
        quick(S);
        quick(T);
        quick(A);
        assertEquals(1, engine.gate().warmUpTakeBacks());
        assertEquals(1, grid.log().takeBackCount(SPACE));
        engine.interrupt();
        // b never learned from the miss; space learned it three times over, plus its own tap.
        assertEquals(0f, grid.model().counts()[B], 0f);
        assertEquals(TouchEngine.TAKE_BACK_WEIGHT + 1f, grid.model().counts()[SPACE], 0f);
        assertTrue(grid.model().biasY(g, SPACE) < 0f);
        // The erased s, t and a were dropped; the retyped ones and the surviving l and s landed.
        assertEquals(1f, grid.model().counts()[L], 0f);
        assertEquals(2f, grid.model().counts()[S], 0f);
        assertEquals(1f, grid.model().counts()[T], 0f);
        assertEquals(1f, grid.model().counts()[A], 0f);
    }

    @Test
    public void twoDifferencesAreEditingAndTeachNothing() {
        typeMissThenThreeMore();
        backspaces(4);
        quick(SPACE);
        quick(S);
        quick(V); // t's neighbour: a second difference
        quick(A);
        engine.interrupt();
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(0f, grid.model().counts()[B], 0f);
        assertEquals(1f, grid.model().counts()[SPACE], 0f);
    }

    @Test
    public void aDifferenceThatIsNotANeighbourTeachesNothing() {
        quick(S);
        quickAt(B, 4.5f, 1.1f);
        quick(T);
        backspaces(2);
        quick(8); // four keys from b
        quick(T);
        engine.interrupt();
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(0f, grid.model().counts()[B], 0f);
        // The retyped keys are new text and land.
        assertEquals(1f, grid.model().counts()[8], 0f);
    }

    @Test
    public void aShorterRetypeTeachesNothing() {
        typeMissThenThreeMore();
        backspaces(4);
        quick(SPACE);
        quick(S);
        engine.interrupt();
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(0f, grid.model().counts()[B], 0f);
    }

    @Test
    public void backspacingPastTheHoldTeachesNothingAndDoesNotCrash() {
        // Ten taps: the first two drop out of the hold and land; the miss is still held.
        quick(L);
        quick(T);
        typeMissThenThreeMore();
        quick(L);
        quick(T);
        assertEquals(TouchEngine.HOLD, engine.heldCount());
        assertEquals(1f, grid.model().counts()[L], 0f);
        backspaces(TouchEngine.HOLD + 1);
        assertEquals(0, engine.heldCount());
        // Retype everything the nine Backspaces erased, the miss now a space.
        quick(T);
        quick(L);
        quick(S);
        quick(SPACE);
        quick(S);
        quick(T);
        quick(A);
        quick(L);
        quick(T);
        engine.interrupt();
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(0f, grid.model().counts()[B], 0f);
    }

    @Test
    public void aLongPauseInsideTheRunTeachesNothing() {
        typeMissThenThreeMore();
        backspaces(4);
        quick(SPACE);
        quick(S);
        now += TouchEngine.TAKE_BACK_WINDOW_MS + 100L;
        engine.onRelease(grid, g, Tap.plain(T, g.centerX(T), g.centerY(T), now));
        quick(A);
        engine.interrupt();
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(0f, grid.model().counts()[B], 0f);
    }

    @Test
    public void erasedTapsAreNeverFiledEvenWithNoRetype() {
        quick(S);
        quickAt(B, 4.5f, 1.1f);
        quick(T);
        backspaces(2);
        engine.interrupt();
        assertEquals(1f, grid.model().counts()[S], 0f);
        assertEquals(0f, grid.model().counts()[B], 0f);
        assertEquals(0f, grid.model().counts()[T], 0f);
    }

    @Test
    public void aBackspaceThatIsNotAPlainTapDropsEverythingHeld() {
        quick(S);
        quick(T);
        now += 200L;
        // A held Backspace repeats: how much it erased is unknown.
        engine.onRelease(grid, g, new Tap(BACKSPACE, BACKSPACE, 0.5f, 1.8f, false, false,
            now, now + 900L, 0f));
        engine.interrupt();
        assertEquals(0f, grid.model().counts()[S], 0f);
        assertEquals(0f, grid.model().counts()[T], 0f);
    }

    @Test
    public void aTapThatDropsOutOfTheHoldLands() {
        for (int i = 0; i < TouchEngine.HOLD; i++)
            quick(L);
        assertEquals(0f, grid.model().counts()[L], 0f);
        quick(T);
        assertEquals(1f, grid.model().counts()[L], 0f);
        assertEquals(TouchEngine.HOLD, engine.heldCount());
    }

    @Test
    public void enterFilesTheHeldTaps() {
        TapGeometry withEnter = withEnter();
        GridMemory enterGrid = new GridMemory(withEnter.keyCount);
        int enter = withEnter.keyCount - 1;
        for (int key : new int[]{L, S, T}) {
            now += 200L;
            engine.onRelease(enterGrid, withEnter,
                Tap.plain(key, withEnter.centerX(key), withEnter.centerY(key), now));
        }
        assertEquals(3, engine.heldCount());
        assertEquals(0f, enterGrid.model().counts()[L], 0f);
        now += 200L;
        engine.onRelease(enterGrid, withEnter, Tap.plain(enter, 9.5f, 1.8f, now));
        assertEquals(0, engine.heldCount());
        assertEquals(1f, enterGrid.model().counts()[L], 0f);
        assertEquals(1f, enterGrid.model().counts()[S], 0f);
        assertEquals(1f, enterGrid.model().counts()[T], 0f);
        // Enter itself is never learned.
        assertEquals(0f, enterGrid.model().counts()[enter], 0f);
    }

    /** {@link TapModelTest#spaceGrid()} with an Enter key at the end of the bottom row. */
    private static TapGeometry withEnter() {
        TapGeometry s = TapModelTest.spaceGrid();
        int n = s.keyCount + 1;
        float[] left = java.util.Arrays.copyOf(s.left, n);
        float[] top = java.util.Arrays.copyOf(s.top, n);
        float[] right = java.util.Arrays.copyOf(s.right, n);
        float[] bottom = java.util.Arrays.copyOf(s.bottom, n);
        int[] row = java.util.Arrays.copyOf(s.row, n);
        int[] role = java.util.Arrays.copyOf(s.role, n);
        left[n - 1] = 9f;
        right[n - 1] = 10f;
        top[n - 1] = 1.2f;
        bottom[n - 1] = 2.4f;
        row[n - 1] = 1;
        role[n - 1] = TapGeometry.ROLE_OTHER;
        return new TapGeometry(left, top, right, bottom, row, role, "space+enter");
    }

    @Test
    public void aRetypeOnAKeyThatIsNotANeighbourIsNotATakeBack() {
        takeBack(0, 0.5f, 0.6f, 8);
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(0f, grid.model().counts()[8], 0f);
    }

    @Test
    public void retypingTheSameKeyIsNotATakeBack() {
        takeBack(B, 4.5f, 0.6f, B);
        assertEquals(0, engine.gate().warmUpTakeBacks());
    }

    @Test
    public void anythingBetweenEndsTheTakeBack() {
        tap(B, 4.5f, 1.05f);
        now += 200L;
        engine.interrupt();
        now += 200L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += 200L;
        engine.onRelease(grid, g, Tap.plain(SPACE, 4.5f, 1.8f, now));
        assertEquals(0, engine.gate().warmUpTakeBacks());
        // The interrupted tap was filed as landed before the Backspace arrived.
        assertEquals(1f, grid.model().counts()[B], 0f);
    }

    @Test
    public void escIsNeverTakenBackAndStillLearnsFromLandedTaps() {
        // Esc, Backspace, space: Backspace does not undo an Esc.
        takeBack(ESC, 8.5f, 1.6f, SPACE);
        engine.interrupt();
        assertEquals(0, engine.gate().warmUpTakeBacks());
        assertEquals(1f, grid.model().counts()[ESC], 0f);
        assertEquals(1f, grid.model().counts()[SPACE], 0f);
        // And a miss on space is never taken back into Esc.
        takeBack(SPACE, 7.9f, 1.8f, ESC);
        assertEquals(0, engine.gate().warmUpTakeBacks());
    }

    @Test
    public void swipesLongPressesAndModifiedPressesTeachNothingAndEndTheChain() {
        now += 3_000L;
        engine.onRelease(grid, g, new Tap(B, B, 4.5f, 0.6f, false, false, now, now + 50, 0f));
        now += 3_000L;
        engine.onRelease(grid, g, new Tap(B, B, 4.5f, 0.6f, true, true, now, now + 50, 0f));
        engine.interrupt();
        assertEquals(0f, grid.model().counts()[B], 0f);
        assertEquals(0, grid.log().sampleCount(B));
        assertEquals(0, engine.gate().warmUpTaps());

        tap(B, 4.5f, 1.1f);
        now += 200L;
        engine.onRelease(grid, g, new Tap(BACKSPACE, BACKSPACE, 0.5f, 1.8f, true, true, now,
            now, 0f));
        now += 200L;
        engine.onRelease(grid, g, Tap.plain(SPACE, 4.5f, 1.8f, now));
        assertEquals(0, engine.gate().warmUpTakeBacks());
    }

    @Test
    public void anOutlierIsNotLearned() {
        // 0.45 right and 0.5 down of b's centre: more than half a key away.
        tap(B, 4.95f, 1.1f);
        engine.interrupt();
        assertEquals(0f, grid.model().counts()[B], 0f);
        // But it is still recorded.
        assertEquals(1, grid.log().sampleCount(B));
    }

    @Test
    public void aWideKeysOutlierTestIgnoresHowFarAlongItTheTapLanded() {
        tap(SPACE, 1.2f, 1.8f);
        engine.interrupt();
        assertEquals(1f, grid.model().counts()[SPACE], 0f);
    }

    @Test
    public void eachKeyAcceptsOnlySoManyObservationsAMinute() {
        long minuteStart = 60_000L * 1_000L;
        for (int i = 0; i < 100; i++)
            engine.onRelease(grid, g, Tap.plain(B, 4.5f, 0.6f, minuteStart + i * 100L));
        engine.interrupt();
        assertEquals(GridMemory.MAX_OBSERVATIONS_PER_KEY_PER_MINUTE,
            grid.model().counts()[B], 0f);
        // Counting for warm-up is not capped.
        assertEquals(100, engine.gate().warmUpTaps());
    }

    @Test
    public void sixtySpacesAMinuteAreAllLearned() {
        long minuteStart = 60_000L * 2_000L;
        for (int i = 0; i < 60; i++)
            engine.onRelease(grid, g, Tap.plain(SPACE, 4.5f, 1.8f, minuteStart + i * 1_000L));
        engine.interrupt();
        assertEquals(60f, grid.model().counts()[SPACE], 0f);
    }

    @Test
    public void correctedTapsTrainTheKeyTheGridGaveThem() {
        now += 3_000L;
        engine.onRelease(grid, g, Tap.corrected(B, SPACE, 4.5f, 1.05f, now));
        engine.interrupt();
        assertEquals(1f, grid.model().counts()[B], 0f);
        assertEquals(0f, grid.model().counts()[SPACE], 0f);
    }

    @Test
    public void takingBackACorrectionTeachesTheRawKey() {
        now += 3_000L;
        engine.onRelease(grid, g, Tap.corrected(B, SPACE, 4.5f, 1.1f, now));
        now += 300L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += 300L;
        engine.onRelease(grid, g, Tap.plain(B, 4.5f, 0.6f, now));
        assertEquals(1, engine.gate().warmUpTakeBacks());
        assertEquals(TouchEngine.TAKE_BACK_WEIGHT, grid.model().counts()[B], 0f);
    }

    @Test
    public void theDriftAlarmPausesCorrectionAndResumeRestartsTheWarmUp() {
        finishWarmUp();
        assertTrue(engine.gate().isCorrecting());
        assertEquals(0f, engine.gate().baseline(), 0f);
        for (int i = 0; i < LearningGate.DRIFT_WINDOW; i++)
            tapCentre(0);
        assertTrue(engine.gate().isCorrecting());
        // Against a zero baseline one take-back is chance; four are not.
        takeBack(B, 4.5f, 1.1f, SPACE);
        assertTrue(engine.gate().isCorrecting());
        for (int i = 0; i < 3; i++)
            takeBack(B, 4.5f, 1.1f, SPACE);
        assertEquals(LearningGate.STATE_PAUSED, engine.gate().state());
        assertFalse(engine.gate().isCorrecting());
        assertEquals(B, engine.resolve(grid, g, B, 4.5f, 1.15f));

        long learned = engine.tapsLearned();
        engine.resume();
        assertEquals(LearningGate.STATE_LEARNING, engine.gate().state());
        assertEquals(0, engine.gate().warmUpTaps());
        assertEquals(learned, engine.tapsLearned());
    }

    @Test
    public void turningTheSwitchOnStartsAWarmUp() {
        finishWarmUp();
        assertFalse(engine.onSwitch(true));
        assertTrue(engine.gate().isCorrecting());
        assertTrue(engine.onSwitch(false));
        assertTrue(engine.onSwitch(true));
        assertEquals(LearningGate.STATE_LEARNING, engine.gate().state());
    }

    @Test
    public void theLogHoldsABoundedBucketPerKey() {
        for (int i = 0; i < 100; i++)
            tapCentre(B);
        assertEquals(KeyLog.SAMPLES_PER_KEY, grid.log().sampleCount(B));
        assertEquals(0, grid.log().sampleCount(N));
    }

    @Test
    public void aMismatchedGridIsIgnored() {
        GridMemory other = new GridMemory(3);
        now += 3_000L;
        engine.onRelease(other, g, Tap.plain(B, 4.5f, 0.6f, now));
        engine.interrupt();
        assertTrue(other.isEmpty());
        assertEquals(B, engine.resolve(other, g, B, 4.5f, 1.15f));
    }
}
