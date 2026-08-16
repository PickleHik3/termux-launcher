package com.termux.app.statusbar;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.app.Application;
import android.os.Build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TopPaneFullRowPolicyTest {
    @Test public void clockOnlyPreservesZeroAndCentresOne() {
        Rect normal = new Rect(12, 0, 388, 68);
        TopPaneFullRowPolicy.Result zero = calculate(TopPaneSlotMode.CLOCK_ONLY, 0, 400,
            normal, new Rect(), new Rect(), 0f, false);
        assertEquals(normal, zero.clock);
        TopPaneFullRowPolicy.Result one = calculate(TopPaneSlotMode.CLOCK_ONLY, 0, 400,
            normal, new Rect(), new Rect(), 1f, false);
        assertEquals(140, one.clock.left);
        assertEquals(260, one.clock.right);
    }

    @Test public void allModesPinsWidthsAndDirectionsDoNotOverlap() {
        for (TopPaneSlotMode mode : TopPaneSlotMode.values()) {
            for (int pins = 0; pins <= 3; pins++) {
                for (int width : new int[] {80, 320, 720}) {
                    Rect clock = new Rect(12, 0, 132, 68);
                    Rect notifications = mode.showsNotifications()
                        ? new Rect(145, 5, 280, 63) : new Rect();
                    Rect media = mode.showsMedia() ? new Rect(145, 5, 388, 63) : new Rect();
                    TopPaneFullRowPolicy.Result zero = calculate(mode, pins, width,
                        clock, notifications, media, 0f, false);
                    assertEquals(clock, zero.clock);
                    assertEquals(notifications, zero.notifications);
                    assertEquals(media, zero.media);
                    TopPaneFullRowPolicy.Result ltr = calculate(mode, pins, width,
                        clock, notifications, media, 1f, false);
                    assertTrue(ltr.clock.left >= 0 && ltr.clock.right <= width);
                    if (!ltr.notifications.isEmpty()) {
                        assertTrue(ltr.clock.right <= ltr.notifications.left);
                    }
                    if (!ltr.media.isEmpty()) {
                        Rect previous = ltr.notifications.isEmpty() ? ltr.clock : ltr.notifications;
                        assertTrue(previous.right <= ltr.media.left);
                    }
                    TopPaneFullRowPolicy.Result rtl = calculate(mode, pins, width,
                        clock, notifications, media, 1f, true);
                    assertEquals(width - ltr.clock.right, rtl.clock.left);
                }
            }
        }
    }

    @Test public void verticalLaneStartsContinuouslyAtHorizontalSeparationThreshold() {
        Rect normalNotifications = new Rect(145, 0, 388, 28);
        Rect normalMedia = new Rect(145, 40, 388, 68);
        Rect clock = new Rect(12, 0, 132, 68);
        TopPaneFullRowPolicy.Result full = calculate(
            TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA, 1, 400, clock,
            normalNotifications, normalMedia, 1f, false);
        float threshold = TopPaneFullRowPolicy.horizontalSeparationProgress(
            normalNotifications, normalMedia, full.notifications, full.media);
        assertTrue(threshold > .9f && threshold < 1f);

        TopPaneFullRowPolicy.Result at = calculate(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA,
            1, 400, clock, normalNotifications, normalMedia,
            threshold, false);
        TopPaneFullRowPolicy.Result after = calculate(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA,
            1, 400, clock, normalNotifications, normalMedia,
            Math.min(1f, threshold + .001f), false);
        assertEquals(normalNotifications.top, at.notifications.top);
        assertEquals(normalMedia.top, at.media.top);
        assertTrue(Math.abs(after.notifications.top - at.notifications.top) <= 1);
        assertTrue(Math.abs(after.media.top - at.media.top) <= 1);
    }

    private static TopPaneFullRowPolicy.Result calculate(TopPaneSlotMode mode, int pins,
                                                          int width, Rect clock,
                                                          Rect notifications, Rect media,
                                                          float progress, boolean rtl) {
        return TopPaneFullRowPolicy.calculate(mode, pins, width, 68, 12, 12,
            120, notifications.isEmpty() ? 0 : 112, media.isEmpty() ? 0 : 112,
            clock, notifications, media, progress, rtl);
    }
}
