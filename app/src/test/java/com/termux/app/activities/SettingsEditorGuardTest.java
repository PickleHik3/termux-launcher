package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.fragments.settings.DirtyEditorFragment;
import com.termux.app.fragments.settings.SettingsSearchFragment;
import com.termux.app.fragments.settings.termux.KeyboardColorSchemeFragment;
import com.termux.app.fragments.settings.termux.TerminalPreferencesFragment;
import com.termux.app.fragments.settings.termux.TerminalTextPreferencesFragment;
import com.termux.shared.termux.TermuxConstants;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * Review fixes: an editor with unsaved edits is never jumped away from behind its own Back
 * handler, editors get no toolbar search, Back cuts under reduce motion whatever the screens were
 * pushed with, and the search screen is not saved as a place to come back to.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsEditorGuardTest {

    @After
    public void motionBackOn() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    }

    private static ActivityController<SettingsActivity> open(Class<? extends Fragment> page) {
        Intent intent = SettingsActivity.createFragmentIntent(RuntimeEnvironment.getApplication(), page, 0);
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class, intent)
            .create().start().resume().visible();
        idle(controller.get());
        return controller;
    }

    private static void idle(SettingsActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Fragment shown(SettingsActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings);
    }

    @Test
    public void aJumpFromUnsavedEditsAsksTheEditorInstead() {
        DirtyEditorFragment.sAsked = 0;
        SettingsActivity activity = open(DirtyEditorFragment.class).get();
        assertFalse("no search over unsaved edits", activity.showsSearchAction());
        activity.openSearchResult("com.termux.app.fragments.settings.termux.StatusBarPreferencesFragment",
            "status_widget_cpu");
        idle(activity);
        assertTrue(shown(activity) instanceof DirtyEditorFragment);
        assertEquals("its own Back handler asked about the edits", 1, DirtyEditorFragment.sAsked);
    }

    @Test
    public void editorsOfferNoToolbarSearch() {
        SettingsActivity activity = open(KeyboardColorSchemeFragment.class).get();
        assertFalse(activity.showsSearchAction());
    }

    @Test
    public void backUnderReduceMotionIsACutEvenAfterAnAnimatedPush() {
        SettingsActivity activity = open(TerminalPreferencesFragment.class).get();
        Fragment terminal = shown(activity);
        activity.openScreen(TerminalTextPreferencesFragment.class, R.string.settings_terminal_sub_text_title, null);
        idle(activity);
        Fragment text = shown(activity);
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        activity.onBackPressed();
        idle(activity);
        assertNull(text.getReturnTransition());
        assertNull(terminal.getReenterTransition());
        assertTrue(shown(activity) instanceof TerminalPreferencesFragment);
    }

    @Test
    public void theSearchScreenIsNotSavedAsAPlace() {
        ActivityController<SettingsActivity> controller = open(TerminalPreferencesFragment.class);
        SettingsActivity activity = controller.get();
        activity.openScreen(TerminalTextPreferencesFragment.class, R.string.settings_terminal_sub_text_title, null);
        idle(activity);
        activity.openSearch();
        idle(activity);
        assertTrue(shown(activity) instanceof SettingsSearchFragment);
        controller.pause().stop();
        String saved = RuntimeEnvironment.getApplication().getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE)
            .getString(SettingsActivity.PREFS_KEY_BACK_STACK_STATE, "");
        assertTrue(saved.contains("TerminalTextPreferencesFragment"));
        assertFalse(saved.contains("SettingsSearchFragment"));
    }
}
