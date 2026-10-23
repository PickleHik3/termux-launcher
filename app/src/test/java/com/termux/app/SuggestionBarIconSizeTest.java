package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.DockSurfaceSpec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The row draws exactly the icon the dock hands it, and works none out of its own (#135): not a
 * share of its band, not a share of the host it was lent to, not one held to its own width. A row
 * handed no size draws no icons rather than guess one.
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

    /** A lone row on Lens beads, seven slots across a 1058 px host: one the slot fit binds. */
    private static DockLayout dock() {
        float density = density();
        return DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .appsRowEnabledPref(true)
            .appsRowAlone(true)
            .appsRowPageStripShown(true)
            .surface(DockSurfaceSpec.forStyle(DockStyle.LENS_BEADS))
            .density(density)
            .barHeightScale(DockLayoutPolicy.maxUsefulScale(false))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .rowWidthPx(1058)
            .slotCount(7)
            .build());
    }

    @Test
    public void aRowHandedNoSizeHasNoIcon() {
        SuggestionBarView bar = inHostOf(1234);
        assertEquals("no size, no icon, whatever the host is", 0, bar.iconSizePx());
        bar.setDockRowHeightHintPx(140);
        assertEquals("a band is not an icon", 0, bar.iconSizePx());
    }

    @Test
    public void theRowDrawsExactlyTheSlotIconItIsHanded() {
        DockLayout dock = dock();
        assertTrue(dock.slotIconPx > 0);
        SuggestionBarView tall = inHostOf(1234);
        tall.setDockLayout(dock);
        SuggestionBarView row = inHostOf(dock.appsRowBandPx);
        row.setDockLayout(dock);
        assertEquals("the host does not decide", dock.slotIconPx, tall.iconSizePx());
        assertEquals(dock.slotIconPx, row.iconSizePx());
    }

    @Test
    public void aRailIconIsTheLayoutsRailIconWhateverTheColumnIs() {
        DockLayout dock = dock();
        SuggestionBarView rail = inHostOf(2000);
        rail.setVerticalForm(true);
        rail.setDockLayout(dock);
        assertEquals(dock.railIconSizePx, rail.iconSizePx());
        assertEquals(DockLayoutPolicy.railIconSizePx(density()), rail.iconSizePx());
    }
}
