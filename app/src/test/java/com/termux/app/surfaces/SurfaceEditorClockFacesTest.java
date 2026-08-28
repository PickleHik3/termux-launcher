package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;

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
