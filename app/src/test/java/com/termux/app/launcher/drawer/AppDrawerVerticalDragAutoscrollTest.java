package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerVerticalDragAutoscrollTest {
    @Test public void fortyEightDpZonesAreProportionalBoundedAndZeroOutside() {
        assertEquals(0f, AppDrawerDragPolicy.verticalAutoscrollVelocity(50, 500, 1), 0f);
        float halfTop = AppDrawerDragPolicy.verticalAutoscrollVelocity(24, 500, 1);
        float edgeTop = AppDrawerDragPolicy.verticalAutoscrollVelocity(0, 500, 1);
        assertEquals(-900f, halfTop, 0.01f);
        assertEquals(-AppDrawerDragPolicy.MAX_AUTOSCROLL_PX_PER_SEC, edgeTop, 0.01f);
        assertEquals(900f, AppDrawerDragPolicy.verticalAutoscrollVelocity(476, 500, 1), 0.01f);
        assertEquals(AppDrawerDragPolicy.MAX_AUTOSCROLL_PX_PER_SEC,
            AppDrawerDragPolicy.verticalAutoscrollVelocity(500, 500, 1), 0.01f);
    }
}
