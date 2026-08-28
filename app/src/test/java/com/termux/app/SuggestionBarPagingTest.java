package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.HapticFeedbackConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
/**
 * The page arithmetic itself now lives in {@code DockPagingModel} and is covered by
 * {@code DockPagingModelTest}; what is left here is the row's own platform-facing choice.
 */
public class SuggestionBarPagingTest {
    @Test
    public void pinnedPageHaptics_distinguishPinnedAndMostUsedDestinations() {
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK,
            SuggestionBarView.pinnedPageTransitionHaptic(false, 34));
        assertEquals(HapticFeedbackConstants.GESTURE_END,
            SuggestionBarView.pinnedPageTransitionHaptic(true, 34));
        assertEquals(HapticFeedbackConstants.CLOCK_TICK,
            SuggestionBarView.pinnedPageTransitionHaptic(false, Build.VERSION_CODES.P));
        assertEquals(HapticFeedbackConstants.CONTEXT_CLICK,
            SuggestionBarView.pinnedPageTransitionHaptic(true, Build.VERSION_CODES.P));
    }
}
