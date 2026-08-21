package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerDragGhostArtworkTest {
    @Test public void pickupUsesBoundIconDrawableAtRenderedIconPixelsNotCellSize() {
        AppDrawerAppCellView cell = new AppDrawerAppCellView(
            RuntimeEnvironment.getApplication());
        cell.setLayoutParams(new ViewGroup.LayoutParams(120, 100));
        cell.applyGeometry(100, 40);
        ColorDrawable icon = new ColorDrawable(0xff123456);
        cell.icon.setImageDrawable(icon);

        AppDrawerDragController.PickupArtwork artwork =
            AppDrawerDragController.pickupArtwork(cell);

        assertSame(icon, artwork.drawable);
        assertEquals(40, artwork.sizePx);
    }
}
