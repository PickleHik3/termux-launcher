package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The clock face has two homes — the editor's Status group and the settings list — and the way that
 * goes wrong is a seventh face reaching one of them only. Both are held against the same array
 * here, so adding a face without offering it in the editor fails a test rather than shipping.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceEditorClockFacesTest {

    @Test
    public void picker_offersEveryFaceTheSettingsListDoes() {
        Context context = ApplicationProvider.getApplicationContext();
        String[] settingsValues = context.getResources()
            .getStringArray(R.array.termux_top_pane_clock_style_values);

        assertEquals("the editor's picker and the settings list must offer the same faces",
            new HashSet<>(Arrays.asList(settingsValues)),
            new HashSet<>(Arrays.asList(SurfaceEditorController.CLOCK_STYLES)));
        assertEquals("no face may be listed twice in the picker",
            SurfaceEditorController.CLOCK_STYLES.length,
            new HashSet<>(Arrays.asList(SurfaceEditorController.CLOCK_STYLES)).size());
    }

    @Test
    public void picker_offersEveryPositionTheSettingsListDoes() {
        Set<String> settingsValues = new HashSet<>(Arrays.asList(
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT,
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER,
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT));
        assertEquals("the editor's position row and the settings pill must offer the same values",
            settingsValues, new HashSet<>(Arrays.asList(SurfaceEditorController.CLOCK_ALIGNMENTS)));
        assertEquals("no position may be listed twice",
            SurfaceEditorController.CLOCK_ALIGNMENTS.length,
            new HashSet<>(Arrays.asList(SurfaceEditorController.CLOCK_ALIGNMENTS)).size());
        assertEquals("the default position must be one the row offers",
            true, settingsValues.contains(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_ALIGNMENT));
    }

    @Test
    public void everyPositionHasItsOwnName() {
        Context context = ApplicationProvider.getApplicationContext();
        Set<String> names = new HashSet<>();
        for (String alignment : SurfaceEditorController.CLOCK_ALIGNMENTS) {
            String name = context.getString(
                SurfaceEditorController.clockAlignmentLabel(alignment));
            assertFalse("position " + alignment + " has no name", name.trim().isEmpty());
            assertTrue("position " + alignment + " shares its name with another",
                names.add(name));
        }
    }

    @Test
    public void everyFaceHasItsOwnName() {
        Context context = ApplicationProvider.getApplicationContext();
        Set<String> names = new HashSet<>();
        for (String style : SurfaceEditorController.CLOCK_STYLES) {
            String name = context.getString(SurfaceEditorController.clockStyleLabel(style));
            assertFalse("face " + style + " has no name", name.trim().isEmpty());
            assertTrue("face " + style + " shares its name with another face", names.add(name));
        }
    }
}
