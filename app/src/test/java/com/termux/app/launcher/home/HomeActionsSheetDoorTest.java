package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A row that opens the Appearance editor is handed to the activity only once Home's sheet has
 * slid away, so the editor's chrome never draws over the closing card; every other row still runs
 * at once, and a sheet put away without its slide drops the waiting row.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w411dp-h918dp-420dpi")
public class HomeActionsSheetDoorTest {

    private ContextThemeWrapper themed;
    private FrameLayout content;
    private final List<HomeActionsSheetPolicy.Item> told = new ArrayList<>();

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        themed = new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        content = new FrameLayout(themed);
        activity.setContentView(content, new ViewGroup.LayoutParams(1080, 2412));
    }

    @Test
    public void onlyTheEditorsDoorsWaitAndOnlyWhileTheCardIsStillSliding() {
        assertTrue(HomeActionsSheetPolicy.opensEditor(HomeActionsSheetPolicy.Item.WALLPAPER));
        assertTrue(HomeActionsSheetPolicy.opensEditor(HomeActionsSheetPolicy.Item.APPEARANCE));
        assertFalse(HomeActionsSheetPolicy.opensEditor(HomeActionsSheetPolicy.Item.ADD_WIDGET));
        assertFalse(HomeActionsSheetPolicy.opensEditor(HomeActionsSheetPolicy.Item.TERMINAL));
        assertTrue(HomeActionsSheetPolicy.runsOnceHidden(true, false));
        assertFalse("no slide to wait for", HomeActionsSheetPolicy.runsOnceHidden(true, true));
        assertFalse(HomeActionsSheetPolicy.runsOnceHidden(false, false));
    }

    @Test
    public void theTerminalSheetsEditorRowsWaitToo() {
        assertTrue(TerminalActionsSheet.Action.APPEARANCE.opensEditor());
        assertTrue(TerminalActionsSheet.Action.SET_WALLPAPER.opensEditor());
        assertFalse(TerminalActionsSheet.Action.SETTINGS.opensEditor());
        assertFalse(TerminalActionsSheet.Action.STYLE.opensEditor());
    }

    @Test
    public void theWallpaperDoorOpensOnceTheSheetHasSettledHidden() {
        HomeActionsSheet sheet = openSheet();
        View row = sheet.rowFor(HomeActionsSheetPolicy.Item.WALLPAPER);
        assertNotNull(row);
        row.performClick();
        assertFalse("the sheet is on its way down", sheet.isOpen());
        assertTrue("nothing opens over the closing card: " + told, told.isEmpty());

        idle(2000);
        assertEquals(View.GONE, sheet.getVisibility());
        assertEquals(Arrays.asList(HomeActionsSheetPolicy.Item.WALLPAPER), told);
    }

    @Test
    public void aRowThatOpensNoEditorRunsAtOnce() {
        HomeActionsSheet sheet = openSheet();
        View row = sheet.rowFor(HomeActionsSheetPolicy.Item.ADD_WIDGET);
        assertNotNull(row);
        row.performClick();
        assertEquals(Arrays.asList(HomeActionsSheetPolicy.Item.ADD_WIDGET), told);
    }

    @Test
    public void aSheetPutAwayWithoutItsSlideDropsTheWaitingDoor() {
        HomeActionsSheet sheet = openSheet();
        View row = sheet.rowFor(HomeActionsSheetPolicy.Item.APPEARANCE);
        assertNotNull(row);
        row.performClick();
        sheet.closeImmediately();
        idle(2000);
        assertTrue(told.toString(), told.isEmpty());
    }

    @NonNull
    private HomeActionsSheet openSheet() {
        HomeActionsSheet sheet = new HomeActionsSheet(themed, new HomeActionsSheet.Listener() {
            @Override public void onHomeAction(@NonNull HomeActionsSheetPolicy.Item item) {
                told.add(item);
            }
        });
        content.addView(sheet, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sheet.open(locked -> HomeActionsSheetPolicy.itemsFor(false, true, true, 2, locked), false);
        idle(2000);
        assertTrue(sheet.isOpen());
        return sheet;
    }

    private static void idle(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }
}
