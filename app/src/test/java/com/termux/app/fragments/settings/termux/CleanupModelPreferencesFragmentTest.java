package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * The Cleanup model screen: "Automatic" plus one row per installed chat model, and a pick writes
 * the "keyboard_voice_polish_model" preference.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class CleanupModelPreferencesFragmentTest {
    private Context context;
    private TaiModelStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences.build(context, true).setInAppKeyboardVoicePolishModelId("");
        store = new TaiModelStore(context);
        store.deleteUserModel("test-chat-model");
    }

    private CleanupModelPreferencesFragment launch() {
        Intent intent = new Intent(context, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, CleanupModelPreferencesFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof CleanupModelPreferencesFragment);
        return (CleanupModelPreferencesFragment) fragment;
    }

    @Test
    public void withNothingInstalledAutomaticSaysSoAndTheEmptyRowPointsAtTaiSettings() {
        CleanupModelPreferencesFragment fragment = launch();

        Preference automatic = fragment.getPreferenceScreen().findPreference("cleanup_model_automatic");
        assertNotNull(automatic);
        assertTrue(automatic.getSummary().toString().contains("No Gemma model installed"));
        assertTrue(automatic.getSummary().toString().contains("In use"));
        Preference empty = fragment.getPreferenceScreen().findPreference("cleanup_model_empty");
        assertNotNull(empty);
        assertTrue(empty.isVisible());
    }

    @Test
    public void anInstalledChatModelIsARowAndTappingItSelectsIt() throws Exception {
        install("test-chat-model", "test.litertlm");

        CleanupModelPreferencesFragment fragment = launch();
        Preference row = fragment.getPreferenceScreen().findPreference(
            "cleanup_model_row_test-chat-model");
        assertNotNull(row);
        assertEquals("Test Chat Model", row.getTitle().toString());
        Preference empty = fragment.getPreferenceScreen().findPreference("cleanup_model_empty");
        assertNotNull(empty);
        assertFalse(empty.isVisible());

        row.getOnPreferenceClickListener().onPreferenceClick(row);

        assertEquals("test-chat-model",
            TermuxAppSharedPreferences.build(context, true).getInAppKeyboardVoicePolishModelId());
    }

    private void install(String id, String fileName) throws Exception {
        File dir = new File(store.getModelsDirectory(), id);
        Files.createDirectories(dir.toPath());
        File file = new File(dir, fileName);
        Files.write(file.toPath(), "not-a-real-model".getBytes(StandardCharsets.UTF_8));
        store.upsertUserModel(new TaiModelSpec(
            id, "Test Chat Model", "Chat test model", "test", file.getAbsolutePath(), "test", 123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            null, null, 4096, 0, null));
    }
}
