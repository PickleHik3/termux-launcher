package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The Terminal section (settings restructure 10-19): a Layout row and one row per part of the
 * Terminal half. The rows the old Terminal page held moved, keys unchanged, to Windows &amp; panes,
 * Text &amp; display and Clipboard &amp; compatibility; Status bar and Linux apps joined from the root.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalPreferencesFragmentTest {

    private <T extends TerminalPreferencesFragment> T launch(Class<T> page) {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, page.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(page.isInstance(fragment));
        return page.cast(fragment);
    }

    @Test
    public void theSectionPageListsTheTerminalHalfInPlanOrder() {
        TerminalPreferencesFragment fragment = launch(TerminalPreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        String[] rows = {"terminal_layout_editor", "terminal_sub_windows", "status_bar",
            "terminal_sub_text", "terminal_sub_clipboard"};
        assertEquals(rows.length, screen.getPreferenceCount());
        for (int i = 0; i < rows.length; i++) assertEquals(rows[i], screen.getPreference(i).getKey());
        assertEquals(StatusBarPreferencesFragment.class.getName(),
            screen.findPreference("status_bar").getFragment());
        assertNull("Linux apps is a root section again", screen.findPreference("display"));
        assertEquals("the font moved in from Appearance", TermuxFontsPreferencesFragment.class.getName(),
            launch(TerminalTextPreferencesFragment.class).findPreference("terminal_fonts").getFragment());
        assertNull("the rows live on the subpages now", screen.findPreference("split_pane_controls"));
        assertEquals("Terminal", activityTitle(fragment));
    }

    @Test
    public void everyTerminalRowIsOnItsSubpageWithItsKey() {
        PreferenceScreen windows = launch(TerminalWindowsPreferencesFragment.class).getPreferenceScreen();
        for (String key : new String[] {"split_pane_controls", "show_key_hints", "pane_dwindle_default",
            "pane_focus_grows", "pane_agent_api"}) {
            assertTrue(key, windows.findPreference(key) instanceof SwitchPreferenceCompat);
        }
        PreferenceScreen text = launch(TerminalTextPreferencesFragment.class).getPreferenceScreen();
        assertTrue(text.findPreference("fullscreen") instanceof SwitchPreferenceCompat);
        assertTrue(text.findPreference("terminal_padding_fill") instanceof SwitchPreferenceCompat);
        assertTrue(text.findPreference("terminal_cursor_trail_style") != null);
        PreferenceScreen clipboard = launch(TerminalClipboardPreferencesFragment.class).getPreferenceScreen();
        assertTrue(clipboard.findPreference("terminal_margin_adjustment") instanceof SwitchPreferenceCompat);
        assertTrue(clipboard.findPreference("terminal_osc52_clipboard_read_enabled") instanceof SwitchPreferenceCompat);
        assertTrue(clipboard.findPreference("terminal_clipboard_cleanup") instanceof SwitchPreferenceCompat);

        // Not on any Terminal page: Recents is General's, the clock is the Status bar's.
        for (PreferenceScreen screen : new PreferenceScreen[] {windows, text, clipboard}) {
            assertNull(screen.findPreference("show_in_recents_when_not_default"));
            assertNull(screen.findPreference("lazy_mode"));
            assertNull(screen.findPreference("top_pane_clock_style"));
            assertNull(screen.findPreference("status_widget_cpu"));
        }
    }

    @Test
    public void splitPaneControlsInvertsIntoTheCompatibilityModeKey() {
        Application app = RuntimeEnvironment.getApplication();
        TerminalIOPreferencesDataStore io = TerminalIOPreferencesDataStore.getInstance(app);
        io.putBoolean("compatibility_mode", false);

        TerminalWindowsPreferencesFragment fragment = launch(TerminalWindowsPreferencesFragment.class);
        SwitchPreferenceCompat splitPanes = fragment.findPreference("split_pane_controls");
        assertTrue(splitPanes.isChecked());

        splitPanes.setChecked(false);
        assertTrue(io.getBoolean("compatibility_mode", false));
    }

    private static String activityTitle(Fragment fragment) {
        return fragment.getActivity() == null ? null : fragment.getActivity().getTitle().toString();
    }
}
