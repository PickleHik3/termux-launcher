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
    @Test public void fullRealGridDisablesCardKeepsPickerAndAllocatesNoId() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(true);
        java.util.List<WidgetCellRect> before = new java.util.ArrayList<>();
        for (LauncherWidgetRecord record : fixture.repository.records()) before.add(record.cell);
        fixture.controller.openPicker(); fixture.idleAndLayout();
        RecyclerView.ViewHolder card = fixture.pane.picker().list().findViewHolderForAdapterPosition(1);
        assertNotNull(card); assertFalse(card.itemView.isEnabled()); assertFalse(card.itemView.performClick());
        assertTrue(fixture.pane.picker().isOpen()); assertEquals(0, fixture.platform.allocations);
        java.util.List<WidgetCellRect> after = new java.util.ArrayList<>();
        for (LauncherWidgetRecord record : fixture.repository.records()) after.add(record.cell);
        assertEquals(before, after);
    }
}
