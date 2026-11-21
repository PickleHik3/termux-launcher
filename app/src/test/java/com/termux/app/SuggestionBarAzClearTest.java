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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/** The chrome's apply asks the row to end its A-Z preview each pass; with none showing it is a no-op. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarAzClearTest {

    private static SuggestionBarView attachedBar() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(1058, 140));
        SuggestionBarView bar = new SuggestionBarView(host.getContext(), null);
        bar.setDockIconSizePx(Math.round(24f * bar.getResources().getDisplayMetrics().density));
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return bar;
    }

    @Test
    public void clearingAPreviewThatIsNotShowingDoesNotReloadTheRow() {
        SuggestionBarView bar = attachedBar();
        bar.reload();
        int before = bar.renderRequestCountForTest();

        bar.clearAzPreviewIfShowing();

        assertEquals("no reload was asked for", before, bar.renderRequestCountForTest());
    }

    @Test
    public void clearingThePreviewOutrightStillReloadsTheRow() {
        SuggestionBarView bar = attachedBar();
        bar.reload();
        int before = bar.renderRequestCountForTest();

        bar.clearAzPreview();

        assertNotEquals(before, bar.renderRequestCountForTest());
    }
}
