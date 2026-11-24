package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The frame is at rest when a Home exit ends, however the exit ended. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EditorFrameHomeExitTest {

    private static AppearanceEditorFrame scaledFrame() {
        View root = new View(ApplicationProvider.getApplicationContext());
        AppearanceEditorFrame frame = new AppearanceEditorFrame(root);
        frame.setPose(0.8f, 0f, 120f);
        assertTrue(frame.isScaled());
        return frame;
    }

    @Test public void theExitsEndPutsAGrowingFrameAtRest() {
        AppearanceEditorFrame frame = scaledFrame();
        frame.hide(true, 250L, null);
        // The close at the exit's end, with the growth cut short (a backstop or a second press).
        final boolean[] hidden = {false};
        frame.hide(false, () -> hidden[0] = true);
        assertTrue(hidden[0]);
        assertFalse(frame.isScaled());
        assertEquals(1f, frame.root().getScaleX(), 0f);
        assertEquals(0f, frame.root().getTranslationY(), 0f);
    }

    @Test public void aFrameAlreadyAtRestStaysAtRest() {
        AppearanceEditorFrame frame = scaledFrame();
        frame.hide(false, null);
        frame.hide(false, null);
        assertFalse(frame.isScaled());
    }
}
