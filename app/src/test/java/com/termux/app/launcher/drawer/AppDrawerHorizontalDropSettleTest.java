package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerHorizontalDropSettleTest {
    @Test public void dwellRejectsDropsUntilIdleAndResolvesOnlySettledPageStableId() {
        AppDrawerDragPolicy.HorizontalDropGate gate =
            new AppDrawerDragPolicy.HorizontalDropGate();
        gate.onNavigationStarted();
        assertFalse(gate.canDrop());
        gate.onScrollStateChanged(androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_SETTLING);
        assertFalse(gate.canDrop());
        gate.onScrollStateChanged(androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE);
        assertTrue(gate.canDrop());

        AppDrawerHorizontalPageAdapter adapter = new AppDrawerHorizontalPageAdapter(null);
        adapter.setMetrics(AppDrawerHorizontalGridMetrics.resolve(400, 200, 1, 11, 4, 2));
        List<LauncherAppEntry> entries = new ArrayList<>();
        for (int i = 0; i < 9; i++) entries.add(new LauncherAppEntry(
            new AppRef("p" + i, "Main"), "P" + i, null));
        adapter.submit(entries);
        assertNull(adapter.itemOnPageByStableId(1, "p0/Main"));
        assertNotNull(adapter.itemOnPageByStableId(1, "p8/Main"));
    }
}
