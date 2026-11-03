package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;

import androidx.fragment.app.Fragment;

import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.termux.R;
import com.termux.app.fragments.settings.termux.TerminalPreferencesFragment;
import com.termux.app.fragments.settings.termux.TerminalTextPreferencesFragment;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** F2 on the real host: a push slides on the shared axis, a jump fades through, reduce motion cuts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsMotionTest {

    @After
    public void motionBackOn() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    }

    private static SettingsActivity openOnTerminal() {
        Intent intent = SettingsActivity.createFragmentIntent(RuntimeEnvironment.getApplication(),
            TerminalPreferencesFragment.class, R.string.settings_destination_terminal);
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class, intent)
            .create().start().resume().visible().get();
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
        return activity;
    }

    private static Fragment shown(SettingsActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings);
    }

    @Test
    public void aPushSlidesOnTheSharedAxisBothWays() {
        SettingsActivity activity = openOnTerminal();
        Fragment terminal = shown(activity);
        activity.openScreen(TerminalTextPreferencesFragment.class, R.string.settings_terminal_sub_text_title, null);
        Fragment text = shown(activity);
        assertTrue(text.getEnterTransition() instanceof MaterialSharedAxis);
        assertTrue(text.getReturnTransition() instanceof MaterialSharedAxis);
        assertTrue(terminal.getExitTransition() instanceof MaterialSharedAxis);
        assertTrue(terminal.getReenterTransition() instanceof MaterialSharedAxis);
    }

    @Test
    public void aSearchResultFadesThroughAndKeepsTheSlideForBack() {
        SettingsActivity activity = openOnTerminal();
        Fragment terminal = shown(activity);
        activity.openSearchResult("com.termux.app.fragments.settings.termux.StatusBarPreferencesFragment",
            "status_widget_cpu");
        Fragment status = shown(activity);
        assertTrue(status.getEnterTransition() instanceof MaterialFadeThrough);
        assertTrue("Back from it walks up the tree", status.getReturnTransition() instanceof MaterialSharedAxis);
        assertTrue(terminal.getExitTransition() instanceof MaterialFadeThrough);
    }

    @Test
    public void reduceMotionIsAnInstantCut() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        SettingsActivity activity = openOnTerminal();
        Fragment terminal = shown(activity);
        activity.openScreen(TerminalTextPreferencesFragment.class, R.string.settings_terminal_sub_text_title, null);
        Fragment text = shown(activity);
        assertNull(text.getEnterTransition());
        assertNull(text.getReturnTransition());
        assertNull(terminal.getExitTransition());
        assertEquals(TerminalTextPreferencesFragment.class, text.getClass());
    }
}
