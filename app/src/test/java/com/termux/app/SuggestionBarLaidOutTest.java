package com.termux.app;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.DockSurfaceSpec;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Both sides' rows stay laid out and dressed at all times (ADR 0030 section 4): the apps row
 * renders whenever it is asked, into whatever bounds it has, and draws what it holds. It waits
 * for no size, and it reports its rest height from the dock's answer, not from any view around it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarLaidOutTest {

    private static final int MAX_IDLE_STEPS = 12;

    /** Counts the children a draw pass reaches: each drawn child is placed with one translate. */
    private static final class CountingCanvas extends Canvas {
        int translates;

        CountingCanvas(Bitmap bitmap) {
            super(bitmap);
        }

        @Override
        public void translate(float dx, float dy) {
            translates++;
            super.translate(dx, dy);
        }
    }

    private static void idleBounded() {
        for (int i = 0; i < MAX_IDLE_STEPS; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private static DockLayout dock() {
        DisplayMetrics metrics =
            RuntimeEnvironment.getApplication().getResources().getDisplayMetrics();
        float density = metrics.density;
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

    /** A row with pinned apps, in a window, in a host of the given size. */
    private static SuggestionBarView rowInHost(int widthPx, int heightPx) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(widthPx, heightPx));
        SuggestionBarView row = new SuggestionBarView(host.getContext(), null);
        row.setDockLayout(dock());
        host.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        }
        ReflectionHelpers.setField(row, "pinnedItems", new ArrayList<>(items));
        ReflectionHelpers.setField(row, "maxButtonCount", 5);
        ReflectionHelpers.setField(row, "pinnedItemsPerPage", 5);
        return row;
    }

    private static int childDrawsOf(SuggestionBarView row) {
        int w = Math.max(1, row.getWidth());
        int h = Math.max(1, row.getHeight());
        CountingCanvas canvas = new CountingCanvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
        row.draw(canvas);
        return canvas.translates;
    }

    private static void assertBuiltAtOnceAndDrawn(SuggestionBarView row) {
        row.reload();
        idleBounded();
        assertTrue("the slots are built at once, whatever the size", row.getChildCount() > 0);
        assertTrue("the row draws what it holds: " + childDrawsOf(row) + " child draws for "
                + row.getChildCount() + " children",
            childDrawsOf(row) >= row.getChildCount());
    }

    @Test
    public void aRowSmallerThanTheDocksBandStillBuildsAndDrawsItsSlots() {
        // 100 x 30 px: under the old wait's width and height floors and under the dock's hint.
        SuggestionBarView row = rowInHost(100, 30);
        assertTrue("the dock's band is bigger than this row", row.restHeightPx() > 30);
        assertBuiltAtOnceAndDrawn(row);
    }

    @Test
    public void aRowInAZeroSizedHostStillBuildsAndDrawsItsSlots() {
        SuggestionBarView row = rowInHost(0, 0);
        assertBuiltAtOnceAndDrawn(row);
    }

    @Test
    public void theRestHeightIsTheDocksBandWhateverTheParentIs() {
        DockLayout dock = dock();
        assertTrue(dock.appsRowBandHintPx > 0);
        SuggestionBarView small = rowInHost(100, 30);
        SuggestionBarView tall = rowInHost(1058, 1234);
        assertEquals(dock.appsRowBandHintPx, small.restHeightPx());
        assertEquals(dock.appsRowBandHintPx, tall.restHeightPx());
        View parent = (View) tall.getParent();
        parent.getLayoutParams().height = 400;
        parent.requestLayout();
        idleBounded();
        assertEquals("a parent that changes size changes nothing", dock.appsRowBandHintPx,
            tall.restHeightPx());
        tall.setVerticalForm(true);
        assertEquals("standing up there is no rest height", 0, tall.restHeightPx());
    }
}
