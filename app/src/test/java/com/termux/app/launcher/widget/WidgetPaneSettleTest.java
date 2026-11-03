package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.app.wall.SettleSpring;

import org.junit.Test;

/** Home pages settle on the wall's spring, not a fixed tween (#153). */
public class WidgetPaneSettleTest {

    private static final float EPS = 1e-4f;

    @Test
    public void thePagesSpringIsTheWalls() {
        float[][] releases = {{-400f, 0f}, {700f, -3000f}, {-120f, 4000f}, {300f, 20_000f}};
        for (float time : new float[] {1f, 0.5f, 2f}) {
            for (float[] r : releases) {
                SettleSpring page = WidgetPaneView.settleSpring(r[0], r[1], time);
                SettleSpring wall = SettleSpring.page(r[0], r[1], time);
                assertSame(SettleSpring.class, page.getClass());
                assertEquals(wall.omega(), page.omega(), EPS);
                assertEquals(wall.durationMs(SettleSpring.PAGE_REST_PX),
                    page.durationMs(SettleSpring.PAGE_REST_PX));
                for (float t = 0f; t < 1f; t += 0.05f) {
                    assertEquals(wall.displacementAt(t), page.displacementAt(t), EPS);
                }
            }
        }
        // From still, the wall's own frequency at Normal speed.
        assertEquals(SettleSpring.PAGE_OMEGA,
            WidgetPaneView.settleSpring(-400f, 0f, 1f).omega(), EPS);
    }

    @Test
    public void aFlickSettlesSoonerThanASlowDrag() {
        long slow = WidgetPaneView.settleSpring(-400f, 0f, 1f).durationMs(SettleSpring.PAGE_REST_PX);
        long flick = WidgetPaneView.settleSpring(-400f, 4000f, 1f)
            .durationMs(SettleSpring.PAGE_REST_PX);
        assertTrue("flick " + flick + " ms, slow " + slow + " ms", flick < slow);
    }

    @Test
    public void theFixedSettleIsGone() {
        try {
            WidgetPaneView.class.getDeclaredField("PAGE_SETTLE_MS");
            fail("the 160 ms tween is back");
        } catch (NoSuchFieldException expected) {
            // The spring decides how long a settle takes.
        }
    }
}
