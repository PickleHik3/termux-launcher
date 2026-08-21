package com.termux.app.launcher.folder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Guards the popup-title regression where the Paint's default black ink vanished into the dark
 * popup glass. Robolectric's canvas does not rasterize text in this environment, so visibility is
 * asserted on the ink itself rather than on painted pixels.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FolderRenameTitleVisibilityTest {
    @Test public void openedFolderTitleUsesVisibleInkAndRemainsFocuslessAndTappable() {
        FolderRenameTitleView title = new FolderRenameTitleView(RuntimeEnvironment.getApplication());
        title.bind(new FolderRenameModel("Utilities"), false);

        assertTrue("title ink must be opaque enough to read",
            Color.alpha(title.currentTextColor()) > 0);
        assertNotEquals("Paint's default black ink is invisible on the popup glass",
            Color.BLACK, title.currentTextColor());

        title.setTextColor(0xFFE0E0E0);
        assertEquals(0xFFE0E0E0, title.currentTextColor());

        assertEquals("Utilities", title.getContentDescription());
        assertTrue(title.isClickable());
        assertFalse(title.isFocusable());
        assertFalse(title.onCheckIsTextEditor());
    }
}
