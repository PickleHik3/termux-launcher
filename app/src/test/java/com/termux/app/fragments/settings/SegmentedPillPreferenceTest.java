package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The two ways a page can hand a pill its segments without a per-row dialog: straight from Java
 * (an existing {@code ListPreference}-style entries array), and straight from XML
 * ({@code app:entries}/{@code app:entryValues}, the same attributes a {@code ListPreference}
 * would use) — the path that lets most of the Settings pages convert without any fragment code.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SegmentedPillPreferenceTest {

    @Test
    public void setSegmentsWithCharSequenceLabelsConfiguresTheSameAsResourceIds() {
        Application app = RuntimeEnvironment.getApplication();
        SegmentedPillPreference pill = new SegmentedPillPreference(app);
        pill.setSegments(
            new String[]{"never", "cursor", "always"},
            new CharSequence[]{"Never", "Cursor", "Always"});
        assertEquals(3, pill.segmentCount());
        // No key set, so nothing is persisted yet: the first value is the fallback.
        assertEquals("never", pill.getValue());
        pill.setValue("cursor");
        assertEquals("cursor", pill.getValue());
    }

    @Test
    public void xmlEntriesAndEntryValuesConfigureAPillWithoutAnyFragmentCode() {
        // termux_style_preferences.xml's theme_mode row declares app:entries/app:entryValues and
        // nothing else — the fragment never calls setSegments for it.
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        PreferenceScreen screen = manager.inflateFromResource(
            app, R.xml.termux_style_preferences, null);
        androidx.preference.Preference found = screen.findPreference("theme_mode");
        assertNotNull(found);
        assertTrue(found instanceof SegmentedPillPreference);
        SegmentedPillPreference pill = (SegmentedPillPreference) found;
        assertEquals(3, pill.segmentCount());
    }
}
