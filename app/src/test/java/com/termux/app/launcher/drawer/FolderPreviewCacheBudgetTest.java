package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FolderPreviewCacheBudgetTest {
    @Test public void dragOverlayRetainsOneDrawableAndReleasesIt() {
        AppDrawerDragOverlayView overlay = new AppDrawerDragOverlayView(RuntimeEnvironment.getApplication());
        ColorDrawable drawable = new ColorDrawable(Color.RED);
        overlay.setGhost(drawable, 24);
        assertSame(drawable, overlay.ghost());
        overlay.clear();
        assertNull(overlay.ghost());
        AppDrawerCategoryGridMetrics metrics = AppDrawerCategoryGridMetrics.resolve(
            1080, 1600, 3, 40, 33, 60, 6 * 1024 * 1024, 3, 48);
        assertTrue(metrics.chargedPreviewBytes() <= 6L * 1024 * 1024 * 60 / 100);
    }
}
