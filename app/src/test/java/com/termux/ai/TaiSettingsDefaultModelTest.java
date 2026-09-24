package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class TaiSettingsDefaultModelTest {
    private SharedPreferences preferences;
    private TaiSettings settings;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        settings = new TaiSettings(context);
    }

    @Test
    public void unreadableUserModel_fallsBackWithoutClearingTheChoice() {
        preferences.edit().putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, "my-mnn-model").commit();

        assertEquals(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, settings.getDefaultAssistantModel());
        assertEquals("my-mnn-model", preferences.getString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, null));
    }

    @Test
    public void builtInModel_isReturnedAsStored() {
        preferences.edit().putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, TaiModelRegistry.MODEL_GEMMA_4_E4B_IT).commit();

        assertEquals(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, settings.getDefaultAssistantModel());
    }
}
