package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.dock.DockLayoutPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;

/**
 * One pinned icon is a share of the row's own band, never of the host the row was lent to. Off the
 * dock that host is a plank standing in an edge stack, and a plank measured before it was sized is
 * a whole content column deep — which is how a single icon came to be drawn across the terminal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarIconSizeTest {

    /** The bar lying down in a host of the given height, laid out. */
    private static SuggestionBarView inHostOf(int heightPx) {
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        SuggestionBarView bar = new SuggestionBarView(host.getContext(), null);
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        host.measure(View.MeasureSpec.makeMeasureSpec(1058, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 1058, heightPx);
        return bar;
    }

    private static float density() {
        DisplayMetrics metrics =
            RuntimeEnvironment.getApplication().getResources().getDisplayMetrics();
        return metrics.density;
    }

    @Test
    public void aHostTallerThanARowDoesNotGiveATallerIcon() {
        SuggestionBarView bar = inHostOf(1234);
        bar.setDockRowHeightHintPx(0);
        int ceiling = DockLayoutPolicy.maxRowBandPx(density());
        assertTrue("an icon the height of the terminal: " + bar.iconSizePx(),
            bar.iconSizePx() <= ceiling);
    }

    @Test
    public void theRowsOwnBandIsTheAnswerWhateverTheHostIs() {
        SuggestionBarView tall = inHostOf(1234);
        tall.setDockRowHeightHintPx(140);
        SuggestionBarView row = inHostOf(140);
        row.setDockRowHeightHintPx(140);
        assertTrue("the band decides, not the host",
            tall.iconSizePx() == row.iconSizePx());
        assertTrue(tall.iconSizePx() <= 140);
    }

    @Test
    public void aRailIconIsTheRailsFixedSizeWhateverTheColumnIs() {
        SuggestionBarView rail = inHostOf(2000);
        rail.setVerticalForm(true);
        rail.setDockRowHeightHintPx(0);
        assertTrue(rail.iconSizePx() == DockLayoutPolicy.railIconSizePx(density()));
    }
}
