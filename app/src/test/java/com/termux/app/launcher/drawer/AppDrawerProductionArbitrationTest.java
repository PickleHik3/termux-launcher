package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerProductionArbitrationTest {
    @Test public void contextClaimBlocksPagerAndAtomicallyRefinesToPickupDrag() {
        AppDrawerDragPolicy policy = new AppDrawerDragPolicy(new AppDrawerDragPolicy.FrozenDown(
            AppDrawerViewType.HORIZONTAL, true, true, true, "app/Main"));
        AppDrawerHorizontalPagerView pager = new AppDrawerHorizontalPagerView(
            RuntimeEnvironment.getApplication());
        pager.setClaimGate(policy::claim);
        pager.layout(0, 0, 400, 200);

        long now = 100L;
        pager.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 80, 80, 0));
        assertTrue(policy.claim(AppDrawerDragPolicy.Claim.CONTEXT));
        pager.dispatchTouchEvent(MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE,
            180, 80, 0));

        assertTrue(pager.isHorizontalScrollLocked());
        assertTrue(policy.claim(AppDrawerDragPolicy.Claim.DRAG));
    }
}
