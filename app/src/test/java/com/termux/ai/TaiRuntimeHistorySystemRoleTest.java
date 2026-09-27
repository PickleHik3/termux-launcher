package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The per-model memory that lets {@link LiteRtTaiRuntime} skip the failed-attempt retry once a
 * model's chat template is known to reject a system-role message.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiRuntimeHistorySystemRoleTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void unknownModel_isNotAssumedUnsupported() {
        assertFalse(TaiRuntimeHistory.isSystemRoleKnownUnsupported(context, "codegemma-7b-it-litert-lm"));
    }

    @Test
    public void recordedModel_staysRememberedAcrossCalls() {
        TaiRuntimeHistory.recordSystemRoleUnsupported(context, "codegemma-7b-it-litert-lm");

        assertTrue(TaiRuntimeHistory.isSystemRoleKnownUnsupported(context, "codegemma-7b-it-litert-lm"));
        // A different model's verdict is untouched.
        assertFalse(TaiRuntimeHistory.isSystemRoleKnownUnsupported(context, "gemma-4-e2b-it-litert-lm"));
    }

    @Test
    public void keyedByTheBaseModel_soAPerModalityVariantSharesTheVerdict() {
        TaiRuntimeHistory.recordSystemRoleUnsupported(context, "codegemma-7b-it-litert-lm");

        assertTrue(TaiRuntimeHistory.isSystemRoleKnownUnsupported(context, "codegemma-7b-it-litert-lm-vision"));
    }
}
