package com.termux.app;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarNotificationPopupTest {
    @Test
    public void adaptiveWidth_keepsPreferredHalfWidthForShortActionRows() {
        assertEquals(500, SuggestionBarView.adaptiveNotificationPopupWidth(500, 320, 240, 900));
    }

    @Test
    public void adaptiveWidth_expandsToFitActionsWithoutUsingTheFullCap() {
        assertEquals(680, SuggestionBarView.adaptiveNotificationPopupWidth(500, 680, 240, 900));
    }

    @Test
    public void adaptiveWidth_capsOversizedActionRows() {
        assertEquals(900, SuggestionBarView.adaptiveNotificationPopupWidth(500, 1100, 240, 900));
    }

    @Test
    public void autoReply_opensOnlyForOneUnambiguousDestination() {
        org.junit.Assert.assertFalse(SuggestionBarView.shouldAutoOpenNotificationReply(0));
        org.junit.Assert.assertTrue(SuggestionBarView.shouldAutoOpenNotificationReply(1));
        org.junit.Assert.assertFalse(SuggestionBarView.shouldAutoOpenNotificationReply(2));
    }
}
