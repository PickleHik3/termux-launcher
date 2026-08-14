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
