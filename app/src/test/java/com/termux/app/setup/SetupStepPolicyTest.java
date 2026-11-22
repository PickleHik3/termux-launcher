package com.termux.app.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SetupStepPolicyTest {

    @Test
    public void currentDotAndOutOfRangeAreInert() {
        assertFalse(SetupStepPolicy.canJump(1, 3, 5, 1));
        assertFalse(SetupStepPolicy.canJump(1, 3, 5, -1));
        assertFalse(SetupStepPolicy.canJump(1, 3, 5, 5));
    }

    @Test
    public void visitedDotsGoBackAndForwardAgain() {
        assertTrue(SetupStepPolicy.canJump(3, 3, 6, 0));
        assertTrue(SetupStepPolicy.canJump(1, 3, 6, 3));
    }

    @Test
    public void nextDotOpensOnlyOneStepAhead() {
        assertTrue(SetupStepPolicy.canJump(2, 2, 6, 3));
        assertFalse(SetupStepPolicy.canJump(2, 2, 6, 4));
        assertFalse(SetupStepPolicy.canJump(0, 0, 6, 5));
    }

    @Test
    public void reviewUnlocksAfterEverySectionWasVisited() {
        // Six dots, Review is index 5; sections 0..4 must all have been shown.
        assertFalse(SetupStepPolicy.canJump(2, 3, 6, 5));
        assertFalse(SetupStepPolicy.canJump(3, 3, 6, 5));
        assertTrue(SetupStepPolicy.canJump(4, 4, 6, 5));
        assertTrue(SetupStepPolicy.canJump(0, 5, 6, 5));
    }

    @Test
    public void twoDotWizardReachesReviewFromTheFirst() {
        assertTrue(SetupStepPolicy.canJump(0, 0, 2, 1));
        assertFalse(SetupStepPolicy.canJump(1, 1, 2, 1));
    }
}
