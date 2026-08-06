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
    public void autoReply_opensForTheNewestReplyCapableNotification() {
        // Notifications arrive newest-first and each card keeps only its first free-form action, so
        // target 0 is the latest conversation — which is what swiping the app's icon asks for. The
        // old "exactly one" rule made the gesture silently do nothing for any app the user talks on.
        org.junit.Assert.assertFalse(SuggestionBarView.shouldAutoOpenNotificationReply(0));
        org.junit.Assert.assertTrue(SuggestionBarView.shouldAutoOpenNotificationReply(1));
        org.junit.Assert.assertTrue(SuggestionBarView.shouldAutoOpenNotificationReply(2));
        org.junit.Assert.assertTrue(SuggestionBarView.shouldAutoOpenNotificationReply(5));
    }

    @Test
    public void aNewNotificationNeverThrowsAwayAHalfTypedReply() {
        // Rebuilding the popup is the right response to the list changing — unless the user is
        // mid-compose, in which case the change they care about is the one under their fingers.
        org.junit.Assert.assertTrue(
            SuggestionBarView.shouldDismissNotificationPopupOnKeyChange(true, false));
        org.junit.Assert.assertFalse(
            SuggestionBarView.shouldDismissNotificationPopupOnKeyChange(true, true));
        org.junit.Assert.assertFalse(
            SuggestionBarView.shouldDismissNotificationPopupOnKeyChange(false, false));
        org.junit.Assert.assertFalse(
            SuggestionBarView.shouldDismissNotificationPopupOnKeyChange(false, true));
    }
}
