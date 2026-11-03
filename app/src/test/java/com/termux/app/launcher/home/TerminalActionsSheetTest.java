package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.launcher.home.TerminalActionsSheet.Action;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The terminal's More menu drawn on Home's sheet, and Home's own rows still as they were. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w411dp-h918dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalActionsSheetTest {

    private ContextThemeWrapper themed;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        themed = new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
    }

    @NonNull
    private HomeActionsSheet sheet() {
        return new HomeActionsSheet(themed, new HomeActionsSheet.Listener() {
            @Override public void onHomeAction(@NonNull HomeActionsSheetPolicy.Item item) { }
        });
    }

    private static List<Object> tokens(List<ActionSheetRow> rows) {
        List<Object> out = new ArrayList<>();
        for (ActionSheetRow row : rows) out.add(row.token);
        return out;
    }

    @Test public void rowsStandInTwoGroupsInOrderWithStyleWhenInstalled() {
        List<ActionSheetRow> rows = TerminalActionsSheet.rows(themed, false, true, 4242);
        assertEquals(Arrays.<Object>asList(Action.COMMAND_PALETTE, Action.SELECT_URL,
            Action.SHARE_TRANSCRIPT, Action.RESET, Action.KILL_PROCESS, Action.APPEARANCE,
            Action.SET_WALLPAPER, Action.SHOW_WALLPAPER, Action.STYLE, Action.SETTINGS),
            tokens(rows));
        for (ActionSheetRow row : rows) {
            assertEquals(row.token.toString(), ((Action) row.token).group, row.group);
            assertEquals(false, row.accent);
        }
        assertEquals(0, rows.get(4).group);
        assertEquals(1, rows.get(5).group);
    }

    @Test public void styleIsLeftOutWithoutTheCompanion() {
        assertEquals(Arrays.<Object>asList(Action.COMMAND_PALETTE, Action.SELECT_URL,
            Action.SHARE_TRANSCRIPT, Action.RESET, Action.KILL_PROCESS, Action.APPEARANCE,
            Action.SET_WALLPAPER, Action.SHOW_WALLPAPER, Action.SETTINGS),
            tokens(TerminalActionsSheet.rows(themed, false, false, 1)));
    }

    @Test public void labelsAndIconsAreTheMoreMenusOwn() {
        List<ActionSheetRow> rows = TerminalActionsSheet.rows(themed, true, true, 4242);
        assertEquals(themed.getString(R.string.action_command_palette), rows.get(0).label.toString());
        assertEquals(R.drawable.ic_symbol_command_palette, rows.get(0).icon);
        assertEquals(R.drawable.ic_symbol_link, rows.get(1).icon);
        assertEquals(R.drawable.ic_symbol_share, rows.get(2).icon);
        assertEquals(R.drawable.ic_symbol_restart, rows.get(3).icon);
        assertEquals(themed.getString(R.string.action_kill_process, 4242), rows.get(4).label.toString());
        assertEquals(R.drawable.ic_symbol_stop, rows.get(4).icon);
        assertEquals(R.drawable.ic_symbol_palette, rows.get(5).icon);
        assertEquals(themed.getString(R.string.home_actions_wallpaper), rows.get(6).label.toString());
        assertEquals(R.drawable.ic_symbol_wallpaper, rows.get(6).icon);
        assertEquals(themed.getString(R.string.action_disable_background_image),
            rows.get(7).label.toString());
        assertEquals(R.drawable.ic_ed_show_wallpaper, rows.get(7).icon);
        assertEquals(R.drawable.ic_symbol_tune, rows.get(8).icon);
        assertEquals(R.drawable.ic_settings, rows.get(9).icon);
        assertEquals(themed.getString(R.string.action_enable_background_image),
            TerminalActionsSheet.rows(themed, false, true, 1).get(7).label.toString());
    }

    @Test public void wallpaperOpensThePickerAndReadsApartFromTheSwitch() {
        assertEquals("Wallpaper", themed.getString(R.string.home_actions_wallpaper));
        assertEquals("Show wallpaper", themed.getString(R.string.action_enable_background_image));
        assertEquals("Hide wallpaper", themed.getString(R.string.action_disable_background_image));
        assertNotNull(Action.SET_WALLPAPER.door());
        assertTrue(Action.SET_WALLPAPER.door().opensWallpaperPicker());
        assertNull("the switch opens nothing", Action.SHOW_WALLPAPER.door());
        // No Home page is needed: the item stands whatever the sheet is given.
        assertTrue(tokens(TerminalActionsSheet.rows(themed, false, false, 1))
            .contains(Action.SET_WALLPAPER));
    }

    @Test public void theSheetDrawsTheRowsUnderItsTitleAndHandsBackTheTappedOne() {
        HomeActionsSheet sheet = sheet();
        List<Object> picked = new ArrayList<>();
        sheet.openRows("Terminal", TerminalActionsSheet.rows(themed, false, true, 7),
            row -> picked.add(row.token));
        assertEquals("Terminal", ((TextView) sheet.findViewById(R.id.home_actions_title)).getText().toString());
        assertTrue(sheet.isOpen());
        View reset = sheet.rowFor(Action.RESET);
        assertNotNull(reset);
        reset.performClick();
        assertEquals(Arrays.<Object>asList(Action.RESET), picked);
        assertEquals(false, sheet.isOpen());
    }

    @Test public void homeRowsAreUnchangedAndTheTitleComesBack() {
        HomeActionsSheet sheet = sheet();
        sheet.openRows("Terminal", TerminalActionsSheet.rows(themed, false, true, 7), row -> { });
        sheet.closeImmediately();
        sheet.open(HomeActionsSheetPolicy.itemsFor(true, true, false, 1, false));
        assertEquals(themed.getString(R.string.home_actions_title),
            ((TextView) sheet.findViewById(R.id.home_actions_title)).getText().toString());
        assertNull(sheet.rowFor(Action.RESET));
        for (HomeActionsSheetPolicy.Item item : HomeActionsSheetPolicy.itemsFor(true, true, false, 1, false)) {
            assertNotNull(item.name(), sheet.rowFor(item));
        }
        assertEquals(R.drawable.ic_symbol_terminal, HomeActionsSheet.iconFor(HomeActionsSheetPolicy.Item.TERMINAL));
        assertEquals(R.string.home_actions_terminal, HomeActionsSheet.labelFor(HomeActionsSheetPolicy.Item.TERMINAL));
    }
}
