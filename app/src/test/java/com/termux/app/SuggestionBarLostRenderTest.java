package com.termux.app;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A render asked for while the dock has no size (Minimal mode hides it, a re-parenting lays it out
 * late) is not forgotten: when the row next gets stable bounds it renders, without a swipe or a
 * settings reload to nudge it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarLostRenderTest {

    /** Gives the host a height through the window's own layout pass, as the chrome does. */
    private static void layoutHost(FrameLayout host, int heightPx) {
        ViewGroup.LayoutParams params = host.getLayoutParams();
        params.height = heightPx;
        host.setLayoutParams(params);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void aRenderDroppedAtZeroSizeIsReissuedOnceTheRowIsStable() {
        // Attached to a window, as the dock always is: a detached row never renders.
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(1058, 0));
        SuggestionBarView bar = new SuggestionBarView(host.getContext(), null);
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        layoutHost(host, 0);

        bar.reload();
        // Spend the bounded retries, as a long stay hidden does.
        for (int i = 0; i < 12; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertTrue("the dropped render is remembered", bar.isRenderLostWhileUnstable());

        layoutHost(host, 140);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse("the layout that made the row stable re-issued the render",
            bar.isRenderLostWhileUnstable());
    }
}
