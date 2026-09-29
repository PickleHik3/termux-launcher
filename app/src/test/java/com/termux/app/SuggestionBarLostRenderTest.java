package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
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

    private static void layoutHost(FrameLayout host, int heightPx) {
        host.measure(View.MeasureSpec.makeMeasureSpec(1058, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 1058, heightPx);
    }

    @Test
    public void aRenderDroppedAtZeroSizeIsReissuedOnceTheRowIsStable() {
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
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
