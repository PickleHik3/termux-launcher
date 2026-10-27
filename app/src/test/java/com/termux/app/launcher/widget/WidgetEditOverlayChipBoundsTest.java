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

    // The editing tab ("4 x 5 / tick / cross / ?") is out of the top-trailing corner for the whole
    // session and takes the taps under it, so the chips of a widget there must clear it.

    @Test public void aTopRightCellsChipsDropBelowTheEditingTab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        int pad = px(activity, 6f);
        Rect tab = new Rect(PANE - px(activity, 150f), 0, PANE, px(activity, 32f));
        overlay.setReservedArea(out -> { out.set(tab); return true; });

        // One column at the top right, the clock in the developer's screenshot.
        Rect clock = new Rect(PANE - px(activity, 60f), pad, PANE - pad, PANE / 2);
        RecordingListener listener = new RecordingListener();
        overlay.setListener(listener);
        overlay.show(clock, true, true, java.util.Collections.emptyList(), true);

        assertClearOf(tab, overlay.chipCenterX(), overlay.chipCenterY(), overlay.chipRadius());
        assertClearOf(tab, overlay.settingsCenterX(), overlay.settingsCenterY(),
            overlay.chipRadius());
        assertEquals("the pair stays on one row", overlay.chipCenterY(),
            overlay.settingsCenterY(), 0.01f);
        assertTrue("still on the widget", overlay.chipCenterY() < clock.bottom);
        assertChipInsidePane(overlay);

        tap(overlay, overlay.chipCenterX(), overlay.chipCenterY());
        assertEquals(1, listener.removes);
        tap(overlay, overlay.settingsCenterX(), overlay.settingsCenterY());
        assertEquals(1, listener.configures);
    }

    @Test public void aTopRowCellPartlyUnderTheTabDropsItsWholePair() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        int pad = px(activity, 6f);
        Rect tab = new Rect(PANE - px(activity, 150f), 0, PANE, px(activity, 32f));
        overlay.setReservedArea(out -> { out.set(tab); return true; });

        // The battery: its left-hung remove chip is clear, but the cog beside it is under the tab.
        Rect battery = new Rect(tab.left - px(activity, 40f), pad, tab.left + px(activity, 40f),
            PANE / 2);
        overlay.show(battery, true, true, java.util.Collections.emptyList(), true);

        assertClearOf(tab, overlay.chipCenterX(), overlay.chipCenterY(), overlay.chipRadius());
        assertClearOf(tab, overlay.settingsCenterX(), overlay.settingsCenterY(),
            overlay.chipRadius());
        assertEquals(overlay.chipCenterY(), overlay.settingsCenterY(), 0.01f);
    }

    @Test public void chipsTheTabDoesNotCoverStayWhereTheyWere() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        int pad = px(activity, 6f);
        Rect topRow = new Rect(pad, pad, PANE - pad, PANE / 2);
        Rect interior = new Rect(PANE / 4, PANE / 4, PANE / 2, PANE / 2);

        overlay.show(topRow, true, true, java.util.Collections.emptyList(), true);
        float topX = overlay.chipCenterX(), topY = overlay.chipCenterY();
        overlay.show(interior, true, true, java.util.Collections.emptyList(), true);
        float interiorX = overlay.chipCenterX(), interiorY = overlay.chipCenterY();

        Rect tab = new Rect(PANE - px(activity, 150f), 0, PANE, px(activity, 32f));
        overlay.setReservedArea(out -> { out.set(tab); return true; });
        overlay.show(topRow, true, true, java.util.Collections.emptyList(), true);
        assertEquals(topX, overlay.chipCenterX(), 0.01f);
        assertEquals(topY, overlay.chipCenterY(), 0.01f);
        overlay.show(interior, true, true, java.util.Collections.emptyList(), true);
        assertEquals(interiorX, overlay.chipCenterX(), 0.01f);
        assertEquals(interiorY, overlay.chipCenterY(), 0.01f);

        // A tab that has gone away reserves nothing.
        Rect clock = new Rect(PANE - px(activity, 60f), pad, PANE - pad, PANE / 2);
        overlay.setReservedArea(out -> false);
        overlay.show(clock, true, true);
        assertEquals(clock.top + overlay.chipRadius() + 2f
            * activity.getResources().getDisplayMetrics().density, overlay.chipCenterY(), 0.01f);
    }

    // A move or resize the page refuses tints the widgets that would not make room.

    @Test public void aRefusalTintsItsBlockersUntilTheFingerLetsGoThenFades() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        overlay.show(new Rect(0, 0, PANE / 2, PANE / 2), true, true);
        Rect blocker = new Rect(0, PANE / 2, PANE / 2, PANE);

        overlay.showRefusal(java.util.Collections.singletonList(blocker));
        assertEquals(java.util.Collections.singletonList(blocker), overlay.refusalBounds());
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            WidgetEditOverlayView.REFUSAL_FADE_MS * 2, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("held while the finger still asks", 1, overlay.refusalBounds().size());

        overlay.flashRefusal();
        assertEquals("the fade starts from the full tint", 1, overlay.refusalBounds().size());
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            WidgetEditOverlayView.REFUSAL_FADE_MS + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(overlay.refusalBounds().isEmpty());
    }

    @Test public void anAskThatIsGrantedClearsTheTint() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetEditOverlayView overlay = laidOutOverlay(activity);
        overlay.show(new Rect(0, 0, PANE / 2, PANE / 2), true, true);
        overlay.showRefusal(java.util.Collections.singletonList(new Rect(0, 200, 200, 400)));

        overlay.showRefusal(java.util.Collections.emptyList());
        assertTrue(overlay.refusalBounds().isEmpty());

        overlay.flashRefusal();
        assertTrue("nothing refused, nothing flashes", overlay.refusalBounds().isEmpty());

        overlay.showRefusal(java.util.Collections.singletonList(new Rect(0, 200, 200, 400)));
        overlay.hide();
        assertTrue("leaving edit mode takes the tint with it", overlay.refusalBounds().isEmpty());
    }

    private static void assertClearOf(Rect area, float x, float y, float radius) {
        boolean overlaps = x + radius > area.left && x - radius < area.right
            && y + radius > area.top && y - radius < area.bottom;
        assertFalse("chip at " + x + "," + y + " is under " + area, overlaps);
    }

    private static void tap(View view, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50,
            android.view.MotionEvent.ACTION_UP, x, y, 0);
        view.dispatchTouchEvent(down);
        view.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private static final class RecordingListener implements WidgetEditOverlayView.Listener {
        int removes, configures;
        @Override public void onMoveDragStart(float rawX, float rawY) {}
        @Override public void onMoveDragMove(float rawX, float rawY) {}
        @Override public void onMoveDragEnd(boolean canceled) {}
        @Override public void onResizeDrag(WidgetEditPolicy.Handle handle, int desiredEdgePx) {}
        @Override public void onResizeDragEnd() {}
        @Override public void onRemove() { removes++; }
        @Override public void onConfigure() { configures++; }
        @Override public void onSelectWidget(int appWidgetId, float rawX, float rawY) {}
        @Override public void onDismiss() {}
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
