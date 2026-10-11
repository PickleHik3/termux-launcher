package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

/** The Status bar settings' Media switch: off, the slot acts exactly as with nothing playing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w411dp-h918dp-420dpi")
public class TopPaneWidgetSlotMediaSwitchTest {

    private Context mContext;
    private TopPaneWidgetSlot mSlot;
    private MediaWidgetView mMedia;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TopPaneFeed.setListenerConnected(true);
        TopPaneFeed.setPinned(null);
        TopPaneFeed.setMedia(new TopPaneMediaState("com.music", "Weightless", "Marconi Union",
            "YouTube Music", null, 30_000L, 100_000L, true));
        mSlot = new TopPaneWidgetSlot(mContext);
        TerminalClockWidget clock = new TerminalClockWidget(mContext, null);
        clock.setId(R.id.terminal_clock_widget);
        clock.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP);
        PinnedNotificationsView cards = new PinnedNotificationsView(mContext);
        cards.setId(R.id.terminal_pinned_notifications);
        mMedia = new MediaWidgetView(mContext);
        mMedia.setId(R.id.terminal_media_widget);
        mSlot.addView(clock);
        mSlot.addView(cards);
        mSlot.addView(mMedia);
        mSlot.onFinishInflate();
    }

    @After
    public void tearDown() {
        TopPaneFeed.setPinned(null);
        TopPaneFeed.setMedia(null);
        TopPaneFeed.setListenerConnected(false);
    }

    @Test
    public void offTheClockKeepsTheSlotWhileSomethingPlays() {
        assertEquals(TopPaneSlotMode.MEDIA, mSlot.getSlotMode());

        mSlot.setMediaEnabled(false);
        layOut();

        assertEquals(TopPaneSlotMode.CLOCK_ONLY, mSlot.getSlotMode());
        assertEquals(View.GONE, mMedia.getVisibility());
    }

    @Test
    public void offPinnedCardsShareTheSlotWithTheClockAlone() {
        TopPaneFeed.setPinned(Collections.singletonList(new PinnedNotification("a",
            "com.whatsapp", "Amma", "WhatsApp", "hi", "rule", false, 1L)));
        mSlot.setMediaEnabled(false);
        TopPaneFeed.setMedia(new TopPaneMediaState("com.music", "Next", "Artist", "YouTube Music",
            null, 0L, 100_000L, true));
        mSlot.onTopPaneFeedChanged();

        assertEquals(TopPaneSlotMode.NOTIFICATIONS, mSlot.getSlotMode());
    }

    @Test
    public void backOnTheRowReturns() {
        mSlot.setMediaEnabled(false);
        mSlot.setMediaEnabled(true);
        layOut();

        assertEquals(TopPaneSlotMode.MEDIA, mSlot.getSlotMode());
        assertEquals(View.VISIBLE, mMedia.getVisibility());
    }

    private void layOut() {
        int width = 1080;
        int height = Math.round(65f * mContext.getResources().getDisplayMetrics().density);
        mSlot.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        mSlot.layout(0, 0, width, height);
    }
}
