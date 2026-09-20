package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetEditOverlayChipBoundsTest {
    private static final int PANE = 400;

    @Test public void edgeCellsTuckTheChipInsideTheirFrame() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        // The grid's edge padding is all that separates an outermost cell from the pane rim.
        int pad = px(activity, 8f);

        Rect topRight = new Rect(PANE / 2, pad, PANE - pad, PANE / 2);
        overlay.show(topRight, true, true);
        assertChipInsidePane(overlay);
        assertChipInsideFrame(overlay, topRight);

        Rect topLeft = new Rect(pad, pad, PANE / 2, PANE / 2);
        overlay.show(topLeft, true, true);
        assertChipInsidePane(overlay);
        assertTrue(overlay.chipCenterY() - overlay.chipRadius() >= topLeft.top);

        Rect bottomRight = new Rect(PANE / 2, PANE / 2, PANE - pad, PANE - pad);
        overlay.show(bottomRight, true, true);
        assertChipInsidePane(overlay);
        assertChipInsideFrame(overlay, bottomRight);
    }

    @Test public void interiorCellKeepsTheCornerAnchor() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        float inset = 2f * activity.getResources().getDisplayMetrics().density;

        Rect interior = new Rect(PANE / 4, PANE / 4, PANE / 2, PANE / 2);
        overlay.show(interior, true, true);

        assertEquals(interior.right - inset, overlay.chipCenterX(), 0.01f);
        assertEquals(interior.top + inset, overlay.chipCenterY(), 0.01f);
    }

    @Test public void aTopRowFrameWearsTheChipOnItsLeft() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        float radius = overlay.chipRadius();
        float inset = 2f * activity.getResources().getDisplayMetrics().density;
        int pad = px(activity, 6f);

        // The page's own grid-size tab comes out of the top-right corner and stays out for the
        // whole edit session, so a top-row widget's remove chip cannot live under it.
        Rect topRow = new Rect(pad, pad, PANE - pad, PANE / 2);
        overlay.show(topRow, true, true);

        assertEquals(topRow.left + radius + inset, overlay.chipCenterX(), 0.01f);
        assertTrue("clear of the frame's right edge, where the tab is",
            overlay.chipCenterX() + radius < topRow.right / 2f);
        assertChipInsidePane(overlay);
    }

    @Test public void theCogSitsInwardOfTheRemoveChipOnWhicheverSideItTook() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        int pad = px(activity, 6f);

        Rect topRow = new Rect(pad, pad, PANE - pad, PANE / 2);
        overlay.show(topRow, true, true, java.util.Collections.emptyList(), true);
        assertTrue("a left-hung chip puts the cog to its right",
            overlay.settingsCenterX() > overlay.chipCenterX());
        assertEquals(overlay.chipCenterY(), overlay.settingsCenterY(), 0.01f);

        Rect interior = new Rect(PANE / 4, PANE / 4, PANE / 2, PANE / 2);
        overlay.show(interior, true, true, java.util.Collections.emptyList(), true);
        assertTrue("a right-hung chip puts the cog to its left",
            overlay.settingsCenterX() < overlay.chipCenterX());

        overlay.show(interior, true, true);
        assertFalse("a provider with no settings gets no cog", overlay.hasSettingsChip());
    }

    private static WidgetEditOverlayView laidOutOverlay(Activity activity) {
        WidgetEditOverlayView overlay = new WidgetEditOverlayView(activity);
        int spec = View.MeasureSpec.makeMeasureSpec(PANE, View.MeasureSpec.EXACTLY);
        overlay.measure(spec, spec);
        overlay.layout(0, 0, PANE, PANE);
        return overlay;
    }

    private static void assertChipInsidePane(WidgetEditOverlayView overlay) {
        float radius = overlay.chipRadius();
        assertTrue(overlay.chipCenterX() - radius >= 0f);
        assertTrue(overlay.chipCenterX() + radius <= PANE);
        assertTrue(overlay.chipCenterY() - radius >= 0f);
        assertTrue(overlay.chipCenterY() + radius <= PANE);
    }

    private static void assertChipInsideFrame(WidgetEditOverlayView overlay, Rect frame) {
        assertTrue(overlay.chipCenterX() + overlay.chipRadius() <= frame.right);
    }

    private static int px(Activity activity, float dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
