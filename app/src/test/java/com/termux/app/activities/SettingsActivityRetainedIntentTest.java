package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.fragments.settings.BenignPreferencesFragment;
import com.termux.app.fragments.settings.ThrowingPreferencesFragment;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SettingsActivityRetainedIntentTest {

    private static final int STALE_RESOURCE_ID = 0x7f7fffff;
    private static final String REMOVED_FRAGMENT = "com.termux.removed.SettingsFragment";

    @Test
    public void onCreateFallsBackForResourceAndFragmentFromPreviousBuild() {
        Intent retainedIntent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TITLE_RES, STALE_RESOURCE_ID)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, REMOVED_FRAGMENT);

        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, retainedIntent).create()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();

            assertEquals(activity.getString(R.string.title_activity_termux_settings),
                activity.getTitle().toString());
            assertRootFragment(activity);
        }
    }

    @Test
    public void onNewIntentFallsBackForResourceAndFragmentFromPreviousBuild() {
        Intent initialIntent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class);
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, initialIntent).create().start().resume()) {
            Intent retainedIntent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_INITIAL_TITLE_RES, STALE_RESOURCE_ID)
                .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, REMOVED_FRAGMENT);

            controller.newIntent(retainedIntent);
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();

            assertEquals(activity.getString(R.string.title_activity_termux_settings),
                activity.getTitle().toString());
            assertRootFragment(activity);
        }
    }

    @Test
    public void currentFragmentConstructorFailureIsRethrownInsteadOfFallingBack() {
        Intent intent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                ThrowingPreferencesFragment.class.getName());

        try {
            Robolectric.buildActivity(SettingsActivity.class, intent).create();
        } catch (RuntimeException e) {
            assertTrue(hasCause(e, ThrowingPreferencesFragment.ConstructorFailure.class));
            return;
        }
        throw new AssertionError("Expected the fragment constructor failure to escape");
    }

    /**
     * SettingsActivity is exported, so the fragment name in an Intent is attacker-controlled: a
     * class outside the settings screens must never be instantiated on its say-so.
     */
    @Test
    public void fragmentOutsideTheSettingsPackageFallsBackToRoot() {
        Intent intent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, ThrowingFragment.class.getName());

        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, intent).create()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertRootFragment(activity);
        }
    }

    @Test
    public void allowlistAcceptsSettingsScreensAndRefusesEverythingElse() {
        assertTrue(SettingsActivity.isAllowedInitialFragment(
            SettingsActivity.RootPreferencesFragment.class));
        assertTrue(SettingsActivity.isAllowedInitialFragment(ThrowingPreferencesFragment.class));
        assertFalse(SettingsActivity.isAllowedInitialFragment(ThrowingFragment.class));
        assertFalse(SettingsActivity.isAllowedInitialFragment(Fragment.class));
    }

    /**
     * openSettingsHome in TermuxActivity sends a bare Intent(context, SettingsActivity.class);
     * everything else (a deep-linked fragment, the QA TAI shortcut, or per-place arguments) is a
     * request for a specific screen, not a plain re-entry that should resume the live stack.
     */
    @Test
    public void isPlainEntryIntentClassifiesPlainVsDeepLinkIntents() {
        assertTrue(SettingsActivity.isPlainEntryIntent(
            new Intent(ApplicationProviderHolder.context(), SettingsActivity.class)));

        assertFalse(SettingsActivity.isPlainEntryIntent(
            SettingsActivity.createFragmentIntent(ApplicationProviderHolder.context(),
                ThrowingPreferencesFragment.class, R.string.title_activity_termux_settings)));

        assertFalse(SettingsActivity.isPlainEntryIntent(
            new Intent(ApplicationProviderHolder.context(), SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_OPEN_TAI_SETTINGS, true)));

        assertFalse(SettingsActivity.isPlainEntryIntent(
            SettingsActivity.createFragmentIntent(ApplicationProviderHolder.context(),
                ThrowingPreferencesFragment.class, R.string.title_activity_termux_settings,
                "widgets", "row_key")));
    }

    /** Seeds the same SharedPreferences file SettingsActivity persists its stack under. */
    private static SharedPreferences settingsBackStackPreferences() {
        return ApplicationProviderHolder.context().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            android.content.Context.MODE_PRIVATE);
    }

    private static void seedSavedBackStack(long stoppedAtEpochMs, SettingsBackStackState.Entry entry) {
        SettingsBackStackState state =
            new SettingsBackStackState(Collections.singletonList(entry), stoppedAtEpochMs);
        settingsBackStackPreferences().edit()
            .putString(SettingsActivity.PREFS_KEY_BACK_STACK_STATE, state.serialize()).apply();
    }

    @Test
    public void plainReentryWithinRetainWindowRestoresTheSavedScreenAndTitle() {
        seedSavedBackStack(System.currentTimeMillis(), new SettingsBackStackState.Entry(
            BenignPreferencesFragment.class.getName(),
            R.string.termux_ai_preferences_title, null, null, null));

        Intent plainIntent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class);
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, plainIntent).create()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();

            Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
            assertTrue(fragment instanceof BenignPreferencesFragment);
            assertEquals(activity.getString(R.string.termux_ai_preferences_title),
                activity.getTitle().toString());
        }
    }

    @Test
    public void plainReentryPastTheRetainWindowFallsBackToRoot() {
        long staleStoppedAt = System.currentTimeMillis() - SettingsBackStackState.RETAIN_WINDOW_MS - 1000;
        seedSavedBackStack(staleStoppedAt, new SettingsBackStackState.Entry(
            BenignPreferencesFragment.class.getName(),
            R.string.termux_ai_preferences_title, null, null, null));

        Intent plainIntent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class);
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, plainIntent).create()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertRootFragment(activity);
        }
    }

    @Test
    public void plainReentryWithAnInvalidSavedClassFallsBackToRoot() {
        seedSavedBackStack(System.currentTimeMillis(), new SettingsBackStackState.Entry(
            "com.termux.removed.SomeOldFragment", R.string.termux_ai_preferences_title,
            null, null, null));

        Intent plainIntent = new Intent(ApplicationProviderHolder.context(), SettingsActivity.class);
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, plainIntent).create()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertRootFragment(activity);
        }
    }

    private static void assertRootFragment(SettingsActivity activity) {
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof SettingsActivity.RootPreferencesFragment);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause())
            if (type.isInstance(cause)) return true;
        return false;
    }

    /** Deliberately not a settings-package fragment: the allowlist must refuse it. */
    public static final class ThrowingFragment extends Fragment {
        public ThrowingFragment() { throw new ConstructorFailure(); }
    }

    private static final class ConstructorFailure extends RuntimeException {}

    /** Keeps AndroidX test-core out of this regression test's public surface. */
    private static final class ApplicationProviderHolder {
        private static Application context() {
            return org.robolectric.RuntimeEnvironment.getApplication();
        }
    }
}
