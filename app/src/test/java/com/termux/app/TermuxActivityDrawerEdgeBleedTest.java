package com.termux.app;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * The open drawer reaches the screen's edges: its host gives back the room the root keeps for the
 * bars, and for the camera cutout, which is all the top has while the status bar is hidden (#69).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.TIRAMISU}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityDrawerEdgeBleedTest {

    private static ViewGroup.MarginLayoutParams bleed(WindowInsetsCompat insets) {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        ReflectionHelpers.callInstanceMethod(activity, "applyAppDrawerEdgeBleed",
            ClassParameter.from(WindowInsetsCompat.class, insets));
        View host = activity.findViewById(R.id.app_drawer_host);
        return (ViewGroup.MarginLayoutParams) host.getLayoutParams();
    }

    @Test
    public void theDrawerBleedsUnderTheBars() {
        ViewGroup.MarginLayoutParams margins = bleed(new WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 80, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .build());

        assertEquals(-80, margins.topMargin);
        assertEquals(-48, margins.bottomMargin);
    }

    @Test
    public void withTheStatusBarHiddenItBleedsUnderTheCutout() {
        ViewGroup.MarginLayoutParams margins = bleed(new WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 120, 0, 0))
            .build());

        assertEquals(-120, margins.topMargin);
        assertEquals(0, margins.bottomMargin);
    }
}
