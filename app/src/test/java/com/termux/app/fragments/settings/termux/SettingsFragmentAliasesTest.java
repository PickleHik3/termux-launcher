package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceScreen;

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
 * Phase 6 moved and split settings pages, but every fragment class name that used to be a valid
 * {@code EXTRA_INITIAL_FRAGMENT} value (a deep link, a shortcut, or a rebroadcast Intent from
 * before the split can still carry one) must keep resolving and inflating rather than crash or
 * silently fall back to the root screen.
 *
 * <p>{@link TerminalStatusPreferencesFragment} is the one genuine alias: its content split into
 * {@link TerminalPreferencesFragment} and {@link StatusBarPreferencesFragment}, so it now resolves
 * to the terminal half. Every other old name ({@link TermuxStylePreferencesFragment},
 * {@link KeyboardPreferencesFragment}, {@link LauncherPreferencesFragment},
 * {@link X11DisplayPreferencesFragment}) still names the same page it always did — only the rows
 * on it changed — so this doubles as a plain inflation smoke test for those.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsFragmentAliasesTest {

    private Fragment launch(Class<? extends Fragment> fragmentClass) {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, fragmentClass.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragmentClass.isInstance(fragment));
        return fragment;
    }

    @Test
    public void theOldCombinedTerminalStatusNameResolvesToTheTerminalPage() {
        Fragment fragment = launch(TerminalStatusPreferencesFragment.class);
        assertTrue(fragment instanceof TerminalPreferencesFragment);
        PreferenceScreen screen = ((TerminalPreferencesFragment) fragment).getPreferenceScreen();
        assertNotNull(screen.findPreference("split_pane_controls"));
        assertNotNull(screen.findPreference("lazy_mode"));
    }

    @Test
    public void theStyleFragmentNameStillResolvesAndCarriesTheMovedKeyboardLookRows() {
        Fragment fragment = launch(TermuxStylePreferencesFragment.class);
        PreferenceScreen screen = ((TermuxStylePreferencesFragment) fragment).getPreferenceScreen();
        assertNotNull(screen.findPreference("live_surface_editor"));
        assertNotNull("keyboard look moved in from the old Keyboard page",
            screen.findPreference("in_app_keyboard_theme"));
        assertNotNull(screen.findPreference("customize_keyboard_surface"));
        assertNotNull(screen.findPreference("in_app_keyboard_bottom_padding"));
    }

    @Test
    public void theKeyboardFragmentNameStillResolvesWithoutTheMovedLookRows() {
        Fragment fragment = launch(KeyboardPreferencesFragment.class);
        PreferenceScreen screen = ((KeyboardPreferencesFragment) fragment).getPreferenceScreen();
        assertNotNull(screen.findPreference("keyboard_input_method"));
        assertNotNull(screen.findPreference("in_app_keyboard_extra_keys"));
        assertTrue("moved to the Look page", screen.findPreference("in_app_keyboard_theme") == null);
        assertTrue("moved to the Look page", screen.findPreference("customize_keyboard_surface") == null);
    }

    @Test
    public void theLauncherFragmentNameStillResolvesWithoutTheDuplicateSurfaceRow() {
        Fragment fragment = launch(LauncherPreferencesFragment.class);
        PreferenceScreen screen = ((LauncherPreferencesFragment) fragment).getPreferenceScreen();
        assertNotNull(screen.findPreference("app_launcher_default_buttons"));
        assertTrue("duplicate of the Layout page's Look of this place",
            screen.findPreference("customize_dock_surface") == null);
    }

    @Test
    public void theX11FragmentNameStillResolvesUnchanged() {
        Fragment fragment = launch(X11DisplayPreferencesFragment.class);
        PreferenceScreen screen = ((X11DisplayPreferencesFragment) fragment).getPreferenceScreen();
        assertNotNull(screen.findPreference("x11_display_enabled"));
    }
}
