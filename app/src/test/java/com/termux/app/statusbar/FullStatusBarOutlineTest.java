package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FullStatusBarOutlineTest {
    @Test public void fullPaneOutlineInterpolatesAndClipsProductionGlassHost() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        View host = activity.findViewById(R.id.terminal_window_bar_host);
        View blur = activity.findViewById(R.id.terminal_window_bar_blur);
        StatusBarSurfaceOutlineProvider outline = ReflectionHelpers.getField(activity,
            "mStatusBarSurfaceOutline");

        apply(activity, host, 0f);
        assertEquals(0f, outline.radiusPx(), 0.001f);
        assertFalse(host.getClipToOutline());

        apply(activity, host, 0.5f);
        assertTrue(outline.radiusPx() > 0f);
        assertTrue(outline.radiusPx() < outline.fullRadiusPx());
        assertEquals(outline.fullRadiusPx() * 0.5f, outline.radiusPx(), 0.001f);
        assertSame(outline, host.getOutlineProvider());
        assertTrue("the production host must clip its live-blur child with the animated outline",
            host.getClipToOutline() && blur.getParent() == host);

        apply(activity, host, 1f);
        assertEquals(outline.fullRadiusPx(), outline.radiusPx(), 0.001f);
        assertTrue(host.getClipToOutline());

        apply(activity, host, 0f);
        assertEquals(0f, outline.radiusPx(), 0.001f);
        assertFalse("settling closed must restore Default's normal square surface",
            host.getClipToOutline());
    }

    private static void apply(TermuxActivity activity, View host, float progress) {
        ReflectionHelpers.callInstanceMethod(activity, "applyFullStatusBarOutline",
            ReflectionHelpers.ClassParameter.from(View.class, host),
            ReflectionHelpers.ClassParameter.from(float.class, progress));
    }
}
