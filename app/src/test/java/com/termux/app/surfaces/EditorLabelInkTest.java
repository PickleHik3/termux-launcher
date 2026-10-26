package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EditorLabelInkTest {
    private static final int DARK = 0xFF1B1B1F;
    private static final int PALE = 0xFFE3E2E6;

    @Test
    public void aLightPictureGetsDarkInkWhateverTheTheme() {
        assertEquals(DARK, EditorLabelInk.pick(0xFFF3EFE6, DARK, PALE));
    }

    @Test
    public void aDarkPictureGetsPaleInk() {
        assertEquals(PALE, EditorLabelInk.pick(0xFF202020, DARK, PALE));
    }

    @Test
    public void theDimIsLaidOverTheAverage() {
        int[] px = {0xFFFFFFFF, 0xFFFFFFFF};
        assertEquals(0xFFFFFFFF, EditorLabelInk.average(px, 2, 0x00000000));
        assertEquals(PALE, EditorLabelInk.pick(EditorLabelInk.average(px, 2, 0xE6000000), DARK, PALE));
    }
}
