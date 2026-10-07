package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerFullGridIntegrationTest {
    @Test public void fullPageKeepsCardLiveKeepsPickerAndAllocatesNoId() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(true);
        java.util.List<WidgetCellRect> before = new java.util.ArrayList<>();
        for (LauncherWidgetRecord record : fixture.repository.records()) before.add(record.cell);
        fixture.controller.openPicker(); fixture.idleAndLayout();
        // Nothing this app offers fits this page, and its row still opens: it is the only way to
        // the cards.
        // Row 0 is the launcher's own widgets; the app row follows it.
        RecyclerView.ViewHolder app = fixture.pane.picker().list().findViewHolderForAdapterPosition(1);
        assertNotNull(app); assertTrue(app.itemView.isEnabled());
        assertTrue(app.itemView.performClick()); fixture.idleAndLayout();
        RecyclerView.ViewHolder card = fixture.pane.picker().list().findViewHolderForAdapterPosition(2);
        // The widget fits the grid, just not this page: the card stays live, since a hold can
        // still carry it to another page. The tap adds nothing and the sheet stays up.
        assertNotNull(card); assertTrue(card.itemView.isEnabled());
        assertEquals(1f, card.itemView.getAlpha(), 0f);
        assertTrue(card.itemView.performClick());
        // The sheet says so itself: the pane's own notice sits under it.
        android.widget.TextView notice = fixture.pane.picker().findViewWithTag("notice");
        assertEquals(android.view.View.VISIBLE, notice.getVisibility());
        assertEquals(fixture.activity.getString(R.string.widget_picker_no_room_on_page),
            notice.getText().toString());
        assertTrue(fixture.pane.picker().isOpen()); assertEquals(0, fixture.platform.allocations);
        java.util.List<WidgetCellRect> after = new java.util.ArrayList<>();
        for (LauncherWidgetRecord record : fixture.repository.records()) after.add(record.cell);
        assertEquals(before, after);
        // The hint goes after a while; the sheet is still the user's.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            4, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(android.view.View.GONE, notice.getVisibility());
        assertTrue(fixture.pane.picker().isOpen());
    }
}
