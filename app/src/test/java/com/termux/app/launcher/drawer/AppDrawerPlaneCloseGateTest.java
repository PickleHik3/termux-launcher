package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * Who owns a downward drag on the open plane.
 *
 * <p>Two behaviours are pinned, and they are the same behaviour seen from both sides. Without a gate
 * the plane is B-1 exactly: every point on it belongs to its own close drag, because in B-1 there was
 * nothing else on the plane to belong to. With a gate that claims the point the plane must not
 * compete at all — not claim late, not obtain a velocity tracker, not begin a drag the grid is
 * already resolving through nested scrolling. That is not a preference: the plane claims at 1.15x
 * slop and a {@code RecyclerView} starts scrolling at 1.0x and kills the plane's interceptor as it
 * does, so a plane that still tried would win or lose according to how fast the finger moved.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerPlaneCloseGateTest {

    private static final float WIDTH = 720f;
    private static final float HEIGHT = 1280f;
    /** Everything below this Y is "the grid" in the tests that install a partial gate. */
    private static final float CHROME_BOTTOM = 200f;

    private AppDrawerPlaneView plane;
    private RecordingCallbacks callbacks;
    private float slop;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        plane = new AppDrawerPlaneView(context);
        callbacks = new RecordingCallbacks();
        plane.setCallbacks(callbacks);
        // Fully open: the whole host rectangle is the plane, so a point is only ever chrome or grid.
        plane.setFrame(new Frame(0f, 0f, WIDTH, HEIGHT), 0f, 1f);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    // ------------------------------------------------------------------ no gate: B-1

    @Test
    public void withNoGateTheB1CloseDragIsUnchanged() {
        press(HEIGHT * 0.5f);
        moveDown(HEIGHT * 0.5f);

        assertEquals(1, callbacks.begins);
        assertTrue(callbacks.updates >= 1);
        // The drag is anchored at the DOWN point, not at the point the claim landed on.
        assertEquals(HEIGHT * 0.5f, callbacks.downRawY, 0.01f);
    }

    @Test
    public void withNoGateACancelledStreamReportsACancel() {
        press(HEIGHT * 0.5f);
        moveDown(HEIGHT * 0.5f);
        cancel();

        assertEquals(1, callbacks.cancels);
        assertEquals(0, callbacks.ends);
    }

    // ------------------------------------------------------------------ gated

    @Test
    public void aGateThatOwnsThePointStopsThePlaneClaimingAnything() {
        plane.setCloseDragGate((x, y) -> true);

        press(HEIGHT * 0.5f);
        moveDown(HEIGHT * 0.5f);
        cancel();

        assertEquals(0, callbacks.begins);
        assertEquals(0, callbacks.updates);
        assertEquals(0, callbacks.cancels);
        assertEquals(0, callbacks.ends);
    }

    @Test
    public void chromeStillBelongsToThePlane() {
        plane.setCloseDragGate((x, y) -> y >= CHROME_BOTTOM);

        press(CHROME_BOTTOM * 0.5f);
        moveDown(CHROME_BOTTOM * 0.5f);

        assertEquals(1, callbacks.begins);
    }

    @Test
    public void ownershipIsSampledAtDownAndSurvivesTheWholeStream() {
        // A gate that would answer differently the second time it is asked. If the plane re-read it
        // mid-stream it would begin a drag the grid is already driving.
        plane.setCloseDragGate(new OneShotGate());

        press(HEIGHT * 0.5f);
        moveDown(HEIGHT * 0.5f);
        moveDown(HEIGHT * 0.5f);

        assertEquals(0, callbacks.begins);
    }

    @Test
    public void aDeferredStreamIsForgottenAndTheNextChromeDragClaims() {
        plane.setCloseDragGate((x, y) -> y >= CHROME_BOTTOM);

        press(HEIGHT * 0.5f);
        moveDown(HEIGHT * 0.5f);
        cancel();
        assertEquals(0, callbacks.begins);

        press(CHROME_BOTTOM * 0.5f);
        moveDown(CHROME_BOTTOM * 0.5f);
        assertEquals(1, callbacks.begins);
    }

    @Test
    public void horizontalPagerIncludingTheFormerColumnDefersWhileChromeStillClaims() {
        AppDrawerContentView content = new AppDrawerContentView(
            RuntimeEnvironment.getApplication());
        content.setViewType(AppDrawerViewType.HORIZONTAL);
        content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(WIDTH, HEIGHT,
            1f, 11f, 4, 2));
        content.setInteractive(true);
        content.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(Math.round(WIDTH),
                android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(Math.round(HEIGHT),
                android.view.View.MeasureSpec.EXACTLY));
        content.layout(0, 0, Math.round(WIDTH), Math.round(HEIGHT));
        plane.setCloseDragGate(content);

        float pagerY = content.getHorizontalPager().getTop() + 20f;
        sendAt(MotionEvent.ACTION_DOWN, WIDTH - 1f, pagerY);
        sendAt(MotionEvent.ACTION_MOVE, WIDTH - 1f, pagerY + (slop * 4f));
        sendAt(MotionEvent.ACTION_CANCEL, WIDTH - 1f, pagerY);
        assertEquals(0, callbacks.begins);

        sendAt(MotionEvent.ACTION_DOWN, WIDTH * 0.5f, 1f);
        sendAt(MotionEvent.ACTION_MOVE, WIDTH * 0.5f, 1f + (slop * 4f));
        assertEquals(1, callbacks.begins);
    }

    // ------------------------------------------------------------------ the content's own claim

    @Test
    public void theContentsCloseDragLandsInTheSameCallbacks() {
        plane.setCloseDragGate((x, y) -> true);
        press(HEIGHT * 0.5f);

        plane.beginCloseDragFromContent(HEIGHT * 0.5f);
        plane.updateCloseDragFromContent(HEIGHT * 0.5f + 40f);
        plane.endCloseDragFromContent(900f);

        assertEquals(1, callbacks.begins);
        assertEquals(1, callbacks.updates);
        assertEquals(1, callbacks.ends);
        assertEquals(900f, callbacks.endVelocity, 0.01f);
        assertEquals(HEIGHT * 0.5f, callbacks.downRawY, 0.01f);
    }

    @Test
    public void theContentCannotClaimAStreamThePlaneIsAlreadyDriving() {
        // Chrome: the plane's own arbiter has the stream. A stray report from the content here would
        // capture the drawer's geometry a second time for one gesture.
        press(HEIGHT * 0.5f);
        moveDown(HEIGHT * 0.5f);
        assertEquals(1, callbacks.begins);

        plane.beginCloseDragFromContent(HEIGHT * 0.5f);
        plane.updateCloseDragFromContent(HEIGHT * 0.5f + 40f);
        plane.endCloseDragFromContent(900f);

        assertEquals(1, callbacks.begins);
        assertEquals(1, callbacks.updates);
        assertEquals(0, callbacks.ends);
    }

    // ------------------------------------------------------------------ plumbing

    private void press(float y) {
        send(MotionEvent.ACTION_DOWN, y);
    }

    /** Far enough past 1.15x slop, and vertical enough, that the arbiter has no excuse. */
    private void moveDown(float downY) {
        send(MotionEvent.ACTION_MOVE, downY + (slop * 4f));
    }

    private void cancel() {
        send(MotionEvent.ACTION_CANCEL, HEIGHT * 0.5f);
    }

    private void send(int action, float y) {
        sendAt(action, WIDTH * 0.5f, y);
    }

    private void sendAt(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        try {
            plane.onInterceptTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** True the first time it is asked and false forever after. */
    private static final class OneShotGate implements AppDrawerPlaneView.CloseDragGate {

        private boolean asked;

        @Override
        public boolean ownsPoint(float x, float y) {
            boolean owns = !asked;
            asked = true;
            return owns;
        }
    }

    private static final class RecordingCallbacks implements AppDrawerPlaneView.Callbacks {

        int begins;
        int updates;
        int ends;
        int cancels;
        float downRawY;
        float endVelocity;

        @Override
        public void onPlaneDragBegin(float downRawY) {
            begins++;
            this.downRawY = downRawY;
        }

        @Override
        public void onPlaneDrag(float rawY) {
            updates++;
        }

        @Override
        public void onPlaneDragEnd(float velocityPxPerSec) {
            ends++;
            endVelocity = velocityPxPerSec;
        }

        @Override
        public void onPlaneDragCancel() {
            cancels++;
        }
    }
}
