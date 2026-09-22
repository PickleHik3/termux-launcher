package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

/**
 * Holding a card in the widget picker takes the widget out of the sheet and lets the finger
 * choose the cell it lands on. The tap is left exactly as it was: it still drops the widget in
 * the first free cell without anything leaving the sheet.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerCarryToCellTest {
    /** Comfortably past the hold the card waits out before it hands the widget to the finger. */
    private static final long PAST_THE_HOLD_MS = ViewConfiguration.getLongPressTimeout() + 100L;
    /** The provider the fixture offers measures 2x2 on the 5x4 grid. */
    private static final WidgetCellRect FIRST_FREE = new WidgetCellRect(0, 0, 2, 2);
    private static final WidgetCellRect CHOSEN = new WidgetCellRect(2, 3, 4, 5);

    @Test public void aHeldCardLeavesTheSheetAndTheDropChoosesTheCell() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(false);
        View card = openCard(fixture);

        hold(fixture, card);
        assertTrue("the widget is in the air", fixture.pane.widgetDragLayer().isLifted());
        assertFalse("and the sheet it came out of has gone", fixture.pane.picker().isOpen());
        assertEquals("nothing is bound while the finger is still down", 0,
            fixture.platform.allocations);

        Rect target = paneBounds(fixture, CHOSEN);
        move(fixture, target.centerX(), target.centerY());
        assertEquals("the page outlines the cell under the finger", target,
            fixture.pane.widgetDragLayer().ghostBounds());
        assertFalse(fixture.pane.widgetDragLayer().ghostBlocked());

        up(fixture, target.centerX(), target.centerY());
        assertEquals(1, fixture.platform.allocations);
        assertEquals(1, fixture.repository.records().size());
        assertEquals("the widget landed where it was dropped, not in the first free cell",
            CHOSEN, fixture.repository.records().get(0).cell);
        assertFalse(fixture.pane.widgetDragLayer().isLifted());
        assertNull(fixture.pane.widgetDragLayer().ghostBounds());
    }

    @Test public void aTapStillFillsTheFirstFreeCellAndCarriesNothing() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(false);
        View card = openCard(fixture);

        send(card, MotionEvent.ACTION_DOWN, 10, 10);
        send(card, MotionEvent.ACTION_UP, 10, 10);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertFalse("a tap never lifts anything", fixture.pane.widgetDragLayer().isLifted());
        assertEquals(1, fixture.repository.records().size());
        assertEquals(FIRST_FREE, fixture.repository.records().get(0).cell);
    }

    @Test public void aPressThatSlidesIntoAScrollIsNotAHold() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(false);
        View card = openCard(fixture);

        send(card, MotionEvent.ACTION_DOWN, 10, 10);
        send(card, MotionEvent.ACTION_MOVE, 10, 400);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(PAST_THE_HOLD_MS, TimeUnit.MILLISECONDS);

        assertFalse(fixture.pane.widgetDragLayer().isLifted());
        assertTrue("the sheet is still the user's to scroll", fixture.pane.picker().isOpen());
    }

    @Test public void lettingGoOffTheGridAddsNothing() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(false);
        View card = openCard(fixture);

        hold(fixture, card);
        // Above the pane entirely: the finger left the page it was choosing a cell on.
        move(fixture, 200, -60);
        assertNull("nothing is offered a cell out there",
            fixture.pane.widgetDragLayer().ghostBounds());
        up(fixture, 200, -60);

        assertEquals(0, fixture.platform.allocations);
        assertTrue(fixture.repository.records().isEmpty());
        assertFalse(fixture.pane.widgetDragLayer().isLifted());
    }

    @Test public void aCancelledStreamDropsTheWidgetRatherThanPlacingIt() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(false);
        View card = openCard(fixture);

        hold(fixture, card);
        Rect target = paneBounds(fixture, CHOSEN);
        move(fixture, target.centerX(), target.centerY());
        dispatch(fixture, MotionEvent.ACTION_CANCEL, target.centerX(), target.centerY());

        assertTrue(fixture.repository.records().isEmpty());
        assertFalse(fixture.pane.widgetDragLayer().isLifted());
        assertFalse(fixture.pane.carrying());
    }

    @Test public void theCarriedWidgetPushesTheCellItLandsOnAside() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(false);
        // One widget sitting exactly where the carried one is going to be dropped.
        fixture.repository.putRecord(new LauncherWidgetRecord(7,
            new ComponentName("other", "P7"), 0, LauncherWidgetRecord.State.PROVIDER_MISSING,
            new WidgetCellRect(2, 3, 3, 4), new Bundle(), null));
        View card = openCard(fixture);

        hold(fixture, card);
        Rect target = paneBounds(fixture, CHOSEN);
        move(fixture, target.centerX(), target.centerY());
        assertEquals(target, fixture.pane.widgetDragLayer().ghostBounds());
        up(fixture, target.centerX(), target.centerY());

        assertEquals(2, fixture.repository.records().size());
        assertFalse("the widget that was there has moved out of the way",
            new WidgetCellRect(2, 3, 3, 4).equals(fixture.repository.get(7).cell));
        LauncherWidgetRecord added = null;
        for (LauncherWidgetRecord record : fixture.repository.records()) {
            if (record.appWidgetId != 7) added = record;
        }
        assertNotNull(added);
        assertEquals(CHOSEN, added.cell);
    }

    @Test public void aCardWithNoRoomRefusesTheHoldAsItRefusesTheTap() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(true);
        View card = openCard(fixture);
        assertFalse(card.isEnabled());

        hold(fixture, card);

        assertFalse(fixture.pane.widgetDragLayer().isLifted());
        assertTrue("the sheet stays up to say so", fixture.pane.picker().isOpen());
        assertEquals(0, fixture.platform.allocations);
    }

    // ---- the gesture, as the framework delivers it ------------------------------------------

    /** Opens the picker, opens the one app row in it, and returns its provider card. */
    private static View openCard(WidgetPickerProductionSelectionTest.Fixture fixture) {
        fixture.controller.openPicker();
        fixture.idleAndLayout();
        RecyclerView.ViewHolder app = fixture.pane.picker().list()
            .findViewHolderForAdapterPosition(0);
        assertNotNull("app row must be attached", app);
        app.itemView.performClick();
        fixture.idleAndLayout();
        RecyclerView.ViewHolder card = fixture.pane.picker().list()
            .findViewHolderForAdapterPosition(1);
        assertNotNull("provider card must be attached", card);
        return card.itemView;
    }

    /** A press on the card that stays still long enough to become a carry. */
    private static void hold(WidgetPickerProductionSelectionTest.Fixture fixture, View card) {
        send(card, MotionEvent.ACTION_DOWN, 10, 10);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(PAST_THE_HOLD_MS, TimeUnit.MILLISECONDS);
    }

    private static void move(WidgetPickerProductionSelectionTest.Fixture fixture, float x, float y) {
        dispatch(fixture, MotionEvent.ACTION_MOVE, x, y);
    }

    private static void up(WidgetPickerProductionSelectionTest.Fixture fixture, float x, float y) {
        dispatch(fixture, MotionEvent.ACTION_UP, x, y);
    }

    /** The rest of the gesture arrives at the pane, because the pane took it off the sheet. */
    private static void dispatch(WidgetPickerProductionSelectionTest.Fixture fixture, int action,
                                 float x, float y) {
        int[] location = new int[2];
        fixture.pane.getLocationOnScreen(location);
        send(fixture.pane, action, x + location[0], y + location[1]);
    }

    private static void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private static Rect paneBounds(WidgetPickerProductionSelectionTest.Fixture fixture,
                                   WidgetCellRect cell) {
        Rect bounds = fixture.pane.grid().metrics().boundsFor(cell);
        bounds.offset(fixture.pane.grid().getLeft(), fixture.pane.grid().getTop());
        return bounds;
    }
}
