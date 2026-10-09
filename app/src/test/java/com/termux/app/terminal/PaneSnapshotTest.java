package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * What a frozen pane may show. A copy has no ground, plate or square corner of its own: it is the
 * pane's slab cut at the slab's radius, or, below API 29 and on a software canvas, the same cut
 * applied when the bitmap is drawn.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@GraphicsMode(GraphicsMode.Mode.NATIVE) // clipPath needs real path geometry.
public class PaneSnapshotTest {

    @Test
    public void aDisplayListNeedsApi29AndAHardwareWindow() {
        assertEquals(PaneSnapshot.Route.RENDER_NODE,
            PaneSnapshot.route(Build.VERSION_CODES.Q, true));
        assertEquals(PaneSnapshot.Route.RENDER_NODE,
            PaneSnapshot.route(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true));
        assertEquals(PaneSnapshot.Route.BITMAP,
            PaneSnapshot.route(Build.VERSION_CODES.Q, false));
        assertEquals(PaneSnapshot.Route.BITMAP,
            PaneSnapshot.route(Build.VERSION_CODES.P, true));
        assertEquals(PaneSnapshot.Route.BITMAP,
            PaneSnapshot.route(Build.VERSION_CODES.LOLLIPOP, false));
    }

    private static PaneContentFrame solidPane(Activity activity, boolean clipToShape) {
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(400, 400));
        PaneContentFrame frame = new PaneContentFrame(activity);
        frame.setPaneShape(60f, clipToShape);
        View fill = new View(activity);
        fill.setBackgroundColor(Color.RED); // stands in for glass, tint and grain: all rectangles
        frame.addView(fill, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.addView(frame, new FrameLayout.LayoutParams(300, 300));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return frame;
    }

    private static Bitmap drawn(PaneSnapshot snapshot) {
        Bitmap out = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        snapshot.draw(new Canvas(out));
        return out;
    }

    @Test
    public void aSoftwareCopyIsCutToTheSlabsRoundedCorners() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneContentFrame frame = solidPane(activity, true);

        PaneSnapshot snapshot = PaneSnapshot.capture(frame, 60f, null);

        assertNotNull(snapshot);
        assertFalse(snapshot.isRenderNode());
        assertEquals(60f, snapshot.radiusPx(), 0.01f);
        Bitmap out = drawn(snapshot);
        // No rectangle around the slab: the corner is see-through, the body is not.
        assertEquals(0, Color.alpha(out.getPixel(2, 2)));
        assertEquals(0, Color.alpha(out.getPixel(297, 297)));
        assertEquals(255, Color.alpha(out.getPixel(150, 150)));
        // Straight edges reach the frame's edge.
        assertEquals(255, Color.alpha(out.getPixel(150, 1)));
        snapshot.release();
    }

    @Test
    public void aFrameThatDoesNotClipKeepsItsSquareCorners() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneContentFrame frame = solidPane(activity, false);

        PaneSnapshot snapshot = PaneSnapshot.capture(frame, 60f, null);

        assertNotNull(snapshot);
        assertEquals(0f, snapshot.radiusPx(), 0f);
        assertTrue(Color.alpha(drawn(snapshot).getPixel(2, 2)) > 0);
        snapshot.release();
    }

    @Test
    public void aTinyPaneIsCutAtTheCappedRadiusNotTheWindowsRadius() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneContentFrame frame = solidPane(activity, true);
        frame.getLayoutParams().height = 60;
        frame.requestLayout();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        PaneSnapshot snapshot = PaneSnapshot.capture(frame, 60f, null);

        assertNotNull(snapshot);
        assertEquals(PaneShape.radiusForBounds(60f, 300, 60), snapshot.radiusPx(), 0.01f);
        snapshot.release();
    }

    @Test
    public void anUnlaidFrameHasNothingToCopy() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneContentFrame loose = new PaneContentFrame(activity);
        assertTrue(PaneSnapshot.capture(loose, 60f, null) == null);
    }
}
