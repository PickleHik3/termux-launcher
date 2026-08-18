package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;
import com.termux.app.terminal.TerminalClockWidget;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TopPaneWidgetSlotFullTest {
    @After public void clearFeed() {
        TopPaneFeed.setMedia(null); TopPaneFeed.setPinned(Collections.emptyList());
        TopPaneFeed.setListenerConnected(false);
    }

    @Test public void zeroMatchesNormalAndOneCentresClock() {
        TopPaneWidgetSlot slot = inflate();
        layout(slot, 400, 68);
        View clock = slot.findViewById(R.id.terminal_clock_widget);
        int normalLeft = clock.getLeft(); int normalRight = clock.getRight();
        slot.setFullExpansionProgress(0f); layout(slot, 400, 68);
        assertEquals(normalLeft, clock.getLeft()); assertEquals(normalRight, clock.getRight());
        slot.setFullExpansionProgress(1f); layout(slot, 400, 68);
        assertEquals(200, (clock.getLeft() + clock.getRight()) / 2, 1);
    }

    @Test public void feedChangeMidProgressKeepsOneOrderedNonOverlappingRow() {
        TopPaneFeed.setListenerConnected(true);
        TopPaneFeed.setMedia(new TopPaneMediaState("pkg", "Title", "Artist", "App",
            null, 1, 10, true));
        TopPaneWidgetSlot slot = inflate();
        slot.setFullExpansionProgress(.5f); layout(slot, 480, 68);
        TopPaneFeed.setPinned(Collections.singletonList(new PinnedNotification("k", "pkg",
            "Sender", "App", "Body", "rule", false, 1)));
        slot.onTopPaneFeedChanged();
        layout(slot, 480, 68);
        View clock = slot.findViewById(R.id.terminal_clock_widget);
        View notifications = slot.findViewById(R.id.terminal_pinned_notifications);
        View media = slot.findViewById(R.id.terminal_media_widget);
        assertEquals(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA, slot.getSlotMode());
        assertTrue(!intersects(clock, notifications));
        assertTrue(!intersects(notifications, media));
        assertEquals(0f, clock.getTranslationX(), 0f);
        assertEquals(0f, notifications.getTranslationY(), 0f);
        assertEquals(0f, media.getTranslationX(), 0f);
    }

    @Test public void threePinHeaderInsetIsIdenticalAtZeroAndTransitionsToFull() {
        TopPaneFeed.setListenerConnected(true);
        TopPaneFeed.setPinned(Arrays.asList(
            new PinnedNotification("1", "pkg", "One", "App", "Body", "rule", false, 1),
            new PinnedNotification("2", "pkg", "Two", "App", "Body", "rule", false, 2),
            new PinnedNotification("3", "pkg", "Three", "App", "Body", "rule", false, 3)));
        TopPaneWidgetSlot slot = inflate();
        layout(slot, 400, 68);
        PinnedNotificationsView notifications = slot.findViewById(
            R.id.terminal_pinned_notifications);
        float normalInset = ReflectionHelpers.getField(notifications, "mHeaderInsetStart");
        assertTrue(normalInset > 0f);
        slot.setFullExpansionProgress(0f); layout(slot, 400, 68);
        assertEquals(normalInset,
            ReflectionHelpers.<Float>getField(notifications, "mHeaderInsetStart"), 0f);
        slot.setFullExpansionProgress(.5f); layout(slot, 400, 68);
        assertEquals(normalInset * .5f,
            ReflectionHelpers.<Float>getField(notifications, "mHeaderInsetStart"), 1f);
        slot.setFullExpansionProgress(1f); layout(slot, 400, 68);
        assertEquals(0f,
            ReflectionHelpers.<Float>getField(notifications, "mHeaderInsetStart"), 0f);
    }

    private static TopPaneWidgetSlot inflate() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        TopPaneWidgetSlot slot = new TopPaneWidgetSlot(context);
        TerminalClockWidget clock = new TerminalClockWidget(context, null);
        clock.setId(R.id.terminal_clock_widget);
        slot.addView(clock);
        PinnedNotificationsView notifications = new PinnedNotificationsView(context);
        notifications.setId(R.id.terminal_pinned_notifications);
        slot.addView(notifications);
        MediaWidgetView media = new MediaWidgetView(context);
        media.setId(R.id.terminal_media_widget);
        slot.addView(media);
        slot.onFinishInflate();
        slot.setVisibility(View.VISIBLE);
        return slot;
    }
    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static boolean intersects(View a, View b) {
        return a.getLeft() < b.getRight() && b.getLeft() < a.getRight()
            && a.getTop() < b.getBottom() && b.getTop() < a.getBottom();
    }
}
