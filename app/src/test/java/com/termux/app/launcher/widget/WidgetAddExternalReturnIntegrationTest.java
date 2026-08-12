package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
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
public class WidgetAddExternalReturnIntegrationTest {
    @Test public void matchingLiveExternalResultRestoresFullOnceAndColdControllerDoesNot() {
        WidgetPickerProductionSelectionTest.Fixture fixture =
            new WidgetPickerProductionSelectionTest.Fixture(false);
        fixture.platform.directBind = false;
        fixture.pane.findViewById(R.id.widget_add_large).performClick(); fixture.idleAndLayout();
        RecyclerView.ViewHolder card = fixture.pane.picker().list().findViewHolderForAdapterPosition(1);
        card.itemView.performClick();
        int id = fixture.repository.pending().appWidgetId;
        fixture.platform.info.put(id, fixture.info); fixture.fullEngaged = false;
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        assertTrue(fixture.widgets.handleActivityResult(4714, Activity.RESULT_OK, result));
        assertEquals(1, fixture.restoreCount); assertEquals(1, fixture.repository.records().size());

        // A newly-created pane coordinator has no live-origin latch and cannot reopen FULL.
        fixture.controller.destroy(); fixture.fullEngaged = false; fixture.restoreCount = 0;
        new WidgetPaneController(fixture.pane, fixture.widgets, new WidgetPaneController.Host() {
            @Override public boolean reducedMotion() { return true; }
            @Override public boolean isFullEngaged() { return false; }
            @Override public com.termux.app.statusbar.TopStatusBarState fullPriorState() {
                return com.termux.app.statusbar.TopStatusBarState.EXPANDED;
            }
            @Override public void restoreFull(com.termux.app.statusbar.TopStatusBarState prior) {
                fixture.restoreCount++;
            }
        });
        assertEquals(0, fixture.restoreCount);
    }
}
