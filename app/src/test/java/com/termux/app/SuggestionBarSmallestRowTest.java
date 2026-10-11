package com.termux.app;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.dock.DockLayoutPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Issue #49: Docked at the smallest Icon size hands the row a 55px box on a 420dpi phone, one icon
 * tall. The row waited for a 24dp (63px) box before it rendered, so it never drew its icons.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class,
    qualifiers = "w411dp-h891dp-port-420dpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarSmallestRowTest {

    @Test
    public void aRowAsShortAsDockedsSmallestIconStillRenders() {
        float density = 2.625f;
        int iconPx = DockLayoutPolicy.iconPxForScale(false, DockLayoutPolicy.minUsefulScale(),
            density);
        assertEquals("the reported box", 55, iconPx);

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(density, activity.getResources().getDisplayMetrics().density, 0f);
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(1058, iconPx));
        SuggestionBarView bar = new SuggestionBarView(host.getContext(), null);
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        // What applyDockLayout hands the row: the box is its icon.
        bar.setDockRowHeightHintPx(iconPx);
        bar.setDockIconSizePx(iconPx);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(iconPx, bar.getHeight());

        bar.reload();
        for (int i = 0; i < 12; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertFalse("a row the dock sized renders", bar.isRenderLostWhileUnstable());
    }
}
