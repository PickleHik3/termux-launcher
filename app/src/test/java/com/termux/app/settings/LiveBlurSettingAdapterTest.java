package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

/**
 * Review P2-4 (ADR 0041): Live blur's one resolved value is what every door of the registry reads
 * (Terl's Settings row, terl.conf, the assistant), the default on under Terl above 6 GiB and the
 * user's own value winning.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
public class LiveBlurSettingAdapterTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        preferences = TermuxAppSharedPreferences.build(context, false);
        preferences.getSharedPreferences().edit().clear()
            .putBoolean(TermuxPreferenceConstants.TERMUX_APP.KEY_TERL_DESKTOP, true).commit();
    }

    private SettingsRegistry registry(long totalMem) {
        return new SettingsRegistry(which -> null, SettingDeclarations.ALL, Collections.singletonList(
            new LiveBlurSettingAdapter(LiveBlurSettingAdapter.over(preferences, totalMem, false))));
    }

    @Test
    public void underTerlAboveSixGibWithNothingStoredTheRegistryReadsOn() {
        assertEquals("true", registry(12L * GIB).get(LiveBlurSettingAdapter.ID));
        assertEquals("false", registry(4L * GIB).get(LiveBlurSettingAdapter.ID));
    }

    @Test
    public void v1ReadsOff() {
        preferences.getSharedPreferences().edit()
            .putBoolean(TermuxPreferenceConstants.TERMUX_APP.KEY_TERL_DESKTOP, false).commit();
        assertEquals("false", registry(12L * GIB).get(LiveBlurSettingAdapter.ID));
    }

    @Test
    public void theUsersOwnValueWinsAndDefaultForgetsIt() {
        SettingsRegistry registry = registry(12L * GIB);
        SettingsRegistry.Outcome off = registry.set(LiveBlurSettingAdapter.ID, "false");
        assertEquals("true", off.change().oldValue);
        assertEquals("false", registry.get(LiveBlurSettingAdapter.ID));
        assertEquals(Boolean.FALSE, preferences.getLiveBlurStored());
        registry.set(LiveBlurSettingAdapter.ID, "default");
        assertEquals("true", registry.get(LiveBlurSettingAdapter.ID));
        assertEquals(null, preferences.getLiveBlurStored());
    }

    @Test
    public void aFileCarriesItOnlyWhenTheUserChoseIt() {
        LiveBlurSettingAdapter adapter = new LiveBlurSettingAdapter(LiveBlurSettingAdapter.over(preferences,
            12L * GIB, false));
        assertFalse(adapter.overrides(LiveBlurSettingAdapter.DECLARATION));
        preferences.setLiveBlurEnabled(true);
        assertTrue(adapter.overrides(LiveBlurSettingAdapter.DECLARATION));
    }

    @Test
    public void itIsNoLongerAPlainDeclaration() {
        for (com.termux.shared.termux.settings.registry.SettingDeclaration each : SettingDeclarations.ALL) {
            assertFalse(each.id.equals(LiveBlurSettingAdapter.ID));
        }
    }
}
