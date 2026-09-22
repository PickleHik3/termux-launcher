package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import android.os.Build;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The root's fitsSystemWindows padding must not cover the cutout's horizontal column: the content
 * root and the landscape dock rail account for it themselves, so a root that padded for it too
 * pushed everything a second cutout width inward.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.R)
public class TermuxActivityRootViewInsetsTest {

    @Test public void aCutoutColumnIsNotRootPadding() {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, 63, 0, 0))
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 0, 40))
            .setInsets(Type.displayCutout(), Insets.of(128, 0, 0, 0))
            .build();

        assertEquals(Insets.of(0, 0, 0, 0), TermuxActivityRootView.horizontalRootInsets(insets));
    }

    @Test public void aSideNavigationBarStaysRootPadding() {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 126, 0))
            .setInsets(Type.displayCutout(), Insets.of(0, 0, 126, 0))
            .build();

        assertEquals(Insets.of(0, 0, 126, 0), TermuxActivityRootView.horizontalRootInsets(insets));
    }
}
