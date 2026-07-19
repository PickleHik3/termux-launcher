package com.termux.app.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OnboardingActivityTest {

    @Test
    public void completedTourNeverLaunchesAutomatically() {
        assertFalse(OnboardingActivity.shouldPrepareAutomaticLaunch(true, false, false));
        assertFalse(OnboardingActivity.shouldPrepareAutomaticLaunch(true, true, false));
    }

    @Test
    public void pendingNewInstallSurvivesActivityRecreation() {
        assertTrue(OnboardingActivity.shouldPrepareAutomaticLaunch(false, true, true));
    }

    @Test
    public void missingBootstrapStartsTourForNewInstall() {
        assertTrue(OnboardingActivity.shouldPrepareAutomaticLaunch(false, false, false));
    }

    @Test
    public void existingInstallIsNotInterruptedAfterUpgrade() {
        assertFalse(OnboardingActivity.shouldPrepareAutomaticLaunch(false, false, true));
    }
}
