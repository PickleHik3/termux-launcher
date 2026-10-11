package com.termux.app.statusbar;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import androidx.annotation.Nullable;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The clock's alignment, laid out by the real slot on the developer's phone (1080px at 420dpi,
 * the docked 65dp slot), in every state the slot can be in.
 *
 * <p>The alignment only ever moved the clock while it had the slot to itself. Beside the media row
 * or a pinned card the clock was always put at the leading edge, sized to its own paint, so a
 * paused player lingering in the shade was enough to make Left, Centre and Right look the same.
 * Left stays exactly as it was — the media and card fixes were all made against it.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w411dp-h918dp-420dpi")
public class TopPaneWidgetSlotAlignmentTest {

    private static final String LEFT = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT;
    private static final String CENTER = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER;
    private static final String RIGHT = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT;

    private Context mContext;
    private float mDensity;
    private int mWidth;
    private int mLeading;
    private int mGutter;
    private int mGap;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mDensity = mContext.getResources().getDisplayMetrics().density;
        mWidth = 1080;
        mLeading = StatusBarLensView.leadingCellWidthPx(mContext);
        mGutter = Math.round(12f * mDensity);
        mGap = Math.round(12f * mDensity);
        // The feed reads empty until the notification listener is connected.
        TopPaneFeed.setListenerConnected(true);
        TopPaneFeed.setPinned(null);
        TopPaneFeed.setMedia(null);
    }

    @After
    public void tearDown() {
        TopPaneFeed.setPinned(null);
        TopPaneFeed.setMedia(null);
        TopPaneFeed.setListenerConnected(false);
    }

    // ---- The clock alone ----------------------------------------------------

    @Test
    public void aloneEachAlignmentPaintsTheFaceWhereItSays() {
        Slot left = slot(LEFT, 65f);
        Slot center = slot(CENTER, 65f);
        Slot right = slot(RIGHT, 65f);
        assertEquals(TopPaneSlotMode.CLOCK_ONLY, left.view.getSlotMode());
        assertEquals(mLeading, left.paintedLeft(), 1f);
        assertEquals(mWidth / 2f, (center.paintedLeft() + center.paintedRight()) / 2f, 1f);
        assertEquals(mWidth - mGutter, right.paintedRight(), 1f);
    }

    // ---- Beside the media row -----------------------------------------------

    @Test
    public void besideMediaLeftKeepsTheClockAtTheLeadingEdgeAndTheRowAfterIt() {
        TopPaneFeed.setMedia(media());
        Slot left = slot(LEFT, 65f);
        assertEquals(TopPaneSlotMode.MEDIA, left.view.getSlotMode());
        assertEquals(mLeading, left.clock.getLeft());
        assertEquals(left.clock.getRight() + mGap, left.media.getLeft());
        assertEquals(mWidth - mGutter, left.media.getRight());
    }

    @Test
    public void besideMediaRightPutsTheClockAtTheTrailingEdgeAndTheRowBeforeIt() {
        TopPaneFeed.setMedia(media());
        Slot left = slot(LEFT, 65f);
        Slot right = slot(RIGHT, 65f);
        assertEquals("the clock ends at the gutter", mWidth - mGutter, right.clock.getRight());
        assertEquals("the face is no smaller than at Left",
            left.clock.getWidth(), right.clock.getWidth());
        assertEquals("the row starts after the place icon", mLeading, right.media.getLeft());
        assertEquals(right.clock.getLeft() - mGap, right.media.getRight());
    }

    @Test
    public void besideMediaCentreStandsWhereLeftDoesSoTheArtKeepsItsRoom() {
        TopPaneFeed.setMedia(media());
        Slot left = slot(LEFT, 65f);
        Slot center = slot(CENTER, 65f);
        assertEquals(left.clock.getLeft(), center.clock.getLeft());
        assertEquals(left.clock.getWidth(), center.clock.getWidth());
        assertEquals(left.media.getLeft(), center.media.getLeft());
        assertEquals(left.media.getWidth(), center.media.getWidth());
    }

    @Test
    public void besideMediaTheRowStaysOnTheDigitLineAtEveryAlignment() {
        TopPaneFeed.setMedia(media());
        int top = slot(LEFT, 65f).media.getTop();
        assertEquals(top, slot(CENTER, 65f).media.getTop());
        assertEquals(top, slot(RIGHT, 65f).media.getTop());
    }

    // ---- Beside pinned cards ------------------------------------------------

    @Test
    public void besidePinnedCardsRightPutsTheClockAtTheTrailingEdge() {
        TopPaneFeed.setPinned(Collections.singletonList(pin("a")));
        Slot left = slot(LEFT, 65f);
        Slot right = slot(RIGHT, 65f);
        assertEquals(TopPaneSlotMode.NOTIFICATIONS, right.view.getSlotMode());
        assertEquals(mLeading, left.clock.getLeft());
        assertEquals(left.clock.getRight() + mGap, left.cards.getLeft());
        assertEquals(mWidth - mGutter, right.clock.getRight());
        assertEquals(mLeading, right.cards.getLeft());
        assertEquals(right.clock.getLeft() - mGap, right.cards.getRight());
        assertEquals("the cards keep their height and air", left.cards.getTop(),
            right.cards.getTop());
        assertEquals(left.cards.getHeight(), right.cards.getHeight());
    }

    @Test
    public void besideCardsAndMediaBothShareTheRunOppositeTheClock() {
        TopPaneFeed.setPinned(Collections.singletonList(pin("a")));
        TopPaneFeed.setMedia(media());
        for (String alignment : new String[] {LEFT, CENTER, RIGHT}) {
            Slot slot = slot(alignment, 65f);
            assertEquals(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA, slot.view.getSlotMode());
            assertEquals(alignment, slot.cards.getLeft(), slot.media.getLeft());
            assertEquals(alignment, slot.cards.getRight(), slot.media.getRight());
            assertTrue(alignment + ": the cards never overlap the clock",
                slot.cards.getRight() <= slot.clock.getLeft()
                    || slot.cards.getLeft() >= slot.clock.getRight());
        }
        assertEquals(mWidth - mGutter, slot(RIGHT, 65f).clock.getRight());
    }

    @Test
    public void theFloatingSlotPlacesTheClockTheSameWay() {
        TopPaneFeed.setMedia(media());
        Slot right = slot(RIGHT, 69f);
        assertEquals(mWidth - mGutter, right.clock.getRight());
        assertEquals(mLeading, right.media.getLeft());
    }

    // ---- Helpers --------------------------------------------------------------

    private static TopPaneMediaState media() {
        return new TopPaneMediaState("com.music", "Weightless", "Marconi Union",
            "YouTube Music", null, 30_000L, 100_000L, false);
    }

    private static PinnedNotification pin(String key) {
        return new PinnedNotification(key, "com.whatsapp", "Amma", "WhatsApp", "hi", "rule",
            false, 1L);
    }

    private Slot slot(String alignment, float heightDp) {
        TopPaneWidgetSlot view = new TopPaneWidgetSlot(mContext);
        TerminalClockWidget clock = new TerminalClockWidget(mContext, null);
        clock.setId(R.id.terminal_clock_widget);
        // Flip, the shipped face: its cards are fixed geometry, so its width does not hang on
        // the JVM's stand-in fonts.
        clock.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP);
        PinnedNotificationsView cards = new PinnedNotificationsView(mContext);
        cards.setId(R.id.terminal_pinned_notifications);
        MediaWidgetView media = new MediaWidgetView(mContext);
        media.setId(R.id.terminal_media_widget);
        view.addView(clock);
        view.addView(cards);
        view.addView(media);
        view.onFinishInflate();
        clock.setAlignment(alignment);
        view.setClockAlignment(alignment);
        int height = Math.round(heightDp * mDensity);
        view.measure(View.MeasureSpec.makeMeasureSpec(mWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, mWidth, height);
        return new Slot(view, clock, cards, media);
    }

    private static final class Slot {
        final TopPaneWidgetSlot view;
        final TerminalClockWidget clock;
        final PinnedNotificationsView cards;
        final MediaWidgetView media;

        Slot(TopPaneWidgetSlot view, TerminalClockWidget clock,
             @Nullable PinnedNotificationsView cards, @Nullable MediaWidgetView media) {
            this.view = view;
            this.clock = clock;
            this.cards = cards;
            this.media = media;
        }

        float paintedLeft() {
            return clock.getLeft() + clock.paintedLeftPx();
        }

        float paintedRight() {
            return clock.getLeft() + clock.paintedRightPx();
        }

        float clockCentre() {
            return (paintedLeft() + paintedRight()) / 2f;
        }
    }
}
