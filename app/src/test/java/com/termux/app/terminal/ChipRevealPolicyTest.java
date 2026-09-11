package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChipRevealPolicyTest {

    @Test
    public void tappingTheSelectedChipRevealsItsClose() {
        ChipRevealPolicy policy = new ChipRevealPolicy();

        assertTrue(policy.onChipTap(1, 1, 1_000L));

        assertEquals(1, policy.revealedIndex());
        assertEquals(1_000L + ChipRevealPolicy.REVEAL_DURATION_MS, policy.hideAt());
    }

    @Test
    public void tappingAnUnselectedChipStillJustSelectsIt() {
        ChipRevealPolicy policy = new ChipRevealPolicy();

        assertFalse(policy.onChipTap(0, 1, 1_000L));

        assertEquals(ChipRevealPolicy.NONE, policy.revealedIndex());
    }

    @Test
    public void tappingTheRevealedChipAgainPutsTheCloseAway() {
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onChipTap(1, 1, 1_000L);

        assertTrue(policy.onChipTap(1, 1, 1_200L));

        assertEquals(ChipRevealPolicy.NONE, policy.revealedIndex());
    }

    @Test
    public void tappingAnotherChipHidesTheCloseAndSelects() {
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onChipTap(1, 1, 1_000L);

        assertFalse(policy.onChipTap(2, 1, 1_200L));

        assertEquals(ChipRevealPolicy.NONE, policy.revealedIndex());
    }

    @Test
    public void aTouchElsewhereOnTheRowHidesTheClose() {
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onChipTap(0, 0, 1_000L);

        policy.onTouchElsewhere();

        assertFalse(policy.isRevealed());
    }

    @Test
    public void aDragNeverRevealsAnythingEvenWhenItEndsOnTheSelectedChip() {
        // The chip scroll and the status bar's pull both arrive here: a stream that moved is not a
        // tap, and must not leave a × behind when the finger lifts over the chip it started on.
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onTouchDown();

        policy.onScrollStarted();

        assertFalse(policy.onChipTap(1, 1, 1_000L));
        assertFalse(policy.isRevealed());
    }

    @Test
    public void aDragHidesACloseThatWasAlreadyShowing() {
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onChipTap(1, 1, 1_000L);
        policy.onTouchDown();

        policy.onScrollStarted();

        assertFalse(policy.isRevealed());
    }

    @Test
    public void theNextFingerCanRevealAgainAfterADrag() {
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onTouchDown();
        policy.onScrollStarted();

        policy.onTouchDown();

        assertTrue(policy.onChipTap(1, 1, 2_000L));
        assertEquals(1, policy.revealedIndex());
    }

    @Test
    public void aSelectionOrWindowListThatMovedHidesTheClose() {
        ChipRevealPolicy selection = new ChipRevealPolicy();
        selection.onChipTap(1, 1, 1_000L);
        selection.onSelectionChanged();
        assertFalse(selection.isRevealed());

        ChipRevealPolicy items = new ChipRevealPolicy();
        items.onChipTap(1, 1, 1_000L);
        items.onItemsChanged();
        assertFalse(items.isRevealed());
    }

    @Test
    public void theCloseLapsesOnItsOwn() {
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onChipTap(0, 0, 1_000L);

        assertFalse(policy.onTimeout(1_000L + ChipRevealPolicy.REVEAL_DURATION_MS - 1));
        assertTrue(policy.isRevealed());

        assertTrue(policy.onTimeout(1_000L + ChipRevealPolicy.REVEAL_DURATION_MS));
        assertFalse(policy.isRevealed());
    }

    @Test
    public void aStaleTimerLeavesAFreshRevealAlone() {
        ChipRevealPolicy policy = new ChipRevealPolicy();
        policy.onChipTap(0, 0, 1_000L);
        policy.onChipTap(0, 0, 1_100L);
        policy.onChipTap(0, 0, 5_500L);

        assertFalse(policy.onTimeout(5_000L));

        assertEquals(0, policy.revealedIndex());
    }

    @Test
    public void aTimerThatFiresWithNothingShowingIsHarmless() {
        ChipRevealPolicy policy = new ChipRevealPolicy();

        assertFalse(policy.onTimeout(9_000L));

        assertEquals(ChipRevealPolicy.NONE, policy.revealedIndex());
    }
}
